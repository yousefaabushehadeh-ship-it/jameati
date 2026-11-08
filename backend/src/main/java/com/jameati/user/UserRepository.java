package com.jameati.user;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface UserRepository extends JpaRepository<User,Long> {
  Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);
  Optional<User> findByIdAndDeletedFalse(Long id);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id=:id and u.deleted=false and u.enabled=true")
  Optional<User> findEnabledByIdForUpdate(@Param("id") Long id);
  boolean existsByEmailIgnoreCase(String email);
  @Query("select u from User u where u.deleted=false and (:q='' or lower(u.email) like lower(concat('%',:q,'%')) or lower(u.fullName) like lower(concat('%',:q,'%')))")
  Page<User> adminSearch(String q, Pageable pageable);
  @Query("select count(distinct u) from User u join u.roles role where u.deleted=false and u.enabled=true and role=com.jameati.user.RoleName.SUPER_ADMIN")
  long countEnabledSuperAdmins();
  Slice<User> findByEnabledTrueAndDeletedFalseOrderByIdAsc(Pageable pageable);
}
