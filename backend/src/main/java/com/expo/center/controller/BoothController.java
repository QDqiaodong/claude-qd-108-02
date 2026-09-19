package com.expo.center.controller;

import com.expo.center.dto.RenameResult;
import com.expo.center.entity.Booth;
import com.expo.center.entity.BoothCodeRegistry;
import com.expo.center.service.BoothService;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/booths/code-history")
    public List<BoothCodeRegistry> codeHistory() {
        return service.codeHistory();
    }

    @PostMapping("/booths")
    public Booth create(@RequestBody Booth input) {
        return service.create(input);
    }

    @PostMapping("/booths/{id}/rename")
    public RenameResult rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.rename(id, body.get("code"));
    }

    @PutMapping("/booths/{id}")
    public Booth update(@PathVariable Long id, @RequestBody Booth input) {
        return service.update(id, input);
    }
}
