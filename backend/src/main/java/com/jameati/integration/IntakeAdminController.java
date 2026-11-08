package com.jameati.integration;

import com.jameati.catalog.CatalogAdminService;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.common.ApiResponse;
import com.jameati.common.NotFoundException;
import com.jameati.integration.OpportunityIntakeService.Kind;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.opportunity.OpportunityService;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** صفحة "المسودات": كل اللي وصل من Tab (فرص + وظائف + دورات) بمكان واحد. */
@RestController
@RequestMapping("/api/v1/admin/intake/drafts")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN','SUPER_ADMIN')")
public class IntakeAdminController {
  private final OpportunityRepository opportunities;
  private final OpportunityService opportunityService;
  private final CatalogRepository catalog;
  private final CatalogAdminService catalogAdmin;

  public IntakeAdminController(OpportunityRepository opportunities, OpportunityService opportunityService,
                               CatalogRepository catalog, CatalogAdminService catalogAdmin) {
    this.opportunities = opportunities;
    this.opportunityService = opportunityService;
    this.catalog = catalog;
    this.catalogAdmin = catalogAdmin;
  }

  public record Draft(Kind kind, Long id, String title, String organization, String category, FundingType funding,
                      String deadline, String link, String description, String sourceName, Instant createdAt) {}
  public record PublishRequest(String category, FundingType funding) {}

  @GetMapping
  @Transactional(readOnly = true)
  public ApiResponse<List<Draft>> list() {
    var drafts = new ArrayList<Draft>();
    opportunities.findByPublishedFalseAndDeletedFalse(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))
        .forEach(o -> drafts.add(view(o)));
    catalog.findByTypeInAndSlugStartingWithAndPublishedFalseAndDeletedFalse(
            List.of(CatalogType.JOB, CatalogType.COURSE), OpportunityIntakeService.SLUG_PREFIX)
        .forEach(c -> drafts.add(view(c)));
    drafts.sort(Comparator.comparing(Draft::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
    return ApiResponse.ok(drafts);
  }

  @PostMapping("/{kind}/{id}/publish")
  @Transactional
  public ApiResponse<Void> publish(@PathVariable Kind kind, @PathVariable Long id, @RequestBody(required = false) PublishRequest r) {
    var category = r == null ? null : r.category();
    if (kind == Kind.OPPORTUNITY) {
      opportunityService.publish(id, category, r == null ? null : r.funding());
    } else {
      var c = catalogItem(kind, id);
      if (category != null && !category.isBlank()) c.setCategory(category.trim().length() > 100 ? category.trim().substring(0, 100) : category.trim());
      c.setPublished(true);
    }
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/{kind}/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Kind kind, @PathVariable Long id) {
    if (kind == Kind.OPPORTUNITY) opportunityService.delete(id);
    else catalogAdmin.delete(type(kind), catalogItem(kind, id).getId());
    return ResponseEntity.noContent().build();
  }

  private CatalogItem catalogItem(Kind kind, Long id) {
    return catalog.findByIdAndDeletedFalse(id).filter(c -> c.getType() == type(kind))
        .orElseThrow(() -> new NotFoundException("العنصر غير موجود"));
  }

  private static CatalogType type(Kind kind) {
    return switch (kind) {
      case JOB -> CatalogType.JOB;
      case COURSE -> CatalogType.COURSE;
      default -> throw new IllegalArgumentException("نوع غير صالح");
    };
  }

  private static Draft view(Opportunity o) {
    var deadline = o.getDeadline() != null ? o.getDeadline().toString() : o.getDeadlineText();
    return new Draft(Kind.OPPORTUNITY, o.getId(), o.getTitle(), o.getOrganization(), o.getCategory(), o.getFunding(),
        deadline, o.getOfficialUrl(), o.getDescription(), o.getSourceName(), o.getCreatedAt());
  }

  private static Draft view(CatalogItem c) {
    var kind = c.getType() == CatalogType.JOB ? Kind.JOB : Kind.COURSE;
    var attrs = c.getAttributes() == null ? Map.<String, Object>of() : c.getAttributes();
    var deadline = attrs.get("آخر موعد") == null ? null : String.valueOf(attrs.get("آخر موعد"));
    var source = attrs.get("source") == null ? null : String.valueOf(attrs.get("source"));
    return new Draft(kind, c.getId(), c.getTitle(), c.getOrganization(), c.getCategory(), null,
        deadline, c.getWebsite(), c.getContent(), source, c.getCreatedAt());
  }
}
