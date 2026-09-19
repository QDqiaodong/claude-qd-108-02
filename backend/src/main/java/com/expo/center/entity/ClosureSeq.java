package com.expo.center.entity;

import jakarta.persistence.*;

/** 封道单号序列：发号时锁这一行，两个人同时提交也不会发重。 */
@Entity
@Table(name = "closure_seq")
public class ClosureSeq {

    /** 固定一行，id = 1 */
    @Id
    public Long id;

    @Column(nullable = false)
    public Long seqValue;
}
