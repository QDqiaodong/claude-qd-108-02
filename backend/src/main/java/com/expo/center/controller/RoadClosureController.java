package com.expo.center.controller;

import com.expo.center.entity.RoadClosure;
import com.expo.center.service.RoadClosureService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RoadClosureController {

    private final RoadClosureService service;

    public RoadClosureController(RoadClosureService service) {
        this.service = service;
    }

    @GetMapping("/closures")
    public List<RoadClosure> list(@RequestParam(required = false) Long bookingId,
                                  @RequestParam(required = false) Long hallId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String keyword) {
        return service.list(bookingId, hallId, status, keyword);
    }

    /** 承租方申报：X-Role=tenant，X-Tenant 为 URL 编码的承租方名称，只能挂自己待布展的排期 */
    @PostMapping("/closures")
    public RoadClosure submit(@RequestBody RoadClosure input,
                              @RequestHeader(value = "X-Role", defaultValue = "tenant") String role,
                              @RequestHeader(value = "X-Tenant", required = false) String tenant) {
        return service.submit(input, roleOf(role), decodeHeader(tenant));
    }

    /** 场馆值班批准 */
    @PostMapping("/closures/{id}/approve")
    public RoadClosure approve(@PathVariable Long id,
                               @RequestHeader(value = "X-Role", defaultValue = "tenant") String role) {
        return service.approve(id, roleOf(role));
    }

    /** 场馆值班驳回，驳回原因写在单上 */
    @PostMapping("/closures/{id}/reject")
    public RoadClosure reject(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              @RequestHeader(value = "X-Role", defaultValue = "tenant") String role) {
        return service.reject(id, reason, roleOf(role));
    }

    /** 身份令牌走 HTTP 头，头里不放中文：tenant=承租方，duty=场馆值班。 */
    private String roleOf(String token) {
        return "duty".equalsIgnoreCase(token) ? "场馆值班" : "承租方";
    }

    /** HTTP 头里的中文由前端 URL 编码后传来，这里按 UTF-8 解回。 */
    private String decodeHeader(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
