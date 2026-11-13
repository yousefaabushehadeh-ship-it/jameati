package com.jameati.opportunity;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.Set;

public final class OpportunityDtos {
  private OpportunityDtos() {}
  public record OpportunityResponse(Long id,String slug,String title,String shortDescription,String description,String category,String organization,String country,FundingType funding,LocalDate deadline,String deadlineText,boolean alwaysOpen,boolean featured,boolean published,String coverImage,String officialUrl,String applyUrl,boolean serviceEnabled,BigDecimal servicePrice,String serviceCurrency,String sourceName,String sourceUrl,Instant lastVerifiedAt,long views,long applyClicks,Set<String> specialities,Set<String> nationalities,Set<String> degreeLevels,OpportunityStatus status,long daysRemaining,Instant createdAt,Instant updatedAt,boolean accessLocked,String requiredDocuments,String requirements) {}
  public record UpsertRequest(@NotBlank @Size(max=190) String slug,@NotBlank @Size(max=300) String title,@NotBlank @Size(max=500) String shortDescription,@NotBlank String description,@NotBlank String category,@NotBlank String organization,@NotBlank String country,@NotNull FundingType funding,LocalDate deadline,String deadlineText,OpportunityStatus statusOverride,boolean alwaysOpen,boolean featured,boolean published,String coverImage,@Size(max=500) String officialUrl,@Size(max=500) String applyUrl,boolean serviceEnabled,@DecimalMin("0.01") BigDecimal servicePrice,@Size(max=10) String serviceCurrency,String sourceName,String sourceUrl,Set<String> specialities,Set<String> nationalities,Set<String> degreeLevels) {
    public UpsertRequest {
      if (serviceEnabled && servicePrice == null) throw new IllegalArgumentException("سعر خدمة التقديم مطلوب عند تفعيلها");
    }
    public UpsertRequest(String slug,String title,String shortDescription,String description,String category,String organization,String country,FundingType funding,LocalDate deadline,String deadlineText,OpportunityStatus statusOverride,boolean alwaysOpen,boolean featured,boolean published,String coverImage,String officialUrl,String applyUrl,String sourceName,String sourceUrl,Set<String> specialities,Set<String> nationalities,Set<String> degreeLevels) {
      this(slug,title,shortDescription,description,category,organization,country,funding,deadline,deadlineText,statusOverride,alwaysOpen,featured,published,coverImage,officialUrl,applyUrl,false,null,"USD",sourceName,sourceUrl,specialities,nationalities,degreeLevels);
    }
  }
  public record Filter(String q,String category,String country,String speciality,String nationality,String degree,FundingType funding,OpportunityStatus status) {}
}
