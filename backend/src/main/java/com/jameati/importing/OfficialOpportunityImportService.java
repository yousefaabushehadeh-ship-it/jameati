package com.jameati.importing;

import static com.jameati.importing.OfficialOpportunityDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.opportunity.OpportunityStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OfficialOpportunityImportService {
  private static final String BATCH_SOURCE = "دفعة المصادر الرسمية";
  private static final String RESOURCE_TYPE = "OPPORTUNITY";
  private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  private final OpportunityRepository opportunities;
  private final ImportRunRepository runs;
  private final OfficialOpportunityCatalog catalog;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate importTransaction;
  private final TransactionTemplate lifecycleTransaction;
  private final ReentrantLock importLock = new ReentrantLock();

  public OfficialOpportunityImportService(
      OpportunityRepository opportunities,
      ImportRunRepository runs,
      OfficialOpportunityCatalog catalog,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.opportunities = opportunities;
    this.runs = runs;
    this.catalog = catalog;
    this.objectMapper = objectMapper;
    this.importTransaction = new TransactionTemplate(transactionManager);
    this.lifecycleTransaction = new TransactionTemplate(transactionManager);
    this.lifecycleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(readOnly = true)
  public ImportPreview preview() {
    var feed = catalog.load();
    validate(feed);
    var existing = existingIndex();
    var records =
        feed.records().stream().map(record -> previewRecord(record, existing)).toList();
    var creates = count(records, ImportAction.CREATE);
    var updates = count(records, ImportAction.UPDATE);
    var skips = count(records, ImportAction.SKIP);
    var conflicts = count(records, ImportAction.CONFLICT);
    return new ImportPreview(
        feed.version(), records.size(), creates, updates, skips, conflicts, records);
  }

  public ImportRunSummary run() {
    if (!importLock.tryLock()) {
      throw new IllegalStateException("توجد عملية استيراد رسمية قيد التنفيذ بالفعل");
    }

    Long runId = null;
    try {
      var feed = catalog.load();
      validateHeader(feed);
      runId = startRun(feed);
      validate(feed);
      var existing = existingIndex();
      var conflicts =
          feed.records().stream()
              .map(record -> conflictReason(record, existing))
              .filter(Objects::nonNull)
              .toList();
      if (!conflicts.isEmpty()) {
        throw new IllegalStateException("تعارضت الدفعة مع بيانات موجودة: " + conflicts.getFirst());
      }

      var counts =
          Objects.requireNonNull(
              importTransaction.execute(status -> importRecords(feed)),
              "تعذر تنفيذ معاملة الاستيراد");
      return completeRun(runId, counts);
    } catch (RuntimeException exception) {
      if (runId != null) {
        failRun(runId, exception);
      }
      throw exception;
    } finally {
      importLock.unlock();
    }
  }

  @Transactional(readOnly = true)
  public Page<ImportRunSummary> history(Pageable pageable) {
    return runs.findAll(pageable).map(ImportRunSummary::from);
  }

  private BatchCounts importRecords(OfficialOpportunityFeed feed) {
    var counts = new BatchCounts();
    var now = Instant.now();
    var index = existingIndex();
    for (var record : feed.records()) {
      var hash = hash(record);
      var existing = index.tracked(record);
      if (existing.isPresent() && hash.equals(existing.get().getSourceHash())) {
        var opportunity = existing.get();
        if (opportunity.getLastVerifiedAt() == null
            || feed.verifiedAt().isAfter(opportunity.getLastVerifiedAt())) {
          opportunity.setLastVerifiedAt(feed.verifiedAt());
          opportunity.setVerifiedBy(feed.verifiedBy());
          opportunity.setLastImportedAt(now);
        }
        counts.skipped++;
        continue;
      }

      var opportunity = existing.orElseGet(Opportunity::new);
      var creating = opportunity.getId() == null;
      apply(feed, record, hash, now, opportunity);
      opportunities.save(opportunity);
      if (creating) {
        counts.created++;
      } else {
        counts.updated++;
      }
    }
    opportunities.flush();
    return counts;
  }

  private Long startRun(OfficialOpportunityFeed feed) {
    return Objects.requireNonNull(
        lifecycleTransaction.execute(
            status -> {
              var run = new ImportRun();
              run.setSourceName(BATCH_SOURCE);
              run.setSourceVersion(feed.version());
              run.setResourceType(RESOURCE_TYPE);
              run.setStatus(ImportStatus.RUNNING);
              run.setTotalRows(feed.records().size());
              return runs.saveAndFlush(run).getId();
            }));
  }

  private ImportRunSummary completeRun(Long runId, BatchCounts counts) {
    return Objects.requireNonNull(
        lifecycleTransaction.execute(
            status -> {
              var run = runs.findById(runId).orElseThrow();
              run.setCreatedRows(counts.created);
              run.setUpdatedRows(counts.updated);
              run.setSkippedRows(counts.skipped);
              run.setFailedRows(0);
              run.setErrorMessage(null);
              run.setStatus(ImportStatus.COMPLETED);
              run.setCompletedAt(Instant.now());
              return ImportRunSummary.from(runs.saveAndFlush(run));
            }));
  }

  private void failRun(Long runId, RuntimeException exception) {
    lifecycleTransaction.executeWithoutResult(
        status -> {
          var run = runs.findById(runId).orElseThrow();
          run.setStatus(ImportStatus.FAILED);
          run.setFailedRows(1);
          run.setErrorMessage(limit(message(exception), 1000));
          run.setCompletedAt(Instant.now());
          runs.saveAndFlush(run);
        });
  }

  private PreviewRecord previewRecord(
      OfficialOpportunityRecord record, ExistingIndex index) {
    var conflict = conflictReason(record, index);
    var existing = index.tracked(record);
    var action =
        conflict != null
            ? ImportAction.CONFLICT
            : existing.isEmpty()
                ? ImportAction.CREATE
                : hash(record).equals(existing.get().getSourceHash())
                    ? ImportAction.SKIP
                    : ImportAction.UPDATE;
    return new PreviewRecord(
        record.externalKey(),
        record.title(),
        record.organization(),
        record.country(),
        record.deadline(),
        record.deadlineText(),
        record.officialUrl(),
        action,
        conflict);
  }

  private String conflictReason(OfficialOpportunityRecord record, ExistingIndex index) {
    var tracked = index.tracked(record);
    var trackedId = tracked.map(Opportunity::getId).orElse(null);
    var slugOwner = index.slugOwner(record.slug());
    if (slugOwner.isPresent() && !Objects.equals(slugOwner.get().getId(), trackedId)) {
      return "الرابط المختصر مستخدم في سجل آخر: " + record.slug();
    }
    var urlOwner = index.urlOwners(record.officialUrl()).stream()
        .filter(owner -> !Objects.equals(owner.getId(), trackedId))
        .findFirst();
    if (urlOwner.isPresent()) {
      return "رابط الجهة الرسمي مستخدم في سجل آخر: " + record.officialUrl();
    }
    return null;
  }

  private ExistingIndex existingIndex() {
    var tracked = new HashMap<String, Opportunity>();
    var slugs = new HashMap<String, Opportunity>();
    var urls = new HashMap<String, List<Opportunity>>();
    for (var opportunity : opportunities.findAll()) {
      if (!isBlank(opportunity.getSourceName()) && !isBlank(opportunity.getSourceExternalKey())) {
        tracked.put(
            sourceIdentity(opportunity.getSourceName(), opportunity.getSourceExternalKey()),
            opportunity);
      }
      if (!isBlank(opportunity.getSlug())) {
        slugs.putIfAbsent(normalized(opportunity.getSlug()), opportunity);
      }
      if (!isBlank(opportunity.getOfficialUrl())) {
        urls.computeIfAbsent(normalized(opportunity.getOfficialUrl()), ignored -> new ArrayList<>())
            .add(opportunity);
      }
    }
    return new ExistingIndex(tracked, slugs, urls);
  }

  private String sourceIdentity(String sourceName, String externalKey) {
    return sourceName + "\u0000" + externalKey;
  }

  private String normalized(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private void apply(
      OfficialOpportunityFeed feed,
      OfficialOpportunityRecord record,
      String hash,
      Instant importedAt,
      Opportunity opportunity) {
    opportunity.setSlug(record.slug());
    opportunity.setTitle(record.title());
    opportunity.setShortDescription(record.shortDescription());
    opportunity.setDescription(record.description());
    opportunity.setCategory(record.category());
    opportunity.setOrganization(record.organization());
    opportunity.setCountry(record.country());
    opportunity.setFunding(record.funding());
    opportunity.setDeadline(record.deadline());
    opportunity.setDeadlineText(record.deadlineText());
    opportunity.setStatusOverride(record.statusOverride());
    opportunity.setAlwaysOpen(record.alwaysOpen());
    opportunity.setFeatured(record.featured());
    opportunity.setPublished(record.published());
    opportunity.setCoverImage(record.coverImage());
    opportunity.setOfficialUrl(record.officialUrl());
    opportunity.setApplyUrl(record.applyUrl());
    opportunity.setSourceName(record.sourceName());
    opportunity.setSourceUrl(record.sourceUrl());
    opportunity.setSourceExternalKey(record.externalKey());
    opportunity.setSourceHash(hash);
    opportunity.setLastVerifiedAt(feed.verifiedAt());
    opportunity.setLastImportedAt(importedAt);
    opportunity.setVerifiedBy(feed.verifiedBy());
    opportunity.setSpecialities(new LinkedHashSet<>(record.specialities()));
    opportunity.setNationalities(new LinkedHashSet<>(record.nationalities()));
    opportunity.setDegreeLevels(new LinkedHashSet<>(record.degreeLevels()));
    opportunity.setDeleted(false);
  }

  private void validate(OfficialOpportunityFeed feed) {
    validateHeader(feed);
    var keys = new java.util.HashSet<String>();
    var slugs = new java.util.HashSet<String>();
    var officialUrls = new java.util.HashSet<String>();
    for (var record : feed.records()) {
      requireText(record.externalKey(), "externalKey", 190);
      requireText(record.slug(), "slug", 190);
      if (!SLUG.matcher(record.slug()).matches()) {
        throw new IllegalStateException("slug غير صالح: " + record.slug());
      }
      requireText(record.title(), "title", 300);
      requireText(record.shortDescription(), "shortDescription", 500);
      requireText(record.description(), "description", Integer.MAX_VALUE);
      requireText(record.category(), "category", 100);
      requireText(record.organization(), "organization", 180);
      requireText(record.country(), "country", 100);
      if (record.funding() == null) {
        throw new IllegalStateException("الحقل funding مطلوب في الدفعة الرسمية");
      }
      optionalLength(record.deadlineText(), "deadlineText", 120);
      optionalLength(record.coverImage(), "coverImage", 500);
      requireHttps(record.sourceUrl(), "sourceUrl");
      requireHttps(record.officialUrl(), "officialUrl");
      requireHttps(record.applyUrl(), "applyUrl");
      requireText(record.sourceName(), "sourceName", 180);
      requireSet(record.specialities(), "specialities");
      requireSet(record.nationalities(), "nationalities");
      requireSet(record.degreeLevels(), "degreeLevels");

      if (record.alwaysOpen() && record.deadline() != null) {
        throw new IllegalStateException("لا يجتمع alwaysOpen مع deadline: " + record.externalKey());
      }
      if (record.alwaysOpen() && record.statusOverride() != null) {
        throw new IllegalStateException(
            "لا يحتاج السجل الدائم إلى statusOverride: " + record.externalKey());
      }
      if (record.statusOverride() != null
          && !Set.of(OpportunityStatus.OPEN, OpportunityStatus.UPCOMING)
              .contains(record.statusOverride())) {
        throw new IllegalStateException(
            "statusOverride غير مسموح في الدفعة: " + record.externalKey());
      }
      if (record.deadline() == null && !record.alwaysOpen() && isBlank(record.deadlineText())) {
        throw new IllegalStateException("يجب تحديد موعد أو وصف موعد للفرصة: " + record.externalKey());
      }
      if (!keys.add(record.sourceName() + "\u0000" + record.externalKey())) {
        throw new IllegalStateException("مفتاح مصدر مكرر: " + record.externalKey());
      }
      if (!slugs.add(record.slug().toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException("slug مكرر في الدفعة: " + record.slug());
      }
      if (!officialUrls.add(record.officialUrl().toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException("رابط رسمي مكرر في الدفعة: " + record.officialUrl());
      }
    }
  }

  private void validateHeader(OfficialOpportunityFeed feed) {
    requireText(feed.version(), "version", 80);
    requireText(feed.verifiedBy(), "verifiedBy", 120);
    if (feed.verifiedAt() == null) {
      throw new IllegalStateException("تاريخ التحقق مطلوب في الدفعة الرسمية");
    }
    if (feed.records() == null || feed.records().isEmpty()) {
      throw new IllegalStateException("دفعة الفرص الرسمية فارغة");
    }
  }

  private void requireSet(Set<String> values, String field) {
    if (values == null || values.isEmpty()) {
      throw new IllegalStateException("الحقل " + field + " مطلوب في الدفعة الرسمية");
    }
    values.forEach(value -> requireText(value, field, 255));
  }

  private void requireHttps(String value, String field) {
    requireText(value, field, 500);
    try {
      var uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new IllegalStateException(field + " يجب أن يكون رابط HTTPS صالحًا");
      }
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(field + " يجب أن يكون رابط HTTPS صالحًا", exception);
    }
  }

  private void requireText(String value, String field, int maxLength) {
    if (isBlank(value)) {
      throw new IllegalStateException("الحقل " + field + " مطلوب في الدفعة الرسمية");
    }
    if (value.length() > maxLength) {
      throw new IllegalStateException("الحقل " + field + " يتجاوز الطول المسموح");
    }
  }

  private void optionalLength(String value, String field, int maxLength) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalStateException("الحقل " + field + " يتجاوز الطول المسموح");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private int count(List<PreviewRecord> records, ImportAction action) {
    return (int) records.stream().filter(row -> row.action() == action).count();
  }

  private String hash(OfficialOpportunityRecord record) {
    try {
      var canonical = new LinkedHashMap<String, Object>();
      canonical.put("externalKey", record.externalKey());
      canonical.put("slug", record.slug());
      canonical.put("title", record.title());
      canonical.put("shortDescription", record.shortDescription());
      canonical.put("description", record.description());
      canonical.put("category", record.category());
      canonical.put("organization", record.organization());
      canonical.put("country", record.country());
      canonical.put("funding", record.funding());
      canonical.put("deadline", record.deadline());
      canonical.put("deadlineText", record.deadlineText());
      canonical.put("statusOverride", record.statusOverride());
      canonical.put("alwaysOpen", record.alwaysOpen());
      canonical.put("featured", record.featured());
      canonical.put("published", record.published());
      canonical.put("coverImage", record.coverImage());
      canonical.put("officialUrl", record.officialUrl());
      canonical.put("applyUrl", record.applyUrl());
      canonical.put("sourceName", record.sourceName());
      canonical.put("sourceUrl", record.sourceUrl());
      canonical.put("specialities", sorted(record.specialities()));
      canonical.put("nationalities", sorted(record.nationalities()));
      canonical.put("degreeLevels", sorted(record.degreeLevels()));
      var content = objectMapper.writeValueAsString(canonical);
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("تعذر تجهيز بصمة بيانات المصدر", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("تعذر حساب بصمة بيانات المصدر", exception);
    }
  }

  private List<String> sorted(Set<String> values) {
    return values == null ? new ArrayList<>() : new ArrayList<>(new TreeSet<>(values));
  }

  private String message(RuntimeException exception) {
    return isBlank(exception.getMessage())
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }

  private String limit(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static final class BatchCounts {
    private int created;
    private int updated;
    private int skipped;
  }

  private record ExistingIndex(
      Map<String, Opportunity> tracked,
      Map<String, Opportunity> slugs,
      Map<String, List<Opportunity>> urls) {
    Optional<Opportunity> tracked(OfficialOpportunityRecord record) {
      return Optional.ofNullable(
          tracked.get(record.sourceName() + "\u0000" + record.externalKey()));
    }

    Optional<Opportunity> slugOwner(String slug) {
      return Optional.ofNullable(slugs.get(slug.toLowerCase(Locale.ROOT)));
    }

    List<Opportunity> urlOwners(String officialUrl) {
      return urls.getOrDefault(officialUrl.toLowerCase(Locale.ROOT), List.of());
    }
  }
}
