package com.jameati.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
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

/**
 * بيستقبل منشورات من Tab (واتساب) وبيوزعها حسب النوع:
 *  - منح / تدريب / تطوع / زمالات / تبادل / مؤتمرات / مسابقات  ← قسم الفرص
 *  - وظائف ← قسم الوظائف
 *  - دورات ← قسم الدورات
 * كلها بتنحفظ مسودات لحد ما يوافق الأدمن.
 */
@Service
public class OpportunityIntakeService {
  public static final String SOURCE = "tab-whatsapp";
  public static final String SLUG_PREFIX = "tab-";
  private static final int MAX_BATCH = 50;
  private static final SecureRandom RANDOM = new SecureRandom();

  public enum Kind { OPPORTUNITY, JOB, COURSE }

  /** نوع المنشور ← (القسم، التصنيف الافتراضي) */
  private record Route(Kind kind, String category) {}

  private static final Map<String, Route> ROUTES = new HashMap<>();
  private static void route(Kind kind, String category, String... keys) {
    for (var k : keys) ROUTES.put(k, new Route(kind, category));
  }
  static {
    route(Kind.OPPORTUNITY, "منح دراسية", "scholarship", "scholarships", "grant", "منحة", "منح", "منح دراسية");
    route(Kind.OPPORTUNITY, "تدريب", "training", "internship", "internships", "تدريب", "تدريب عملي");
    route(Kind.OPPORTUNITY, "تطوع", "volunteering", "volunteer", "تطوع");
    route(Kind.OPPORTUNITY, "زمالات", "fellowship", "fellowships", "زمالة", "زمالات");
    route(Kind.OPPORTUNITY, "تبادل ثقافي", "exchange", "تبادل", "تبادل ثقافي");
    route(Kind.OPPORTUNITY, "مؤتمرات", "conference", "conferences", "summit", "مؤتمر", "مؤتمرات");
    route(Kind.OPPORTUNITY, "مسابقات", "competition", "competitions", "hackathon", "award", "مسابقة", "مسابقات", "هاكاثون", "جائزة");
    route(Kind.JOB, "وظائف", "job", "jobs", "vacancy", "work", "freelance", "وظيفة", "وظائف", "شاغر", "عمل");
    route(Kind.COURSE, "تطوير مهني", "course", "courses", "workshop", "bootcamp", "دورة", "دورات", "كورس", "ورشة", "ورشة عمل");
  }

  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;

  public OpportunityIntakeService(OpportunityRepository opportunities, CatalogRepository catalog) {
    this.opportunities = opportunities;
    this.catalog = catalog;
  }

  public record ItemResult(String status, String section, Long id, String slug, String title, String reason) {}
  public record IntakeResult(int created, int duplicates, int rejected, List<ItemResult> results, String message) {}

  /** الحقول المشتركة بعد التنظيف */
  private record Post(String title, String organization, String link, String description, String category,
                      String country, String deadline, String image, String funding) {}

  @Transactional
  public IntakeResult intake(JsonNode body) {
    List<JsonNode> items = new ArrayList<>();
    if (body != null && body.isArray()) body.forEach(items::add);
    else if (body != null && body.has("items") && body.get("items").isArray()) body.get("items").forEach(items::add);
    else if (body != null && body.isObject()) items.add(body);
    if (items.isEmpty()) throw new IllegalArgumentException("لازم تبعت منشور واحد على الأقل");
    if (items.size() > MAX_BATCH) throw new IllegalArgumentException("الحد الأقصى " + MAX_BATCH + " منشور بالطلب");

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
    if (title == null) return new ItemResult("rejected", null, null, null, null, "العنوان (title) مطلوب");

    var link = clip(text(n, "link", "url", "applyUrl", "officialUrl", "website"), 500);
    if (link != null && !link.matches("(?i)^https?://\\S+$")) link = null;
    var description = text(n, "description", "details", "summary", "text", "rawText");
    if (description == null) description = title;
    var source = text(n, "source", "group");
    if (source != null) description = description + "\n\nالمصدر: " + source;

    var route = route(text(n, "type", "kind"), text(n, "category"));
    var post = new Post(
        title,
        Optional.ofNullable(clip(text(n, "organization", "org", "provider", "organizer", "company"), 180)).orElse("غير محدد"),
        link,
        description,
        route.category(),
        Optional.ofNullable(clip(text(n, "country", "location"), 100)).orElse("غير محدد"),
        text(n, "deadline"),
        clip(text(n, "image", "coverImage"), 500),
        text(n, "funding", "price"));

    return route.kind() == Kind.OPPORTUNITY ? saveOpportunity(post) : saveCatalog(post, route.kind());
  }

  // ---------- الفرص ----------

  private ItemResult saveOpportunity(Post p) {
    var key = fingerprint(p.title(), p.organization());
    Optional<Opportunity> existing = Optional.empty();
    if (p.link() != null) {
      existing = opportunities.findAllByOfficialUrlIgnoreCase(p.link()).stream().filter(o -> !o.isDeleted()).findFirst();
    }
    // حتى المحذوفة بنعتبرها مكررة: الأدمن رفضها قبل، وما بدنا ترجع
    if (existing.isEmpty()) existing = opportunities.findBySourceNameAndSourceExternalKey(SOURCE, key);
    if (existing.isPresent()) {
      var o = existing.get();
      return new ItemResult("duplicate", "opportunities", o.getId(), o.getSlug(), o.getTitle(), reason(o.isDeleted(), o.isPublished()));
    }

    var o = new Opportunity();
    o.setSlug(newSlug());
    o.setTitle(p.title());
    o.setShortDescription(clip(p.description().replaceAll("\\s+", " "), 480));
    o.setDescription(p.description());
    o.setCategory(p.category());
    o.setOrganization(p.organization());
    o.setCountry(p.country());
    o.setFunding(funding(p.funding()));
    if (p.deadline() != null) {
      var d = parseDate(p.deadline());
      if (d != null) o.setDeadline(d); else o.setDeadlineText(clip(p.deadline(), 120));
    }
    o.setCoverImage(p.image());
    o.setOfficialUrl(p.link());
    o.setApplyUrl(p.link());
    o.setPublished(false);
    o.setSourceName(SOURCE);
    o.setSourceExternalKey(key);
    o.setLastImportedAt(Instant.now());
    opportunities.save(o);
    return new ItemResult("created", "opportunities", o.getId(), o.getSlug(), o.getTitle(), "انحفظت كمسودة بانتظار موافقة الأدمن");
  }

  // ---------- الوظائف والدورات ----------

  private ItemResult saveCatalog(Post p, Kind kind) {
    var type = kind == Kind.JOB ? CatalogType.JOB : CatalogType.COURSE;
    var section = kind == Kind.JOB ? "jobs" : "courses";
    // السلَغ ثابت من العنوان + الجهة، فهو نفسه بيمنع التكرار (حتى لو انحذفت)
    var slug = SLUG_PREFIX + fingerprint(p.title(), p.organization()).substring(0, 20);
    Optional<CatalogItem> existing = catalog.findByTypeAndSlugIgnoreCase(type, slug);
    if (existing.isEmpty() && p.link() != null) {
      existing = catalog.findAllByTypeAndWebsiteIgnoreCase(type, p.link()).stream().filter(c -> !c.isDeleted()).findFirst();
    }
    if (existing.isPresent()) {
      var c = existing.get();
      return new ItemResult("duplicate", section, c.getId(), c.getSlug(), c.getTitle(), reason(c.isDeleted(), c.isPublished()));
    }

    var c = new CatalogItem();
    c.setType(type);
    c.setSlug(slug);
    c.setTitle(p.title());
    c.setSummary(clip(p.description().replaceAll("\\s+", " "), 480));
    c.setContent(p.description());
    c.setCategory(p.category());
    c.setOrganization(p.organization());
    c.setCountry(p.country());
    c.setImage(p.image());
    c.setWebsite(p.link());
    c.setPublished(false);
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("source", SOURCE);
    if (p.deadline() != null) {
      var d = parseDate(p.deadline());
      attrs.put("آخر موعد", d != null ? d.toString() : clip(p.deadline(), 120));
      c.setMeta(clip("آخر موعد: " + (d != null ? d.toString() : p.deadline()), 80));
    }
    if (p.funding() != null) attrs.put(kind == Kind.JOB ? "الراتب" : "السعر", clip(p.funding(), 120));
    c.setAttributes(attrs);
    catalog.save(c);
    return new ItemResult("created", section, c.getId(), c.getSlug(), c.getTitle(), "انحفظت كمسودة بانتظار موافقة الأدمن");
  }

  // ---------- مساعدات ----------

  private static String reason(boolean deleted, boolean published) {
    return deleted ? "انرفضت أو انحذفت قبل من الأدمن" : (published ? "منشورة من قبل" : "موجودة كمسودة من قبل");
  }

  static Route route(String type, String category) {
    var byType = type == null ? null : ROUTES.get(type.trim().toLowerCase(Locale.ROOT));
    var byCategory = category == null ? null : ROUTES.get(category.trim().toLowerCase(Locale.ROOT));
    var r = byType != null ? byType : byCategory != null ? byCategory : new Route(Kind.OPPORTUNITY, "منح دراسية");
    // تصنيف حر بالعربي (مثلاً "تقنية") بيضل زي ما هو داخل القسم الصح
    if (category != null && byCategory == null && !category.isBlank()) return new Route(r.kind(), clip(category.trim(), 100));
    return r;
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

  private static LocalDate parseDate(String raw) {
    try {
      return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
    } catch (Exception e) {
      return null;
    }
  }

  private static String newSlug() {
    var date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now());
    var suffix = Long.toString(Math.abs(RANDOM.nextLong()), 36);
    return SLUG_PREFIX + date + "-" + suffix.substring(0, Math.min(8, suffix.length()));
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
