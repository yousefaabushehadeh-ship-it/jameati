package com.jameati.importing;

import static com.jameati.importing.OfficialOpportunityDtos.OfficialOpportunityFeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class OfficialOpportunityCatalog {
  private static final List<String> RESOURCES =
      List.of(
          "data/official-opportunities.json",
          "data/official-scholarships.json",
          "data/official-expanded-opportunities.json",
          "data/official-jordan-scholarships.json");
  private final ObjectMapper objectMapper;

  OfficialOpportunityCatalog(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  OfficialOpportunityFeed load() {
    var feeds = RESOURCES.stream().map(this::loadResource).toList();
    var records = new ArrayList<OfficialOpportunityDtos.OfficialOpportunityRecord>();
    feeds.forEach(feed -> records.addAll(feed.records()));
    var componentVersions =
        feeds.stream().map(OfficialOpportunityFeed::version).collect(Collectors.joining("+"));
    var version =
        componentVersions.length() <= 80
            ? componentVersions
            : "official-merged-" + sha256(componentVersions).substring(0, 24);
    var verifiedAt =
        feeds.stream()
            .map(OfficialOpportunityFeed::verifiedAt)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    return new OfficialOpportunityFeed(
        version,
        verifiedAt,
        "فريق تحرير جامعتي — كتالوجات رسمية موثقة",
        List.copyOf(records));
  }

  private String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 غير متاح لحساب إصدار الدفعة", exception);
    }
  }

  private OfficialOpportunityFeed loadResource(String resource) {
    try (var input = new ClassPathResource(resource).getInputStream()) {
      var feed = objectMapper.readValue(input, OfficialOpportunityFeed.class);
      if (feed.version() == null || feed.version().isBlank() || feed.verifiedAt() == null) {
        throw new IllegalStateException(
            "بيانات الدفعة الرسمية تفتقد الإصدار أو تاريخ التحقق: " + resource);
      }
      if (feed.records() == null || feed.records().isEmpty()) {
        throw new IllegalStateException("دفعة الفرص الرسمية فارغة: " + resource);
      }
      return feed;
    } catch (IOException exception) {
      throw new IllegalStateException("تعذر قراءة دفعة الفرص الرسمية: " + resource, exception);
    }
  }
}
