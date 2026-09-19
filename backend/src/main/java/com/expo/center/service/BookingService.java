package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booking;
import com.expo.center.entity.Booth;
import com.expo.center.entity.RoadClosure;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.CalibOccRepository;
import com.expo.center.repository.HallRepository;
import com.expo.center.repository.RoadClosureRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final BoothRepository booths;
    private final HallRepository halls;
    private final RoadClosureRepository closures;
    private final CalibOccRepository calibOccs;

    public BookingService(BookingRepository bookings, BoothRepository booths,
                          HallRepository halls, RoadClosureRepository closures,
                          CalibOccRepository calibOccs) {
        this.bookings = bookings;
        this.booths = booths;
        this.halls = halls;
        this.closures = closures;
        this.calibOccs = calibOccs;
    }

    public List<Booking> list(Long boothId, String status, String expoName) {
        return bookings.findAllByOrderByStartDateDesc().stream()
                .filter(b -> boothId == null || boothId.equals(b.boothId))
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .filter(b -> expoName == null || expoName.isEmpty()
                        || b.expoName.contains(expoName) || b.tenant.contains(expoName))
                .toList();
    }

    private boolean overlap(Booking busy, LocalDate start, LocalDate end) {
        return !busy.startDate.isAfter(end) && !start.isAfter(busy.endDate);
    }

    @Transactional
    public Booking create(Booking input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("排期单号不能为空");
        }
        if (bookings.existsByCode(input.code)) {
            throw new BizException("排期单号 " + input.code + " 已经用过了");
        }
        if (input.boothId == null) {
            throw new BizException("请选择展位");
        }
        if (input.expoName == null || input.expoName.isBlank()) {
            throw new BizException("要填展会名称");
        }
        if (input.tenant == null || input.tenant.isBlank()) {
            throw new BizException("要填承租方");
        }
        if (input.startDate == null || input.endDate == null) {
            throw new BizException("开展日期和结束日期都要填");
        }
        if (!input.endDate.isAfter(input.startDate)) {
            throw new BizException("结束日期要晚于开展日期");
        }
        // 锁展位行 + 当前读：改号事务若正在走，这里等它走完再读到新号，
        // 确认函上印的展位号不会是它刚退掉的旧号；同展位的排期也借此串行，重叠校验不看旧快照
        Booth booth = booths.findForUpdate(input.boothId)
                .orElseThrow(() -> new BizException("展位不存在"));
        if ("维修".equals(booth.status)) {
            throw new BizException("展位 " + booth.code + " 正在维修，排不了");
        }
        for (Booking busy : bookings.findByBoothIdAndStatusNot(booth.id, "已结束")) {
            if (overlap(busy, input.startDate, input.endDate)) {
                throw new BizException("展位 " + booth.code + " 在 " + busy.startDate + " 到 "
                        + busy.endDate + " 已经租给「" + busy.expoName + "」了，日期撞上了");
            }
        }
        Booking saved = new Booking();
        saved.code = input.code.trim();
        saved.boothId = booth.id;
        saved.boothCode = booth.code;
        saved.expoName = input.expoName.trim();
        saved.tenant = input.tenant.trim();
        saved.startDate = input.startDate;
        saved.endDate = input.endDate;
        saved.status = "待布展";
        booth.status = "已租";
        booths.save(booth);
        return bookings.save(saved);
    }

    @Transactional
    public Booking update(Long id, Booking input) {
        Booking preview = bookings.findById(id).orElseThrow(() -> new BizException("排期不存在"));
        Booth boothOfBooking = booths.findById(preview.boothId)
                .orElseThrow(() -> new BizException("展位不存在"));
        // 先锁展馆、再锁排期，跟封道提交/审单的加锁顺序一致，两边并发不会互锁
        halls.findForUpdate(boothOfBooking.hallId);
        Booking b = bookings.findForUpdate(id).orElseThrow(() -> new BizException("排期不存在"));
        if ("已结束".equals(b.status) && input.status == null) {
            throw new BizException("这条排期已经结束了，改不了");
        }
        if (input.tenant != null && !input.tenant.isBlank()) {
            b.tenant = input.tenant.trim();
        }
        if (input.expoName != null && !input.expoName.isBlank()) {
            b.expoName = input.expoName.trim();
        }
        LocalDate start = input.startDate == null ? b.startDate : input.startDate;
        LocalDate end = input.endDate == null ? b.endDate : input.endDate;
        if (!end.isAfter(start)) {
            throw new BizException("结束日期要晚于开展日期");
        }
        if (!start.equals(b.startDate) || !end.equals(b.endDate)) {
            for (Booking busy : bookings.findByBoothIdAndStatusNot(b.boothId, "已结束")) {
                if (!busy.id.equals(b.id) && overlap(busy, start, end)) {
                    throw new BizException("改完之后跟「" + busy.expoName + "」的 "
                            + busy.startDate + " 到 " + busy.endDate + " 撞上了");
                }
            }
            b.startDate = start;
            b.endDate = end;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(b.status)) {
            String oldStatus = b.status;
            b.status = input.status;
            if ("展出中".equals(input.status) || "已结束".equals(input.status)) {
                // 排期一旦离开待布展，这条排期底下待审、已批准的封道单全部作废，
                // 原因写在单上；作废单不再占那天的通道名额
                voidClosures(b, oldStatus, input.status);
            }
            if ("已结束".equals(input.status)) {
                // 排期结束还挂在校准里的占用改成待归还厂：灯还在厂里，可借不放，
                // 等批次回厂时才加回可借。条件更新只扫「校准中」的行，
                // 已回厂的行不会被改回来
                calibOccs.markPendingBackByBooking(b.id);
                Booth booth = booths.findById(b.boothId)
                        .orElseThrow(() -> new BizException("展位不存在"));
                boolean others = bookings.findByBoothIdAndStatusNot(booth.id, "已结束").stream()
                        .anyMatch(x -> !x.id.equals(b.id));
                booth.status = others ? "已租" : "空闲";
                booths.save(booth);
            }
        }
        return bookings.save(b);
    }

    /** 把一条排期名下还占着名额的封道单全部作废，原因留痕。 */
    private void voidClosures(Booking b, String oldStatus, String newStatus) {
        List<RoadClosure> active = closures.findByBookingIdAndStatusIn(
                b.id, List.of("待审", "已批准"));
        if (active.isEmpty()) {
            return;
        }
        String reason = "排期已由「" + oldStatus + "」改为「" + newStatus
                + "」，封道单自动作废，不再占用当天通道名额";
        LocalDateTime now = LocalDateTime.now();
        for (RoadClosure c : active) {
            c.status = "作废";
            c.reason = reason;
            c.reviewedAt = now;
            closures.save(c);
        }
    }
}
