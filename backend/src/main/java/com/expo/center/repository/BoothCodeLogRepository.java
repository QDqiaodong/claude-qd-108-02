package com.expo.center.repository;

import com.expo.center.entity.BoothCodeLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothCodeLogRepository extends JpaRepository<BoothCodeLog, Long> {

    /** 这个号是不是改号退下来的旧号：退下来的号永久停用，谁都不能再用。 */
    boolean existsByOldCode(String oldCode);

    List<BoothCodeLog> findAllByOrderByIdAsc();
}
