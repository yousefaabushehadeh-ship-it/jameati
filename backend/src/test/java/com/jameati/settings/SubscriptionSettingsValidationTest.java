package com.jameati.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogRepository;
import com.jameati.opportunity.OpportunityRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubscriptionSettingsValidationTest {
  @Test
  void rejectsMoreThanFourFreeItemsInASection() {
    var repository = mock(SiteSettingRepository.class);
    when(repository.findByKeyIgnoreCase("subscription")).thenReturn(Optional.empty());
    var service = service(repository);
    var values = validSettings();
    values.put("freeContent", Map.of("OPPORTUNITY", List.of(1, 2, 3, 4, 5)));

    assertThatThrownBy(() -> service.replace("subscription", values))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4 عناصر");
    verify(repository, never()).save(any());
  }

  @Test
  void rejectsEnabledPlanWithoutAPositivePrice() {
    var service = service(mock(SiteSettingRepository.class));
    var values = validSettings();
    values.put("annualPrice", 0);

    assertThatThrownBy(() -> service.replace("subscription", values))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("سعر الاشتراك");
  }

  @Test
  void rejectsEnabledPlanWithoutCliqAlias() {
    var service = service(mock(SiteSettingRepository.class));
    var values = validSettings();
    values.put("cliqAlias", "   ");

    assertThatThrownBy(() -> service.replace("subscription", values))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CliQ");
  }

  @Test
  void rejectsCliqAliasLongerThanOneHundredCharacters() {
    var service = service(mock(SiteSettingRepository.class));
    var values = validSettings();
    values.put("cliqAlias", "A".repeat(101));

    assertThatThrownBy(() -> service.replace("subscription", values))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("100");
  }

  @Test
  void trimsCliqAliasBeforePersistingSubscriptionSettings() {
    var repository = mock(SiteSettingRepository.class);
    when(repository.findByKeyIgnoreCase("subscription")).thenReturn(Optional.empty());
    var service = service(repository);
    var values = validSettings();
    values.put("cliqAlias", "  JAMEATI.CLIQ  ");

    var saved = service.replace("subscription", Map.copyOf(values));

    assertThat(saved).containsEntry("cliqAlias", "JAMEATI.CLIQ");
    verify(repository).save(any(SiteSetting.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void enabledPlanRequiresExactlyFourFreeItemsForEverySection() {
    var service = service(mock(SiteSettingRepository.class));
    var values = validSettings();
    var freeContent = new LinkedHashMap<>((Map<String, List<Integer>>) values.get("freeContent"));
    freeContent.put("COURSE", List.of(5, 6, 7));
    values.put("freeContent", freeContent);

    assertThatThrownBy(() -> service.replace("subscription", values))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4 عناصر مجانية لكل قسم");
  }

  @Test
  void enabledPlanRejectsMissingOrUnpublishedSelections() {
    var opportunities = mock(OpportunityRepository.class);
    var catalog = mock(CatalogRepository.class);
    var validator = new SubscriptionContentSelectionValidator(opportunities, catalog);
    var service = new SiteSettingService(
        mock(SiteSettingRepository.class), new ObjectMapper(), validator);
    when(opportunities.countByIdInAndPublishedTrueAndDeletedFalse(any())).thenReturn(3L);

    assertThatThrownBy(() -> service.replace("subscription", validSettings()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("موجودة ومنشورة");
    verifyNoInteractions(catalog);
  }

  private static SiteSettingService service(SiteSettingRepository repository) {
    return new SiteSettingService(
        repository,
        new ObjectMapper(),
        mock(SubscriptionContentSelectionValidator.class));
  }

  private static Map<String, Object> validSettings() {
    var values = new LinkedHashMap<String, Object>();
    values.put("enabled", true);
    values.put("annualPrice", 120.00);
    values.put("currency", "USD");
    values.put("cliqAlias", "JAMEATI.CLIQ");
    values.put("billingPeriodMonths", 12);
    values.put("freeLimit", 4);
    values.put("freeContent", Map.of(
        "OPPORTUNITY", List.of(1, 2, 3, 4),
        "COURSE", List.of(5, 6, 7, 8),
        "JOB", List.of(9, 10, 11, 12),
        "UNIVERSITY", List.of(13, 14, 15, 16),
        "ORGANIZATION", List.of(17, 18, 19, 20),
        "ARTICLE", List.of(21, 22, 23, 24),
        "SPECIALITY", List.of(25, 26, 27, 28)));
    return values;
  }
}
