package com.jameati.subscription;

import com.jameati.common.NotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MembershipReceiptStorageService {
  private static final long ABSOLUTE_MAX_SIZE = 20 * 1024 * 1024L;
  private static final Map<String, String> EXTENSIONS = Map.of(
      "image/jpeg", ".jpg",
      "image/png", ".png",
      "image/webp", ".webp");

  private final Path root;
  private final long maxSize;

  public MembershipReceiptStorageService(
      @Value("${app.membership.receipt-storage-path:./data/membership-receipts}") String storagePath,
      @Value("${app.membership.receipt-max-size-bytes:5242880}") long maxSize) {
    this.root = Path.of(storagePath).toAbsolutePath().normalize();
    if (maxSize < 1 || maxSize > ABSOLUTE_MAX_SIZE) {
      throw new IllegalArgumentException("app.membership.receipt-max-size-bytes خارج النطاق الآمن");
    }
    this.maxSize = maxSize;
  }

  StoredReceipt store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("صورة الحوالة مطلوبة");
    }
    if (file.getSize() > maxSize) {
      throw new IllegalArgumentException("حجم صورة الحوالة أكبر من المسموح");
    }

    Path temporary = null;
    try {
      Files.createDirectories(root);
      var detectedType = detectType(file);
      var storedName = UUID.randomUUID() + EXTENSIONS.get(detectedType);
      var target = safePath(storedName);
      temporary = Files.createTempFile(root, ".receipt-", ".tmp");
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = new DigestInputStream(file.getInputStream(), digest)) {
        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
      }
      var actualSize = Files.size(temporary);
      if (actualSize < 1 || actualSize > maxSize) {
        throw new IllegalArgumentException("حجم صورة الحوالة أكبر من المسموح");
      }
      move(temporary, target);
      temporary = null;
      registerRollbackDelete(target);
      return new StoredReceipt(
          storedName,
          safeOriginalName(file.getOriginalFilename()),
          detectedType,
          actualSize,
          HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("تعذر التحقق من صورة الحوالة", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر حفظ صورة الحوالة", exception);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // The temporary file is deliberately best-effort cleanup only.
        }
      }
    }
  }

  StoredReceiptResource read(AnnualMembership membership) {
    if (membership == null || membership.getReceiptStoredName() == null) {
      throw new NotFoundException("صورة الحوالة غير موجودة");
    }
    try {
      var path = safePath(membership.getReceiptStoredName());
      if (!Files.isRegularFile(path)) {
        throw new NotFoundException("صورة الحوالة غير موجودة");
      }
      return new StoredReceiptResource(
          new UrlResource(path.toUri()),
          membership.getReceiptOriginalName(),
          membership.getReceiptContentType(),
          membership.getReceiptSizeBytes() == null
              ? Files.size(path)
              : membership.getReceiptSizeBytes());
    } catch (MalformedURLException exception) {
      throw new NotFoundException("صورة الحوالة غير موجودة");
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر قراءة صورة الحوالة", exception);
    }
  }

  void discard(StoredReceipt receipt) {
    if (receipt == null) return;
    try {
      Files.deleteIfExists(safePath(receipt.storedName()));
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر حذف نسخة صورة الحوالة المكررة", exception);
    }
  }

  private Path safePath(String storedName) {
    if (storedName == null
        || !storedName.matches("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")) {
      throw new NotFoundException("صورة الحوالة غير موجودة");
    }
    var path = root.resolve(storedName).normalize();
    if (!path.getParent().equals(root)) {
      throw new NotFoundException("صورة الحوالة غير موجودة");
    }
    return path;
  }

  private static String detectType(MultipartFile file) throws IOException {
    byte[] header;
    try (var input = file.getInputStream()) {
      header = input.readNBytes(16);
    }
    if (starts(header, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
    if (starts(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "image/png";
    }
    if (header.length >= 12
        && startsText(header, "RIFF")
        && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      return "image/webp";
    }
    throw new IllegalArgumentException(
        "نوع صورة الحوالة غير مدعوم؛ المسموح JPG وPNG وWEBP");
  }

  private static boolean starts(byte[] bytes, int... expected) {
    if (bytes.length < expected.length) return false;
    for (int i = 0; i < expected.length; i++) {
      if ((bytes[i] & 0xff) != expected[i]) return false;
    }
    return true;
  }

  private static boolean startsText(byte[] bytes, String text) {
    return starts(bytes, text.chars().toArray());
  }

  private static String safeOriginalName(String value) {
    if (value == null || value.isBlank()) return "receipt";
    String name;
    try {
      name = Path.of(value.replace('\\', '/')).getFileName().toString();
    } catch (InvalidPathException exception) {
      name = "receipt";
    }
    name = name.replaceAll("[\\p{Cntrl}]", "").trim();
    return name.isBlank() ? "receipt" : name.substring(0, Math.min(255, name.length()));
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target);
    }
  }

  private static void registerRollbackDelete(Path path) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
          try {
            Files.deleteIfExists(path);
          } catch (IOException ignored) {
            // A later maintenance pass may remove an orphan if the file system is unavailable here.
          }
        }
      }
    });
  }

  record StoredReceipt(
      String storedName,
      String originalName,
      String contentType,
      long sizeBytes,
      String sha256) {}

  public record StoredReceiptResource(
      Resource resource,
      String originalName,
      String contentType,
      long sizeBytes) {}
}
