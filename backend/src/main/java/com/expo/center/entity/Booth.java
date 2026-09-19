package com.expo.center.entity;

import jakarta.persistence.*;

/** 展位：摆在某个展馆里的一个摊位。 */
@Entity
@Table(name = "booth")
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(name = "hall_id", nullable = false)
    public Long hallId;

    /** 展位面积，平方米 */
    @Column(nullable = false)
    public Integer area;

    /** 标准 / 特装 / 光地 */
    @Column(nullable = false, length = 16)
    public String kind;

    /** 空闲 / 已租 / 维修 */
    @Column(nullable = false, length = 16)
    public String status;
}
