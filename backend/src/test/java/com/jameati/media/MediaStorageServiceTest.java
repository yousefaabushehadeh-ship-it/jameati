package com.jameati.media;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class MediaStorageServiceTest {
  @TempDir Path directory;

  @Test
  void detectsTheFileSignatureAndDoesNotUseTheSuppliedPath() {
    var repository = mock(MediaAssetRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service = new MediaStorageService(repository, directory.toString(), 1024);
    var bytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};

    var response = service.upload(new MockMultipartFile("file", "../../attack.svg", "image/svg+xml", bytes), "editor@example.com");

    assertThat(response.contentType()).isEqualTo("image/png");
    assertThat(response.originalName()).isEqualTo("attack.svg");
    assertThat(response.publicUrl()).endsWith(".png").doesNotContain("attack");
  }

  @Test
  void rejectsUnsupportedContentEvenWhenMimeTypeClaimsItIsAnImage() {
    var service = new MediaStorageService(mock(MediaAssetRepository.class), directory.toString(), 1024);
    var file = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
    assertThatThrownBy(() -> service.upload(file, "editor@example.com"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("نوع الملف");
  }
}
