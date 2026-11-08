package com.jameati.catalog;

import com.jameati.common.ApiResponse;
import com.jameati.common.ArabicTitleSupport;
import com.jameati.common.NotFoundException;
import com.jameati.subscription.ContentAccessService;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/catalog")
public class CatalogController {
  private final CatalogRepository repo;
  private final ContentAccessService access;

  public CatalogController(CatalogRepository repo, ContentAccessService access) {
    this.repo = repo;
    this.access = access;
  }

  public record Item(
      Long id,
      String slug,
      String title,
      String summary,
      String content,
      String category,
      String organization,
      String country,
      String image,
      String website,
      boolean featured,
      long views,
      Double rating,
      String meta,
      Map<String, Object> attributes,
      boolean accessLocked) {
    static Item of(CatalogItem item, boolean locked) {
      return new Item(
          item.getId(),
          item.getSlug(),
          item.getTitle(),
          item.getSummary(),
          locked ? null : item.getContent(),
          item.getCategory(),
          item.getOrganization(),
          item.getCountry(),
          item.getImage(),
          locked ? null : item.getWebsite(),
          item.isFeatured(),
          item.getViews(),
          item.getRating(),
          locked ? null : item.getMeta(),
          locked ? Map.of() : item.getAttributes(),
          locked);
    }
  }

  @GetMapping("/{type}")
  ApiResponse<Page<Item>> list(
      @PathVariable CatalogType type,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "false") boolean countryMix,
      @RequestParam(defaultValue = "false") boolean easyFirst,
      @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      Principal principal) {
    var viewer = access.resolve(principal);
    var effectivePageable = type == CatalogType.JOB
        ? ArabicTitleSupport.arabicFirst(pageable)
        : pageable;
    Page<CatalogItem> page;
    if (type == CatalogType.UNIVERSITY && countryMix) {
      page = countryMixedUniversityPage(q.trim(), category.trim(), effectivePageable);
    } else if (type == CatalogType.JOB && easyFirst) {
      page = easyFirstJobPage(q.trim(), category.trim(), effectivePageable);
    } else {
      page = repo.publicSearch(type, q.trim(), category.trim(), effectivePageable);
    }
    return ApiResponse.ok(page.map(item -> Item.of(item, isLocked(viewer, type, item))));
  }

  private Page<CatalogItem> countryMixedUniversityPage(
      String q, String category, Pageable pageable) {
    var ordered = UniversityCatalogOrdering.interleave(
        repo.publicSearchAll(CatalogType.UNIVERSITY, q, category, stableUniversitySort(pageable.getSort())));
    return page(ordered, pageable);
  }

  private Page<CatalogItem> easyFirstJobPage(
      String q, String category, Pageable pageable) {
    var ordered = JobCatalogOrdering.easyFirst(
        repo.publicSearchAll(CatalogType.JOB, q, category, pageable.getSort()));
    return page(ordered, pageable);
  }

  private static Page<CatalogItem> page(List<CatalogItem> ordered, Pageable pageable) {
    if (pageable.isUnpaged()) return new PageImpl<>(ordered);

    var start = (int) Math.min(pageable.getOffset(), ordered.size());
    var end = Math.min(start + pageable.getPageSize(), ordered.size());
    return new PageImpl<>(ordered.subList(start, end), pageable, ordered.size());
  }

  @GetMapping("/{type}/homepage")
  ApiResponse<Page<Item>> homepage(
      @PathVariable CatalogType type,
      @RequestParam(defaultValue = "4") int requestedLimit,
      Principal principal) {
    var limit = Math.max(1, Math.min(requestedLimit, 12));
    var viewer = access.resolve(principal);
    if (type == CatalogType.UNIVERSITY) {
      return countryMixedUniversityHomepage(limit, viewer);
    }
    var selectedIds = viewer.plan() == null
        ? List.<Long>of()
        : viewer.plan().freeContent().getOrDefault(type.name(), List.of());
    var selectedById = selectedIds.isEmpty()
        ? Map.<Long, CatalogItem>of()
        : repo.findByTypeAndIdInAndPublishedTrueAndDeletedFalse(type, selectedIds).stream()
            .collect(Collectors.toMap(CatalogItem::getId, item -> item));
    var latestPageable = PageRequest.of(
        0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    Pageable effectivePageable = type == CatalogType.JOB
        ? ArabicTitleSupport.arabicFirst(latestPageable)
        : latestPageable;
    var latest = repo.findByTypeAndPublishedTrueAndDeletedFalse(type, effectivePageable);
    var merged = new LinkedHashMap<Long, CatalogItem>();
    selectedIds.stream()
        .map(selectedById::get)
        .filter(Objects::nonNull)
        .forEach(item -> merged.put(item.getId(), item));
    latest.getContent().forEach(item -> merged.putIfAbsent(item.getId(), item));
    var mergedItems = merged.values().stream();
    if (type == CatalogType.JOB) {
      mergedItems = mergedItems.sorted(
          java.util.Comparator.comparing(CatalogItem::isArabicTitle).reversed());
    }
    var orderedItems = mergedItems.toList();
    if (type == CatalogType.JOB) {
      var easiest = JobCatalogOrdering.firstEasy(
          repo.publicSearchAll(CatalogType.JOB, "", "", effectivePageable.getSort()))
          .orElse(null);
      if (easiest != null) {
        var easyFirst = new LinkedHashMap<Long, CatalogItem>();
        easyFirst.put(easiest.getId(), easiest);
        orderedItems.forEach(item -> easyFirst.putIfAbsent(item.getId(), item));
        orderedItems = List.copyOf(easyFirst.values());
      }
    }
    var content = orderedItems.stream()
        .limit(limit)
        .map(item -> Item.of(item, isLocked(viewer, type, item)))
        .toList();
    return ApiResponse.ok(new PageImpl<>(
        content,
        effectivePageable,
        latest.getTotalElements()));
  }

  private ApiResponse<Page<Item>> countryMixedUniversityHomepage(
      int limit, ContentAccessService.AccessContext viewer) {
    var pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    var ordered = UniversityCatalogOrdering.interleave(
        repo.publicSearchAll(CatalogType.UNIVERSITY, "", "", stableUniversitySort(pageable.getSort())));
    var content = ordered.stream()
        .limit(limit)
        .map(item -> Item.of(item, isLocked(viewer, CatalogType.UNIVERSITY, item)))
        .toList();
    return ApiResponse.ok(new PageImpl<>(content, pageable, ordered.size()));
  }

  private static Sort stableUniversitySort(Sort requested) {
    if (requested.getOrderFor("id") != null) return requested;
    return requested.and(Sort.by(Sort.Direction.ASC, "id"));
  }

  @GetMapping("/{type}/{slug}")
  ApiResponse<Item> detail(
      @PathVariable CatalogType type,
      @PathVariable String slug,
      Principal principal) {
    var item = repo.findByTypeAndSlugAndPublishedTrueAndDeletedFalse(type, slug)
        .orElseThrow(() -> new NotFoundException("المحتوى غير موجود"));
    // A detail read must not save the versioned editorial record.
    repo.incrementViews(item.getId());
    var viewer = access.resolve(principal);
    return ApiResponse.ok(Item.of(item, isLocked(viewer, type, item)));
  }

  private boolean isLocked(
      ContentAccessService.AccessContext viewer, CatalogType type, CatalogItem item) {
    return !hasRequiredPublicSourceLink(item)
        && access.isCatalogLocked(viewer, type, item.getId());
  }

  private static boolean hasRequiredPublicSourceLink(CatalogItem item) {
    return item.getAttributes() != null
        && Boolean.TRUE.equals(item.getAttributes().get("publicSourceLink"));
  }
}
