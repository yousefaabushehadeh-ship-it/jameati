package com.jameati.media;

import static com.jameati.media.MediaDtos.*;

import com.jameati.common.NotFoundException;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class MediaStorageService {
  private static final Map<String, String> EXTENSIONS = Map.of(
      "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif",
      "image/webp", ".webp", "application/pdf", ".pdf");

  private final MediaAssetRepository repository;
  private final Path root;
  private final long maxSize;

  public MediaStorageService(
      MediaAssetRepository repository,
      @Value("${app.media.storage-path}") String storagePath,
      @Value("${app.media.max-size-bytes}") long maxSize) {
    this.repository = repository;
    this.root = Path.of(storagePath).toAbsolutePath().normalize();
    if (maxSize < 1 || maxSize > 100 * 1024 * 1024L) {
      throw new IllegalArgumentException("app.media.max-size-bytes خارج النطاق الآمن");
    }
    this.maxSize = maxSize;
  }

  public Page<MediaResponse> list(String query, Pageable pageable) {
    return repository.search(query == null ? "" : query.trim(), pageable).map(MediaResponse::of);
  }

  @Transactional
  public MediaResponse upload(MultipartFile file, String actor) {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("الملف مطلوب");
    if (file.getSize() > maxSize) throw new IllegalArgumentException("حجم الملف أكبر من المسموح");
    try {
      Files.createDirectories(root);
      var detectedType = detectType(file);
      var storedName = UUID.randomUUID() + EXTENSIONS.get(detectedType);
      var target = safePath(storedName);
      var temporary = Files.createTempFile(root, ".upload-", ".tmp");
      try {
        try (var input = file.getInputStream()) {
          Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(temporary) > maxSize) {
          throw new IllegalArgumentException("حجم الملف أكبر من المسموح");
        }
        move(temporary, target);
      } finally {
        Files.deleteIfExists(temporary);
      }
      registerRollbackDelete(target);

      var asset = new MediaAsset();
      asset.setOriginalName(safeOriginalName(file.getOriginalFilename()));
      asset.setStoredName(storedName);
      asset.setContentType(detectedType);
      asset.setSizeBytes(Files.size(target));
      asset.setStoragePath(storedName);
      asset.setPublicUrl("/api/v1/public/media/" + storedName);
      asset.setUploadedBy(actor == null ? "unknown" : actor);
      return MediaResponse.of(repository.save(asset));
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر حفظ الملف", exception);
    }
  }

  public StoredMedia read(String storedName) {
    if (storedName == null || !storedName.matches("^[0-9a-fA-F-]{36}\\.(jpg|png|gif|webp|pdf)$")) {
      throw new NotFoundException("الملف غير موجود");
    }
    var asset = repository.findByStoredNameAndDeletedFalse(storedName)
        .orElseThrow(() -> new NotFoundException("الملف غير موجود"));
    try {
      var path = safePath(asset.getStoragePath());
      if (!Files.isRegularFile(path)) throw new NotFoundException("الملف غير موجود");
      return new StoredMedia(new UrlResource(path.toUri()), asset.getContentType(), asset.getSizeBytes());
    } catch (MalformedURLException exception) {
      throw new NotFoundException("الملف غير موجود");
    }
  }

  @Transactional
  public void delete(Long id) {
    var asset = repository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("الملف غير موجود"));
    try {
      var source = safePath(asset.getStoragePath());
      if (Files.exists(source)) {
        var trash = root.resolve(".trash").normalize();
        Files.createDirectories(trash);
        var destination = trash.resolve(UUID.randomUUID() + "-" + asset.getStoredName()).normalize();
        move(source, destination);
        registerRollbackMove(destination, source);
      }
      asset.setDeleted(true);
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر حذف الملف", exception);
    }
  }

  private Path safePath(String storedName) {
    var path = root.resolve(storedName).normalize();
    if (!path.getParent().equals(root)) throw new IllegalArgumentException("مسار الملف غير صالح");
    return path;
  }

  private static String safeOriginalName(String value) {
    if (value == null || value.isBlank()) return "file";
    String name;
    try {
      name = Path.of(value.replace('\\', '/')).getFileName().toString();
    } catch (InvalidPathException exception) {
      name = "file";
    }
    name = name.replaceAll("[\\p{Cntrl}]", "").trim();
    return name.isBlank() ? "file" : name.substring(0, Math.min(255, name.length()));
  }

  private static String detectType(MultipartFile file) throws IOException {
    byte[] header;
    try (var input = file.getInputStream()) {
      header = input.readNBytes(16);
    }
    if (starts(header, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
    if (starts(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
    if (startsText(header, "GIF87a") || startsText(header, "GIF89a")) return "image/gif";
    if (header.length >= 12 && startsText(header, "RIFF")
        && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
    if (startsText(header, "%PDF-")) return "application/pdf";
    throw new IllegalArgumentException("نوع الملف غير مدعوم؛ المسموح JPG وPNG وGIF وWEBP وPDF");
  }

  private static boolean starts(byte[] bytes, int... expected) {
    if (bytes.length < expected.length) return false;
    for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
    return true;
  }

  private static boolean startsText(byte[] bytes, String text) {
    return starts(bytes, text.chars().toArray());
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
      @Override public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
      }
    });
  }

  private static void registerRollbackMove(Path source, Path target) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK && Files.exists(source)) {
          try { move(source, target); } catch (IOException ignored) { }
        }
      }
    });
  }

  public record StoredMedia(Resource resource, String contentType, long size) {}
}
