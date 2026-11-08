package com.jameati.onboarding;

import static com.jameati.onboarding.OnboardingDtos.*;

import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.user.User;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnboardingService {
  private final UserProfileRepository profiles;

  public OnboardingService(UserProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public OnboardingResponse get(User user) {
    return response(requireProfile(user));
  }

  public OnboardingResponse update(User user, OnboardingUpdateRequest request) {
    var profile = requireProfile(user);
    if (request.minimumBudget() != null && request.maximumBudget() != null
        && request.minimumBudget().compareTo(request.maximumBudget()) > 0) {
      throw new IllegalArgumentException("الحد الأدنى للميزانية يجب ألا يتجاوز الحد الأعلى");
    }
    profile.setCurrentStatus(request.currentStatus());
    profile.setDegreeLevel(clean(request.educationLevel()));
    profile.setInstitution(clean(request.institution()));
    profile.setCurrentMajor(clean(request.currentMajor()));
    profile.setDesiredDegree(clean(request.desiredDegree()));
    profile.setAcademicAverage(request.academicAverage());
    profile.setGraduationYear(request.graduationYear());
    profile.setNationality(clean(request.nationality()));
    profile.setCountry(clean(request.countryOfResidence()));
    profile.setEnglishLevel(clean(request.englishLevel()));
    profile.setYearsOfExperience(request.yearsOfExperience());
    profile.setPreferredWorkMode(request.preferredWorkMode());
    profile.setPreferredEmploymentType(request.preferredEmploymentType());
    profile.setMinimumBudget(request.minimumBudget());
    profile.setMaximumBudget(request.maximumBudget());
    profile.setWillingToRelocate(request.willingToRelocate());
    profile.setPreferredStudyMode(request.preferredStudyMode());
    profile.setTargetYear(request.targetYear());
    profile.setPreferredFunding(request.preferredFunding());
    replace(profile.getGoals(), request.goals());
    replaceStrings(profile.getPreferredCountries(), request.preferredCountries());
    replaceStrings(profile.getPreferredSpecialities(), request.preferredFields());
    replaceStrings(profile.getSkills(), request.skills());
    replaceStrings(profile.getInterests(), request.interests());
    replaceStrings(profile.getLanguages(), request.languages());
    replaceStrings(profile.getPreferredJobTitles(), request.preferredJobTitles());
    profile.setProfileCompletionPercentage(completion(profile));
    profile.setRecommendationProfileVersion(profile.getRecommendationProfileVersion() + 1);
    return response(profiles.save(profile));
  }

  public OnboardingResponse complete(User user) {
    var profile = requireProfile(user);
    if (profile.getCurrentStatus() == null) {
      throw new IllegalArgumentException("المرحلة الحالية مطلوبة لإكمال الإعداد");
    }
    if (profile.getGoals().isEmpty()) {
      throw new IllegalArgumentException("اختر هدفًا واحدًا على الأقل لإكمال الإعداد");
    }
    profile.setOnboardingCompleted(true);
    profile.setOnboardingCompletedAt(Instant.now());
    profile.setProfileCompletionPercentage(completion(profile));
    return response(profiles.save(profile));
  }

  private UserProfile requireProfile(User user) {
    return profiles.findByUserIdAndDeletedFalse(user.getId()).orElseThrow(
        () -> new IllegalStateException("ملف المستخدم غير موجود"));
  }

  private static int completion(UserProfile profile) {
    int completed = 0;
    if (profile.getCurrentStatus() != null) completed++;
    if (!profile.getGoals().isEmpty()) completed++;
    if (profile.getDegreeLevel() != null || profile.getYearsOfExperience() != null) completed++;
    if (profile.getNationality() != null && profile.getCountry() != null) completed++;
    if (!profile.getPreferredCountries().isEmpty()
        || !profile.getPreferredSpecialities().isEmpty()
        || !profile.getSkills().isEmpty()
        || !profile.getInterests().isEmpty()) completed++;
    return completed * 20;
  }

  private static <T> void replace(Set<T> target, Set<T> values) {
    if (values == null) return;
    target.clear();
    target.addAll(values);
  }

  private static void replaceStrings(Set<String> target, Set<String> values) {
    if (values == null) return;
    var cleaned = new LinkedHashSet<String>();
    values.stream().map(OnboardingService::clean).filter(Objects::nonNull).forEach(cleaned::add);
    target.clear();
    target.addAll(cleaned);
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static OnboardingResponse response(UserProfile profile) {
    return new OnboardingResponse(
        profile.getCurrentStatus(), profile.getDegreeLevel(), profile.getInstitution(),
        profile.getCurrentMajor(), profile.getDesiredDegree(), profile.getAcademicAverage(),
        profile.getGraduationYear(), profile.getNationality(), profile.getCountry(),
        profile.getEnglishLevel(), profile.getYearsOfExperience(), profile.getPreferredWorkMode(),
        profile.getPreferredEmploymentType(), profile.getMinimumBudget(), profile.getMaximumBudget(),
        profile.getWillingToRelocate(), profile.getPreferredStudyMode(), profile.getTargetYear(),
        profile.getPreferredFunding(), Set.copyOf(profile.getGoals()),
        Set.copyOf(profile.getPreferredCountries()), Set.copyOf(profile.getPreferredSpecialities()),
        Set.copyOf(profile.getSkills()), Set.copyOf(profile.getInterests()),
        Set.copyOf(profile.getLanguages()), Set.copyOf(profile.getPreferredJobTitles()),
        profile.isOnboardingCompleted(), profile.getOnboardingCompletedAt(),
        profile.getProfileCompletionPercentage(), profile.getRecommendationProfileVersion());
  }
}
