package com.expo.center.repository;

import com.expo.center.entity.EquipLoan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipLoanRepository extends JpaRepository<EquipLoan, Long> {

    List<EquipLoan> findByEquipmentIdAndBookingIdAndStatus(Long equipmentId, Long bookingId,
                                                           String status);

    List<EquipLoan> findByBookingIdAndStatus(Long bookingId, String status);

    List<EquipLoan> findByEquipmentIdAndStatus(Long equipmentId, String status);

    List<EquipLoan> findAllByOrderByIdDesc();

    /** 开校准批占用借用行时锁这一行，两批同时占同一条借用不会占超。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from EquipLoan l where l.id = :id")
    Optional<EquipLoan> findForUpdate(@Param("id") Long id);
}
