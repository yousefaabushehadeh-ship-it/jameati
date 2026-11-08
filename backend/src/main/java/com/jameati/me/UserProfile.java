package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.opportunity.FundingType;
import com.jameati.onboarding.CurrentStatus;
import com.jameati.onboarding.EmploymentType;
import com.jameati.onboarding.StudyMode;
import com.jameati.onboarding.UserGoal;
import com.jameati.onboarding.WorkMode;
import com.jameati.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(length = 160)
  private String headline;

  @Column(length = 1200)
  private String bio;

  @Column(length = 40)
  private String phone;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(length = 100)
  private String country;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String nationality;

  @Column(name = "degree_level", length = 100)
  private String degreeLevel;

  @Column(name = "primary_speciality", length = 180)
  private String primarySpeciality;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(length = 180)
  private String institution;

  @Column(name = "english_level", length = 30)
  private String englishLevel;

  @Column(name = "experience_summary", length = 3000)
  private String experienceSummary;

  @Column(name = "remote_preferred")
  private Boolean remotePreferred;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", length = 40)
  private CurrentStatus currentStatus;

  @Column(name = "current_major", length = 180)
  private String currentMajor;

  @Column(name = "desired_degree", length = 100)
  private String desiredDegree;

  @Column(name = "academic_average", precision = 5, scale = 2)
  private BigDecimal academicAverage;

  @Column(name = "graduation_year")
  private Integer graduationYear;

  @Column(name = "years_of_experience")
  private Integer yearsOfExperience;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_work_mode", length = 30)
  private WorkMode preferredWorkMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_employment_type", length = 30)
  private EmploymentType preferredEmploymentType;

  @Column(name = "minimum_budget", precision = 12, scale = 2)
  private BigDecimal minimumBudget;

  @Column(name = "maximum_budget", precision = 12, scale = 2)
  private BigDecimal maximumBudget;

  @Column(name = "willing_to_relocate")
  private Boolean willingToRelocate;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_study_mode", length = 30)
  private StudyMode preferredStudyMode;

  @Column(name = "target_year")
  private Integer targetYear;

  @Column(name = "onboarding_completed", nullable = false)
  private boolean onboardingCompleted;

  @Column(name = "onboarding_completed_at")
  private Instant onboardingCompletedAt;

  @Column(name = "profile_completion_percentage", nullable = false)
  private int profileCompletionPercentage;

  @Column(name = "recommendation_profile_version", nullable = false)
  private long recommendationProfileVersion;

  @Column(name = "preferred_language", nullable = false, length = 12)
  private String preferredLanguage = "ar";

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_funding", length = 30)
  private FundingType preferredFunding;

  @Column(name = "email_notifications", nullable = false)
  private boolean emailNotifications = true;

  @Column(name = "deadline_reminders", nullable = false)
  private boolean deadlineReminders = true;

  @Column(name = "profile_public", nullable = false)
  private boolean profilePublic;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_profile_categories",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "category", nullable = false, length = 100)
  private Set<String> preferredCategories = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_profile_countries",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "country", nullable = false, length = 100)
  private Set<String> preferredCountries = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_profile_specialities",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "speciality", nullable = false, length = 180)
  private Set<String> preferredSpecialities = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_profile_skills",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "skill", nullable = false, length = 120)
  private Set<String> skills = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_profile_interests",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "interest", nullable = false, length = 120)
  private Set<String> interests = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_profile_goals", joinColumns = @JoinColumn(name = "profile_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "goal", nullable = false, length = 40)
  private Set<UserGoal> goals = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_profile_languages", joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "language", nullable = false, length = 80)
  private Set<String> languages = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_profile_job_titles", joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "job_title", nullable = false, length = 160)
  private Set<String> preferredJobTitles = new LinkedHashSet<>();
}
