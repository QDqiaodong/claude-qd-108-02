package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 展具借用：某个展会从库里借走一批展具。 */
@Entity
@Table(name = "equip_loan")
public class EquipLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "equipment_id", nullable = false)
    public Long equipmentId;

    @Column(name = "booking_id", nullable = false)
    public Long bookingId;

    @Column(nullable = false)
    public Integer quantity;

    @Column(name = "out_date", nullable = false)
    public LocalDate outDate;

    @Column(name = "back_date")
    public LocalDate backDate;

    /** 借用中 / 已归还 */
    @Column(nullable = false, length = 16)
    public String status;
}
