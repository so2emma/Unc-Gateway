package com.unc.admin.api.service;

import com.unc.admin.api.dto.ConsumerDto;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped consumer CRUD. Every operation is bound to the authenticated caller's
 * {@code tenant_id} taken from {@code TenantContext}.
 */
public interface ConsumerService {

    ConsumerDto createConsumer(ConsumerDto dto);

    List<ConsumerDto> listConsumers();

    ConsumerDto getConsumer(UUID id);

    ConsumerDto updateConsumer(UUID id, ConsumerDto dto);

    void deleteConsumer(UUID id);
}
