package com.jameati.me;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.opportunity.FundingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;

public final class UserSpaceDtos {
  private UserSpaceDtos() {}

  public record ProfileResponse(
      Long id,
      Long userId,
      String fullName,
      String email,
      String headline,
      String bio,
      String phone,
      String avatarUrl,
      String country,
      String city,
      String nationality,
      String degreeLevel,
      String primarySpeciality,
      LocalDate dateOfBirth,
      String institution,
      String englishLevel,
      String experienceSummary,
      Boolean remotePreferred,
      String preferredLanguage,
      FundingType preferredFunding,
      boolean emailNotifications,
      boolean deadlineReminders,
      boolean profilePublic,
      Set<String> preferredCategories,
      Set<String> preferredCountries,
      Set<String> preferredSpecialities,
      Set<String> skills,
      Set<String> interests,
      Instant updatedAt) {}

  public record ProfileUpdate(
      @Size(max = 120) String fullName,
      @Size(max = 160) String headline,
      @Size(max = 1200) String bio,
      @Size(max = 40) String phone,
      @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String avatarUrl,
      @Size(max = 100) String country,
      @Size(max = 100) String city,
      @Size(max = 100) String nationality,
      @Size(max = 100) String degreeLevel,
      @Size(max = 180) String primarySpeciality,
      @Past LocalDate dateOfBirth,
      @Size(max = 180) String institution,
      @Size(max = 30) @Pattern(regexp = "A1|A2|B1|B2|C1|C2|NATIVE|OTHER") String englishLevel,
      @Size(max = 3000) String experienceSummary,
      Boolean remotePreferred,
      @Pattern(regexp = "ar|en") String preferredLanguage,
      FundingType preferredFunding,
      Boolean emailNotifications,
      Boolean deadlineReminders,
      Boolean profilePublic,
      @Size(max = 12) Set<@NotBlank @Size(max = 100) String> preferredCategories,
      @Size(max = 20) Set<@NotBlank @Size(max = 100) String> preferredCountries,
      @Size(max = 20) Set<@NotBlank @Size(max = 180) String> preferredSpecialities,
      @Size(max = 40) Set<@NotBlank @Size(max = 120) String> skills,
      @Size(max = 30) Set<@NotBlank @Size(max = 120) String> interests) {}

  public record ApplicationCreate(
      @NotNull Long opportunityId,
      ApplicationStatus status,
      @Size(max = 4000) String notes,
      Instant appliedAt,
      Instant nextActionAt) {}

  public record ApplicationUpdate(
      @NotNull ApplicationStatus status,
      @Size(max = 4000) String notes,
      Instant appliedAt,
      Instant nextActionAt) {}

  public record OpportunitySummary(
      Long id,
      String slug,
      String title,
      String organization,
      String country,
      String category,
      FundingType funding,
      LocalDate deadline,
      String deadlineText,
      boolean alwaysOpen,
      String coverImage) {}

  public record ApplicationResponse(
      Long id,
      ApplicationStatus status,
      String notes,
      Instant appliedAt,
      Instant nextActionAt,
      OpportunitySummary opportunity,
      Instant createdAt,
      Instant updatedAt) {}

  public record NotificationResponse(
      Long id,
      String type,
      String title,
      String body,
      String link,
      Instant readAt,
      Instant createdAt) {}

  public record NotificationInbox(Page<NotificationResponse> items, long unreadCount) {}

  public record FollowedOrganizationRequest(
      @NotBlank @Size(max = 180) String organization,
      @Size(max = 100) String country,
      @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String website) {}

  public record FollowedOrganizationResponse(
      Long id,
      Long organizationId,
      String organization,
      String country,
      String website,
      Instant followedAt) {}

  public record RecentlyViewedRequest(@NotNull Long opportunityId) {}

  public record RecentlyViewedResponse(
      Long id, OpportunitySummary opportunity, Instant viewedAt) {}

  public record CvDocumentRequest(
      @NotBlank @Size(max = 180) String title,
      @NotBlank @Pattern(regexp = "ar|en") String language,
      @NotBlank @Size(max = 60) String templateKey,
      @NotNull JsonNode content,
      boolean primaryDocument) {}

  public record CvDocumentResponse(
      Long id,
      String title,
      String language,
      String templateKey,
      JsonNode content,
      boolean primaryDocument,
      Instant createdAt,
      Instant updatedAt) {}

  public record PersonalityResultRequest(
      @NotBlank @Pattern(regexp = "[IE][NS][TF][JP]") String personalityType,
      @NotNull JsonNode scores,
      JsonNode answers,
      Instant completedAt) {}

  public record PersonalityResultResponse(
      Long id,
      String personalityType,
      JsonNode scores,
      JsonNode answers,
      Instant completedAt,
      boolean latestResult) {}

  public record RecommendationResponse(
      OpportunitySummary opportunity, int score, List<String> reasons) {}
}
