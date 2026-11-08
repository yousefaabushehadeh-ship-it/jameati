package com.jameati.ai;

import static com.jameati.ai.AiRecommendationDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.me.CvDocument;
import com.jameati.me.CvDocumentRepository;
import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.subscription.ContentAccessService;
import com.jameati.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AiRecommendationService {
  private static final int MAX_SOURCE_CANDIDATES = 250;
  private static final Pattern LIST_SEPARATOR = Pattern.compile("[,،;|\\n]+");
  private static final Set<String> REMOTE_TERMS = Set.of(
      "remote", "work from home", "عن بعد", "العمل من المنزل", "عن بُعد");
  private static final Set<String> ONSITE_TERMS = Set.of(
      "onsite", "on-site", "in person", "حضوري", "في المكتب", "هجين", "hybrid");

  private final UserProfileRepository profiles;
  private final CvDocumentRepository cvs;
  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;
  private final ContentAccessService contentAccess;
  private final ObjectMapper mapper;

  public AiRecommendationService(
      UserProfileRepository profiles,
      CvDocumentRepository cvs,
      OpportunityRepository opportunities,
      CatalogRepository catalog,
      ContentAccessService contentAccess,
      ObjectMapper mapper) {
    this.profiles = profiles;
    this.cvs = cvs;
    this.opportunities = opportunities;
    this.catalog = catalog;
    this.contentAccess = contentAccess;
    this.mapper = mapper;
  }

  public RecommendationBundle localRecommendations(User user, int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, 24));
    var profile = profiles.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
    var cvSignals = cvSignals(user.getId());
    var signals = ProfileSignals.from(profile, cvSignals);
    var missing = missingProfileFields(signals);
    var access = contentAccess.resolve(user);
    var ranked = sourceCandidates(access).stream()
        .map(candidate -> score(signals, candidate))
        .sorted(Comparator.comparingInt(RankedCandidate::score).reversed()
            .thenComparing(item -> item.source().deadline(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(item -> item.source().createdAt(), Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limit)
        .map(this::fallbackItem)
        .toList();
    String notice = missing.isEmpty()
        ? "تمت المطابقة محليًا من بيانات ملفك والفرص المنشورة في جامعتي."
        : "أكمل الحقول الناقصة لرفع دقة المطابقة؛ النسب الحالية إرشادية.";
    return new RecommendationBundle(
        profileCompleteness(missing),
        missing,
        cvSignals.included(),
        Engine.RULES,
        notice,
        Instant.now(),
        ranked);
  }

  ProfileSnapshot snapshot(User user, int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, 24));
    var profile = profiles.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
    var cvSignals = cvSignals(user.getId());
    var signals = ProfileSignals.from(profile, cvSignals);
    var missing = missingProfileFields(signals);
    var access = contentAccess.resolve(user);
    var ranked = sourceCandidates(access).stream()
        .map(candidate -> score(signals, candidate))
        .sorted(Comparator.comparingInt(RankedCandidate::score).reversed()
            .thenComparing(item -> item.source().deadline(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(item -> item.source().createdAt(), Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limit)
        .toList();
    return new ProfileSnapshot(signals, missing, cvSignals.included(), ranked);
  }

  RecommendationBundle fallback(ProfileSnapshot snapshot, String notice) {
    return new RecommendationBundle(
        profileCompleteness(snapshot.missingProfileFields()),
        snapshot.missingProfileFields(),
        snapshot.cvIncluded(),
        Engine.RULES,
        notice,
        Instant.now(),
        snapshot.ranked().stream().map(this::fallbackItem).toList());
  }

  private List<SourceCandidate> sourceCandidates(ContentAccessService.AccessContext access) {
    var candidates = new ArrayList<SourceCandidate>();
    opportunities.findByPublishedTrueAndDeletedFalse(PageRequest.of(
            0, MAX_SOURCE_CANDIDATES, Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent().stream()
        .filter(this::available)
        .map(opportunity -> opportunityCandidate(opportunity, access))
        .forEach(candidates::add);
    catalog.findByTypeAndPublishedTrueAndDeletedFalse(
            CatalogType.JOB,
            PageRequest.of(0, MAX_SOURCE_CANDIDATES, Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent().stream()
        .map(job -> jobCandidate(job, access))
        .forEach(candidates::add);
    return candidates;
  }

  private SourceCandidate opportunityCandidate(
      Opportunity opportunity, ContentAccessService.AccessContext access) {
    boolean locked = contentAccess.isOpportunityLocked(access, opportunity.getId());
    String originalUrl = locked ? null : firstPresent(
        opportunity.getSourceUrl(), opportunity.getOfficialUrl(), opportunity.getApplyUrl());
    String searchable = joinText(
        opportunity.getTitle(),
        opportunity.getShortDescription(),
        opportunity.getCategory(),
        opportunity.getCountry(),
        opportunity.getOrganization(),
        locked ? null : opportunity.getDescription(),
        String.join(" ", safeSet(opportunity.getSpecialities())),
        String.join(" ", safeSet(opportunity.getDegreeLevels())));
    return new SourceCandidate(
        "OPPORTUNITY:" + opportunity.getId(),
        CandidateType.OPPORTUNITY,
        opportunity.getId(),
        opportunity.getSlug(),
        "/opportunities/" + opportunity.getSlug(),
        opportunity.getTitle(),
        opportunity.getShortDescription(),
        opportunity.getOrganization(),
        opportunity.getCountry(),
        opportunity.getCategory(),
        opportunity.getFunding() == null ? null : opportunity.getFunding().name(),
        opportunity.getDeadline(),
        opportunity.getDeadlineText(),
        opportunity.isAlwaysOpen(),
        opportunity.getCoverImage(),
        locked ? null : firstPresent(opportunity.getSourceName(), opportunity.getOrganization()),
        originalUrl,
        locked,
        opportunity.isFeatured(),
        safeSet(opportunity.getSpecialities()),
        safeSet(opportunity.getNationalities()),
        safeSet(opportunity.getDegreeLevels()),
        Map.of(),
        searchable,
        locked ? opportunity.getShortDescription() : truncate(opportunity.getDescription(), 2600),
        opportunity.getCreatedAt());
  }

  private SourceCandidate jobCandidate(
      CatalogItem job, ContentAccessService.AccessContext access) {
    boolean publicSource = job.getAttributes() != null
        && Boolean.TRUE.equals(job.getAttributes().get("publicSourceLink"));
    boolean locked = !publicSource && contentAccess.isCatalogLocked(access, CatalogType.JOB, job.getId());
    Map<String, Object> attributes = job.getAttributes() == null ? Map.of() : job.getAttributes();
    String attributeText;
    try {
      attributeText = mapper.writeValueAsString(attributes);
    } catch (Exception ignored) {
      attributeText = "";
    }
    String searchable = joinText(
        job.getTitle(), job.getSummary(), job.getCategory(), job.getOrganization(), job.getCountry(),
        locked ? null : job.getContent(), attributeText);
    return new SourceCandidate(
        "JOB:" + job.getId(),
        CandidateType.JOB,
        job.getId(),
        job.getSlug(),
        "/jobs/" + job.getSlug(),
        job.getTitle(),
        job.getSummary(),
        job.getOrganization(),
        job.getCountry(),
        job.getCategory(),
        null,
        null,
        Objects.toString(attributes.get("deadline"), null),
        Boolean.TRUE.equals(attributes.get("alwaysOpen")),
        job.getImage(),
        firstPresent(job.getOrganization(), "جهة التوظيف"),
        locked ? null : job.getWebsite(),
        locked,
        job.isFeatured(),
        stringSet(attributes.get("skills")),
        stringSet(attributes.get("nationalities")),
        stringSet(attributes.get("degreeLevels")),
        attributes,
        searchable,
        locked ? job.getSummary() : truncate(job.getContent(), 2600),
        job.getCreatedAt());
  }

  private RankedCandidate score(ProfileSignals profile, SourceCandidate candidate) {
    int earned = candidate.featured() ? 3 : 0;
    int possible = 3;
    var reasons = new ArrayList<String>();
    var gaps = new ArrayList<String>();
    var steps = new ArrayList<String>();

    if (!profile.preferredCategories().isEmpty()) {
      possible += 18;
      if (categoryMatches(profile.preferredCategories(), candidate)) {
        earned += 18;
        reasons.add(candidate.type() == CandidateType.JOB
            ? "تبحث عن فرص مهنية أو وظائف" : "نوع الفرصة ضمن اهتماماتك");
      }
    }
    if (!profile.preferredCountries().isEmpty() && present(candidate.country())) {
      possible += 11;
      if (containsIgnoreCase(profile.preferredCountries(), candidate.country())) {
        earned += 11;
        reasons.add("الفرصة في دولة تفضّلها");
      }
    }
    if (profile.preferredFunding() != null && candidate.funding() != null) {
      possible += 9;
      if (profile.preferredFunding().equalsIgnoreCase(candidate.funding())) {
        earned += 9;
        reasons.add("نوع التمويل يطابق تفضيلك");
      }
    }

    var studyTerms = new LinkedHashSet<String>();
    if (present(profile.major())) studyTerms.add(profile.major());
    studyTerms.addAll(profile.preferredSpecialities());
    if (!studyTerms.isEmpty()) {
      possible += 19;
      if (hasAnyTerm(candidate.searchableText(), studyTerms)
          || intersectsNormalized(studyTerms, candidate.specialities())) {
        earned += 19;
        reasons.add("المجال قريب من تخصصك");
      }
    }
    if (present(profile.degreeLevel()) && !candidate.degreeLevels().isEmpty()) {
      possible += 12;
      if (containsIgnoreCase(candidate.degreeLevels(), profile.degreeLevel())
          || containsIgnoreCase(candidate.degreeLevels(), "ALL")) {
        earned += 12;
        reasons.add("مرحلتك التعليمية ضمن المراحل المذكورة");
      } else {
        gaps.add("مرحلتك التعليمية ليست ضمن المراحل المذكورة بوضوح");
      }
    }
    if (present(profile.nationality()) && !candidate.nationalities().isEmpty()) {
      possible += 10;
      if (containsIgnoreCase(candidate.nationalities(), profile.nationality())
          || containsIgnoreCase(candidate.nationalities(), "ALL")) {
        earned += 10;
        reasons.add("الجنسية تبدو متوافقة مع الأهلية المنشورة");
      } else {
        gaps.add("تحقق من شرط الجنسية في المصدر الأصلي");
      }
    }
    if (!profile.skills().isEmpty()) {
      possible += 16;
      int skillMatches = termMatches(candidate.searchableText(), profile.skills());
      if (skillMatches > 0) {
        earned += Math.min(16, 8 + (skillMatches - 1) * 4);
        reasons.add(0, skillMatches > 1
            ? "عدة مهارات لديك مذكورة في وصف الفرصة" : "إحدى مهاراتك مرتبطة بالفرصة");
      } else if (!candidate.specialities().isEmpty()) {
        gaps.add("لم يظهر تطابق مباشر مع مهاراتك المسجلة");
      }
    }
    if (!profile.interests().isEmpty()) {
      possible += 7;
      if (hasAnyTerm(candidate.searchableText(), profile.interests())) {
        earned += 7;
        reasons.add("موضوع الفرصة قريب من اهتماماتك");
      }
    }
    if (profile.remotePreferred() != null) {
      boolean remote = hasAnyTerm(candidate.searchableText(), REMOTE_TERMS);
      boolean onsite = hasAnyTerm(candidate.searchableText(), ONSITE_TERMS);
      if (remote || onsite) {
        possible += 7;
        if ((profile.remotePreferred() && remote) || (!profile.remotePreferred() && onsite)) {
          earned += 7;
          reasons.add(profile.remotePreferred()
              ? "الوصف يشير إلى إمكانية العمل عن بُعد" : "نمط الحضور قريب من تفضيلك");
        }
      }
    }

    if (candidate.featured()) reasons.add("فرصة مختارة من فريق جامعتي");
    if (reasons.isEmpty()) reasons.add("فرصة منشورة ومتاحة حاليًا في قاعدة جامعتي");
    if (!present(profile.englishLevel())) gaps.add("بحاجة للتحقق: مستوى اللغة المطلوب");
    if (profile.dateOfBirth() == null) gaps.add("بحاجة للتحقق: شرط العمر إن وُجد");
    if (gaps.isEmpty()) gaps.add("راجع الشروط التفصيلية في المصدر قبل التقديم");
    if (candidate.accessLocked()) {
      steps.add("فعّل العضوية لفتح التفاصيل ورابط المصدر");
    } else if (present(candidate.originalUrl())) {
      steps.add("افتح المصدر الأصلي وتحقق من جميع شروط الأهلية");
    } else {
      steps.add("افتح صفحة الفرصة وراجع طريقة التقديم");
    }
    if (candidate.deadline() != null) {
      steps.add("جهّز المستندات قبل " + candidate.deadline());
    } else {
      steps.add("حدّث سيرتك ورسالة الدافع بما يناسب الفرصة");
    }

    int score = possible <= 3
        ? (candidate.featured() ? 45 : 35)
        : (int) Math.round(20 + (78.0 * earned / possible));
    score = Math.max(15, Math.min(98, score));
    boolean uncertain = candidate.degreeLevels().isEmpty()
        || candidate.nationalities().isEmpty()
        || profile.dateOfBirth() == null
        || !present(profile.englishLevel());
    return new RankedCandidate(
        candidate,
        score,
        copyLimit(reasons, 3),
        copyLimit(gaps, 3),
        copyLimit(steps, 3),
        uncertain);
  }

  private RecommendationItem fallbackItem(RankedCandidate ranked) {
    return new RecommendationItem(
        ranked.source().publicCandidate(),
        ranked.score(),
        matchLevel(ranked.score()),
        ranked.reasons(),
        ranked.missingRequirements(),
        ranked.nextSteps(),
        ranked.eligibilityUncertain(),
        false);
  }

  private CvSignals cvSignals(Long userId) {
    var documents = cvs.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId);
    CvDocument selected = documents.stream().filter(CvDocument::isPrimaryDocument).findFirst()
        .orElse(documents.isEmpty() ? null : documents.get(0));
    if (selected == null || !present(selected.getContentJson())) return CvSignals.empty();
    try {
      JsonNode root = mapper.readTree(selected.getContentJson());
      var skills = splitValues(root.path("skills").asText(""));
      boolean hasExperience = nonEmptyArray(root.path("experience"))
          || nonEmptyArray(root.path("projects"))
          || present(root.path("summary").asText(""));
      boolean hasEducation = nonEmptyArray(root.path("education"));
      return new CvSignals(true, skills, hasExperience, hasEducation);
    } catch (Exception ignored) {
      return CvSignals.empty();
    }
  }

  private static List<String> missingProfileFields(ProfileSignals profile) {
    var missing = new ArrayList<String>();
    if (!present(profile.degreeLevel())) missing.add("المرحلة التعليمية");
    if (!present(profile.major())) missing.add("التخصص");
    if (profile.skills().isEmpty()) missing.add("المهارات");
    if (!present(profile.englishLevel())) missing.add("مستوى الإنجليزية");
    if (!profile.hasExperience()) missing.add("الخبرة أو السيرة الذاتية");
    if (profile.dateOfBirth() == null) missing.add("تاريخ الميلاد");
    if (!present(profile.country())) missing.add("الدولة");
    if (profile.remotePreferred() == null) missing.add("تفضيل العمل عن بُعد");
    if (profile.interests().isEmpty()) missing.add("الاهتمامات");
    if (profile.preferredCategories().isEmpty()) missing.add("أنواع الفرص المطلوبة");
    return List.copyOf(missing);
  }

  private static int profileCompleteness(List<String> missing) {
    return Math.max(0, Math.min(100, (10 - missing.size()) * 10));
  }

  private boolean available(Opportunity opportunity) {
    return opportunity.isAlwaysOpen()
        || opportunity.getDeadline() == null
        || !opportunity.getDeadline().isBefore(LocalDate.now());
  }

  static String matchLevel(int score) {
    if (score >= 80) return "STRONG";
    if (score >= 65) return "GOOD";
    if (score >= 45) return "POSSIBLE";
    return "LOW";
  }

  private static boolean categoryMatches(Set<String> preferences, SourceCandidate candidate) {
    if (candidate.type() == CandidateType.JOB
        && preferences.stream().map(AiRecommendationService::normalize)
            .anyMatch(value -> value.contains("وظ") || value.contains("عمل") || value.contains("job"))) {
      return true;
    }
    return containsIgnoreCase(preferences, candidate.category());
  }

  private static int termMatches(String text, Set<String> terms) {
    int matches = 0;
    for (String term : terms) if (hasTerm(text, term)) matches++;
    return matches;
  }

  private static boolean hasAnyTerm(String text, Iterable<String> terms) {
    for (String term : terms) if (hasTerm(text, term)) return true;
    return false;
  }

  private static boolean hasTerm(String text, String term) {
    String normalizedTerm = normalize(term);
    if (normalizedTerm.length() < 2) return false;
    return normalize(text).contains(normalizedTerm);
  }

  private static boolean containsIgnoreCase(Set<String> values, String wanted) {
    if (!present(wanted)) return false;
    String normalized = normalize(wanted);
    return values.stream().map(AiRecommendationService::normalize).anyMatch(normalized::equals);
  }

  private static boolean intersectsNormalized(Set<String> first, Set<String> second) {
    return first.stream().map(AiRecommendationService::normalize)
        .anyMatch(value -> second.stream().map(AiRecommendationService::normalize).anyMatch(value::equals));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{M}\\p{P}\\p{S}]", " ")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replaceAll("\\s+", " ").trim();
  }

  private static Set<String> stringSet(Object value) {
    if (value == null) return Set.of();
    if (value instanceof Iterable<?> iterable) {
      var values = new LinkedHashSet<String>();
      iterable.forEach(item -> {
        if (item != null && present(item.toString())) values.add(item.toString().trim());
      });
      return Set.copyOf(values);
    }
    return splitValues(value.toString());
  }

  private static Set<String> splitValues(String value) {
    if (!present(value)) return Set.of();
    var values = new LinkedHashSet<String>();
    for (String item : LIST_SEPARATOR.split(value)) {
      String cleaned = item.trim();
      if (!cleaned.isBlank() && cleaned.length() <= 120) values.add(cleaned);
    }
    return Set.copyOf(values);
  }

  private static Set<String> safeSet(Set<String> values) {
    return values == null ? Set.of() : Set.copyOf(values);
  }

  private static boolean nonEmptyArray(JsonNode node) {
    return node.isArray() && !node.isEmpty();
  }

  private static List<String> copyLimit(List<String> values, int limit) {
    return values.stream().filter(AiRecommendationService::present).distinct().limit(limit).toList();
  }

  private static String joinText(Object... values) {
    var result = new StringBuilder();
    for (Object value : values) {
      if (value != null && present(value.toString())) result.append(' ').append(value);
    }
    return result.toString();
  }

  private static String firstPresent(String... values) {
    for (String value : values) if (present(value)) return value.trim();
    return null;
  }

  private static String truncate(String value, int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  record ProfileSnapshot(
      ProfileSignals profile,
      List<String> missingProfileFields,
      boolean cvIncluded,
      List<RankedCandidate> ranked) {}

  record RankedCandidate(
      SourceCandidate source,
      int score,
      List<String> reasons,
      List<String> missingRequirements,
      List<String> nextSteps,
      boolean eligibilityUncertain) {}

  record SourceCandidate(
      String key,
      CandidateType type,
      Long id,
      String slug,
      String route,
      String title,
      String summary,
      String organization,
      String country,
      String category,
      String funding,
      LocalDate deadline,
      String deadlineText,
      boolean alwaysOpen,
      String coverImage,
      String sourceName,
      String originalUrl,
      boolean accessLocked,
      boolean featured,
      Set<String> specialities,
      Set<String> nationalities,
      Set<String> degreeLevels,
      Map<String, Object> attributes,
      String searchableText,
      String details,
      Instant createdAt) {
    Candidate publicCandidate() {
      return new Candidate(
          key, type, id, slug, route, title, summary, organization, country, category, funding,
          deadline, deadlineText, alwaysOpen, coverImage, sourceName, originalUrl, accessLocked);
    }
  }

  record CvSignals(
      boolean included, Set<String> skills, boolean hasExperience, boolean hasEducation) {
    static CvSignals empty() {
      return new CvSignals(false, Set.of(), false, false);
    }
  }

  record ProfileSignals(
      LocalDate dateOfBirth,
      String country,
      String nationality,
      String degreeLevel,
      String major,
      String institution,
      String englishLevel,
      Boolean remotePreferred,
      String preferredFunding,
      Set<String> preferredCategories,
      Set<String> preferredCountries,
      Set<String> preferredSpecialities,
      Set<String> skills,
      Set<String> interests,
      boolean hasExperience,
      boolean cvIncluded) {
    static ProfileSignals from(UserProfile profile, CvSignals cv) {
      if (profile == null) {
        return new ProfileSignals(
            null, null, null, null, null, null, null, null, null,
            Set.of(), Set.of(), Set.of(), cv.skills(), Set.of(), cv.hasExperience(), cv.included());
      }
      var skills = new LinkedHashSet<String>(safeSet(profile.getSkills()));
      skills.addAll(cv.skills());
      return new ProfileSignals(
          profile.getDateOfBirth(),
          profile.getCountry(),
          profile.getNationality(),
          profile.getDegreeLevel(),
          profile.getPrimarySpeciality(),
          profile.getInstitution(),
          profile.getEnglishLevel(),
          profile.getRemotePreferred(),
          profile.getPreferredFunding() == null ? null : profile.getPreferredFunding().name(),
          safeSet(profile.getPreferredCategories()),
          safeSet(profile.getPreferredCountries()),
          safeSet(profile.getPreferredSpecialities()),
          Set.copyOf(skills),
          safeSet(profile.getInterests()),
          present(profile.getExperienceSummary()) || cv.hasExperience(),
          cv.included());
    }

    String ageBand() {
      if (dateOfBirth == null) return "UNKNOWN";
      int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
      if (age < 18) return "UNDER_18";
      if (age <= 24) return "18_24";
      if (age <= 34) return "25_34";
      if (age <= 44) return "35_44";
      return "45_PLUS";
    }
  }
}
