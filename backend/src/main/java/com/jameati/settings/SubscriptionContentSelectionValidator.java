package com.jameati.settings;

import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.opportunity.OpportunityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionContentSelectionValidator {
  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;

  public SubscriptionContentSelectionValidator(
      OpportunityRepository opportunities,
      CatalogRepository catalog) {
    this.opportunities = opportunities;
    this.catalog = catalog;
  }

  public void validate(Map<String, Object> values) {
    if (!Boolean.TRUE.equals(values.get("enabled"))) return;

    var freeContent = (Map<?, ?>) values.get("freeContent");
    var opportunityIds = ids(freeContent.get("OPPORTUNITY"));
    if (opportunities.countByIdInAndPublishedTrueAndDeletedFalse(opportunityIds)
        != opportunityIds.size()) {
      throw new IllegalArgumentException("يجب أن تكون الفرص المجانية الأربع موجودة ومنشورة");
    }

    for (var type : CatalogType.values()) {
      var selectedIds = ids(freeContent.get(type.name()));
      if (catalog.countByTypeAndIdInAndPublishedTrueAndDeletedFalse(type, selectedIds)
          != selectedIds.size()) {
        throw new IllegalArgumentException(
            "يجب أن تكون عناصر " + type.name() + " المجانية الأربع موجودة ومنشورة في القسم الصحيح");
      }
    }
  }

  private static List<Long> ids(Object value) {
    return ((Collection<?>) value).stream()
        .map(Number.class::cast)
        .map(Number::longValue)
        .toList();
  }
}
