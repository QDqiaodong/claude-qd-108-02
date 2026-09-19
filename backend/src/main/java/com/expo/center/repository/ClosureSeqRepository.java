package com.expo.center.repository;

import com.expo.center.entity.ClosureSeq;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClosureSeqRepository extends JpaRepository<ClosureSeq, Long> {

    /** 全新库首报时让序列行原子就位，并发首报不会撞序列主键。 */
    @Modifying
    @Query(value = "INSERT INTO closure_seq (id, seq_value) VALUES (1, 0) "
            + "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void ensureSeqRow();

    /** 发封道单号时锁序列行，保证并发提交不发重号。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClosureSeq s where s.id = 1")
    Optional<ClosureSeq> lockRow();
}
