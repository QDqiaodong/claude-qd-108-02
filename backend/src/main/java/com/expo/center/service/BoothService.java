package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booth;
import com.expo.center.entity.Hall;
import com.expo.center.repository.BookingRepository;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.HallRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoothService {

    private final BoothRepository booths;
    private final HallRepository halls;
    private final BookingRepository bookings;

    public BoothService(BoothRepository booths, HallRepository halls, BookingRepository bookings) {
        this.booths = booths;
        this.halls = halls;
        this.bookings = bookings;
    }

    public List<Booth> list(Long hallId, String status, String kind, String keyword) {
        return booths.findAllByOrderByIdAsc().stream()
                .filter(b -> hallId == null || hallId.equals(b.hallId))
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .filter(b -> kind == null || kind.isEmpty() || kind.equals(b.kind))
                .filter(b -> keyword == null || keyword.isEmpty() || b.code.contains(keyword))
                .toList();
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
        if (booths.existsByCode(input.code)) {
            throw new BizException("展位 " + input.code + " 已经存在了");
        }
        if (input.hallId == null) {
            throw new BizException("请选择摆在哪个展馆");
        }
        if (input.area == null || input.area <= 0) {
            throw new BizException("展位面积要大于 0 平方米");
        }
        checkHall(input.hallId, input.area);
        Booth saved = new Booth();
        saved.code = input.code.trim();
        saved.hallId = input.hallId;
        saved.area = input.area;
        saved.kind = (input.kind == null || input.kind.isBlank()) ? "标准" : input.kind;
        saved.status = (input.status == null || input.status.isBlank()) ? "空闲" : input.status;
        return booths.save(saved);
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
