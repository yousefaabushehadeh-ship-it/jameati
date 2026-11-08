package com.jameati.subscription;

import com.jameati.catalog.CatalogType;
import com.jameati.common.NotFoundException;
import com.jameati.settings.SiteSettingService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlanService {
  public static final String OPPORTUNITY = "OPPORTUNITY";
  public static final int ANNUAL_MONTHS = 12;
  public static final int FREE_LIMIT = 4;

  private final SiteSettingService settings;

  public SubscriptionPlanService(SiteSettingService settings) {
    this.settings = settings;
  }

  public PlanConfig current() {
    Map<String, Object> values;
    try {
      values = settings.publicSettings("subscription");
    } catch (NotFoundException exception) {
      values = Map.of();
    }
    var enabled = booleanValue(values.get("enabled"), false);
    var annualPrice = MembershipPlanCode.ANNUAL.price();
    var currency = "JOD";
    var cliqAlias = cliqAliasValue(values.get("cliqAlias"));
    var freeContent = freeContent(values.get("freeContent"));
    if (enabled && annualPrice.signum() <= 0) {
      throw new IllegalStateException("سعر خطة الاشتراك المفعلة غير صالح");
    }
    return new PlanConfig(
        enabled, annualPrice, currency, ANNUAL_MONTHS, FREE_LIMIT, freeContent, cliqAlias);
  }

  public boolean isSelectedFree(String contentType, Long contentId) {
    if (contentType == null || contentId == null) return false;
    var plan = current();
    return plan.enabled()
        && plan.freeContent().getOrDefault(contentType, List.of()).contains(contentId);
  }

  private static Map<String, List<Long>> freeContent(Object raw) {
    var result = emptyFreeContent();
    if (!(raw instanceof Map<?, ?> values)) return result;
    for (var type : result.keySet()) {
      if (!(values.get(type) instanceof Collection<?> ids)) continue;
      var parsed = new LinkedHashSet<Long>();
      for (var value : ids) {
        if (!(value instanceof Number number)) continue;
        try {
          var id = new BigDecimal(number.toString()).longValueExact();
          if (id > 0 && parsed.size() < FREE_LIMIT) parsed.add(id);
        } catch (ArithmeticException ignored) {
          // Invalid persisted values are ignored so public content remains safely locked.
        }
      }
      result.put(type, List.copyOf(parsed));
    }
    return Collections.unmodifiableMap(result);
  }

  private static LinkedHashMap<String, List<Long>> emptyFreeContent() {
    var result = new LinkedHashMap<String, List<Long>>();
    result.put(OPPORTUNITY, List.of());
    Arrays.stream(CatalogType.values()).map(Enum::name).forEach(type -> result.put(type, List.of()));
    return result;
  }

  private static boolean booleanValue(Object value, boolean fallback) {
    return value instanceof Boolean bool ? bool : fallback;
  }

  private static BigDecimal decimalValue(Object value, BigDecimal fallback) {
    if (!(value instanceof Number number)) return fallback;
    try {
      var parsed = new BigDecimal(number.toString());
      return parsed.signum() >= 0 ? parsed : fallback;
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static String stringValue(Object value, String fallback) {
    if (!(value instanceof String text) || !text.matches("^[A-Za-z]{3}$")) return fallback;
    return text;
  }

  private static String cliqAliasValue(Object value) {
    if (!(value instanceof String text)) return "";
    var normalized = text.trim();
    return normalized.length() <= 100 ? normalized : "";
  }

  public record PlanConfig(
      boolean enabled,
      BigDecimal annualPrice,
      String currency,
      int billingPeriodMonths,
      int freeLimit,
      Map<String, List<Long>> freeContent,
      String cliqAlias) {}
}
