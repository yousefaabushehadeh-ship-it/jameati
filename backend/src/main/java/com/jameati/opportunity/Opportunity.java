package com.jameati.opportunity;

import com.jameati.common.BaseEntity;
import com.jameati.common.ArabicTitleSupport;
import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.*;

@Entity @Table(name="opportunities",indexes={@Index(name="idx_opportunity_slug",columnList="slug",unique=true),@Index(name="idx_opportunity_deadline",columnList="deadline"),@Index(name="idx_opportunity_published",columnList="published,createdAt")})
@Getter @Setter @NoArgsConstructor
public class Opportunity extends BaseEntity {
  @Version @Column(nullable=false) private long version;
  @Column(nullable=false,unique=true,length=190) private String slug;
  @Column(nullable=false,length=300) private String title;
  @Column(nullable=false) private boolean arabicTitle;
  @Column(nullable=false,length=500) private String shortDescription;
  @Column(nullable=false,columnDefinition="text") private String description;
  @Column(nullable=false,length=100) private String category;
  @Column(nullable=false,length=180) private String organization;
  @Column(nullable=false,length=100) private String country;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private FundingType funding;
  private LocalDate deadline;
  @Column(length=120) private String deadlineText;
  @Enumerated(EnumType.STRING) @Column(length=30) private OpportunityStatus statusOverride;
  @Column(nullable=false) private boolean alwaysOpen;
  @Column(nullable=false) private boolean featured;
  @Column(nullable=false) private boolean published;
  @Column(length=500) private String coverImage;
  @Column(length=500) private String officialUrl;
  @Column(length=500) private String applyUrl;
  @Column(nullable=false) private boolean serviceEnabled;
  @Column(precision=10,scale=2) private BigDecimal servicePrice;
  @Column(nullable=false,length=10) private String serviceCurrency="USD";
  @Column(length=180) private String sourceName;
  @Column(length=500) private String sourceUrl;
  @Column(length=190) private String sourceExternalKey;
  @Column(length=64) private String sourceHash;
  private Instant lastVerifiedAt;
  private Instant lastImportedAt;
  @Column(length=120) private String verifiedBy;
  @Column(nullable=false) private long views;
  @Column(nullable=false) private long applyClicks;
  @ElementCollection @CollectionTable(name="opportunity_specialities",joinColumns=@JoinColumn(name="opportunity_id")) @Column(name="speciality") private Set<String> specialities=new HashSet<>();
  @ElementCollection @CollectionTable(name="opportunity_nationalities",joinColumns=@JoinColumn(name="opportunity_id")) @Column(name="nationality") private Set<String> nationalities=new HashSet<>();
  @ElementCollection @CollectionTable(name="opportunity_degree_levels",joinColumns=@JoinColumn(name="opportunity_id")) @Column(name="degree_level") private Set<String> degreeLevels=new HashSet<>();
  @PrePersist @PreUpdate void normalizeService(){arabicTitle=ArabicTitleSupport.startsWithArabicLetter(title);if(serviceCurrency==null||serviceCurrency.isBlank())serviceCurrency="USD";if(!serviceEnabled)servicePrice=null;}
}
