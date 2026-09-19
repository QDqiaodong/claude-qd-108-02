package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 展位改号留痕：旧号 → 新号。
 * 旧号永久停用（old_code 全库唯一），财务的旧函、门卫手里的旧票都还能对上现在的新号；
 * 旧号不能再摆新展位，同一块面积不会被新旧两套号各算一次。
 */
@Entity
@Table(name = "booth_code_log")
public class BoothCodeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "booth_id", nullable = false)
    public Long boothId;

    /** 退下来的旧号，从此停用，谁都不能再用 */
    @Column(name = "old_code", nullable = false, length = 32, unique = true)
    public String oldCode;

    @Column(name = "new_code", nullable = false, length = 32)
    public String newCode;

    @Column(name = "changed_at", nullable = false)
    public LocalDateTime changedAt;
}
