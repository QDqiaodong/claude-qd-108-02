package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 展会排期：某个展位在某段时间租给某个展会。 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(name = "booth_id", nullable = false)
    public Long boothId;

    /** 随单快照：排期确认函上印的展位号；展位改号时未结束的函一起换新号，已结束的留旧号 */
    @Column(name = "booth_code", nullable = false, length = 32)
    public String boothCode;

    /** 展会名称 */
    @Column(name = "expo_name", nullable = false, length = 128)
    public String expoName;

    /** 承租方 */
    @Column(nullable = false, length = 64)
    public String tenant;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    /** 待布展 / 展出中 / 已结束 */
    @Column(nullable = false, length = 16)
    public String status;
}
