package com.expo.center.repository;

import com.expo.center.entity.Equipment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    boolean existsByCode(String code);

    List<Equipment> findAllByOrderByIdAsc();

    /** 开批扣可借、回厂加可借时锁展具行，并发批改可借不会互相覆盖。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findForUpdate(@Param("id") Long id);
}
