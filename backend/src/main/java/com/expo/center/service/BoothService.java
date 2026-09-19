package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booth;
import com.expo.center.entity.BoothCodeLog;
import com.expo.center.entity.Hall;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothCodeLogRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.HallRepository;
import com.expo.center.repository.RoadClosureRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoothService {

    private final BoothRepository booths;
    private final HallRepository halls;
    private final BookingRepository bookings;
    private final RoadClosureRepository closures;
    private final BoothCodeLogRepository codeLogs;

    public BoothService(BoothRepository booths, HallRepository halls, BookingRepository bookings,
                        RoadClosureRepository closures, BoothCodeLogRepository codeLogs) {
        this.booths = booths;
        this.halls = halls;
        this.bookings = bookings;
        this.closures = closures;
        this.codeLogs = codeLogs;
    }

    public List<Booth> list(Long hallId, String status, String kind, String keyword) {
        // 原号留痕按展位分组：名片上摆出「原号」，门卫拿旧函来对得上现在的新号
        Map<Long, List<String>> former = codeLogs.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(l -> l.boothId, LinkedHashMap::new,
                        Collectors.mapping(l -> l.oldCode, Collectors.toList())));
        return booths.findAllByOrderByIdAsc().stream()
                .peek(b -> b.formerCodes = former.get(b.id))
                .filter(b -> hallId == null || hallId.equals(b.hallId))
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .filter(b -> kind == null || kind.isEmpty() || kind.equals(b.kind))
                .filter(b -> keyword == null || keyword.isEmpty() || b.code.contains(keyword)
                        || (b.formerCodes != null
                            && b.formerCodes.stream().anyMatch(c -> c.contains(keyword))))
                .toList();
    }

    /** 这个号还能不能用：在用的不行，改号退下来的旧号也不行（旧号永久停用）。 */
    private void checkCodeUsable(String code) {
        if (booths.existsByCode(code) || codeLogs.existsByOldCode(code)) {
            throw new BizException("展位号 " + code + " 已经被人用过，换一个新的");
        }
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
        // 旧号留痕停用：改号退下来的号也不能再摆新展位，
        // 不然同一块面积被新旧两套号各算一次，门卫的旧票还会指到空展位上
        checkCodeUsable(code);
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
            return booths.saveAndFlush(saved);
        } catch (DataIntegrityViolationException e) {
            // 并发摆同一个号：唯一索引只放过去一个，晚到的在这里看见号已被人用
            throw new BizException("展位号 " + code + " 已经被人用过，换一个新的");
        }
    }

    /**
     * 改号连带走件：名牌换新号的同时，这个展位名下未结束的排期确认函、
     * 待审 / 已批准的封道条一起换成新号，门卫按新号放行、卸货口对得上票。
     * 旧号写进改号留痕永久停用：已结束的旧函仍印旧号、继续有效（财务少改合同），
     * 但旧号从可选票面消失，不能再摆新展位占同一块面积。
     * 两人同时改同一个号：唯一索引只放过去一个，晚到的看见「已经被人用过」。
     */
    private void rename(Booth b, String newCode) {
        checkCodeUsable(newCode);
        String oldCode = b.code;
        b.code = newCode;
        try {
            booths.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            // 并发改到同一个号：唯一索引只留一条有效编号，晚到的在这里被顶回来
            throw new BizException("展位号 " + newCode + " 已经被人用过，换一个新的");
        }
        BoothCodeLog log = new BoothCodeLog();
        log.boothId = b.id;
        log.oldCode = oldCode;
        log.newCode = newCode;
        log.changedAt = LocalDateTime.now();
        codeLogs.save(log);
        // 连带走件：未结束排期、未结封道条换新号；已结束 / 已驳回 / 作废的旧函不动
        bookings.cascadeBoothCode(b.id, newCode);
        closures.cascadeBoothCode(b.id, newCode);
    }

    @Transactional
    public Booth update(Long id, Booth input) {
        Booth b = booths.findById(id).orElseThrow(() -> new BizException("展位不存在"));
        String newCode = (input.code == null) ? null : input.code.trim();
        if (newCode != null && !newCode.isEmpty() && !newCode.equals(b.code)) {
            // 锁顺序跟排期、封道一致：先锁展馆行、再锁展位行，并发改号串行不互锁；
            // 锁后当前读重拿展位，看到的是别人刚改完的最新号
            halls.findForUpdate(b.hallId).orElseThrow(() -> new BizException("展馆不存在"));
            b = booths.findForUpdate(id).orElseThrow(() -> new BizException("展位不存在"));
            rename(b, newCode);
        }
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
