package com.jameati.settings;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "site_settings", indexes = @Index(name = "idx_site_settings_group_public", columnList = "setting_group,public_visible,deleted"))
@Getter @Setter @NoArgsConstructor
public class SiteSetting extends BaseEntity {
  @Column(name = "setting_key", nullable = false, unique = true, length = 190)
  private String key;
  @Column(name = "setting_group", nullable = false, length = 50)
  private String group;
  @Column(nullable = false, columnDefinition = "text")
  private String valueJson;
  @Column(nullable = false)
  private boolean publicVisible;
}
