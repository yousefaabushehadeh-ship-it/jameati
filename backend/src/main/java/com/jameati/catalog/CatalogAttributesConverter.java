package com.jameati.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.*;

@Converter
public class CatalogAttributesConverter implements AttributeConverter<Map<String, Object>, String> {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, Object> value) {
    if (value == null || value.isEmpty()) return null;
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("خصائص المحتوى غير صالحة", exception);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String value) {
    if (value == null || value.isBlank()) return new LinkedHashMap<>();
    try {
      return JSON.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("تعذر قراءة خصائص المحتوى", exception);
    }
  }
}
