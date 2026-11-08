package com.jameati.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityIntakeService {
  public static final String SOURCE = "tab-whatsapp";
  private static final int MAX_BATCH = 50;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Map<String, String> CATEGORIES = Map.ofEntries(
      Map.entry("scholarship", "منح دراسية"), Map.entry("scholarships", "منح دراسية"),
      Map.entry("منحة", "منح دراسية"), Map.entry("منح", "منح دراسية"),
      Map.entry("training", "تدريب"), Map.entry("internship", "تدريب"), Map.entry("course", "تدريب"),
      Map.entry("تدريب", "تدريب"), Map.entry("دورة", "تدريب"),
      Map.entry("volunteering", "تطوع"), Map.entry("volunteer", "تطوع"), Map.entry("تطوع", "تطوع"),
      Map.entry("fellowship", "زمالات"), Map.entry("fellowships", "زمالات"), Map.entry("زمالة", "زمالات"),
      Map.entry("exchange", "تبادل ثقافي"), Map.entry("تبادل", "تبادل ثقافي"),
      Map.entry("conference", "مؤتمرات"), Map.entry("conferences", "مؤتمرات"), Map.entry("مؤتمر", "مؤتمرات"),
      Map.entry("job", "وظائف"), Map.entry("jobs", "وظائف"), Map.entry("وظيفة", "وظائف"));

  private final OpportunityRepository repository;

  public OpportunityIntakeService(OpportunityRepository repository) {
    this.repository = repository;
  }

  public record ItemResult(String status, Long id, String slug, String title, String reason) {}
  public record IntakeResult(int created, int duplicates, int rejected, List<ItemResult> results, String message) {}

  @Transactional
  public IntakeResult intake(JsonNode body) {
    List<JsonNode> items = new ArrayList<>();
    if (body != null && body.isArray()) body.forEach(items::add);
    else if (body != null && body.has("items") && body.get("items").isArray()) body.get("items").forEach(items::add);
    else if (body != null && body.isObject()) items.add(body);
    if (items.isEmpty()) throw new IllegalArgumentException("لازم تبعت فرصة وحدة على الأقل");
    if (items.size() > MAX_BATCH) throw new IllegalArgumentException("الحد الأقصى " + MAX_BATCH + " فرصة بالطلب");

    var results = new ArrayList<ItemResult>();
    int created = 0, duplicates = 0, rejected = 0;
    for (var item : items) {
      var r = intakeOne(item);
      results.add(r);
      switch (r.status()) {
        case "created" -> created++;
        case "duplicate" -> duplicates++;
        default -> rejected++;
      }
    }
    var message = "انضافت " + created + " كمسودة، " + duplicates + " مكررة، " + rejected + " مرفوضة";
    return new IntakeResult(created, duplicates, rejected, results, message);
  }

  private ItemResult intakeOne(JsonNode n) {
    var title = clip(text(n, "title", "name"), 300);
    if (title == null) return new ItemResult("rejected", null, null, null, "العنوان (title) مطلوب");

    var organization = Optional.ofNullable(clip(text(n, "organization", "org", "provider", "organizer"), 180)).orElse("غير محدد");
    var link = clip(text(n, "link", "url", "applyUrl", "officialUrl"), 500);
    if (link != null && !link.matches("(?i)^https?://\\S+$")) link = null;

    var existing = findDuplicate(title, organization, link);
    if (existing.isPresent()) {
      var o = existing.get();
      var reason = o.isDeleted() ? "انرفضت أو انحذفت قبل من الأدمن" : (o.isPublished() ? "منشورة من قبل" : "موجودة كمسودة من قبل");
      return new ItemResult("duplicate", o.getId(), o.getSlug(), o.getTitle(), reason);
    }

    var description = text(n, "description", "details", "summary", "text", "rawText");
    if (description == null) description = title;
    var source = text(n, "source", "group");
    if (source != null) description = description + "\n\nالمصدر: " + source;

    var o = new Opportunity();
    o.setSlug(newSlug());
    o.setTitle(title);
    o.setShortDescription(clip(description.replaceAll("\\s+", " "), 480));
    o.setDescription(description);
    o.setCategory(category(text(n, "category", "type")));
    o.setOrganization(organization);
    o.setCountry(Optional.ofNullable(clip(text(n, "country", "location"), 100)).orElse("غير محدد"));
    o.setFunding(funding(text(n, "funding")));
    applyDeadline(o, text(n, "deadline"));
    o.setCoverImage(clip(text(n, "image", "coverImage"), 500));
    o.setOfficialUrl(link);
    o.setApplyUrl(link);
    o.setPublished(false);
    o.setFeatured(false);
    o.setSourceName(SOURCE);
    o.setSourceExternalKey(fingerprint(title, organization));
    o.setLastImportedAt(Instant.now());
    repository.save(o);
    return new ItemResult("created", o.getId(), o.getSlug(), o.getTitle(), "انحفظت كمسودة بانتظار موافقة الأدمن");
  }

  private Optional<Opportunity> findDuplicate(String title, String organization, String link) {
    if (link != null) {
      var byLink = repository.findAllByOfficialUrlIgnoreCase(link).stream().filter(o -> !o.isDeleted()).findFirst();
      if (byLink.isPresent()) return byLink;
    }
    // حتى المحذوفة بنعتبرها مكررة: الأدمن رفضها قبل، وما بدنا ترجع
    return repository.findBySourceNameAndSourceExternalKey(SOURCE, fingerprint(title, organization));
  }

  private static void applyDeadline(Opportunity o, String raw) {
    if (raw == null) return;
    try {
      o.setDeadline(LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw));
    } catch (Exception e) {
      o.setDeadlineText(clip(raw, 120));
    }
  }

  static String category(String raw) {
    if (raw == null) return "منح دراسية";
    var key = raw.trim().toLowerCase(Locale.ROOT);
    return CATEGORIES.getOrDefault(key, clip(raw.trim(), 100));
  }

  static FundingType funding(String raw) {
    if (raw == null) return FundingType.PARTIALLY_FUNDED;
    var v = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    try {
      return FundingType.valueOf(v);
    } catch (IllegalArgumentException ignored) {
      // نكمل بالتطابق العربي
    }
    if (raw.contains("كامل") || v.equals("FULL")) return FundingType.FULLY_FUNDED;
    if (raw.contains("جزئ") || v.equals("PARTIAL")) return FundingType.PARTIALLY_FUNDED;
    if (raw.contains("مجان")) return FundingType.FREE;
    if (raw.contains("مدفوع")) return FundingType.PAID;
    return FundingType.PARTIALLY_FUNDED;
  }

  private static String newSlug() {
    var date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now());
    var suffix = Long.toString(Math.abs(RANDOM.nextLong()), 36);
    return "tab-" + date + "-" + suffix.substring(0, Math.min(8, suffix.length()));
  }

  static String fingerprint(String title, String organization) {
    var normalized = (title + "|" + organization).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}ـ]+", "");
    try {
      var hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String text(JsonNode n, String... names) {
    for (var name : names) {
      var v = n.get(name);
      if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
    }
    return null;
  }

  private static String clip(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
