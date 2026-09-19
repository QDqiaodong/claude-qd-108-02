package com.expo.center.entity;

import jakarta.persistence.*;

/** 校准批次号序列：发号时锁这一行，两个人同时开批也不会发重。 */
@Entity
@Table(name = "calib_seq")
public class CalibSeq {

    /** 固定一行，id = 1 */
    @Id
    public Long id;

    @Column(nullable = false)
    public Long seqValue;
}
