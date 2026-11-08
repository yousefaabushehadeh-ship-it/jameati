package com.jameati.media;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_assets", indexes = @Index(name = "idx_media_asset_created", columnList = "deleted,created_at"))
@Getter @Setter @NoArgsConstructor
public class MediaAsset extends BaseEntity {
  @Column(nullable = false, length = 255) private String originalName;
  @Column(nullable = false, unique = true, length = 255) private String storedName;
  @Column(nullable = false, length = 120) private String contentType;
  @Column(nullable = false) private long sizeBytes;
  @Column(nullable = false, length = 500) private String storagePath;
  @Column(nullable = false, length = 500) private String publicUrl;
  @Column(nullable = false, length = 190) private String uploadedBy;
}
