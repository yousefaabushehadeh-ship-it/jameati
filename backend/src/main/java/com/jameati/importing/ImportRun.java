package com.jameati.importing;

import com.jameati.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_runs")
@Getter
@Setter
@NoArgsConstructor
public class ImportRun extends BaseEntity {
  @Column(nullable = false, length = 180)
  private String sourceName;

  @Column(nullable = false, length = 80)
  private String sourceVersion;

  @Column(nullable = false, length = 40)
  private String resourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ImportStatus status;

  @Column(nullable = false)
  private int totalRows;

  @Column(nullable = false)
  private int createdRows;

  @Column(nullable = false)
  private int updatedRows;

  @Column(nullable = false)
  private int skippedRows;

  @Column(nullable = false)
  private int failedRows;

  @Column(length = 1000)
  private String errorMessage;

  private Instant completedAt;
}
