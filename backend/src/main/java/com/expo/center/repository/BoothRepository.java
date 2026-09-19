package com.expo.center.repository;

import com.expo.center.entity.Booth;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    boolean existsByCode(String code);

    List<Booth> findByHallId(Long hallId);

    List<Booth> findAllByOrderByIdAsc();
}
