package com.expo.center.controller;

import com.expo.center.entity.CalibBatch;
import com.expo.center.service.CalibrationService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CalibrationController {

    private final CalibrationService service;

    public CalibrationController(CalibrationService service) {
        this.service = service;
    }

    /** 开批请求体：类别、件数、预计回厂日 + 点名占用的借用行。 */
    public static class CreateReq {
        public String kind;
        public Integer quantity;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate expectBackDate;
        public List<CalibrationService.OccLine> lines;
    }

    /** 批次列表：值班（X-Role: duty）必须带 X-Hall-Id，只能看本馆相关批次。 */
    @GetMapping("/calibrations")
    public List<CalibBatch> list(@RequestParam(required = false) String status,
                                 @RequestHeader(value = "X-Role", defaultValue = "duty") String role,
                                 @RequestHeader(value = "X-Hall-Id", required = false) Long hallId) {
        return service.list(roleOf(role), hallId, status);
    }

    /** 批次明细（含占用行）：值班只能打开沾了本馆的批次。 */
    @GetMapping("/calibrations/{id}")
    public CalibrationService.BatchDetail detail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", defaultValue = "duty") String role,
            @RequestHeader(value = "X-Hall-Id", required = false) Long hallId) {
        return service.detail(id, roleOf(role), hallId);
    }

    /** 开批候选借用行：库房开批对话框用。 */
    @GetMapping("/calibrations/candidates")
    public List<CalibrationService.Candidate> candidates(
            @RequestParam(required = false) String kind,
            @RequestHeader(value = "X-Role", defaultValue = "duty") String role) {
        return service.candidates(kind, roleOf(role));
    }

    /** 库房开批。 */
    @PostMapping("/calibrations")
    public CalibBatch create(@RequestBody CreateReq req,
                             @RequestHeader(value = "X-Role", defaultValue = "duty") String role) {
        return service.create(req.kind, req.quantity, req.expectBackDate, req.lines, roleOf(role));
    }

    /** 库房标已回厂；值班点这里会被服务端挡下。 */
    @PostMapping("/calibrations/{id}/return")
    public CalibBatch returnBatch(@PathVariable Long id,
                                  @RequestHeader(value = "X-Role", defaultValue = "duty") String role) {
        return service.returnBatch(id, roleOf(role));
    }

    /** 身份令牌走 HTTP 头：warehouse=库房，其余一律按值班看，值班天然看不了全量。 */
    private String roleOf(String token) {
        return "warehouse".equalsIgnoreCase(token) ? "库房" : "值班";
    }
}
