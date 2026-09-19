package com.expo.center.controller;

import com.expo.center.entity.EquipLoan;
import com.expo.center.entity.Equipment;
import com.expo.center.service.EquipmentService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EquipmentController {

    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping("/equipments")
    public List<Equipment> listEquipments(@RequestParam(required = false) String kind,
                                          @RequestParam(required = false) String keyword) {
        return service.listEquipments(kind, keyword);
    }

    @PostMapping("/equipments")
    public Equipment createEquipment(@RequestBody Equipment input) {
        return service.createEquipment(input);
    }

    @PutMapping("/equipments/{id}")
    public Equipment updateEquipment(@PathVariable Long id, @RequestBody Equipment input) {
        return service.updateEquipment(id, input);
    }

    @GetMapping("/loans")
    public List<EquipLoan> listLoans(@RequestParam(required = false) Long equipmentId,
                                     @RequestParam(required = false) String status) {
        return service.listLoans(equipmentId, status);
    }

    @PostMapping("/loans")
    public EquipLoan lend(@RequestBody EquipLoan input) {
        return service.lend(input);
    }

    @PostMapping("/loans/{id}/giveback")
    public EquipLoan giveBack(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate backDate) {
        return service.giveBack(id, backDate);
    }
}
