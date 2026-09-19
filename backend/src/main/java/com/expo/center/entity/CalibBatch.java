package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 外送校准批次：库房把一批展具送厂校准，批次下点名占用哪些借用行。 */
@Entity
@Table(name = "calib_batch")
public class CalibBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 批次号 JC-xxxx */
    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 展具类别：灯具 / 桁架 / 展板 / 桌椅 / 地毯 */
    @Column(nullable = false, length = 16)
    public String kind;

    /** 本批件数 = 各占用行件数合计 */
    @Column(nullable = false)
    public Integer quantity;

    /** 预计回厂日 */
    @Column(name = "expect_back_date", nullable = false)
    public LocalDate expectBackDate;

    /** 校准中 / 已回厂 */
    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    /** 实际回厂时间，回厂时写 */
    @Column(name = "back_at")
    public LocalDateTime backAt;
}
