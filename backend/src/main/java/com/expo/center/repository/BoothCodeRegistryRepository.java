package com.expo.center.repository;

import com.expo.center.entity.BoothCodeRegistry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothCodeRegistryRepository extends JpaRepository<BoothCodeRegistry, Long> {

    Optional<BoothCodeRegistry> findByCode(String code);

    boolean existsByCode(String code);

    /** 某展位当前在用的登记行（正常恰好一行）。 */
    List<BoothCodeRegistry> findByBoothIdAndStatus(Long boothId, String status);

    List<BoothCodeRegistry> findAllByOrderByIdDesc();
}
