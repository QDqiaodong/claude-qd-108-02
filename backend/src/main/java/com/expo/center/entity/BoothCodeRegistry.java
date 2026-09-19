package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 展位编号登记簿：所有出现过的展位号都登记在册（在用 / 已改号退役）。
 * 改号不是把旧号抹掉，而是旧号退役、新号上岗，一个号一行；
 * 退役号永远不能再注册，同一块面积不会被新旧两套号各算一次；
 * 退役行留着「旧号 → 新号」的对应，门卫拿旧函来对票还能查得到。
 */
@Entity
@Table(name = "booth_code_registry")
public class BoothCodeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 展位号，全库唯一：在用的、退役的都占着这个号 */
    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 这个号挂在哪个展位上 */
    @Column(name = "booth_id", nullable = false)
    public Long boothId;

    /** 在用 / 已改号 */
    @Column(nullable = false, length = 16)
    public String status;

    /** 退役后换成了哪个新号；在用行为空 */
    @Column(name = "replaced_by", length = 32)
    public String replacedBy;

    /** 退役时间；在用行为空 */
    @Column(name = "changed_at")
    public LocalDateTime changedAt;
}
