package com.expo.center.repository;

import com.expo.center.entity.CalibSeq;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CalibSeqRepository extends JpaRepository<CalibSeq, Long> {

    /** 全新库首次开批时让序列行原子就位，并发开批不会撞序列主键。 */
    @Modifying
    @Query(value = "INSERT INTO calib_seq (id, seq_value) VALUES (1, 0) "
            + "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void ensureSeqRow();

    /** 发批次号时锁序列行，保证并发开批不发重号。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CalibSeq s where s.id = 1")
    Optional<CalibSeq> lockRow();
}
