package com.expo.center.controller;

import com.expo.center.entity.Booth;
import com.expo.center.service.BoothService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BoothController {

    private final BoothService service;

    public BoothController(BoothService service) {
        this.service = service;
    }

    @GetMapping("/booths")
    public List<Booth> list(@RequestParam(required = false) Long hallId,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String kind,
                            @RequestParam(required = false) String keyword) {
        return service.list(hallId, status, kind, keyword);
    }

    @PostMapping("/booths")
    public Booth create(@RequestBody Booth input) {
        return service.create(input);
    }

    @PutMapping("/booths/{id}")
    public Booth update(@PathVariable Long id, @RequestBody Booth input) {
        return service.update(id, input);
    }
}
