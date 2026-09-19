package com.expo.center.repository;

import com.expo.center.entity.Booking;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByCode(String code);

    List<Booking> findByBoothIdAndStatusNot(Long boothId, String status);

    List<Booking> findAllByOrderByStartDateDesc();

    /** 排期改状态/封道审批时锁排期行，读到的状态是最新的，不会拿旧状态放行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);
}
