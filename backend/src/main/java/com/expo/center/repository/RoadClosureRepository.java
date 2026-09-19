package com.expo.center.repository;

import com.expo.center.entity.RoadClosure;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadClosureRepository extends JpaRepository<RoadClosure, Long> {

    List<RoadClosure> findAllByOrderByCloseDateDescIdDesc();

    /** 这条排期底下还挂着的有效封道单（待审 / 已批准），排期改状态时要一起作废。 */
    List<RoadClosure> findByBookingIdAndStatusIn(Long bookingId, List<String> statuses);

    /** 审批时锁当前单。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RoadClosure c where c.id = :id")
    Optional<RoadClosure> findForUpdate(@Param("id") Long id);

    /**
     * 同馆、同日、同一条通道上还占着名额的单（待审 / 已批准）。
     * 消防口径：交叉时段整条通道只能封一段，段段不看，只看时间是否交叉。
     * id 升序，撞单时取最早交的那张当「先占着的」。
     * 必须是当前读（FOR UPDATE）：并发提交时各事务在排队等馆行锁之前就已建立读快照，
     * 普通 SELECT 会拿到旧快照、看不到前面事务刚提交的抢单单；当前读强制读最新已提交版本。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RoadClosure c where c.hallId = :hallId "
            + "and c.closeDate = :closeDate and c.channel = :channel "
            + "and c.status in :statuses order by c.id asc")
    List<RoadClosure> findBlockers(@Param("hallId") Long hallId,
                                   @Param("closeDate") java.time.LocalDate closeDate,
                                   @Param("channel") String channel,
                                   @Param("statuses") List<String> statuses);
}
