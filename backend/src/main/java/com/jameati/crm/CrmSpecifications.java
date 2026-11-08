package com.jameati.crm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.jpa.domain.Specification;

final class CrmSpecifications {
  private CrmSpecifications() {}

  static Specification<StudentLead> leads(
      String query,
      LeadStatus status,
      String source,
      Long ownerId,
      CrmPaymentStatus paymentStatus,
      FollowUpBucket followUpBucket,
      ZoneId zone) {
    return (root, ignored, cb) -> {
      var predicate = cb.isFalse(root.get("deleted"));
      var q = query == null ? "" : query.trim().toLowerCase();
      if (!q.isBlank()) {
        var like = "%" + q + "%";
        var code = q.toUpperCase().replace("L-", "");
        var textMatch = cb.or(
            cb.like(cb.lower(root.get("fullName")), like),
            cb.like(cb.lower(root.get("email")), like),
            cb.like(cb.lower(root.get("phone")), like),
            cb.like(cb.lower(root.get("major")), like));
        try {
          predicate = cb.and(predicate, cb.or(textMatch, cb.equal(root.get("id"), Long.valueOf(code))));
        } catch (NumberFormatException ignoredCode) {
          predicate = cb.and(predicate, textMatch);
        }
      }
      if (status != null) predicate = cb.and(predicate, cb.equal(root.get("status"), status));
      if (source != null && !source.isBlank()) {
        predicate = cb.and(predicate, cb.equal(cb.lower(root.get("source")), source.trim().toLowerCase()));
      }
      if (ownerId != null) {
        predicate = ownerId == 0
            ? cb.and(predicate, cb.isNull(root.get("owner")))
            : cb.and(predicate, cb.equal(root.get("owner").get("id"), ownerId));
      }
      if (paymentStatus != null) {
        predicate = cb.and(predicate, cb.equal(root.get("paymentStatus"), paymentStatus));
      }
      return withFollowUpBucket(predicate, root.get("nextFollowUpAt"), followUpBucket, zone, cb);
    };
  }

  static Specification<B2bAccount> accounts(
      String query,
      B2bStage stage,
      OrganizationType type,
      Long ownerId,
      FollowUpBucket followUpBucket,
      ZoneId zone) {
    return (root, ignored, cb) -> {
      var predicate = cb.isFalse(root.get("deleted"));
      var q = query == null ? "" : query.trim().toLowerCase();
      if (!q.isBlank()) {
        var like = "%" + q + "%";
        predicate = cb.and(predicate, cb.or(
            cb.like(cb.lower(root.get("organization")), like),
            cb.like(cb.lower(root.get("contactPerson")), like),
            cb.like(cb.lower(root.get("email")), like),
            cb.like(cb.lower(root.get("phone")), like)));
      }
      if (stage != null) predicate = cb.and(predicate, cb.equal(root.get("stage"), stage));
      if (type != null) predicate = cb.and(predicate, cb.equal(root.get("type"), type));
      if (ownerId != null) {
        predicate = ownerId == 0
            ? cb.and(predicate, cb.isNull(root.get("owner")))
            : cb.and(predicate, cb.equal(root.get("owner").get("id"), ownerId));
      }
      return withFollowUpBucket(predicate, root.get("nextFollowUpAt"), followUpBucket, zone, cb);
    };
  }

  static Specification<CrmFollowUp> followUps(
      FollowUpBucket bucket, Long ownerId, CrmTargetType targetType, ZoneId zone) {
    return (root, ignored, cb) -> {
      var predicate = cb.isFalse(root.get("deleted"));
      var effective = bucket == null ? FollowUpBucket.ALL : bucket;
      var day = LocalDate.now(zone);
      var start = day.atStartOfDay(zone).toInstant();
      var end = day.plusDays(1).atStartOfDay(zone).toInstant();
      predicate = switch (effective) {
        case OVERDUE -> cb.and(predicate,
            cb.equal(root.get("status"), FollowUpStatus.PENDING), cb.lessThan(root.get("dueAt"), start));
        case TODAY -> cb.and(predicate,
            cb.equal(root.get("status"), FollowUpStatus.PENDING),
            cb.greaterThanOrEqualTo(root.get("dueAt"), start), cb.lessThan(root.get("dueAt"), end));
        case UPCOMING -> cb.and(predicate,
            cb.equal(root.get("status"), FollowUpStatus.PENDING), cb.greaterThanOrEqualTo(root.get("dueAt"), end));
        case DONE -> cb.and(predicate, cb.equal(root.get("status"), FollowUpStatus.DONE));
        case ALL -> predicate;
      };
      if (ownerId != null) {
        predicate = ownerId == 0
            ? cb.and(predicate, cb.isNull(root.get("owner")))
            : cb.and(predicate, cb.equal(root.get("owner").get("id"), ownerId));
      }
      if (targetType == CrmTargetType.STUDENT) predicate = cb.and(predicate, cb.isNotNull(root.get("studentLead")));
      if (targetType == CrmTargetType.B2B) predicate = cb.and(predicate, cb.isNotNull(root.get("b2bAccount")));
      return predicate;
    };
  }

  private static <T> jakarta.persistence.criteria.Predicate withFollowUpBucket(
      jakarta.persistence.criteria.Predicate predicate,
      jakarta.persistence.criteria.Path<Instant> path,
      FollowUpBucket bucket,
      ZoneId zone,
      jakarta.persistence.criteria.CriteriaBuilder cb) {
    if (bucket == null || bucket == FollowUpBucket.ALL || bucket == FollowUpBucket.DONE) return predicate;
    var day = LocalDate.now(zone);
    var start = day.atStartOfDay(zone).toInstant();
    var end = day.plusDays(1).atStartOfDay(zone).toInstant();
    return switch (bucket) {
      case OVERDUE -> cb.and(predicate, cb.lessThan(path, start));
      case TODAY -> cb.and(predicate, cb.greaterThanOrEqualTo(path, start), cb.lessThan(path, end));
      case UPCOMING -> cb.and(predicate, cb.greaterThanOrEqualTo(path, end));
      default -> predicate;
    };
  }
}
