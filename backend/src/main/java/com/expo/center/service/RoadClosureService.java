package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booking;
import com.expo.center.entity.Booth;
import com.expo.center.entity.ClosureSeq;
import com.expo.center.entity.Hall;
import com.expo.center.entity.RoadClosure;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.ClosureSeqRepository;
import com.expo.center.repository.HallRepository;
import com.expo.center.repository.RoadClosureRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadClosureService {

    /** 还占着通道名额的状态：这两种才参与撞单，已驳回、作废都放名额。 */
    private static final List<String> ACTIVE = List.of("待审", "已批准");

    /** 只有已批准才真正占住通道；值班从一堆待审里只能批出第一张。 */
    private static final List<String> APPROVED_ONLY = List.of("已批准");

    private final RoadClosureRepository closures;
    private final ClosureSeqRepository seq;
    private final BookingRepository bookings;
    private final BoothRepository booths;
    private final HallRepository halls;

    public RoadClosureService(RoadClosureRepository closures, ClosureSeqRepository seq,
                              BookingRepository bookings, BoothRepository booths,
                              HallRepository halls) {
        this.closures = closures;
        this.seq = seq;
        this.bookings = bookings;
        this.booths = booths;
        this.halls = halls;
    }

    public List<RoadClosure> list(Long bookingId, Long hallId, String status, String keyword) {
        return closures.findAllByOrderByCloseDateDescIdDesc().stream()
                .filter(c -> bookingId == null || bookingId.equals(c.bookingId))
                .filter(c -> hallId == null || hallId.equals(c.hallId))
                .filter(c -> status == null || status.isEmpty() || status.equals(c.status))
                .filter(c -> keyword == null || keyword.isEmpty()
                        || c.expoName.contains(keyword) || c.tenant.contains(keyword)
                        || c.channel.contains(keyword) || c.code.contains(keyword))
                .toList();
    }

    /** 同一钟点算不交叉：上一家封到 10:00，下一家 10:00 开始不算撞。 */
    private boolean timeOverlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    /** 撞单提示：把先占着的那家展会名、单号和起止钟点带出来。 */
    private String conflictMessage(RoadClosure blocker) {
        return "按消防口径，同一条卸货通道在交叉时段只能封一段："
                + blocker.closeDate + "「" + blocker.channel + "·" + blocker.segment
                + "」已被先交的「" + blocker.expoName + "」（单号 " + blocker.code
                + "，" + blocker.startTime + "-" + blocker.endTime
                + "，" + blocker.status + "）占着，这张批不过去";
    }

    /** 在已占名额的单里找时段交叉的那张。 */
    private RoadClosure findConflict(Long hallId, LocalDate date, String channel,
                                     LocalTime start, LocalTime end, Long selfId,
                                     List<String> statuses) {
        for (RoadClosure c : closures.findBlockers(hallId, date, channel, statuses)) {
            if (selfId != null && selfId.equals(c.id)) {
                continue;
            }
            if (timeOverlap(start, end, c.startTime, c.endTime)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 发封道单号：先让序列行原子就位，再锁行自增。
     * 全新库两张单同时首报时，不会因为都「查不到就插」而撞序列主键。
     */
    private String nextCode() {
        seq.ensureSeqRow();
        ClosureSeq row = seq.lockRow().orElseThrow(() -> new BizException("单号序列没就位"));
        row.seqValue = row.seqValue + 1;
        seq.save(row);
        return String.format("RD-%04d", row.seqValue);
    }

    /**
     * 承租方申报封道。
     * 只能挑自己那条仍停在「待布展」的排期；停用的馆批不出去所以这里就挡住；
     * 提交当下先撞已占名额的单（含别人待审的），按后交不过处理。
     */
    @Transactional
    public RoadClosure submit(RoadClosure input, String role, String tenant) {
        if (!"承租方".equals(role)) {
            throw new BizException("封道申报只能由承租方提交，场馆值班是审单的，不替承租方申报");
        }
        if (tenant == null || tenant.isBlank()) {
            throw new BizException("没认出是哪家承租方，先在页面顶上选一下承租方");
        }
        if (input.bookingId == null) {
            throw new BizException("要选一条自己的排期，封道单必须挂在排期上");
        }
        if (input.closeDate == null) {
            throw new BizException("要写清哪一天封道");
        }
        if (input.channel == null || input.channel.isBlank()) {
            throw new BizException("要写清封的是哪条卸货通道");
        }
        if (input.segment == null || input.segment.isBlank()) {
            throw new BizException("要写清封通道的哪一段");
        }
        if (input.startTime == null || input.endTime == null) {
            throw new BizException("要写清从几点封到几点");
        }
        if (!input.endTime.isAfter(input.startTime)) {
            throw new BizException("解封钟点要晚于开封钟点");
        }

        // 锁顺序统一为「展馆 → 排期/单据」，跟排期改状态那条事务一致，不会互锁
        Booking booking = bookings.findById(input.bookingId)
                .orElseThrow(() -> new BizException("排期不存在"));
        Booth booth = booths.findById(booking.boothId)
                .orElseThrow(() -> new BizException("排期挂的展位不存在，对不上展馆"));
        Hall hall = halls.findForUpdate(booth.hallId)
                .orElseThrow(() -> new BizException("展馆不存在"));
        // 馆锁住之后再锁排期重读，挡住院审单期间状态被改
        booking = bookings.findForUpdate(booking.id)
                .orElseThrow(() -> new BizException("排期不存在"));
        if (!"待布展".equals(booking.status)) {
            throw new BizException("排期「" + booking.expoName + "」已经是「" + booking.status
                    + "」了，只有仍停在待布展的排期能申报封道");
        }
        if (!tenant.trim().equals(booking.tenant)) {
            throw new BizException("这条排期是「" + booking.tenant
                    + "」的，承租方只能挑自己名下待布展的排期");
        }
        if ("停用".equals(hall.status)) {
            throw new BizException("展馆「" + hall.name + "」已经停用，封道新单批不出去，先别报");
        }

        String channel = input.channel.trim();
        String segment = input.segment.trim();
        RoadClosure blocker = findConflict(hall.id, input.closeDate, channel,
                input.startTime, input.endTime, null, ACTIVE);
        if (blocker != null) {
            throw new BizException(conflictMessage(blocker));
        }

        RoadClosure saved = new RoadClosure();
        saved.code = nextCode();
        saved.bookingId = booking.id;
        // 条上印的展位号取锁后当前读的排期快照：改号若刚走完，这里印的就是新号，
        // 不会印出它刚退掉的旧号；已批准的条子随后也会被改号级联一起换新
        saved.boothCode = booking.boothCode;
        saved.hallId = hall.id;
        saved.expoName = booking.expoName;
        saved.tenant = booking.tenant;
        saved.closeDate = input.closeDate;
        saved.channel = channel;
        saved.segment = segment;
        saved.startTime = input.startTime;
        saved.endTime = input.endTime;
        saved.status = "待审";
        saved.submittedAt = LocalDateTime.now();
        return closures.save(saved);
    }

    /**
     * 场馆值班审单：批准。
     * 批准前在锁里重查排期状态、展馆状态和通道交叉——
     * 两个人抢同一展馆交叉时段时只许一张留下，另一张带着先到的单号失败。
     */
    @Transactional
    public RoadClosure approve(Long id, String role) {
        requireDuty(role);
        RoadClosure c = closures.findById(id)
                .orElseThrow(() -> new BizException("封道单不存在"));
        // 锁顺序「展馆 → 单据」：同一展馆两张抢单的审批在这里串行
        Hall hall = halls.findForUpdate(c.hallId)
                .orElseThrow(() -> new BizException("展馆不存在"));
        c = closures.findForUpdate(id)
                .orElseThrow(() -> new BizException("封道单不存在"));
        if (!"待审".equals(c.status)) {
            throw new BizException("封道单 " + c.code + " 现在是「" + c.status
                    + "」，只有待审的单能批");
        }
        if ("停用".equals(hall.status)) {
            throw new BizException("展馆「" + hall.name
                    + "」已经停用，新单批不出去");
        }
        Booking booking = bookings.findForUpdate(c.bookingId)
                .orElseThrow(() -> new BizException("这张单挂的排期已经没了"));
        if (!"待布展".equals(booking.status)) {
            // 排期在审单期间被改成展出中/已结束：BookingService 改状态的级联已把这张单
            // 作废并写好原因；真走到这里说明作废级联没跑到，直接报死，不批出脏单
            throw new BizException("排期「" + booking.expoName + "」已变为「" + booking.status
                    + "」，封道单 " + c.code + " 不能批准");
        }
        // 审批只认已批准占位：一堆待审里第一张批成已批准，后面的靠馆锁串行 +
        // 当前读看到它，随后在这里被拒并带出先到的单号
        RoadClosure blocker = findConflict(c.hallId, c.closeDate, c.channel,
                c.startTime, c.endTime, c.id, APPROVED_ONLY);
        if (blocker != null) {
            throw new BizException(conflictMessage(blocker));
        }
        c.status = "已批准";
        c.reason = null;
        c.reviewedAt = LocalDateTime.now();
        return closures.save(c);
    }

    /** 场馆值班审单：驳回，驳回原因写在单上。 */
    @Transactional
    public RoadClosure reject(Long id, String reason, String role) {
        requireDuty(role);
        if (reason == null || reason.isBlank()) {
            throw new BizException("驳回要写明原因，承租方得知道为什么没过");
        }
        RoadClosure preview = closures.findById(id)
                .orElseThrow(() -> new BizException("封道单不存在"));
        // 跟批准/提交一致，先锁展馆再锁单据，避免两个审单动作各写一半
        halls.findForUpdate(preview.hallId);
        RoadClosure c = closures.findForUpdate(id)
                .orElseThrow(() -> new BizException("封道单不存在"));
        if (!"待审".equals(c.status)) {
            throw new BizException("封道单 " + c.code + " 现在是「" + c.status
                    + "」，只有待审的单能驳回");
        }
        c.status = "已驳回";
        c.reason = reason.trim();
        c.reviewedAt = LocalDateTime.now();
        return closures.save(c);
    }

    private void requireDuty(String role) {
        if (!"场馆值班".equals(role)) {
            throw new BizException("只有场馆值班能审单：批准和驳回都是值班的权限，承租方不能自己批");
        }
    }
}
