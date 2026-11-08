package com.jameati.onboarding;

import com.jameati.opportunity.FundingType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public final class OnboardingDtos {
  private OnboardingDtos() {}

  public record OnboardingResponse(
      CurrentStatus currentStatus,
      String educationLevel,
      String institution,
      String currentMajor,
      String desiredDegree,
      BigDecimal academicAverage,
      Integer graduationYear,
      String nationality,
      String countryOfResidence,
      String englishLevel,
      Integer yearsOfExperience,
      WorkMode preferredWorkMode,
      EmploymentType preferredEmploymentType,
      BigDecimal minimumBudget,
      BigDecimal maximumBudget,
      Boolean willingToRelocate,
      StudyMode preferredStudyMode,
      Integer targetYear,
      FundingType preferredFunding,
      Set<UserGoal> goals,
      Set<String> preferredCountries,
      Set<String> preferredFields,
      Set<String> skills,
      Set<String> interests,
      Set<String> languages,
      Set<String> preferredJobTitles,
      boolean onboardingCompleted,
      Instant onboardingCompletedAt,
      int profileCompletionPercentage,
      long profileVersion) {}

  public record OnboardingUpdateRequest(
      CurrentStatus currentStatus,
      @Size(max = 100) String educationLevel,
      @Size(max = 180) String institution,
      @Size(max = 180) String currentMajor,
      @Size(max = 100) String desiredDegree,
      @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal academicAverage,
      @Min(1950) @Max(2200) Integer graduationYear,
      @Size(max = 100) String nationality,
      @Size(max = 100) String countryOfResidence,
      @Size(max = 30) String englishLevel,
      @Min(0) @Max(80) Integer yearsOfExperience,
      WorkMode preferredWorkMode,
      EmploymentType preferredEmploymentType,
      @DecimalMin("0.00") BigDecimal minimumBudget,
      @DecimalMin("0.00") BigDecimal maximumBudget,
      Boolean willingToRelocate,
      StudyMode preferredStudyMode,
      @Min(2020) @Max(2200) Integer targetYear,
      FundingType preferredFunding,
      @Size(max = 8) Set<UserGoal> goals,
      @Size(max = 20) Set<@NotBlank @Size(max = 100) String> preferredCountries,
      @Size(max = 20) Set<@NotBlank @Size(max = 180) String> preferredFields,
      @Size(max = 40) Set<@NotBlank @Size(max = 120) String> skills,
      @Size(max = 30) Set<@NotBlank @Size(max = 120) String> interests,
      @Size(max = 20) Set<@NotBlank @Size(max = 80) String> languages,
      @Size(max = 20) Set<@NotBlank @Size(max = 160) String> preferredJobTitles) {}
}
