package com.jameati.media;

import java.time.Instant;

public final class MediaDtos {
  private MediaDtos() {}

  public record MediaResponse(
      Long id,
      String originalName,
      String contentType,
      long sizeBytes,
      String publicUrl,
      String uploadedBy,
      Instant createdAt) {
    static MediaResponse of(MediaAsset asset) {
      return new MediaResponse(asset.getId(), asset.getOriginalName(), asset.getContentType(),
          asset.getSizeBytes(), asset.getPublicUrl(), asset.getUploadedBy(), asset.getCreatedAt());
    }
  }
}
