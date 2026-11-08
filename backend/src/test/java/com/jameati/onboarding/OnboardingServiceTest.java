package com.jameati.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.user.User;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnboardingServiceTest {
  private User user;
  private UserProfile profile;
  private OnboardingService service;

  @BeforeEach
  void setUp() {
    user = new User("student@example.com", "hash", "طالب");
    user.setId(7L);
    profile = new UserProfile();
    profile.setId(9L);
    profile.setUser(user);
    var profiles = mock(UserProfileRepository.class);
    when(profiles.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(profile));
    when(profiles.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    service = new OnboardingService(profiles);
  }

  @Test
  void updateStoresNormalizedGoalsAndPreferences() {
    var result = service.update(user, request(new BigDecimal("100"), new BigDecimal("5000")));

    assertThat(result.currentStatus()).isEqualTo(CurrentStatus.UNIVERSITY_STUDENT);
    assertThat(result.goals()).containsExactlyInAnyOrder(
        UserGoal.FIND_SCHOLARSHIP, UserGoal.FIND_REMOTE_JOB);
    assertThat(result.preferredCountries()).containsExactly("Germany");
    assertThat(result.skills()).containsExactly("Java");
    assertThat(result.profileVersion()).isEqualTo(1);
    assertThat(result.profileCompletionPercentage()).isEqualTo(100);
    assertThat(result.onboardingCompleted()).isFalse();
  }

  @Test
  void completeRequiresStatusAndAtLeastOneGoal() {
    assertThatThrownBy(() -> service.complete(user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("المرحلة الحالية");

    profile.setCurrentStatus(CurrentStatus.GRADUATE);
    assertThatThrownBy(() -> service.complete(user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("هدفًا واحدًا");
  }

  @Test
  void completeMarksOnboardingFinished() {
    service.update(user, request(null, null));

    var result = service.complete(user);

    assertThat(result.onboardingCompleted()).isTrue();
    assertThat(result.onboardingCompletedAt()).isNotNull();
  }

  @Test
  void updateRejectsInvertedBudgetRange() {
    assertThatThrownBy(
            () -> service.update(user, request(new BigDecimal("5000"), new BigDecimal("100"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("الحد الأدنى للميزانية يجب ألا يتجاوز الحد الأعلى");
  }

  @Test
  void profileLookupIsAlwaysScopedToTheAuthenticatedUser() {
    var other = new User("other@example.com", "hash", "Other");
    other.setId(88L);

    assertThatThrownBy(() -> service.get(other))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ملف المستخدم غير موجود");
  }

  private static OnboardingDtos.OnboardingUpdateRequest request(
      BigDecimal minimumBudget, BigDecimal maximumBudget) {
    return new OnboardingDtos.OnboardingUpdateRequest(
        CurrentStatus.UNIVERSITY_STUDENT,
        "BACHELOR",
        "University",
        "Computer Science",
        "MASTER",
        new BigDecimal("88.50"),
        2027,
        "Jordanian",
        "Jordan",
        "B2",
        2,
        WorkMode.REMOTE,
        EmploymentType.FULL_TIME,
        minimumBudget,
        maximumBudget,
        true,
        StudyMode.ON_CAMPUS,
        2028,
        null,
        Set.of(UserGoal.FIND_SCHOLARSHIP, UserGoal.FIND_REMOTE_JOB),
        Set.of(" Germany "),
        Set.of("Software Engineering"),
        Set.of(" Java "),
        Set.of("Technology"),
        Set.of("Arabic", "English"),
        Set.of("Backend Developer"));
  }
}
