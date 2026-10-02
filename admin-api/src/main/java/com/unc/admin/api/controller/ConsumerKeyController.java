package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;
import com.unc.admin.api.service.ConsumerKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/consumers/{consumerId}/keys")
public class ConsumerKeyController {

    private final ConsumerKeyService consumerKeyService;

    public ConsumerKeyController(ConsumerKeyService consumerKeyService) {
        this.consumerKeyService = consumerKeyService;
    }

    @PostMapping
    public ResponseEntity<ConsumerKeyDto> issueKey(
            @PathVariable("consumerId") UUID consumerId,
            @RequestBody(required = false) IssueConsumerKeyRequest request) {
        ConsumerKeyDto issued = consumerKeyService.issueKey(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }

    @GetMapping
    public List<ConsumerKeyDto> listKeys(@PathVariable("consumerId") UUID consumerId) {
        return consumerKeyService.listKeys(consumerId);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(
            @PathVariable("consumerId") UUID consumerId,
            @PathVariable("keyId") UUID keyId) {
        consumerKeyService.revokeKey(consumerId, keyId);
        return ResponseEntity.noContent().build();
    }
}
