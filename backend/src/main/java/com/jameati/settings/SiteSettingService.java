package com.jameati.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.common.NotFoundException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SiteSettingService {
  private static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9-]{1,49}$");
  private static final Set<String> PUBLIC_NAMESPACES = Set.of(
      "homepage",
      "seo",
      "settings",
      "categories",
      "countries",
      "funding-types",
      "nationalities",
      "cv-templates",
      "personality",
      "subscription");
  private static final Set<String> SUPPORTED_NAMESPACES = Set.of(
      "homepage",
      "seo",
      "settings",
      "categories",
      "countries",
      "funding-types",
      "nationalities",
      "cv-templates",
      "personality",
      "notifications",
      "subscription");
  private final SiteSettingRepository repository;
  private final ObjectMapper objectMapper;
  private final SubscriptionContentSelectionValidator subscriptionContentSelectionValidator;

  public SiteSettingService(
      SiteSettingRepository repository,
      ObjectMapper objectMapper,
      SubscriptionContentSelectionValidator subscriptionContentSelectionValidator) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.subscriptionContentSelectionValidator = subscriptionContentSelectionValidator;
  }

  public Map<String, Object> publicSettings(String namespace) {
    var key = normalize(namespace);
    if (!PUBLIC_NAMESPACES.contains(key)) {
      throw new NotFoundException("الإعدادات غير موجودة");
    }
    return parse(repository.findByKeyIgnoreCaseAndPublicVisibleTrueAndDeletedFalse(key)
        .orElseThrow(() -> new NotFoundException("الإعدادات غير موجودة")));
  }

  public Map<String, Object> adminSettings(String namespace) {
    var key = normalize(namespace);
    return repository.findByKeyIgnoreCase(key)
        .filter(value -> !value.isDeleted())
        .map(this::parse)
        .orElseGet(LinkedHashMap::new);
  }

  /**
   * Reads a public boolean feature flag without exposing JSON parsing to the
   * service that enforces it. Missing settings deliberately keep the supplied
   * safe default so a fresh database remains usable.
   */
  public boolean publicBoolean(String namespace, String property, boolean defaultValue) {
    var key = normalize(namespace);
    if (!PUBLIC_NAMESPACES.contains(key)) return defaultValue;
    return repository.findByKeyIgnoreCaseAndPublicVisibleTrueAndDeletedFalse(key)
        .map(this::parse)
        .map(values -> values.get(property))
        .filter(Boolean.class::isInstance)
        .map(Boolean.class::cast)
        .orElse(defaultValue);
  }

  @Transactional
  public Map<String, Object> replace(String namespace, Map<String, Object> values) {
    var key = normalize(namespace);
    if (values == null) throw new IllegalArgumentException("محتوى الإعدادات مطلوب");
    values = new LinkedHashMap<>(values);
    if ("subscription".equals(key)) {
      validateSubscription(values);
      subscriptionContentSelectionValidator.validate(values);
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("JSON الإعدادات غير صالح");
    }
    if (json.length() > 100_000) throw new IllegalArgumentException("حجم الإعدادات أكبر من المسموح");
    var setting = repository.findByKeyIgnoreCase(key).orElseGet(SiteSetting::new);
    setting.setKey(key);
    setting.setGroup(key);
    setting.setValueJson(json);
    setting.setPublicVisible(PUBLIC_NAMESPACES.contains(key));
    setting.setDeleted(false);
    repository.save(setting);
    return parse(setting);
  }

  private Map<String, Object> parse(SiteSetting setting) {
    try {
      return objectMapper.readValue(setting.getValueJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("تعذر قراءة إعدادات " + setting.getKey(), exception);
    }
  }

  private static String normalize(String namespace) {
    if (namespace == null) throw new IllegalArgumentException("namespace مطلوب");
    var normalized = namespace.trim().toLowerCase(Locale.ROOT);
    if (!NAMESPACE.matcher(normalized).matches()) throw new IllegalArgumentException("namespace غير صالح");
    if (!SUPPORTED_NAMESPACES.contains(normalized)) throw new IllegalArgumentException("namespace غير مدعوم");
    return normalized;
  }

  private static void validateSubscription(Map<String, Object> values) {
    var supported = Set.of(
        "enabled", "annualPrice", "currency", "cliqAlias", "billingPeriodMonths", "freeLimit", "freeContent");
    if (!supported.containsAll(values.keySet())) {
      throw new IllegalArgumentException("إعدادات الاشتراك تحتوي حقولاً غير مدعومة");
    }

    var enabled = requireType(values, "enabled", Boolean.class);
    var annualPrice = decimal(values.get("annualPrice"), "annualPrice");
    if (annualPrice.signum() < 0 || (enabled && annualPrice.signum() == 0)
        || annualPrice.compareTo(new java.math.BigDecimal("99999999.99")) > 0
        || Math.max(annualPrice.scale(), 0) > 2) {
      throw new IllegalArgumentException("سعر الاشتراك السنوي غير صالح");
    }

    var currency = requireType(values, "currency", String.class);
    if (!currency.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException("عملة الاشتراك يجب أن تكون رمزاً من 3 أحرف كبيرة");
    }

    var rawCliqAlias = values.getOrDefault("cliqAlias", "");
    if (!(rawCliqAlias instanceof String cliqAlias)) {
      throw new IllegalArgumentException("اسم CliQ المستعار غير صالح");
    }
    cliqAlias = cliqAlias.trim();
    if (cliqAlias.length() > 100 || (enabled && cliqAlias.isBlank())) {
      throw new IllegalArgumentException("اسم CliQ المستعار مطلوب وبحد أقصى 100 حرف عند تفعيل الاشتراك");
    }
    values.put("cliqAlias", cliqAlias);

    var billingPeriod = integer(values.getOrDefault("billingPeriodMonths", 12), "billingPeriodMonths");
    if (billingPeriod != 12) throw new IllegalArgumentException("مدة الاشتراك السنوي يجب أن تكون 12 شهراً");
    var freeLimit = integer(values.get("freeLimit"), "freeLimit");
    if (freeLimit != 4) throw new IllegalArgumentException("عدد العناصر المجانية يجب أن يكون 4");

    if (!(values.get("freeContent") instanceof Map<?, ?> freeContent)) {
      throw new IllegalArgumentException("freeContent غير صالح");
    }
    var contentTypes = new LinkedHashSet<String>();
    contentTypes.add("OPPORTUNITY");
    Arrays.stream(com.jameati.catalog.CatalogType.values()).map(Enum::name).forEach(contentTypes::add);
    if (enabled && (!freeContent.keySet().containsAll(contentTypes) || freeContent.size() != contentTypes.size())) {
      throw new IllegalArgumentException("يجب اختيار 4 عناصر مجانية لكل قسم قبل تفعيل الاشتراك");
    }
    for (var entry : freeContent.entrySet()) {
      if (!(entry.getKey() instanceof String key) || !contentTypes.contains(key)) {
        throw new IllegalArgumentException("نوع المحتوى المجاني غير مدعوم");
      }
      if (!(entry.getValue() instanceof Collection<?> ids)
          || (enabled && ids.size() != freeLimit)
          || (!enabled && ids.size() > freeLimit)) {
        throw new IllegalArgumentException(enabled
            ? "يجب اختيار 4 عناصر مجانية لكل قسم قبل تفعيل الاشتراك"
            : "يمكن اختيار 4 عناصر مجانية كحد أقصى لكل قسم");
      }
      var uniqueIds = new HashSet<Long>();
      for (var value : ids) {
        var id = positiveLong(value);
        if (!uniqueIds.add(id)) throw new IllegalArgumentException("معرّفات المحتوى المجاني يجب ألا تتكرر");
      }
    }
  }

  private static java.math.BigDecimal decimal(Object value, String field) {
    if (!(value instanceof Number)) throw new IllegalArgumentException(field + " غير صالح");
    try {
      return new java.math.BigDecimal(value.toString());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " غير صالح");
    }
  }

  private static int integer(Object value, String field) {
    if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " غير صالح");
    var decimal = new java.math.BigDecimal(number.toString());
    try {
      return decimal.intValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(field + " غير صالح");
    }
  }

  private static long positiveLong(Object value) {
    if (!(value instanceof Number number)) throw new IllegalArgumentException("معرّف المحتوى المجاني غير صالح");
    try {
      var id = new java.math.BigDecimal(number.toString()).longValueExact();
      if (id <= 0) throw new ArithmeticException();
      return id;
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("معرّف المحتوى المجاني غير صالح");
    }
  }

  private static <T> T requireType(Map<String, Object> values, String field, Class<T> type) {
    var value = values.get(field);
    if (!type.isInstance(value)) throw new IllegalArgumentException(field + " غير صالح");
    return type.cast(value);
  }
}
