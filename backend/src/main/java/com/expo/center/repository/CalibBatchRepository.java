package com.expo.center.repository;

import com.expo.center.entity.CalibBatch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalibBatchRepository extends JpaRepository<CalibBatch, Long> {

    List<CalibBatch> findAllByOrderByIdDesc();

    /** 标已回厂时锁批次行，两个人同时点回厂只放得过去一个。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CalibBatch b where b.id = :id")
    Optional<CalibBatch> findForUpdate(@Param("id") Long id);
}
