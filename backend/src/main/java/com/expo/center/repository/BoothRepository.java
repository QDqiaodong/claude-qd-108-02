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

    /** 改号 / 排期占展位时锁展位行：同一展位的写操作串行，读到的编号是最新的。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booth b where b.id = :id")
    Optional<Booth> findForUpdate(@Param("id") Long id);
}
