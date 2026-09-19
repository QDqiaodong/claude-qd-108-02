package com.expo.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 卸货通道封道申报单：布展日临时封掉某间展馆卸货通道的一段。
 * 状态：待审 → 已批准 / 已驳回；待审、已批准都可能被系统「作废」。
 */
@Entity
@Table(name = "road_closure")
public class RoadClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 封道单号，RD-xxxx，全库唯一，提交时按单号序列发 */
    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 挂的是哪条排期（提交时必须是承租方自己「待布展」的那条） */
    @Column(name = "booking_id", nullable = false)
    public Long bookingId;

    /** 随单快照：封道条上印的展位号，卸货口门卫对的就是这个号。
     *  改号时待审、已批准的条子一起换新号；已驳回、作废的旧条保持旧号不动 */
    @Column(name = "booth_code", nullable = false, length = 32)
    public String boothCode;

    /** 随单快照：哪间展馆，避免排期展位变动后对不上馆 */
    @Column(name = "hall_id", nullable = false)
    public Long hallId;

    /** 随单快照：先占着的那家展会名，撞单时直接带得出 */
    @Column(name = "expo_name", nullable = false, length = 128)
    public String expoName;

    /** 随单快照：承租方 */
    @Column(nullable = false, length = 64)
    public String tenant;

    /** 封道日期（布展日） */
    @Column(name = "close_date", nullable = false)
    public LocalDate closeDate;

    /** 哪条卸货通道，同馆同名才算同一条 */
    @Column(nullable = false, length = 64)
    public String channel;

    /** 封通道的哪一段（如 东段/西段/3号门段），消防口径下撞时段时段段都不许并行 */
    @Column(nullable = false, length = 64)
    public String segment;

    @Column(name = "start_time", nullable = false)
    public LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    public LocalTime endTime;

    /** 待审 / 已批准 / 已驳回 / 作废 */
    @Column(nullable = false, length = 16)
    public String status;

    /** 驳回或作废时写在单上的原因；作废要留痕，关掉页面再打开还在 */
    @Column(name = "reason", length = 255)
    public String reason;

    @Column(name = "submitted_at", nullable = false)
    public LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    public LocalDateTime reviewedAt;
}
