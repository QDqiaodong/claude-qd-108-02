package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.dto.RenameResult;
import com.expo.center.entity.Booking;
import com.expo.center.entity.Booth;
import com.expo.center.entity.BoothCodeRegistry;
import com.expo.center.entity.Hall;
import com.expo.center.entity.RoadClosure;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothCodeRegistryRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.HallRepository;
import com.expo.center.repository.RoadClosureRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoothService {

    private final BoothRepository booths;
    private final HallRepository halls;
    private final BookingRepository bookings;
    private final RoadClosureRepository closures;
    private final BoothCodeRegistryRepository registry;

    public BoothService(BoothRepository booths, HallRepository halls, BookingRepository bookings,
                        RoadClosureRepository closures, BoothCodeRegistryRepository registry) {
        this.booths = booths;
        this.halls = halls;
        this.bookings = bookings;
        this.closures = closures;
        this.registry = registry;
    }

    public List<Booth> list(Long hallId, String status, String kind, String keyword) {
        return booths.findAllByOrderByIdAsc().stream()
                .filter(b -> hallId == null || hallId.equals(b.hallId))
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .filter(b -> kind == null || kind.isEmpty() || kind.equals(b.kind))
                .filter(b -> keyword == null || keyword.isEmpty() || b.code.contains(keyword))
                .toList();
    }

    /** 编号沿革：谁在用什么号、旧号都改成了什么新号，门卫对票、财务对合同都查这本册。 */
    public List<BoothCodeRegistry> codeHistory() {
        return registry.findAllByOrderByIdDesc();
    }

    /** 展位面积不能超展馆剩余可摆面积。 */
    private Hall checkHall(Long hallId, Integer area) {
        Hall hall = halls.findById(hallId).orElseThrow(() -> new BizException("展馆不存在"));
        if (!"启用".equals(hall.status)) {
            throw new BizException("展馆 " + hall.name + " 现在是「" + hall.status + "」，摆不了展位");
        }
        int used = booths.findByHallId(hall.id).stream().mapToInt(b -> b.area).sum();
        if (used + area > hall.area) {
            throw new BizException("展馆 " + hall.name + " 可用 " + hall.area + " 平方米，已经摆走 "
                    + used + " 平方米，放不下这个 " + area + " 平方米的展位");
        }
        return hall;
    }

    @Transactional
    public Booth create(Booth input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("展位编号不能为空");
        }
        String code = input.code.trim();
        // 登记簿里在用的、退役的号都占着：退役号再注册一个空展位，
        // 同一块面积就被新旧两套号各算了一次
        if (registry.existsByCode(code) || booths.existsByCode(code)) {
            throw new BizException("展位编号 " + code + " 已经被人用过，不能再注册");
        }
        if (input.hallId == null) {
            throw new BizException("请选择摆在哪个展馆");
        }
        if (input.area == null || input.area <= 0) {
            throw new BizException("展位面积要大于 0 平方米");
        }
        checkHall(input.hallId, input.area);
        Booth saved = new Booth();
        saved.code = code;
        saved.hallId = input.hallId;
        saved.area = input.area;
        saved.kind = (input.kind == null || input.kind.isBlank()) ? "标准" : input.kind;
        saved.status = (input.status == null || input.status.isBlank()) ? "空闲" : input.status;
        try {
            saved = booths.saveAndFlush(saved);
            // 登记占号：登记簿唯一索引兜底，两人同时注册同一个新号只放得过一个
            BoothCodeRegistry row = new BoothCodeRegistry();
            row.code = code;
            row.boothId = saved.id;
            row.status = "在用";
            registry.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("展位编号 " + code + " 已经被人用过，不能再注册");
        }
        return saved;
    }

    /**
     * 改展位编号：不是换个名牌就完事，要连带走件。
     * 未结束的排期确认函、待审 / 已批准的封道条一起换印新号，旧号从票面上消失；
     * 已结束、已驳回、作废的旧函仍印旧号（财务旧函继续有效，少改合同）；
     * 旧号在登记簿里退役，永远不能再注册，面积不会被新旧两套号各算一次。
     */
    @Transactional
    public RenameResult rename(Long id, String newCodeInput) {
        if (newCodeInput == null || newCodeInput.isBlank()) {
            throw new BizException("新展位编号不能为空");
        }
        String newCode = newCodeInput.trim();
        // 锁展位行（当前读）：两人同时改同一个展位的号，在这里串行，只留一条有效编号
        Booth b = booths.findForUpdate(id).orElseThrow(() -> new BizException("展位不存在"));
        String oldCode = b.code;
        if (newCode.equals(oldCode)) {
            throw new BizException("新编号跟现在的 " + oldCode + " 一样，没改");
        }
        if (registry.existsByCode(newCode) || booths.existsByCode(newCode)) {
            throw new BizException("展位编号 " + newCode + " 已经被人用过，换一个新号");
        }

        // 旧号退役：登记簿留痕「旧号 → 新号」，门卫拿印旧号的函来对票还能查到新号
        LocalDateTime now = LocalDateTime.now();
        List<BoothCodeRegistry> activeRows = registry.findByBoothIdAndStatus(b.id, "在用");
        if (activeRows.isEmpty()) {
            // 老数据没登记过：补一条退役记录，旧号一样进册，不能再被注册
            BoothCodeRegistry missing = new BoothCodeRegistry();
            missing.code = oldCode;
            missing.boothId = b.id;
            missing.status = "已改号";
            missing.replacedBy = newCode;
            missing.changedAt = now;
            registry.save(missing);
        } else {
            for (BoothCodeRegistry row : activeRows) {
                row.status = "已改号";
                row.replacedBy = newCode;
                row.changedAt = now;
                registry.save(row);
            }
        }
        // 新号上岗登记；两人同时抢同一个新号，登记簿唯一索引只放过一个，
        // 另一个在这里被顶回来，看见「这个号已经被人用过」
        BoothCodeRegistry fresh = new BoothCodeRegistry();
        fresh.code = newCode;
        fresh.boothId = b.id;
        fresh.status = "在用";
        try {
            registry.saveAndFlush(fresh);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("展位编号 " + newCode + " 已经被人用过，换一个新号");
        }

        b.code = newCode;
        booths.save(b);

        // 连带走件：未结束的排期确认函换印新号（已结束的旧函不动，继续有效）
        List<Booking> openBookings = bookings.findByBoothIdAndStatusNot(b.id, "已结束");
        for (Booking bk : openBookings) {
            bk.boothCode = newCode;
            bookings.save(bk);
        }
        // 这些排期底下还没闭环的封道条（待审 / 已批准）也换印新号；
        // 已驳回、作废的旧条不动，留着旧号继续有效
        int closuresUpdated = 0;
        if (!openBookings.isEmpty()) {
            List<Long> bookingIds = openBookings.stream().map(bk -> bk.id).toList();
            List<RoadClosure> openClosures = closures.findByBookingIdInAndStatusIn(
                    bookingIds, List.of("待审", "已批准"));
            for (RoadClosure rc : openClosures) {
                rc.boothCode = newCode;
                closures.save(rc);
            }
            closuresUpdated = openClosures.size();
        }
        return new RenameResult(oldCode, newCode, b, openBookings.size(), closuresUpdated);
    }

    @Transactional
    public Booth update(Long id, Booth input) {
        Booth b = booths.findById(id).orElseThrow(() -> new BizException("展位不存在"));
        if (input.kind != null && !input.kind.isBlank()) {
            b.kind = input.kind;
        }
        boolean hallChanged = input.hallId != null && !input.hallId.equals(b.hallId);
        boolean areaChanged = input.area != null && !input.area.equals(b.area);
        if (hallChanged || areaChanged) {
            if (!bookings.findByBoothIdAndStatusNot(b.id, "已结束").isEmpty()) {
                if (hallChanged) {
                    throw new BizException("这个展位还有没结束的排期，先结束掉才能换展馆");
                }
            }
            Long hallId = hallChanged ? input.hallId : b.hallId;
            Integer area = areaChanged ? input.area : b.area;
            if (area == null || area <= 0) {
                throw new BizException("展位面积要大于 0 平方米");
            }
            Hall hall = halls.findById(hallId).orElseThrow(() -> new BizException("展馆不存在"));
            if (!"启用".equals(hall.status)) {
                throw new BizException("展馆 " + hall.name + " 现在是「" + hall.status
                        + "」，摆不了展位");
            }
            int used = booths.findByHallId(hall.id).stream()
                    .filter(x -> !x.id.equals(b.id))
                    .mapToInt(x -> x.area).sum();
            if (used + area > hall.area) {
                throw new BizException("展馆 " + hall.name + " 只剩 " + (hall.area - used)
                        + " 平方米，放不下这个 " + area + " 平方米的展位");
            }
            b.hallId = hallId;
            b.area = area;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(b.status)) {
            if ("维修".equals(input.status)
                    && !bookings.findByBoothIdAndStatusNot(b.id, "已结束").isEmpty()) {
                throw new BizException("这个展位还有没结束的排期，先结束掉才能转维修");
            }
            b.status = input.status;
        }
        return booths.save(b);
    }
}
