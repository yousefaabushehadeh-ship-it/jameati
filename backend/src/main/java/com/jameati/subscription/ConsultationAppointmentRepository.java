package com.jameati.subscription;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConsultationAppointmentRepository extends JpaRepository<ConsultationAppointment, Long> {
  @Query("select a from ConsultationAppointment a where a.deleted = false "
      + "and a.status = :status "
      + "and a.startsAt < :endsAt and a.endsAt > :startsAt")
  List<ConsultationAppointment> findOverlapping(@Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt, @Param("status") ConsultationDtos.AppointmentStatus status);

  default List<ConsultationAppointment> overlapping(Instant startsAt, Instant endsAt) {
    return findOverlapping(startsAt, endsAt, ConsultationDtos.AppointmentStatus.BOOKED);
  }

  List<ConsultationAppointment> findByMembershipUserIdAndDeletedFalseOrderByStartsAtDesc(Long userId);
  List<ConsultationAppointment> findByDeletedFalseOrderByStartsAtDesc();

  boolean existsByMembershipIdAndStatusAndEndsAtAfterAndDeletedFalse(
      Long membershipId, ConsultationDtos.AppointmentStatus status, Instant now);
}
