package com.jameati.subscription;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ConsultationScheduleRepository extends JpaRepository<ConsultationSchedule, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ConsultationSchedule s where s.id = 1")
  Optional<ConsultationSchedule> lockCalendar();
}
