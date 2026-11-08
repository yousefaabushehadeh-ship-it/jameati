package com.jameati.me;

import static com.jameati.me.UserSpaceDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.common.NotFoundException;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserSpaceService {
  private final UserProfileRepository profiles;
  private final UserApplicationRepository applications;
  private final UserNotificationRepository notifications;
  private final FollowedOrganizationRepository followedOrganizations;
  private final RecentlyViewedOpportunityRepository recentlyViewed;
  private final CvDocumentRepository cvs;
  private final PersonalityResultRepository personalityResults;
  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;
  private final ObjectMapper objectMapper;

  public UserSpaceService(
      UserProfileRepository profiles,
      UserApplicationRepository applications,
      UserNotificationRepository notifications,
      FollowedOrganizationRepository followedOrganizations,
      RecentlyViewedOpportunityRepository recentlyViewed,
      CvDocumentRepository cvs,
      PersonalityResultRepository personalityResults,
      OpportunityRepository opportunities,
      CatalogRepository catalog,
      ObjectMapper objectMapper) {
    this.profiles = profiles;
    this.applications = applications;
    this.notifications = notifications;
    this.followedOrganizations = followedOrganizations;
    this.recentlyViewed = recentlyViewed;
    this.cvs = cvs;
    this.personalityResults = personalityResults;
    this.opportunities = opportunities;
    this.catalog = catalog;
    this.objectMapper = objectMapper;
  }

  public ProfileResponse profile(User user) {
    return profileResponse(profileEntity(user));
  }

  public ProfileResponse updateProfile(User user, ProfileUpdate request) {
    var profile = profileEntity(user);
    if (request.fullName() != null && !request.fullName().isBlank()) {
      user.setFullName(request.fullName().trim());
    }
    profile.setHeadline(clean(request.headline()));
    profile.setBio(clean(request.bio()));
    profile.setPhone(clean(request.phone()));
    profile.setAvatarUrl(clean(request.avatarUrl()));
    profile.setCountry(clean(request.country()));
    profile.setCity(clean(request.city()));
    profile.setNationality(clean(request.nationality()));
    profile.setDegreeLevel(clean(request.degreeLevel()));
    profile.setPrimarySpeciality(clean(request.primarySpeciality()));
    profile.setDateOfBirth(request.dateOfBirth());
    profile.setInstitution(clean(request.institution()));
    profile.setEnglishLevel(clean(request.englishLevel()));
    profile.setExperienceSummary(clean(request.experienceSummary()));
    profile.setRemotePreferred(request.remotePreferred());
    if (request.preferredLanguage() != null) {
      profile.setPreferredLanguage(request.preferredLanguage());
    }
    profile.setPreferredFunding(request.preferredFunding());
    if (request.emailNotifications() != null) {
      profile.setEmailNotifications(request.emailNotifications());
    }
    if (request.deadlineReminders() != null) {
      profile.setDeadlineReminders(request.deadlineReminders());
    }
    if (request.profilePublic() != null) {
      profile.setProfilePublic(request.profilePublic());
    }
    if (request.preferredCategories() != null) {
      profile.setPreferredCategories(cleanSet(request.preferredCategories()));
    }
    if (request.preferredCountries() != null) {
      profile.setPreferredCountries(cleanSet(request.preferredCountries()));
    }
    if (request.preferredSpecialities() != null) {
      profile.setPreferredSpecialities(cleanSet(request.preferredSpecialities()));
    }
    if (request.skills() != null) {
      profile.setSkills(cleanSet(request.skills()));
    }
    if (request.interests() != null) {
      profile.setInterests(cleanSet(request.interests()));
    }
    return profileResponse(profiles.save(profile));
  }

  @Transactional(readOnly = true)
  public Page<ApplicationResponse> applications(User user, Pageable pageable) {
    return applications.findByUserIdAndDeletedFalse(user.getId(), pageable).map(this::applicationResponse);
  }

  public ApplicationResponse createApplication(User user, ApplicationCreate request) {
    var opportunity = opportunity(request.opportunityId());
    var application =
        applications
            .findByUserIdAndOpportunityId(user.getId(), opportunity.getId())
            .orElseGet(UserApplication::new);
    application.setUser(user);
    application.setOpportunity(opportunity);
    application.setDeleted(false);
    application.setStatus(
        request.status() == null ? ApplicationStatus.PLANNING : request.status());
    application.setNotes(clean(request.notes()));
    application.setAppliedAt(request.appliedAt());
    application.setNextActionAt(request.nextActionAt());
    var saved = applications.save(application);
    notify(
        user,
        "APPLICATION",
        "تمت إضافة طلبك",
        "أضيفت " + opportunity.getTitle() + " إلى متتبّع الطلبات.",
        "/opportunities/" + opportunity.getSlug());
    return applicationResponse(saved);
  }

  public ApplicationResponse updateApplication(
      User user, Long id, ApplicationUpdate request) {
    var application = requireApplication(user, id);
    application.setStatus(request.status());
    application.setNotes(clean(request.notes()));
    application.setAppliedAt(request.appliedAt());
    application.setNextActionAt(request.nextActionAt());
    return applicationResponse(application);
  }

  public void deleteApplication(User user, Long id) {
    var application = requireApplication(user, id);
    application.setDeleted(true);
  }

  @Transactional(readOnly = true)
  public NotificationInbox notificationInbox(User user, Pageable pageable) {
    var page =
        notifications
            .findByUserIdAndDeletedFalse(user.getId(), pageable)
            .map(this::notificationResponse);
    return new NotificationInbox(
        page, notifications.countByUserIdAndDeletedFalseAndReadAtIsNull(user.getId()));
  }

  public NotificationResponse readNotification(User user, Long id) {
    var notification = requireNotification(user, id);
    if (notification.getReadAt() == null) {
      notification.setReadAt(Instant.now());
    }
    return notificationResponse(notification);
  }

  public void readAllNotifications(User user) {
    var now = Instant.now();
    notifications
        .findByUserIdAndDeletedFalseAndReadAtIsNull(user.getId())
        .forEach(notification -> notification.setReadAt(now));
  }

  public void deleteNotification(User user, Long id) {
    requireNotification(user, id).setDeleted(true);
  }

  @Transactional(readOnly = true)
  public List<FollowedOrganizationResponse> followedOrganizations(User user) {
    return followedOrganizations
        .findByUserIdAndDeletedFalseOrderByCreatedAtDesc(user.getId()).stream()
        .map(this::followedOrganizationResponse)
        .toList();
  }

  public FollowedOrganizationResponse followOrganization(
      User user, FollowedOrganizationRequest request) {
    var name = request.organization().trim();
    var followed =
        followedOrganizations
            .findByUserIdAndOrganizationIgnoreCase(user.getId(), name)
            .orElseGet(FollowedOrganization::new);
    followed.setUser(user);
    followed.setOrganization(name);
    followed.setCountry(clean(request.country()));
    followed.setWebsite(clean(request.website()));
    followed.setDeleted(false);
    return followedOrganizationResponse(followedOrganizations.save(followed));
  }

  public FollowedOrganizationResponse followOrganization(User user, Long organizationId) {
    var item =
        catalog
            .findByIdAndDeletedFalse(organizationId)
            .filter(candidate -> candidate.getType() == CatalogType.ORGANIZATION)
            .filter(candidate -> candidate.isPublished())
            .orElseThrow(() -> new NotFoundException("الجهة غير موجودة"));
    var followed =
        followedOrganizations
            .findByUserIdAndOrganizationItemId(user.getId(), organizationId)
            .orElseGet(FollowedOrganization::new);
    followed.setUser(user);
    followed.setOrganizationItem(item);
    followed.setOrganization(item.getTitle());
    followed.setCountry(item.getCountry());
    // The catalog detail endpoint owns membership-aware website disclosure.
    followed.setWebsite(null);
    followed.setDeleted(false);
    return followedOrganizationResponse(followedOrganizations.save(followed));
  }

  public void unfollowOrganization(User user, Long id) {
    followedOrganizations
        .findByUserIdAndOrganizationItemId(user.getId(), id)
        .filter(followed -> !followed.isDeleted())
        .or(() -> followedOrganizations.findByIdAndUserIdAndDeletedFalse(id, user.getId()))
        .orElseThrow(() -> new NotFoundException("الجهة المتابَعة غير موجودة"))
        .setDeleted(true);
  }

  @Transactional(readOnly = true)
  public List<RecentlyViewedResponse> recentlyViewed(User user) {
    return recentlyViewed
        .findTop50ByUserIdAndDeletedFalseOrderByViewedAtDesc(user.getId()).stream()
        .map(this::recentlyViewedResponse)
        .toList();
  }

  public RecentlyViewedResponse recordView(User user, RecentlyViewedRequest request) {
    var opportunity = opportunity(request.opportunityId());
    recentlyViewed.recordView(user.getId(), opportunity.getId(), Instant.now());
    var view = recentlyViewed.findByUserIdAndOpportunityId(user.getId(), opportunity.getId())
        .orElseThrow(() -> new IllegalStateException("Recorded view was not found"));
    return recentlyViewedResponse(view);
  }

  public void clearRecentlyViewed(User user) {
    recentlyViewed
        .findByUserIdAndDeletedFalse(user.getId())
        .forEach(view -> view.setDeleted(true));
  }

  @Transactional(readOnly = true)
  public List<CvDocumentResponse> cvs(User user) {
    return cvs.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(user.getId()).stream()
        .map(this::cvResponse)
        .toList();
  }

  public CvDocumentResponse createCv(User user, CvDocumentRequest request) {
    var cv = new CvDocument();
    cv.setUser(user);
    applyCv(user, cv, request);
    return cvResponse(cvs.save(cv));
  }

  public CvDocumentResponse updateCv(User user, Long id, CvDocumentRequest request) {
    var cv = requireCv(user, id);
    applyCv(user, cv, request);
    return cvResponse(cv);
  }

  public void deleteCv(User user, Long id) {
    requireCv(user, id).setDeleted(true);
  }

  @Transactional(readOnly = true)
  public List<PersonalityResultResponse> personalityResults(User user) {
    return personalityResults
        .findByUserIdAndDeletedFalseOrderByCompletedAtDesc(user.getId()).stream()
        .map(this::personalityResponse)
        .toList();
  }

  public PersonalityResultResponse createPersonalityResult(
      User user, PersonalityResultRequest request) {
    personalityResults
        .findByUserIdAndDeletedFalseAndLatestResultTrue(user.getId())
        .forEach(result -> result.setLatestResult(false));
    var result = new PersonalityResult();
    result.setUser(user);
    result.setPersonalityType(request.personalityType().toUpperCase(Locale.ROOT));
    result.setScoresJson(json(request.scores()));
    result.setAnswersJson(request.answers() == null ? null : json(request.answers()));
    result.setCompletedAt(
        request.completedAt() == null ? Instant.now() : request.completedAt());
    result.setLatestResult(true);
    return personalityResponse(personalityResults.save(result));
  }

  public void deletePersonalityResult(User user, Long id) {
    var result =
        personalityResults
            .findByIdAndUserIdAndDeletedFalse(id, user.getId())
            .orElseThrow(() -> new NotFoundException("نتيجة الشخصية غير موجودة"));
    var wasLatest = result.isLatestResult();
    result.setDeleted(true);
    result.setLatestResult(false);
    if (wasLatest) {
      personalityResults
          .findByUserIdAndDeletedFalseOrderByCompletedAtDesc(user.getId()).stream()
          .filter(candidate -> !Objects.equals(candidate.getId(), id))
          .findFirst()
          .ifPresent(candidate -> candidate.setLatestResult(true));
    }
  }

  @Transactional(readOnly = true)
  public List<RecommendationResponse> recommendations(User user, int requestedLimit) {
    var profile = profiles.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
    int limit = Math.max(1, Math.min(requestedLimit, 50));
    var candidates =
        opportunities
            .findAll(
                (root, query, cb) ->
                    cb.and(cb.isTrue(root.get("published")), cb.isFalse(root.get("deleted"))),
                PageRequest.of(0, 250, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent();
    return candidates.stream()
        .filter(this::available)
        .map(opportunity -> score(profile, opportunity))
        .sorted(
            Comparator.comparingInt(RecommendationResponse::score)
                .reversed()
                .thenComparing(
                    item -> item.opportunity().deadline(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(limit)
        .toList();
  }

  public void notify(
      User user, String type, String title, String body, String link) {
    var notification = new UserNotification();
    notification.setUser(user);
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setLink(link);
    notifications.save(notification);
  }

  private UserProfile profileEntity(User user) {
    return profiles
        .findByUserIdAndDeletedFalse(user.getId())
        .orElseGet(
            () -> {
              var profile = new UserProfile();
              profile.setUser(user);
              return profiles.save(profile);
            });
  }

  private UserApplication requireApplication(User user, Long id) {
    return applications
        .findByIdAndUserIdAndDeletedFalse(id, user.getId())
        .orElseThrow(() -> new NotFoundException("طلب التقديم غير موجود"));
  }

  private UserNotification requireNotification(User user, Long id) {
    return notifications
        .findByIdAndUserIdAndDeletedFalse(id, user.getId())
        .orElseThrow(() -> new NotFoundException("الإشعار غير موجود"));
  }

  private CvDocument requireCv(User user, Long id) {
    return cvs
        .findByIdAndUserIdAndDeletedFalse(id, user.getId())
        .orElseThrow(() -> new NotFoundException("السيرة الذاتية غير موجودة"));
  }

  private Opportunity opportunity(Long id) {
    return opportunities
        .findByIdAndDeletedFalse(id)
        .filter(Opportunity::isPublished)
        .orElseThrow(() -> new NotFoundException("الفرصة غير موجودة"));
  }

  private void applyCv(User user, CvDocument cv, CvDocumentRequest request) {
    if (request.primaryDocument()) {
      cvs.findByUserIdAndDeletedFalseAndPrimaryDocumentTrue(user.getId()).stream()
          .filter(existing -> !Objects.equals(existing.getId(), cv.getId()))
          .forEach(existing -> existing.setPrimaryDocument(false));
    }
    cv.setTitle(request.title().trim());
    cv.setLanguage(request.language());
    cv.setTemplateKey(request.templateKey().trim());
    cv.setContentJson(json(request.content()));
    cv.setPrimaryDocument(request.primaryDocument());
  }

  private ProfileResponse profileResponse(UserProfile profile) {
    var user = profile.getUser();
    return new ProfileResponse(
        profile.getId(),
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        profile.getHeadline(),
        profile.getBio(),
        profile.getPhone(),
        profile.getAvatarUrl(),
        profile.getCountry(),
        profile.getCity(),
        profile.getNationality(),
        profile.getDegreeLevel(),
        profile.getPrimarySpeciality(),
        profile.getDateOfBirth(),
        profile.getInstitution(),
        profile.getEnglishLevel(),
        profile.getExperienceSummary(),
        profile.getRemotePreferred(),
        profile.getPreferredLanguage(),
        profile.getPreferredFunding(),
        profile.isEmailNotifications(),
        profile.isDeadlineReminders(),
        profile.isProfilePublic(),
        Set.copyOf(profile.getPreferredCategories()),
        Set.copyOf(profile.getPreferredCountries()),
        Set.copyOf(profile.getPreferredSpecialities()),
        Set.copyOf(profile.getSkills()),
        Set.copyOf(profile.getInterests()),
        profile.getUpdatedAt());
  }

  private ApplicationResponse applicationResponse(UserApplication application) {
    return new ApplicationResponse(
        application.getId(),
        application.getStatus(),
        application.getNotes(),
        application.getAppliedAt(),
        application.getNextActionAt(),
        opportunitySummary(application.getOpportunity()),
        application.getCreatedAt(),
        application.getUpdatedAt());
  }

  private NotificationResponse notificationResponse(UserNotification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getBody(),
        notification.getLink(),
        notification.getReadAt(),
        notification.getCreatedAt());
  }

  private FollowedOrganizationResponse followedOrganizationResponse(
      FollowedOrganization followed) {
    return new FollowedOrganizationResponse(
        followed.getId(),
        followed.getOrganizationItem() == null ? null : followed.getOrganizationItem().getId(),
        followed.getOrganization(),
        followed.getCountry(),
        followed.getOrganizationItem() == null ? followed.getWebsite() : null,
        followed.getCreatedAt());
  }

  private RecentlyViewedResponse recentlyViewedResponse(RecentlyViewedOpportunity view) {
    return new RecentlyViewedResponse(
        view.getId(), opportunitySummary(view.getOpportunity()), view.getViewedAt());
  }

  private CvDocumentResponse cvResponse(CvDocument cv) {
    return new CvDocumentResponse(
        cv.getId(),
        cv.getTitle(),
        cv.getLanguage(),
        cv.getTemplateKey(),
        tree(cv.getContentJson()),
        cv.isPrimaryDocument(),
        cv.getCreatedAt(),
        cv.getUpdatedAt());
  }

  private PersonalityResultResponse personalityResponse(PersonalityResult result) {
    return new PersonalityResultResponse(
        result.getId(),
        result.getPersonalityType(),
        tree(result.getScoresJson()),
        result.getAnswersJson() == null ? null : tree(result.getAnswersJson()),
        result.getCompletedAt(),
        result.isLatestResult());
  }

  private OpportunitySummary opportunitySummary(Opportunity opportunity) {
    return new OpportunitySummary(
        opportunity.getId(),
        opportunity.getSlug(),
        opportunity.getTitle(),
        opportunity.getOrganization(),
        opportunity.getCountry(),
        opportunity.getCategory(),
        opportunity.getFunding(),
        opportunity.getDeadline(),
        opportunity.getDeadlineText(),
        opportunity.isAlwaysOpen(),
        opportunity.getCoverImage());
  }

  private RecommendationResponse score(UserProfile profile, Opportunity opportunity) {
    int score = opportunity.isFeatured() ? 1 : 0;
    var reasons = new ArrayList<String>();
    if (profile != null) {
      if (contains(profile.getPreferredCategories(), opportunity.getCategory())) {
        score += 5;
        reasons.add("يناسب فئاتك المفضلة");
      }
      if (contains(profile.getPreferredCountries(), opportunity.getCountry())) {
        score += 3;
        reasons.add("في بلد تفضّله");
      }
      if (profile.getPreferredFunding() != null
          && profile.getPreferredFunding() == opportunity.getFunding()) {
        score += 3;
        reasons.add("نوع التمويل مطابق لتفضيلك");
      }
      if (intersects(profile.getPreferredSpecialities(), opportunity.getSpecialities())
          || contains(opportunity.getSpecialities(), profile.getPrimarySpeciality())) {
        score += 6;
        reasons.add("متوافق مع تخصصك");
      }
      if (contains(opportunity.getDegreeLevels(), profile.getDegreeLevel())) {
        score += 4;
        reasons.add("مناسب لدرجتك العلمية");
      }
      if (contains(opportunity.getNationalities(), profile.getNationality())
          || contains(opportunity.getNationalities(), "ALL")) {
        score += 2;
        reasons.add("أهليتك الجغرافية متوافقة");
      }
    }
    if (reasons.isEmpty()) {
      reasons.add(opportunity.isFeatured() ? "فرصة مختارة من المحررين" : "فرصة متاحة حديثًا");
    }
    return new RecommendationResponse(opportunitySummary(opportunity), score, List.copyOf(reasons));
  }

  private boolean available(Opportunity opportunity) {
    return opportunity.isAlwaysOpen()
        || opportunity.getDeadline() == null
        || !opportunity.getDeadline().isBefore(LocalDate.now());
  }

  private static boolean contains(Set<String> values, String wanted) {
    if (values == null || wanted == null) {
      return false;
    }
    return values.stream().anyMatch(value -> value.equalsIgnoreCase(wanted));
  }

  private static boolean intersects(Set<String> first, Set<String> second) {
    if (first == null || second == null) {
      return false;
    }
    return first.stream().anyMatch(value -> contains(second, value));
  }

  private static Set<String> cleanSet(Set<String> values) {
    var cleaned = new LinkedHashSet<String>();
    values.stream()
        .map(UserSpaceService::clean)
        .filter(Objects::nonNull)
        .forEach(cleaned::add);
    return cleaned;
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("محتوى JSON غير صالح", exception);
    }
  }

  private JsonNode tree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("تعذر قراءة محتوى JSON المحفوظ", exception);
    }
  }
}
