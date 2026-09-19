package com.expo.center.controller;

import com.expo.center.entity.Booking;
import com.expo.center.service.BookingService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/bookings")
    public List<Booking> list(@RequestParam(required = false) Long boothId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String expoName) {
        return service.list(boothId, status, expoName);
    }

    @PostMapping("/bookings")
    public Booking create(@RequestBody Booking input) {
        return service.create(input);
    }

    @PutMapping("/bookings/{id}")
    public Booking update(@PathVariable Long id, @RequestBody Booking input) {
        return service.update(id, input);
    }
}
