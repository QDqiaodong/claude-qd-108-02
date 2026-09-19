package com.expo.center.entity;

import jakarta.persistence.*;

/** 展具台账：桁架、展板、洽谈桌椅这类可借的物件。 */
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    /** 桁架 / 展板 / 桌椅 / 灯具 / 地毯 */
    @Column(nullable = false, length = 16)
    public String kind;

    /** 总数量 */
    @Column(nullable = false)
    public Integer total;

    /** 可借数量：已扣掉外借和校准占用，新排期只能借到这个数 */
    @Column(nullable = false)
    public Integer available;

    /** 当前挂在校准里的件数（校准中 + 待归还厂），列表查询时现算填入，不落库 */
    @Transient
    public Integer calibrating;
}
