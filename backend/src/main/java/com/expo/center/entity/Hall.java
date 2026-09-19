package com.expo.center.entity;

import jakarta.persistence.*;

/** 展馆：一个能摆展位的大厅。 */
@Entity
@Table(name = "hall")
public class Hall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    /** 可用面积，平方米 */
    @Column(nullable = false)
    public Integer area;

    /** 启用 / 布展 / 停用 */
    @Column(nullable = false, length = 16)
    public String status;
}
