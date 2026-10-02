package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.ConsumerDto;
import com.unc.admin.api.entity.ConsumerEntity;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.service.ConsumerService;
import com.unc.admin.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConsumerServiceImpl implements ConsumerService {

    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$";

    private final ConsumerRepository consumerRepository;

    public ConsumerServiceImpl(ConsumerRepository consumerRepository) {
        this.consumerRepository = consumerRepository;
    }

    @Override
    public ConsumerDto createConsumer(ConsumerDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        String username = dto.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumer name is required");
        }
        username = username.trim();
        validateEmail(dto.getEmail());

        if (consumerRepository.existsByUsernameAndTenantId(username, tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consumer '" + username + "' already exists for this tenant");
        }

        ConsumerEntity entity = new ConsumerEntity();
        entity.setTenantId(tenantId);
        entity.setUsername(username);
        entity.setCustomId(trimToNull(dto.getCustomId()));
        entity.setEmail(trimToNull(dto.getEmail()));
        entity.setOrganization(trimToNull(dto.getOrganization()));

        return toDto(consumerRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumerDto> listConsumers() {
        UUID tenantId = TenantContext.getTenantId();
        return consumerRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDto getConsumer(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return consumerRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumer not found"));
    }

    @Override
    public ConsumerDto updateConsumer(UUID id, ConsumerDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        ConsumerEntity entity = consumerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumer not found"));

        if (dto.getUsername() != null) {
            String username = dto.getUsername().trim();
            if (username.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumer name must not be blank");
            }
            if (!username.equals(entity.getUsername())
                    && consumerRepository.existsByUsernameAndTenantId(username, tenantId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Consumer '" + username + "' already exists for this tenant");
            }
            entity.setUsername(username);
        }
        if (dto.getEmail() != null) {
            validateEmail(dto.getEmail());
            entity.setEmail(trimToNull(dto.getEmail()));
        }
        if (dto.getCustomId() != null) {
            entity.setCustomId(trimToNull(dto.getCustomId()));
        }
        if (dto.getOrganization() != null) {
            entity.setOrganization(trimToNull(dto.getOrganization()));
        }

        return toDto(consumerRepository.save(entity));
    }

    @Override
    public void deleteConsumer(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        if (!consumerRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumer not found");
        }
        consumerRepository.deleteByIdAndTenantId(id, tenantId);
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        if (!email.trim().matches(EMAIL_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumer email is not a valid email address");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ConsumerDto toDto(ConsumerEntity entity) {
        ConsumerDto dto = new ConsumerDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setUsername(entity.getUsername());
        dto.setCustomId(entity.getCustomId());
        dto.setEmail(entity.getEmail());
        dto.setOrganization(entity.getOrganization());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
