package com.jameati.catalog;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;

public final class CatalogAdminDtos {
  private CatalogAdminDtos() {}

  public record UpsertRequest(
      @NotBlank @Size(max = 190)
          @Pattern(regexp = "^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$", message = "الرابط المختصر غير صالح")
          String slug,
      @NotBlank @Size(max = 300) String title,
      @NotBlank @Size(max = 500) String summary,
      @NotBlank @Size(max = 100000) String content,
      @NotBlank @Size(max = 100) String category,
      @Size(max = 180) String organization,
      @Size(max = 100) String country,
      @Size(max = 500) String image,
      @Size(max = 500) String website,
      boolean featured,
      boolean published,
      @DecimalMin("0.0") @DecimalMax("5.0") Double rating,
      @Size(max = 80) String meta,
      @Size(max = 40) Map<@Size(max = 80) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$") String, Object> attributes) {}

  public record ItemResponse(
      Long id,
      CatalogType type,
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
      boolean published,
      long views,
      Double rating,
      String meta,
      Map<String, Object> attributes,
      Instant createdAt,
      Instant updatedAt) {
    static ItemResponse of(CatalogItem item) {
      return new ItemResponse(
          item.getId(), item.getType(), item.getSlug(), item.getTitle(), item.getSummary(),
          item.getContent(), item.getCategory(), item.getOrganization(), item.getCountry(),
          item.getImage(), item.getWebsite(), item.isFeatured(), item.isPublished(),
          item.getViews(), item.getRating(), item.getMeta(), item.getAttributes(), item.getCreatedAt(), item.getUpdatedAt());
    }
  }
}
