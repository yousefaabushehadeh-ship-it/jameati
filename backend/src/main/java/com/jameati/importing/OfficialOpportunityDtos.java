package com.jameati.importing;

import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.OpportunityStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class OfficialOpportunityDtos {
  private OfficialOpportunityDtos() {}

  public record OfficialOpportunityFeed(
      String version,
      Instant verifiedAt,
      String verifiedBy,
      List<OfficialOpportunityRecord> records) {}

  public record OfficialOpportunityRecord(
      String externalKey,
      String slug,
      String title,
      String shortDescription,
      String description,
      String category,
      String organization,
      String country,
      FundingType funding,
      LocalDate deadline,
      String deadlineText,
      OpportunityStatus statusOverride,
      boolean alwaysOpen,
      boolean featured,
      boolean published,
      String coverImage,
      String officialUrl,
      String applyUrl,
      String sourceName,
      String sourceUrl,
      Set<String> specialities,
      Set<String> nationalities,
      Set<String> degreeLevels) {}

  public enum ImportAction {
    CREATE,
    UPDATE,
    SKIP,
    CONFLICT
  }

  public record PreviewRecord(
      String externalKey,
      String title,
      String organization,
      String country,
      LocalDate deadline,
      String deadlineText,
      String officialUrl,
      ImportAction action,
      String conflictReason) {}

  public record ImportPreview(
      String sourceVersion,
      int total,
      int createCount,
      int updateCount,
      int skipCount,
      int conflictCount,
      List<PreviewRecord> records) {}

  public record ImportRunSummary(
      Long id,
      String sourceName,
      String sourceVersion,
      String resourceType,
      int totalRows,
      int createdRows,
      int updatedRows,
      int skippedRows,
      int failedRows,
      ImportStatus status,
      String errorMessage,
      Instant completedAt,
      Instant createdAt) {
    static ImportRunSummary from(ImportRun run) {
      return new ImportRunSummary(
          run.getId(),
          run.getSourceName(),
          run.getSourceVersion(),
          run.getResourceType(),
          run.getTotalRows(),
          run.getCreatedRows(),
          run.getUpdatedRows(),
          run.getSkippedRows(),
          run.getFailedRows(),
          run.getStatus(),
          run.getErrorMessage(),
          run.getCompletedAt(),
          run.getCreatedAt());
    }
  }
}
