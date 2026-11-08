package com.jameati.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jameati.common.NotFoundException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class MembershipReceiptStorageServiceTest {
  private static final byte[] PNG = new byte[] {
      (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4
  };

  @TempDir
  Path storageDirectory;

  @Test
  void storesReceiptUnderARandomPrivateNameAndReadsItBack() throws Exception {
    var storage = new MembershipReceiptStorageService(storageDirectory.toString(), 1024);
    var file = new MockMultipartFile(
        "receiptImage", "../../receipt.png", "image/png", PNG);

    var stored = storage.store(file);

    assertThat(stored.storedName()).matches("^[0-9a-f-]{36}\\.png$");
    assertThat(stored.originalName()).isEqualTo("receipt.png");
    assertThat(stored.contentType()).isEqualTo("image/png");
    assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
    assertThat(stored.sha256()).hasSize(64);

    var membership = new AnnualMembership();
    membership.setReceiptStoredName(stored.storedName());
    membership.setReceiptOriginalName(stored.originalName());
    membership.setReceiptContentType(stored.contentType());
    membership.setReceiptSizeBytes(stored.sizeBytes());
    var resource = storage.read(membership);

    try (var input = resource.resource().getInputStream()) {
      assertThat(input.readAllBytes()).containsExactly(PNG);
    }
    assertThat(resource.originalName()).isEqualTo("receipt.png");

    storage.discard(stored);
    assertThatThrownBy(() -> storage.read(membership))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void detectsTheActualFileSignatureInsteadOfTrustingTheBrowserMimeType() {
    var storage = new MembershipReceiptStorageService(storageDirectory.toString(), 1024);
    var fake = new MockMultipartFile(
        "receiptImage", "fake.png", "image/png", "not an image".getBytes());

    assertThatThrownBy(() -> storage.store(fake))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JPG وPNG وWEBP");
  }

  @Test
  void rejectsAnOversizedReceiptBeforeWritingIt() {
    var storage = new MembershipReceiptStorageService(storageDirectory.toString(), 8);
    var oversized = new MockMultipartFile(
        "receiptImage", "large.png", "image/png", PNG);

    assertThatThrownBy(() -> storage.store(oversized))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("أكبر من المسموح");
  }
}
