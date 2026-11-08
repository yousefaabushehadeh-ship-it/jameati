package com.jameati.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.subscription.SubscriptionPlanService;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class StartupDataConfiguration {
  private static final String MANAGED_BY = "official-catalog";
  private static final String JORDAN_UNIVERSITIES_FEED_ID = "official-jordan-universities";
  private static final String EXPANDED_CATALOG_FEED_ID = "official-expanded-catalog";
  private static final String ROR_ID_ATTRIBUTE = "rorId";
  private static final int SAVE_BATCH_SIZE = 500;
  private static final Logger LOG = LoggerFactory.getLogger(StartupDataConfiguration.class);

  @Bean
  CommandLineRunner seedStartupData(
      OpportunityRepository opportunities,
      CatalogRepository catalog,
      UserRepository users,
      PasswordEncoder encoder,
      ObjectMapper objectMapper,
      SubscriptionPlanService subscriptionPlan,
      @Value("${app.official-catalog.enabled:true}") boolean officialCatalogEnabled,
      @Value("${app.official-catalog.resource:data/official-catalog.json}") String catalogResource,
      @Value("${app.official-catalog.expanded-resource:data/official-expanded-catalog.json}") String expandedCatalogResource,
      @Value("${app.official-catalog.supplemental-resource:data/official-hourly-work-links.json}") String supplementalCatalogResource,
      @Value("${app.official-catalog.jordan-universities-resource:data/official-jordan-universities.json}") String jordanUniversitiesResource,
      @Value("${app.bootstrap-admin.enabled:false}") boolean bootstrapAdminEnabled,
      @Value("${app.bootstrap-admin.email:}") String bootstrapEmail,
      @Value("${app.bootstrap-admin.password:}") String bootstrapPassword,
      @Value("${app.bootstrap-admin.full-name:مدير المنصة}") String bootstrapFullName) {
    return args -> {
      bootstrapAdministrator(users, encoder, bootstrapAdminEnabled, bootstrapEmail, bootstrapPassword, bootstrapFullName);
      if (officialCatalogEnabled) {
        var protectedSelections = subscriptionPlan.current().freeContent().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        importOfficialCatalog(
            catalog,
            objectMapper,
            List.of(catalogResource, expandedCatalogResource, supplementalCatalogResource, jordanUniversitiesResource).stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList(),
            protectedSelections);
        retireSupersededJordanianRorUniversities(catalog, protectedSelections);
      }
      removeKnownDemoRecords(opportunities, catalog);
    };
  }

  static void bootstrapAdministrator(
      UserRepository users,
      PasswordEncoder encoder,
      boolean enabled,
      String rawEmail,
      String rawPassword,
      String rawName) {
    if (!enabled) return;
    var email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    var name = rawName == null ? "" : rawName.trim();
    if (email.length() > 190
        || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        || rawPassword == null
        || rawPassword.length() < 12
        || rawPassword.length() > 72
        || name.isBlank()
        || name.length() > 120) {
      throw new IllegalStateException(
          "BOOTSTRAP_ADMIN_EMAIL واسم صالح وكلمة مرور من 12 إلى 72 حرفًا مطلوبة عند تفعيل bootstrap");
    }
    if (!users.existsByEmailIgnoreCase(email)) {
      var admin = new User(email, encoder.encode(rawPassword), name);
      admin.setEmailVerifiedAt(Instant.now());
      admin.getRoles().addAll(Set.of(RoleName.ADMIN, RoleName.SUPER_ADMIN));
      users.save(admin);
    }
  }

  static CatalogImportResult importOfficialCatalog(
      CatalogRepository repository,
      ObjectMapper objectMapper,
      List<String> resourcePaths,
      Map<String, Set<Long>> protectedSelections) throws Exception {
    if (resourcePaths.isEmpty()) {
      throw new IllegalStateException("يجب تحديد ملف واحد على الأقل للكتالوج الرسمي");
    }

    var existingByIdentity = repository.findAll().stream()
        .collect(Collectors.toMap(
            StartupDataConfiguration::identity,
            item -> item,
            (first, ignored) -> first,
            LinkedHashMap::new));
    var existingByRorId = managedUniversityRecordsByRorId(existingByIdentity.values());
    var snapshotKeys = new LinkedHashMap<String, Set<String>>();
    var snapshotFeeds = new HashSet<String>();
    var generatedKeys = new HashSet<String>();
    var imported = 0;
    var updated = 0;
    var unchanged = 0;
    var manualConflicts = 0;

    for (var resourcePath : resourcePaths) {
      var resource = new ClassPathResource(resourcePath);
      if (!resource.exists()) {
        throw new IllegalStateException("ملف الكتالوج الرسمي غير موجود: " + resourcePath);
      }
      var slugs = new HashSet<String>();
      var counts = new EnumMap<CatalogType, Integer>(CatalogType.class);
      var changed = new ArrayList<CatalogItem>();
      String version = null;
      String feedId = null;
      JsonNode minimumCounts = null;
      var completeSnapshot = false;
      var recordCount = 0;
      var persistedCount = 0;

      try (var input = resource.getInputStream();
          var parser = objectMapper.getFactory().createParser(input)) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
          throw new IllegalStateException("بنية ملف الكتالوج الرسمي غير صالحة: " + resourcePath);
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          if (parser.currentToken() != JsonToken.FIELD_NAME) {
            throw new IllegalStateException("بنية ملف الكتالوج الرسمي غير صالحة: " + resourcePath);
          }
          var field = parser.currentName();
          var valueToken = parser.nextToken();
          if ("version".equals(field)) {
            version = requiredScalarText(parser, field, resourcePath);
          } else if ("feedId".equals(field)) {
            feedId = requiredScalarText(parser, field, resourcePath);
          } else if ("completeSnapshot".equals(field)) {
            completeSnapshot = parser.getValueAsBoolean(false);
          } else if ("minimumCounts".equals(field)) {
            minimumCounts = objectMapper.readTree(parser);
          } else if ("records".equals(field)) {
            if (valueToken != JsonToken.START_ARRAY || version == null) {
              throw new IllegalStateException(
                  "يجب أن تسبق نسخة الدفعة مصفوفة السجلات في " + resourcePath);
            }
            if (feedId == null) {
              feedId = resourcePath.replaceAll("^data/|\\.json$", "");
            }
            var present = snapshotKeys.computeIfAbsent(feedId, ignored -> new HashSet<>());
            while (parser.nextToken() != JsonToken.END_ARRAY) {
              if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("سجل غير صالح في " + resourcePath);
              }
              JsonNode record = objectMapper.readTree(parser);
              recordCount += 1;
              var type = parseType(record);
              var slug = requiredText(record, "slug").toLowerCase(Locale.ROOT);
              var key = identity(type, slug);
              if (!slugs.add(key)) {
                throw new IllegalStateException("رابط مكرر في الكتالوج الرسمي: " + slug);
              }
              if (!generatedKeys.add(key)) {
                throw new IllegalStateException(
                    "رابط مكرر بين دفعات الكتالوج الرسمية: " + slug);
              }
              counts.merge(type, 1, Integer::sum);
              requireHttps(record, "website");
              requireHttps(record, "sourceUrl");
              requireHttps(record, "officialUrl");
              present.add(key);

              if (isSupersededExpandedJordanianRorRecord(type, feedId, record)) {
                unchanged += 1;
                continue;
              }
              var existing = existingByIdentity.get(key);
              var rorId = rorId(record);
              if (existing == null && isJordanUniversitiesFeed(feedId) && rorId != null) {
                existing = existingByRorId.get(rorId);
              }
              if (existing != null && !isManaged(existing)) {
                manualConflicts += 1;
                continue;
              }
              if (existing != null && recordIsUnchanged(existing, record, version)) {
                unchanged += 1;
                continue;
              }
              var item = existing == null ? new CatalogItem() : existing;
              applyOfficialRecord(item, record, type, slug, version, feedId, objectMapper);
              changed.add(item);
              if (existing == null) imported += 1;
              else updated += 1;
              if (changed.size() == SAVE_BATCH_SIZE) {
                repository.saveAll(changed);
                persistedCount += changed.size();
                changed.clear();
              }
            }
          } else {
            parser.skipChildren();
          }
        }
      }
      if (recordCount == 0 || version == null) {
        throw new IllegalStateException("ملف الكتالوج الرسمي فارغ: " + resourcePath);
      }
      validateCounts(minimumCounts, counts, resourcePath);
      saveInBatches(repository, changed);
      persistedCount += changed.size();
      if (completeSnapshot) snapshotFeeds.add(feedId);
      LOG.info(
          "Official catalog feed {} validated with {} records; {} rows scheduled for persistence",
          feedId,
          recordCount,
          persistedCount);
    }

    var retired = new ArrayList<CatalogItem>();
    var protectedMissing = 0;
    for (var item : existingByIdentity.values()) {
      if (!isManaged(item)) continue;
      var feedId = String.valueOf(item.getAttributes().get("feedId"));
      if (!snapshotFeeds.contains(feedId)
          || snapshotKeys.getOrDefault(feedId, Set.of()).contains(identity(item))) continue;
      if (isProtectedSelection(item, protectedSelections)) {
        protectedMissing += 1;
        continue;
      }
      if (!item.isDeleted() || item.isPublished()) {
        item.setDeleted(true);
        item.setPublished(false);
        retired.add(item);
      }
    }
    saveInBatches(repository, retired);
    var result = new CatalogImportResult(
        imported, updated, unchanged, retired.size(), manualConflicts, protectedMissing);
    LOG.info("Official catalog import completed: {}", result);
    return result;
  }

  private static void applyOfficialRecord(
      CatalogItem item,
      JsonNode record,
      CatalogType type,
      String slug,
      String version,
      String feedId,
      ObjectMapper objectMapper) {
    item.setType(type);
    item.setSlug(slug);
    item.setTitle(requiredText(record, "title"));
    item.setSummary(requiredText(record, "summary"));
    item.setContent(requiredText(record, "content"));
    item.setCategory(requiredText(record, "category"));
    item.setOrganization(nullableText(record, "organization"));
    item.setCountry(nullableText(record, "country"));
    item.setImage(nullableText(record, "image"));
    item.setWebsite(requiredText(record, "website"));
    item.setFeatured(record.path("featured").asBoolean(false));
    item.setPublished(record.path("published").asBoolean(true));
    item.setDeleted(false);
    item.setRating(record.path("rating").isNumber() ? record.path("rating").doubleValue() : null);
    item.setMeta(nullableText(record, "meta"));
    Map<String, Object> attributes = record.path("attributes").isObject()
        ? objectMapper.convertValue(
            record.path("attributes"), new TypeReference<LinkedHashMap<String, Object>>() {})
        : new LinkedHashMap<>();
    attributes.put("sourceName", requiredText(record, "sourceName"));
    attributes.put("sourceUrl", requiredText(record, "sourceUrl"));
    attributes.put("officialUrl", requiredText(record, "officialUrl"));
    attributes.put("verifiedAt", requiredText(record, "verifiedAt"));
    putOptional(attributes, "sourceExternalKey", nullableText(record, "sourceExternalKey"));
    putOptional(attributes, "sourceFingerprint", nullableText(record, "sourceFingerprint"));
    putOptional(attributes, "license", nullableText(record, "license"));
    attributes.put("managedBy", MANAGED_BY);
    attributes.put("feedId", feedId);
    attributes.put("seedVersion", version);
    item.setAttributes(attributes);
  }

  private static boolean recordIsUnchanged(CatalogItem existing, JsonNode record, String version) {
    var attributes = existing.getAttributes();
    var fingerprint = nullableText(record, "sourceFingerprint");
    var samePayload = fingerprint == null
        ? version.equals(String.valueOf(attributes.get("seedVersion")))
        : fingerprint.equals(String.valueOf(attributes.get("sourceFingerprint")));
    return samePayload
        && !existing.isDeleted()
        && existing.isPublished() == record.path("published").asBoolean(true);
  }

  private static void validateCounts(
      JsonNode minimumCounts, EnumMap<CatalogType, Integer> counts, String resourcePath) {
    if (minimumCounts != null && minimumCounts.isObject()) {
      minimumCounts.fields().forEachRemaining(entry -> {
        CatalogType type;
        try {
          type = CatalogType.valueOf(entry.getKey());
        } catch (IllegalArgumentException exception) {
          throw new IllegalStateException("نوع غير معروف في minimumCounts: " + entry.getKey(), exception);
        }
        var minimum = entry.getValue().asInt(-1);
        if (minimum < 0 || counts.getOrDefault(type, 0) < minimum) {
          throw new IllegalStateException(
              "الكتالوج الرسمي " + resourcePath + " يجب أن يحتوي على الأقل " + minimum
                  + " سجلًا من النوع " + type);
        }
      });
      return;
    }
    if (resourcePath.endsWith("official-catalog.json")) {
      for (var type : CatalogType.values()) {
        if (counts.getOrDefault(type, 0) != 10) {
          throw new IllegalStateException("الكتالوج الرسمي الأساسي يجب أن يحتوي 10 سجلات من النوع " + type);
        }
      }
    }
  }

  private static CatalogType parseType(JsonNode record) {
    try {
      return CatalogType.valueOf(requiredText(record, "type"));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("نوع غير معروف في الكتالوج الرسمي", exception);
    }
  }

  private static String requiredScalarText(
      com.fasterxml.jackson.core.JsonParser parser, String field, String resourcePath)
      throws java.io.IOException {
    var value = parser.getValueAsString();
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "حقل " + field + " مطلوب في ملف الكتالوج الرسمي: " + resourcePath);
    }
    return value.trim();
  }

  private static boolean isManaged(CatalogItem item) {
    return item.getAttributes() != null
        && MANAGED_BY.equals(String.valueOf(item.getAttributes().get("managedBy")));
  }

  private static Map<String, CatalogItem> managedUniversityRecordsByRorId(
      Collection<CatalogItem> items) {
    var records = new LinkedHashMap<String, CatalogItem>();
    for (var item : items) {
      var rorId = rorId(item);
      if (item.getType() == CatalogType.UNIVERSITY && isManaged(item) && rorId != null) {
        records.putIfAbsent(rorId, item);
      }
    }
    return records;
  }

  private static boolean isSupersededExpandedJordanianRorRecord(
      CatalogType type,
      String feedId,
      JsonNode record) {
    if (type != CatalogType.UNIVERSITY
        || !EXPANDED_CATALOG_FEED_ID.equals(feedId)) return false;
    var country = nullableText(record, "country");
    return "الأردن".equals(country) || "Jordan".equalsIgnoreCase(country);
  }

  private static boolean isJordanUniversitiesFeed(String feedId) {
    return JORDAN_UNIVERSITIES_FEED_ID.equals(feedId);
  }

  private static String rorId(CatalogItem item) {
    if (item.getAttributes() == null) return null;
    var value = item.getAttributes().get(ROR_ID_ATTRIBUTE);
    return value instanceof String text && !text.isBlank() ? text.trim() : null;
  }

  private static String rorId(JsonNode record) {
    var attributes = record.path("attributes");
    return attributes.isObject() ? nullableText(attributes, ROR_ID_ATTRIBUTE) : null;
  }

  private static boolean isProtectedSelection(
      CatalogItem item, Map<String, Set<Long>> protectedSelections) {
    return item.getId() != null
        && protectedSelections.getOrDefault(item.getType().name(), Set.of()).contains(item.getId());
  }

  private static void retireSupersededJordanianRorUniversities(
      CatalogRepository repository, Map<String, Set<Long>> protectedSelections) {
    var retired = repository
        .findAllByTypeAndCountryAndDeletedFalse(CatalogType.UNIVERSITY, "الأردن")
        .stream()
        .filter(StartupDataConfiguration::isSupersededJordanianRorUniversity)
        .filter(item -> !isProtectedSelection(item, protectedSelections))
        .peek(item -> {
          item.setDeleted(true);
          item.setPublished(false);
        })
        .toList();
    if (retired.isEmpty()) return;
    repository.saveAll(retired);
    LOG.info("Retired {} superseded ROR university records for Jordan", retired.size());
  }

  private static boolean isSupersededJordanianRorUniversity(CatalogItem item) {
    return item.getType() == CatalogType.UNIVERSITY
        && isManaged(item)
        && rorId(item) != null
        && !isJordanUniversitiesFeed(String.valueOf(item.getAttributes().get("feedId")));
  }

  private static String identity(CatalogItem item) {
    return identity(item.getType(), item.getSlug());
  }

  private static String identity(CatalogType type, String slug) {
    return type + ":" + slug.toLowerCase(Locale.ROOT);
  }

  private static void putOptional(Map<String, Object> target, String key, String value) {
    if (value != null) target.put(key, value);
  }

  private static void saveInBatches(
      CatalogRepository repository, Collection<CatalogItem> items) {
    var records = new ArrayList<>(items);
    for (var start = 0; start < records.size(); start += SAVE_BATCH_SIZE) {
      repository.saveAll(records.subList(start, Math.min(start + SAVE_BATCH_SIZE, records.size())));
    }
  }

  private static void removeKnownDemoRecords(
      OpportunityRepository opportunities, CatalogRepository catalog) {
    var demoOpportunities = opportunities
        .findAllBySourceNameAndDeletedFalse("بيانات جامعتي التجريبية").stream()
        .filter(item -> item.getSlug() != null && item.getSlug().matches("demo-opportunity-[0-9]+"))
        .filter(item -> "بيانات جامعتي التجريبية".equals(item.getSourceName()))
        .peek(item -> { item.setDeleted(true); item.setPublished(false); })
        .toList();
    if (!demoOpportunities.isEmpty()) opportunities.saveAll(demoOpportunities);

    var demoCatalog = catalog.findAllByOrganizationAndDeletedFalse("أكاديمية أفق").stream()
        .filter(StartupDataConfiguration::isKnownDemoCatalogItem)
        .peek(item -> { item.setDeleted(true); item.setPublished(false); })
        .toList();
    if (!demoCatalog.isEmpty()) catalog.saveAll(demoCatalog);
  }

  private static boolean isKnownDemoCatalogItem(CatalogItem item) {
    if (item.getSlug() == null || !item.getSlug().matches("(course|job|university|organization|article|speciality)-[0-9]+")) return false;
    if (!"أكاديمية أفق".equals(item.getOrganization())) return false;
    return item.getSummary() != null && item.getSummary().contains("تجريبي");
  }

  private static String requiredText(JsonNode node, String field) {
    var value = nullableText(node, field);
    if (value == null) throw new IllegalStateException("حقل مطلوب مفقود في الكتالوج الرسمي: " + field);
    return value;
  }

  private static String nullableText(JsonNode node, String field) {
    var value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
  }

  private static void requireHttps(JsonNode node, String field) {
    var value = requiredText(node, field);
    var uri = URI.create(value);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalStateException("رابط غير آمن في الكتالوج الرسمي: " + field);
    }
  }

  record CatalogImportResult(
      int imported,
      int updated,
      int unchanged,
      int retired,
      int manualConflicts,
      int protectedMissing) {}
}
