package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cv_documents")
@Getter
@Setter
@NoArgsConstructor
public class CvDocument extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(nullable = false, length = 12)
  private String language;

  @Column(name = "template_key", nullable = false, length = 60)
  private String templateKey;

  @Column(name = "content_json", nullable = false, columnDefinition = "json")
  private String contentJson;

  @Column(name = "primary_document", nullable = false)
  private boolean primaryDocument;
}
