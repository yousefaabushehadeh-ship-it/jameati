package com.jameati.support;

import com.jameati.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "contact_messages",
    indexes = {
      @Index(name = "idx_contact_message_status_created", columnList = "status,created_at"),
      @Index(name = "idx_contact_message_email", columnList = "email")
    })
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage extends BaseEntity {
  @Column(name = "reference_code", nullable = false, unique = true, length = 24)
  private String referenceCode;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, length = 190)
  private String email;

  @Column(nullable = false, length = 30)
  private String subject;

  @Column(name = "page_url", length = 500)
  private String pageUrl;

  @Column(nullable = false, columnDefinition = "text")
  private String message;

  @Column(nullable = false)
  private boolean consent;

  @Column(nullable = false, length = 30)
  private String status = "NEW";
}
