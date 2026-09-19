package com.expo.center.repository;

import com.expo.center.entity.Hall;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HallRepository extends JpaRepository<Hall, Long> {

    boolean existsByCode(String code);

    List<Hall> findAllByOrderByIdAsc();

    /** 封道提交/审批时锁展馆行：同一展馆的封道写操作全部串起来，抢单不留脏记录。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hall h where h.id = :id")
    Optional<Hall> findForUpdate(@Param("id") Long id);
}
