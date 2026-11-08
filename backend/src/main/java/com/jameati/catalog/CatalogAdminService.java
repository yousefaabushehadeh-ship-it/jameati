package com.jameati.catalog;

import static com.jameati.catalog.CatalogAdminDtos.*;

import com.jameati.common.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.subscription.SubscriptionPlanService;
import java.net.URI;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogAdminService {
  private final CatalogRepository repository;
  private final ObjectMapper objectMapper;
  private final SubscriptionPlanService subscriptionPlan;

  public CatalogAdminService(
      CatalogRepository repository,
      ObjectMapper objectMapper,
      SubscriptionPlanService subscriptionPlan) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.subscriptionPlan = subscriptionPlan;
  }

  public Page<ItemResponse> list(CatalogType type, String query, Pageable pageable) {
    return repository.adminSearch(type, query == null ? "" : query.trim(), pageable).map(ItemResponse::of);
  }

  public ItemResponse get(CatalogType type, Long id) {
    return ItemResponse.of(item(type, id));
  }

  @Transactional
  public ItemResponse create(CatalogType type, UpsertRequest request, boolean draftOnly) {
    request = draftOnly ? asDraft(request) : request;
    validateUrls(request);
    validateAttributes(request.attributes());
    var slug = normalizeSlug(request.slug());
    if (repository.findByTypeAndSlugIgnoreCase(type, slug).isPresent()) {
      throw new IllegalArgumentException("الرابط المختصر مستخدم لهذا النوع");
    }
    var item = new CatalogItem();
    item.setType(type);
    apply(item, request, slug);
    return ItemResponse.of(repository.save(item));
  }

  @Transactional
  public ItemResponse update(CatalogType type, Long id, UpsertRequest request, boolean draftOnly) {
    var item = item(type, id);
    if (draftOnly && item.isPublished()) {
      throw new AccessDeniedException("لا يستطيع مدخل البيانات تعديل محتوى منشور");
    }
    request = draftOnly ? asDraft(request) : request;
    preventRemovingFreeSelection(type, id, request.published());
    validateUrls(request);
    validateAttributes(request.attributes());
    var slug = normalizeSlug(request.slug());
    repository.findByTypeAndSlugIgnoreCase(type, slug)
        .filter(existing -> !existing.getId().equals(id))
        .ifPresent(existing -> { throw new IllegalArgumentException("الرابط المختصر مستخدم لهذا النوع"); });
    apply(item, request, slug);
    return ItemResponse.of(item);
  }

  @Transactional
  public void delete(CatalogType type, Long id) {
    var item = item(type, id);
    preventRemovingFreeSelection(type, id, false);
    item.setDeleted(true);
    item.setPublished(false);
  }

  private void preventRemovingFreeSelection(CatalogType type, Long id, boolean remainsPublished) {
    if (!remainsPublished && subscriptionPlan.isSelectedFree(type.name(), id)) {
      throw new IllegalArgumentException(
          "استبدل هذا العنصر من اختيارات العضوية المجانية قبل إلغاء نشره أو حذفه");
    }
  }

  private CatalogItem item(CatalogType type, Long id) {
    var item = repository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("المحتوى غير موجود"));
    if (item.getType() != type) throw new NotFoundException("المحتوى غير موجود");
    return item;
  }

  private static UpsertRequest asDraft(UpsertRequest request) {
    return new UpsertRequest(request.slug(), request.title(), request.summary(), request.content(),
        request.category(), request.organization(), request.country(), request.image(), request.website(),
        false, false, request.rating(), request.meta(), request.attributes());
  }

  private static String normalizeSlug(String slug) {
    return slug.trim().toLowerCase(Locale.ROOT);
  }

  private static void validateUrls(UpsertRequest request) {
    validateHttpUrl(request.image(), "رابط الصورة", true);
    validateHttpUrl(request.website(), "رابط الموقع", false);
  }

  private static void validateHttpUrl(String value, String label, boolean allowRelative) {
    if (value == null || value.isBlank()) return;
    if (allowRelative && value.startsWith("/") && !value.startsWith("//")) return;
    try {
      var uri = URI.create(value);
      var scheme = uri.getScheme();
      if ((!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
          || uri.getHost() == null) throw new Exception();
    } catch (Exception exception) {
      throw new IllegalArgumentException(label + " يجب أن يكون HTTP أو HTTPS");
    }
  }

  private void apply(CatalogItem item, UpsertRequest request, String slug) {
    item.setSlug(slug);
    item.setTitle(request.title().trim());
    item.setSummary(request.summary().trim());
    item.setContent(request.content().trim());
    item.setCategory(request.category().trim());
    item.setOrganization(trimToNull(request.organization()));
    item.setCountry(trimToNull(request.country()));
    item.setImage(trimToNull(request.image()));
    item.setWebsite(trimToNull(request.website()));
    item.setFeatured(request.featured());
    item.setPublished(request.published());
    item.setRating(request.rating());
    item.setMeta(trimToNull(request.meta()));
    item.setAttributes(request.attributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.attributes()));
  }

  private void validateAttributes(Map<String, Object> attributes) {
    if (attributes == null) return;
    try {
      if (objectMapper.writeValueAsBytes(attributes).length > 20_000) {
        throw new IllegalArgumentException("خصائص المحتوى أكبر من المسموح");
      }
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException("خصائص المحتوى غير صالحة");
    }
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
