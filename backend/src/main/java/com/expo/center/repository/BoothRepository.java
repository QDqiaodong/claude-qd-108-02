package com.expo.center.repository;

import com.expo.center.entity.Booth;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    boolean existsByCode(String code);

    List<Booth> findByHallId(Long hallId);

    List<Booth> findAllByOrderByIdAsc();

    /** 改号时锁展位行（当前读）：两人同时改同一个展位的号，在这里串行，只留一条有效编号。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booth b where b.id = :id")
    Optional<Booth> findForUpdate(@Param("id") Long id);
}
