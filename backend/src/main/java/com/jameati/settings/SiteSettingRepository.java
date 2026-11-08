package com.jameati.settings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
  Optional<SiteSetting> findByKeyIgnoreCase(String key);
  Optional<SiteSetting> findByKeyIgnoreCaseAndPublicVisibleTrueAndDeletedFalse(String key);
}
