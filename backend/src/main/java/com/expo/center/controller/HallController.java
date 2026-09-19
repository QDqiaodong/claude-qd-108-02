package com.expo.center.controller;

import com.expo.center.entity.Hall;
import com.expo.center.service.HallService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HallController {

    private final HallService service;

    public HallController(HallService service) {
        this.service = service;
    }

    @GetMapping("/halls")
    public List<Hall> list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String keyword) {
        return service.list(status, keyword);
    }

    @PostMapping("/halls")
    public Hall create(@RequestBody Hall input) {
        return service.create(input);
    }

    @PutMapping("/halls/{id}")
    public Hall update(@PathVariable Long id, @RequestBody Hall input) {
        return service.update(id, input);
    }
}
