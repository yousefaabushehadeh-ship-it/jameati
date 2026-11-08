package com.jameati.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.subscription.SubscriptionPlanService;
import java.util.*;
import org.junit.jupiter.api.Test;

class CatalogAdminServiceTest {
  private final CatalogRepository repository = mock(CatalogRepository.class);
  private final SubscriptionPlanService subscriptionPlan = mock(SubscriptionPlanService.class);
  private final CatalogAdminService service = new CatalogAdminService(repository, new ObjectMapper(), subscriptionPlan);

  @Test
  void dataEntryCanCreateDraftWithStructuredAttributes() {
    when(repository.findByTypeAndSlugIgnoreCase(CatalogType.COURSE, "java-basics"))
        .thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var request = request(false, false, Map.of("provider", "جامعة", "level", "BEGINNER"));

    var response = service.create(CatalogType.COURSE, request, true);

    assertThat(response.published()).isFalse();
    assertThat(response.attributes()).containsEntry("provider", "جامعة");
  }

  @Test
  void dataEntryPublishFlagsAreForcedBackToDraft() {
    when(repository.findByTypeAndSlugIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var response = service.create(CatalogType.ARTICLE, request(true, true, Map.of()), true);
    assertThat(response.published()).isFalse();
    assertThat(response.featured()).isFalse();
  }

  @Test
  void selectedFreeItemCannotBeDeletedUntilAdminReplacesIt() {
    var item = new CatalogItem();
    item.setId(8L);
    item.setType(CatalogType.COURSE);
    when(repository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(item));
    when(subscriptionPlan.isSelectedFree("COURSE", 8L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(CatalogType.COURSE, 8L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("اختيارات العضوية");
    assertThat(item.isDeleted()).isFalse();
  }

  private static CatalogAdminDtos.UpsertRequest request(
      boolean published, boolean featured, Map<String, Object> attributes) {
    return new CatalogAdminDtos.UpsertRequest("java-basics", "Java", "Summary", "Content",
        "Technology", "Provider", "Palestine", null, "https://example.com", featured,
        published, 4.5, "Online", attributes);
  }
}
