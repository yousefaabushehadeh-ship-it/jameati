package com.jameati.catalog;

import com.jameati.common.BaseEntity;
import com.jameati.common.ArabicTitleSupport;
import jakarta.persistence.*;
import java.util.*;
import lombok.*;

@Entity @Table(name="catalog_items",uniqueConstraints=@UniqueConstraint(columnNames={"type","slug"}),indexes=@Index(name="idx_catalog_type_published",columnList="type,published"))
@Getter @Setter @NoArgsConstructor
public class CatalogItem extends BaseEntity {
  @Version @Column(nullable=false) private long version;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private CatalogType type;
  @Column(nullable=false,length=190) private String slug;
  @Column(nullable=false,length=300) private String title;
  @Column(nullable=false) private boolean arabicTitle;
  @Column(nullable=false,length=500) private String summary;
  @Column(nullable=false,columnDefinition="text") private String content;
  @Column(nullable=false,length=100) private String category;
  @Column(length=180) private String organization;
  @Column(length=100) private String country;
  @Column(length=500) private String image;
  @Column(length=500) private String website;
  @Column(nullable=false) private boolean featured;
  @Column(nullable=false) private boolean published=true;
  @Column(nullable=false) private long views;
  private Double rating;
  @Column(length=80) private String meta;
  @Convert(converter=CatalogAttributesConverter.class)
  @Column(name="attributes_json",columnDefinition="text") private Map<String,Object> attributes = new LinkedHashMap<>();
  @PrePersist @PreUpdate void classifyTitle(){arabicTitle=ArabicTitleSupport.startsWithArabicLetter(title);}
}
