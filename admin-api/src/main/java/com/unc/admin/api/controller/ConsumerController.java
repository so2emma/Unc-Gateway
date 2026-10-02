package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ConsumerDto;
import com.unc.admin.api.service.ConsumerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/consumers")
public class ConsumerController {

    private final ConsumerService consumerService;

    public ConsumerController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping
    public ResponseEntity<ConsumerDto> createConsumer(@RequestBody ConsumerDto dto) {
        ConsumerDto created = consumerService.createConsumer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<ConsumerDto> listConsumers() {
        return consumerService.listConsumers();
    }

    @GetMapping("/{id}")
    public ConsumerDto getConsumer(@PathVariable("id") UUID id) {
        return consumerService.getConsumer(id);
    }

    @PutMapping("/{id}")
    public ConsumerDto updateConsumer(@PathVariable("id") UUID id, @RequestBody ConsumerDto dto) {
        return consumerService.updateConsumer(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConsumer(@PathVariable("id") UUID id) {
        consumerService.deleteConsumer(id);
        return ResponseEntity.noContent().build();
    }
}
