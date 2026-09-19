package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booking;
import com.expo.center.entity.CalibBatch;
import com.expo.center.entity.CalibOcc;
import com.expo.center.entity.EquipLoan;
import com.expo.center.entity.Equipment;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.CalibBatchRepository;
import com.expo.center.repository.CalibOccRepository;
import com.expo.center.repository.EquipLoanRepository;
import com.expo.center.repository.EquipmentRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {

    private final EquipmentRepository equipments;
    private final EquipLoanRepository loans;
    private final BookingRepository bookings;
    private final CalibOccRepository calibOccs;
    private final CalibBatchRepository calibBatches;

    public EquipmentService(EquipmentRepository equipments, EquipLoanRepository loans,
                            BookingRepository bookings, CalibOccRepository calibOccs,
                            CalibBatchRepository calibBatches) {
        this.equipments = equipments;
        this.loans = loans;
        this.bookings = bookings;
        this.calibOccs = calibOccs;
        this.calibBatches = calibBatches;
    }

    public List<Equipment> listEquipments(String kind, String keyword) {
        // 每类展具现在压在校准里的件数（校准中 + 待归还厂），展具卡上随可借数一起摆出来
        Map<Long, Integer> calMap = new HashMap<>();
        for (Object[] row : calibOccs.sumGroupByEquipmentAndStatusIn(CalibrationService.HOLDING)) {
            calMap.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return equipments.findAllByOrderByIdAsc().stream()
                .filter(e -> kind == null || kind.isEmpty() || kind.equals(e.kind))
                .filter(e -> keyword == null || keyword.isEmpty()
                        || e.name.contains(keyword) || e.code.contains(keyword))
                .peek(e -> e.calibrating = calMap.getOrDefault(e.id, 0))
                .toList();
    }

    public List<EquipLoan> listLoans(Long equipmentId, String status) {
        return loans.findAllByOrderByIdDesc().stream()
                .filter(l -> equipmentId == null || equipmentId.equals(l.equipmentId))
                .filter(l -> status == null || status.isEmpty() || status.equals(l.status))
                .toList();
    }

    @Transactional
    public Equipment createEquipment(Equipment input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("展具编号不能为空");
        }
        if (equipments.existsByCode(input.code)) {
            throw new BizException("编号 " + input.code + " 已经用过了");
        }
        if (input.total == null || input.total <= 0) {
            throw new BizException("总数量要大于 0");
        }
        if (input.available != null && (input.available < 0 || input.available > input.total)) {
            throw new BizException("可借数量要在 0 到总数之间");
        }
        Equipment saved = new Equipment();
        saved.code = input.code.trim();
        saved.name = input.name;
        saved.kind = (input.kind == null || input.kind.isBlank()) ? "桁架" : input.kind;
        saved.total = input.total;
        saved.available = input.available == null ? input.total : input.available;
        return equipments.save(saved);
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment input) {
        Equipment e = equipments.findForUpdate(id)
                .orElseThrow(() -> new BizException("展具不存在"));
        if (input.name != null) {
            e.name = input.name;
        }
        if (input.kind != null && !input.kind.isBlank()) {
            e.kind = input.kind;
        }
        if (input.total != null && !input.total.equals(e.total)) {
            if (input.total <= 0) {
                throw new BizException("总数量要大于 0");
            }
            // 在外数 = 借用中的件数 + 还压在校准里的件数，可借数已被校准扣过，
            // 不能再拿 total - available 反推，会把校准占用漏掉
            int lending = loans.findByEquipmentIdAndStatus(e.id, "借用中").stream()
                    .mapToInt(l -> l.quantity).sum();
            Long cal = calibOccs.sumByEquipmentAndStatusIn(e.id, CalibrationService.HOLDING);
            int holding = cal == null ? 0 : cal.intValue();
            if (input.total < lending + holding) {
                throw new BizException("现在还有 " + lending + " 件在外面借着、"
                        + holding + " 件在厂里校准，总数不能改到比 " + (lending + holding) + " 少");
            }
            e.total = input.total;
            e.available = input.total - lending - holding;
        }
        return equipments.save(e);
    }

    @Transactional
    public EquipLoan lend(EquipLoan input) {
        if (input.equipmentId == null) {
            throw new BizException("请选择展具");
        }
        if (input.bookingId == null) {
            throw new BizException("请选择这个展具是给哪个展会用的");
        }
        if (input.quantity == null || input.quantity <= 0) {
            throw new BizException("借用数量要大于 0");
        }
        if (input.outDate == null) {
            throw new BizException("要填借出日期");
        }
        // 锁展具行再改可借：校准开批/回厂也在改这行，不锁会互相覆盖
        Equipment e = equipments.findForUpdate(input.equipmentId)
                .orElseThrow(() -> new BizException("展具不存在"));
        Booking booking = bookings.findById(input.bookingId)
                .orElseThrow(() -> new BizException("排期不存在"));
        if ("已结束".equals(booking.status)) {
            throw new BizException("排期「" + booking.expoName + "」已经结束了，不能再借展具");
        }
        if (input.quantity > e.available) {
            throw new BizException("「" + e.name + "」现在可借 " + e.available + " 件，借不出 "
                    + input.quantity + " 件");
        }
        if (!loans.findByEquipmentIdAndBookingIdAndStatus(e.id, booking.id, "借用中").isEmpty()) {
            throw new BizException("这个展会已经借过「" + e.name + "」了，别重复借");
        }
        EquipLoan saved = new EquipLoan();
        saved.equipmentId = e.id;
        saved.bookingId = booking.id;
        saved.quantity = input.quantity;
        saved.outDate = input.outDate;
        saved.status = "借用中";
        e.available = e.available - input.quantity;
        equipments.save(e);
        return loans.save(saved);
    }

    @Transactional
    public EquipLoan giveBack(Long id, LocalDate backDate) {
        EquipLoan loan = loans.findById(id).orElseThrow(() -> new BizException("借用记录不存在"));
        if (!"借用中".equals(loan.status)) {
            throw new BizException("这条借用已经还过了");
        }
        // 还压在校准里的件数没法收回：灯在厂里，等批次回厂后才能收这条借用
        List<CalibOcc> holding = calibOccs.findByLoanIdAndStatusIn(loan.id,
                CalibrationService.HOLDING);
        if (!holding.isEmpty()) {
            int n = holding.stream().mapToInt(o -> o.quantity).sum();
            String codes = calibBatches.findAllById(
                    holding.stream().map(o -> o.batchId).collect(Collectors.toSet()))
                    .stream().map(b -> b.code).sorted().collect(Collectors.joining("、"));
            throw new BizException("这条借用还有 " + n + " 件在厂里校准（批次 " + codes
                    + "），等回厂后才能收回");
        }
        Equipment e = equipments.findForUpdate(loan.equipmentId)
                .orElseThrow(() -> new BizException("展具不存在"));
        loan.backDate = backDate == null ? LocalDate.now() : backDate;
        loan.status = "已归还";
        e.available = e.available + loan.quantity;
        if (e.available > e.total) {
            e.available = e.total;
        }
        equipments.save(e);
        return loans.save(loan);
    }
}
