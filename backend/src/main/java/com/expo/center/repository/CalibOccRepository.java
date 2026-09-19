package com.expo.center.repository;

import com.expo.center.entity.CalibOcc;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalibOccRepository extends JpaRepository<CalibOcc, Long> {

    List<CalibOcc> findByBatchIdOrderByIdAsc(Long batchId);

    List<CalibOcc> findByLoanIdAndStatusIn(Long loanId, List<String> statuses);

    List<CalibOcc> findByStatusIn(List<String> statuses);

    /** 值班按本馆筛批次：哪些批次的占用行沾了这个馆。 */
    @Query("select distinct o.batchId from CalibOcc o where o.hallId = :hallId")
    List<Long> findBatchIdsByHallId(@Param("hallId") Long hallId);

    /** 每类展具现在还挂在校准里的件数（校准中 + 待归还厂），展具卡显示用。 */
    @Query("select o.equipmentId, sum(o.quantity) from CalibOcc o "
            + "where o.status in :statuses group by o.equipmentId")
    List<Object[]> sumGroupByEquipmentAndStatusIn(@Param("statuses") List<String> statuses);

    /** 某一类展具现在挂在校准里的件数，改总数、收回时校验用；没有占用行时 sum 为 null。 */
    @Query("select sum(o.quantity) from CalibOcc o "
            + "where o.equipmentId = :equipmentId and o.status in :statuses")
    Long sumByEquipmentAndStatusIn(@Param("equipmentId") Long equipmentId,
                                   @Param("statuses") List<String> statuses);

    /**
     * 排期结束时把还挂在校准里的占用改成待归还厂。
     * 条件更新自带状态判断：批次若已抢先回厂（行已变已回厂），这里一行也扫不动，
     * 不会把回厂的行改回待归还厂。
     */
    @Modifying
    @Query("update CalibOcc o set o.status = '待归还厂' "
            + "where o.bookingId = :bookingId and o.status = '校准中'")
    int markPendingBackByBooking(@Param("bookingId") Long bookingId);
}
