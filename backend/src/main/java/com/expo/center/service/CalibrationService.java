package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booking;
import com.expo.center.entity.Booth;
import com.expo.center.entity.CalibBatch;
import com.expo.center.entity.CalibOcc;
import com.expo.center.entity.CalibSeq;
import com.expo.center.entity.EquipLoan;
import com.expo.center.entity.Equipment;
import com.expo.center.entity.Hall;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.CalibBatchRepository;
import com.expo.center.repository.CalibOccRepository;
import com.expo.center.repository.CalibSeqRepository;
import com.expo.center.repository.EquipLoanRepository;
import com.expo.center.repository.EquipmentRepository;
import com.expo.center.repository.HallRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalibrationService {

    /** 还占着可借的占用状态：校准中、待归还厂都不放可借，新排期借不到这部分。 */
    public static final List<String> HOLDING = List.of("校准中", "待归还厂");

    /** 占用能挂的排期状态：待布展、展出中；已结束的排期挂不上新占用。 */
    private static final List<String> OCCUPYABLE = List.of("待布展", "展出中");

    private final CalibBatchRepository batches;
    private final CalibOccRepository occs;
    private final CalibSeqRepository seq;
    private final EquipmentRepository equipments;
    private final EquipLoanRepository loans;
    private final BookingRepository bookings;
    private final BoothRepository booths;
    private final HallRepository halls;

    public CalibrationService(CalibBatchRepository batches, CalibOccRepository occs,
                              CalibSeqRepository seq, EquipmentRepository equipments,
                              EquipLoanRepository loans, BookingRepository bookings,
                              BoothRepository booths, HallRepository halls) {
        this.batches = batches;
        this.occs = occs;
        this.seq = seq;
        this.equipments = equipments;
        this.loans = loans;
        this.bookings = bookings;
        this.booths = booths;
        this.halls = halls;
    }

    /** 开批请求里的一条占用：点名哪条借用行、占几件。 */
    public static class OccLine {
        public Long loanId;
        public Integer quantity;
    }

    /** 批次明细：批次 + 占用行。 */
    public static class BatchDetail {
        public CalibBatch batch;
        public List<CalibOcc> lines;

        BatchDetail(CalibBatch batch, List<CalibOcc> lines) {
            this.batch = batch;
            this.lines = lines;
        }
    }

    /** 开批对话框里的候选借用行：还能占多少、展馆是否已关停都摆出来。 */
    public static class Candidate {
        public Long loanId;
        public Long equipmentId;
        public String equipmentName;
        public Long bookingId;
        public String expoName;
        public String bookingStatus;
        public Long hallId;
        public String hallName;
        public String hallStatus;
        public Integer quantity;
        public Integer holding;
        public Integer remain;
    }

    /** 批次列表：库房看全部；值班只能看沾了本馆占用行的批次。 */
    public List<CalibBatch> list(String role, Long hallId, String status) {
        List<CalibBatch> all = batches.findAllByOrderByIdDesc();
        if (isDuty(role)) {
            Long hid = requireHall(hallId);
            List<Long> ids = occs.findBatchIdsByHallId(hid);
            all = all.stream().filter(b -> ids.contains(b.id)).toList();
        }
        return all.stream()
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .toList();
    }

    /** 批次明细：值班只能打开沾了本馆占用行的批次。 */
    public BatchDetail detail(Long id, String role, Long hallId) {
        CalibBatch b = batches.findById(id).orElseThrow(() -> new BizException("校准批次不存在"));
        List<CalibOcc> lines = occs.findByBatchIdOrderByIdAsc(id);
        if (isDuty(role)) {
            Long hid = requireHall(hallId);
            boolean related = lines.stream().anyMatch(o -> hid.equals(o.hallId));
            if (!related) {
                throw new BizException("批次 " + b.code + " 没有占用你本馆的借用行，值班只能看本馆相关批次");
            }
        }
        return new BatchDetail(b, lines);
    }

    /** 开批候选：这一类展具、仍挂在待布展/展出中排期上的借用行，逐条算出还能占几件。 */
    public List<Candidate> candidates(String kind, String role) {
        requireWarehouse(role);
        Map<Long, Equipment> eqMap = equipments.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(e -> e.id, Function.identity()));
        Map<Long, Booking> bkMap = bookings.findAllByOrderByStartDateDesc().stream()
                .collect(Collectors.toMap(b -> b.id, Function.identity()));
        Map<Long, Booth> boothMap = booths.findAll().stream()
                .collect(Collectors.toMap(b -> b.id, Function.identity()));
        Map<Long, Hall> hallMap = halls.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(h -> h.id, Function.identity()));
        Map<Long, Integer> holdMap = new HashMap<>();
        for (CalibOcc o : occs.findByStatusIn(HOLDING)) {
            holdMap.merge(o.loanId, o.quantity, Integer::sum);
        }
        List<Candidate> out = new ArrayList<>();
        for (EquipLoan l : loans.findAllByOrderByIdDesc()) {
            if (!"借用中".equals(l.status)) {
                continue;
            }
            Equipment e = eqMap.get(l.equipmentId);
            if (e == null || (kind != null && !kind.isBlank() && !kind.equals(e.kind))) {
                continue;
            }
            Booking b = bkMap.get(l.bookingId);
            if (b == null || !OCCUPYABLE.contains(b.status)) {
                continue;
            }
            Booth booth = boothMap.get(b.boothId);
            Hall hall = booth == null ? null : hallMap.get(booth.hallId);
            Candidate c = new Candidate();
            c.loanId = l.id;
            c.equipmentId = e.id;
            c.equipmentName = e.name;
            c.bookingId = b.id;
            c.expoName = b.expoName;
            c.bookingStatus = b.status;
            c.hallId = hall == null ? null : hall.id;
            c.hallName = hall == null ? "?" : hall.name;
            c.hallStatus = hall == null ? "?" : hall.status;
            c.quantity = l.quantity;
            c.holding = holdMap.getOrDefault(l.id, 0);
            c.remain = l.quantity - c.holding;
            out.add(c);
        }
        return out;
    }

    /**
     * 库房开校准批次：选类别、件数、预计回厂日，并点名占用哪几条借用行。
     * 占用件数当场从可借里扣掉，新排期借不到这部分；
     * 借用行本身一件不动，租金照旧按借用件数算。
     */
    @Transactional
    public CalibBatch create(String kind, Integer quantity, LocalDate expectBackDate,
                             List<OccLine> lines, String role) {
        requireWarehouse(role);
        if (kind == null || kind.isBlank()) {
            throw new BizException("要选这一批校准的展具类别");
        }
        kind = kind.trim();
        if (quantity == null || quantity <= 0) {
            throw new BizException("本批件数要大于 0");
        }
        if (expectBackDate == null) {
            throw new BizException("要填预计回厂日");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BizException("要点名占用哪几条借用行，只填个件数开不了批");
        }
        Set<Long> seen = new HashSet<>();
        for (OccLine l : lines) {
            if (l.loanId == null) {
                throw new BizException("占用行要点明是哪条借用");
            }
            if (!seen.add(l.loanId)) {
                throw new BizException("借用行 " + l.loanId + " 一批里点重了，件数合在一起写一行");
            }
            if (l.quantity == null || l.quantity <= 0) {
                throw new BizException("每条占用的件数要大于 0");
            }
        }
        int sum = lines.stream().mapToInt(l -> l.quantity).sum();
        if (sum != quantity) {
            throw new BizException("各占用行合计 " + sum + " 件，跟本批件数 " + quantity
                    + " 对不上，点齐再开");
        }

        // 锁顺序统一为「借用行 → 展具」，两边都按 id 从小到大，并发开批不会互锁
        Map<Long, EquipLoan> loanMap = new HashMap<>();
        for (Long loanId : lines.stream().map(l -> l.loanId).sorted().toList()) {
            EquipLoan loan = loans.findForUpdate(loanId)
                    .orElseThrow(() -> new BizException("借用行 " + loanId + " 不存在"));
            loanMap.put(loanId, loan);
        }
        Map<Long, Equipment> eqMap = new HashMap<>();
        for (Long eqId : loanMap.values().stream().map(l -> l.equipmentId).distinct().sorted()
                .toList()) {
            Equipment e = equipments.findForUpdate(eqId)
                    .orElseThrow(() -> new BizException("展具不存在"));
            eqMap.put(eqId, e);
        }

        List<CalibOcc> toSave = new ArrayList<>();
        Map<Long, Integer> deduct = new HashMap<>();
        for (OccLine l : lines) {
            EquipLoan loan = loanMap.get(l.loanId);
            if (!"借用中".equals(loan.status)) {
                throw new BizException("借用行 " + loan.id + " 已经是「" + loan.status
                        + "」了，只有借用中的行能被校准占用");
            }
            Booking booking = bookings.findById(loan.bookingId)
                    .orElseThrow(() -> new BizException("借用行挂的排期不存在"));
            if (!OCCUPYABLE.contains(booking.status)) {
                throw new BizException("排期「" + booking.expoName + "」已经是「" + booking.status
                        + "」，校准只能占用仍挂在待布展或展出中排期的借用行");
            }
            Equipment e = eqMap.get(loan.equipmentId);
            if (!kind.equals(e.kind)) {
                throw new BizException("借用行 " + loan.id + " 是「" + e.kind + "」的「" + e.name
                        + "」，跟本批类别「" + kind + "」对不上");
            }
            Booth booth = booths.findById(booking.boothId)
                    .orElseThrow(() -> new BizException("排期挂的展位不存在，对不上展馆"));
            Hall hall = halls.findById(booth.hallId)
                    .orElseThrow(() -> new BizException("展馆不存在"));
            if ("停用".equals(hall.status)) {
                throw new BizException("展馆「" + hall.name
                        + "」已经关停，该馆的排期挂不上新的校准占用");
            }
            int holding = occs.findByLoanIdAndStatusIn(loan.id, HOLDING).stream()
                    .mapToInt(o -> o.quantity).sum();
            int remain = loan.quantity - holding;
            if (l.quantity > remain) {
                throw new BizException("借用行 " + loan.id + "（" + booking.expoName + "）借出 "
                        + loan.quantity + " 件，已在校准里占着 " + holding + " 件，最多再占 "
                        + remain + " 件");
            }
            CalibOcc o = new CalibOcc();
            o.loanId = loan.id;
            o.equipmentId = e.id;
            o.bookingId = booking.id;
            o.hallId = hall.id;
            o.expoName = booking.expoName;
            o.quantity = l.quantity;
            o.status = "校准中";
            toSave.add(o);
            deduct.merge(e.id, l.quantity, Integer::sum);
        }

        // 现场口径：校准占用的件数从可借里拿掉，新排期借不到这部分
        for (Map.Entry<Long, Integer> d : deduct.entrySet()) {
            Equipment e = eqMap.get(d.getKey());
            e.available = e.available - d.getValue();
            equipments.save(e);
        }

        CalibBatch saved = new CalibBatch();
        saved.code = nextCode();
        saved.kind = kind;
        saved.quantity = quantity;
        saved.expectBackDate = expectBackDate;
        saved.status = "校准中";
        saved.createdAt = LocalDateTime.now();
        batches.save(saved);
        for (CalibOcc o : toSave) {
            o.batchId = saved.id;
            occs.save(o);
        }
        return saved;
    }

    /**
     * 库房标已回厂：批下还压着的占用行全部回厂，件数加回可借。
     * 值班只能看本馆批次，回厂是库房的活，这里直接挡。
     */
    @Transactional
    public CalibBatch returnBatch(Long id, String role) {
        requireWarehouse(role);
        CalibBatch b = batches.findForUpdate(id)
                .orElseThrow(() -> new BizException("校准批次不存在"));
        if (!"校准中".equals(b.status)) {
            throw new BizException("批次 " + b.code + " 已经是「" + b.status + "」了，不能重复回厂");
        }
        List<CalibOcc> holding = occs.findByBatchIdOrderByIdAsc(id).stream()
                .filter(o -> HOLDING.contains(o.status))
                .toList();
        Map<Long, Integer> back = new HashMap<>();
        for (CalibOcc o : holding) {
            back.merge(o.equipmentId, o.quantity, Integer::sum);
        }
        // 回厂的件数加回可借；待归还厂的行一样加回——灯回的是库房，不再占可借
        for (Long eqId : back.keySet().stream().sorted().toList()) {
            Equipment e = equipments.findForUpdate(eqId)
                    .orElseThrow(() -> new BizException("展具不存在"));
            e.available = e.available + back.get(eqId);
            equipments.save(e);
        }
        for (CalibOcc o : holding) {
            o.status = "已回厂";
            occs.save(o);
        }
        b.status = "已回厂";
        b.backAt = LocalDateTime.now();
        return batches.save(b);
    }

    /**
     * 发批次号：先让序列行原子就位，再锁行自增。
     * 全新库两批同时首开时，不会因为都「查不到就插」而撞序列主键。
     */
    private String nextCode() {
        seq.ensureSeqRow();
        CalibSeq row = seq.lockRow().orElseThrow(() -> new BizException("批次号序列没就位"));
        row.seqValue = row.seqValue + 1;
        seq.save(row);
        return String.format("JC-%04d", row.seqValue);
    }

    private boolean isDuty(String role) {
        return !"库房".equals(role);
    }

    private Long requireHall(Long hallId) {
        if (hallId == null) {
            throw new BizException("值班要先在页面顶上选本馆，才能看本馆相关的校准批次");
        }
        return hallId;
    }

    private void requireWarehouse(String role) {
        if (!"库房".equals(role)) {
            throw new BizException("开校准批次、标已回厂都是库房的活，值班只能查看本馆相关批次");
        }
    }
}
