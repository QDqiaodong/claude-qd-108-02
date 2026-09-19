package com.expo.center.entity;

import jakarta.persistence.*;

/**
 * 校准占用行：批次点名哪条借用行被占走了多少件。
 * 排期 / 展馆 / 展会名随单快照，值班按本馆筛批次、展馆关停判定都直接用快照。
 */
@Entity
@Table(name = "calib_occ")
public class CalibOcc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    /** 被占用的借用行 */
    @Column(name = "loan_id", nullable = false)
    public Long loanId;

    @Column(name = "equipment_id", nullable = false)
    public Long equipmentId;

    /** 快照：占用时借用行挂的排期 */
    @Column(name = "booking_id", nullable = false)
    public Long bookingId;

    /** 快照：排期所在展馆，值班按本馆看批次就靠它 */
    @Column(name = "hall_id", nullable = false)
    public Long hallId;

    /** 快照：展会名 */
    @Column(name = "expo_name", nullable = false, length = 128)
    public String expoName;

    /** 这一行从借用里占走多少件 */
    @Column(nullable = false)
    public Integer quantity;

    /** 校准中 / 待归还厂 / 已回厂，状态只往前不走回头路 */
    @Column(nullable = false, length = 16)
    public String status;
}
