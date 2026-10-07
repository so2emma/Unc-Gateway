package com.unc.analytics.api.ingest;

import com.unc.analytics.api.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive controller exposing the request-log ingestion endpoint.
 */
@RestController
@RequestMapping("/api/analytics")
public class IngestController {

    private final RequestLogRepository requestLogRepository;

    public IngestController(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<IngestResponse>> ingest(
            @RequestHeader(name = TenantContext.TENANT_ID_HEADER, required = false) String tenantHeader,
            @Valid @RequestBody IngestRequest request,
            ServerWebExchange exchange
    ) {
        UUID tenantId = TenantContext.getTenantId(exchange);
        if (tenantId == null && tenantHeader != null && !tenantHeader.isBlank()) {
            tenantId = TenantContext.parseTenantId(tenantHeader);
        }
        if (tenantId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required"));
        }

        return requestLogRepository.save(tenantId, request)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new IngestResponse(saved.getId(), "created", saved.getCreatedAt())));
    }
}
