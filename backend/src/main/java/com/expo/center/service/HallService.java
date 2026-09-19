package com.expo.center.service;

import com.expo.center.dto.BizException;
import com.expo.center.entity.Booth;
import com.expo.center.entity.Hall;
import com.expo.center.repository.BoothRepository;
import com.expo.center.repository.HallRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HallService {

    private final HallRepository halls;
    private final BoothRepository booths;

    public HallService(HallRepository halls, BoothRepository booths) {
        this.halls = halls;
        this.booths = booths;
    }

    /** 这个展馆现在摆了多少个展位。 */
    public int boothCount(Long hallId) {
        return booths.findByHallId(hallId).size();
    }

    /** 这个展馆展位面积合计占了多少。 */
    public int usedArea(Long hallId) {
        return booths.findByHallId(hallId).stream().mapToInt(b -> b.area).sum();
    }

    public List<Hall> list(String status, String keyword) {
        return halls.findAllByOrderByIdAsc().stream()
                .filter(h -> status == null || status.isEmpty() || status.equals(h.status))
                .filter(h -> keyword == null || keyword.isEmpty()
                        || h.name.contains(keyword) || h.code.contains(keyword))
                .toList();
    }

    @Transactional
    public Hall create(Hall input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("展馆编号不能为空");
        }
        if (halls.existsByCode(input.code)) {
            throw new BizException("编号 " + input.code + " 已经被别的展馆用掉了");
        }
        if (input.area == null || input.area <= 0) {
            throw new BizException("展馆可用面积要大于 0 平方米");
        }
        Hall saved = new Hall();
        saved.code = input.code.trim();
        saved.name = input.name;
        saved.area = input.area;
        saved.status = (input.status == null || input.status.isBlank()) ? "启用" : input.status;
        return halls.save(saved);
    }

    @Transactional
    public Hall update(Long id, Hall input) {
        Hall h = halls.findById(id).orElseThrow(() -> new BizException("展馆不存在"));
        int used = usedArea(h.id);
        if (input.name != null) {
            h.name = input.name;
        }
        if (input.area != null && !input.area.equals(h.area)) {
            if (input.area <= 0) {
                throw new BizException("展馆可用面积要大于 0 平方米");
            }
            if (input.area < used) {
                throw new BizException("这个展馆的展位面积合计已经占走 " + used
                        + " 平方米，可用面积不能改到比它小");
            }
            h.area = input.area;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(h.status)) {
            int count = boothCount(h.id);
            if (!"启用".equals(input.status) && count > 0) {
                throw new BizException("这个展馆里还摆着 " + count + " 个展位，先撤掉才能改成「"
                        + input.status + "」");
            }
            h.status = input.status;
        }
        return halls.save(h);
    }
}
