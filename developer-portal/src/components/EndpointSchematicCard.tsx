'use client';

import React from 'react';
import { theme } from '@/styles/theme';

export interface SchematicRequestData {
  method?: string;
  path: string;
  headers?: Record<string, string>;
  body?: any;
}

export interface SchematicRouteData {
  routeId?: string;
  routeName?: string;
  routePath: string;
  serviceName?: string;
  serviceUrl?: string;
  stripPath?: boolean;
}

export interface SchematicResponseData {
  status?: number;
  statusText?: string;
  headers?: Record<string, string>;
  body?: any;
  rawBody?: string;
  durationMs?: number;
  error?: string;
}

export interface EndpointSchematicCardProps {
  route: SchematicRouteData;
  request?: SchematicRequestData;
  response?: SchematicResponseData;
  isLoading?: boolean;
  onSendRequest?: () => void;
  selectedApiKey?: string;
}

export function EndpointSchematicCard({
  route,
  request,
  response,
  isLoading = false,
  onSendRequest,
  selectedApiKey,
}: EndpointSchematicCardProps) {
  const method = (request?.method || 'GET').toUpperCase();
  const requestPath = request?.path || route.routePath;
  const is2xx = response?.status && response.status >= 200 && response.status < 300;
  const isError = response && (response.status ? response.status >= 400 : !!response.error);

  const displayMaskedKey = (key?: string) => {
    if (!key) return 'unc_key_••••••••••••';
    if (key.length <= 16) return `${key}••••`;
    return `${key.slice(0, 12)}••••••••••••`;
  };

  const formatBodyPreview = (data: any, raw?: string) => {
    if (!data && !raw) return null;
    if (typeof data === 'object') {
      try {
        const json = JSON.stringify(data, null, 2);
        return json.length > 300 ? json.slice(0, 300) + '...' : json;
      } catch {
        return String(data);
      }
    }
    return String(raw || data);
  };

  return (
    <div
      className="card"
      data-testid="endpoint-schematic-card"
      style={{
        background: theme.surface,
        borderRadius: theme.radiusCard,
        border: `1px solid ${theme.border}`,
        boxShadow: 'var(--shadow-soft, 0 1px 2px rgba(0,0,0,.04), 0 8px 24px rgba(0,0,0,.06))',
        padding: '24px',
        marginBottom: '24px',
        transition: 'all 0.2s ease',
      }}
    >
      {/* Card Header */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '12px',
          marginBottom: '20px',
          paddingBottom: '16px',
          borderBottom: `1px solid ${theme.border}`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span
            style={{
              fontWeight: 800,
              fontSize: '12px',
              color: '#FFFFFF',
              background: method === 'GET' ? '#2563EB' : theme.accent,
              padding: '3px 8px',
              borderRadius: '5px',
              letterSpacing: '0.5px',
            }}
          >
            {method}
          </span>
          <h3 style={{ fontSize: '16px', fontWeight: 700, color: theme.ink, margin: 0 }}>
            {route.routeName || route.routePath}
          </h3>
          <span
            className="mono"
            style={{
              fontSize: '13px',
              color: theme.muted,
              background: theme.background,
              padding: '2px 8px',
              borderRadius: '4px',
              border: `1px solid ${theme.border}`,
            }}
          >
            {route.routePath}
          </span>
        </div>

        <div>
          {onSendRequest && (
            <button
              onClick={onSendRequest}
              disabled={isLoading}
              className="btn btn-primary btn-sm"
              style={{
                backgroundColor: theme.accent,
                fontFamily: theme.fontFamily,
                minWidth: '110px',
              }}
            >
              {isLoading ? 'Executing...' : '⚡ Send Request'}
            </button>
          )}
        </div>
      </div>

      {/* Schematic 3-Segment Flow: Request -> Route -> Response */}
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'stretch',
          gap: '12px',
          position: 'relative',
        }}
      >
        {/* Segment 1: Inbound Request */}
        <div
          data-testid="schematic-segment-request"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            background: theme.background,
            border: `1px solid ${theme.border}`,
            borderRadius: theme.radiusControl,
            padding: '14px',
            fontFamily: theme.fontFamilyMono,
            fontSize: '12.5px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '10px',
              borderBottom: `1px solid ${theme.border}`,
              paddingBottom: '6px',
            }}
          >
            <span style={{ fontSize: '11px', fontWeight: 700, color: theme.muted, letterSpacing: '0.5px' }}>
              1. INBOUND REQUEST
            </span>
            <span style={{ fontSize: '11px', color: theme.muted }}>Client → Gateway</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', color: theme.ink }}>
            <div>
              <span style={{ color: theme.muted }}>Path: </span>
              <strong style={{ color: theme.ink }}>{requestPath}</strong>
            </div>

            <div>
              <span style={{ color: theme.muted }}>Method: </span>
              <span>{method}</span>
            </div>

            <div>
              <span style={{ color: theme.muted }}>Header: </span>
              <span>X-Api-Key: {displayMaskedKey(selectedApiKey || request?.headers?.['X-Api-Key'])}</span>
            </div>

            {request?.body && (
              <div style={{ marginTop: '4px' }}>
                <span style={{ color: theme.muted }}>Body: </span>
                <pre
                  style={{
                    background: '#FFFFFF',
                    border: `1px solid ${theme.border}`,
                    borderRadius: '4px',
                    padding: '6px',
                    fontSize: '11px',
                    maxHeight: '70px',
                    overflow: 'auto',
                    marginTop: '4px',
                  }}
                >
                  {typeof request.body === 'object' ? JSON.stringify(request.body, null, 2) : String(request.body)}
                </pre>
              </div>
            )}
          </div>
        </div>

        {/* Connector 1: Thin Orange Directional Arrow */}
        <div
          data-testid="schematic-connector-1"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: theme.accent,
            padding: '0 4px',
            flexShrink: 0,
          }}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={theme.accent} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="4" y1="12" x2="20" y2="12"></line>
            <polyline points="13 5 20 12 13 19"></polyline>
          </svg>
        </div>

        {/* Segment 2: Matched Route / Service */}
        <div
          data-testid="schematic-segment-route"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            background: theme.background,
            border: `1px solid ${theme.border}`,
            borderRadius: theme.radiusControl,
            padding: '14px',
            fontFamily: theme.fontFamilyMono,
            fontSize: '12.5px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '10px',
              borderBottom: `1px solid ${theme.border}`,
              paddingBottom: '6px',
            }}
          >
            <span style={{ fontSize: '11px', fontWeight: 700, color: theme.muted, letterSpacing: '0.5px' }}>
              2. ROUTE & SERVICE
            </span>
            <span
              data-testid="service-pill"
              className="pill pill-accent"
              style={{
                fontSize: '11px',
                padding: '2px 8px',
                background: theme.accentTint,
                color: theme.accent,
                border: '1px solid rgba(255, 107, 53, 0.4)',
              }}
            >
              {route.serviceName || 'mock-upstream'}
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', color: theme.ink }}>
            <div>
              <span style={{ color: theme.muted }}>Matched: </span>
              <strong style={{ color: theme.ink }}>{route.routePath}</strong>
            </div>

            <div>
              <span style={{ color: theme.muted }}>Upstream: </span>
              <span style={{ color: theme.muted, wordBreak: 'break-all' }}>
                {route.serviceUrl || 'http://mock-upstream:9090'}
              </span>
            </div>

            <div>
              <span style={{ color: theme.muted }}>Strip Path: </span>
              <span>{route.stripPath !== false ? 'true' : 'false'}</span>
            </div>

            <div style={{ marginTop: '2px' }}>
              <span
                style={{
                  fontSize: '11px',
                  color: theme.accent,
                  fontWeight: 600,
                }}
              >
                ● Dynamic Cache Active
              </span>
            </div>
          </div>
        </div>

        {/* Connector 2: Thin Orange Directional Arrow */}
        <div
          data-testid="schematic-connector-2"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: theme.accent,
            padding: '0 4px',
            flexShrink: 0,
          }}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={theme.accent} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="4" y1="12" x2="20" y2="12"></line>
            <polyline points="13 5 20 12 13 19"></polyline>
          </svg>
        </div>

        {/* Segment 3: Upstream Response */}
        <div
          data-testid="schematic-segment-response"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            background: theme.background,
            border: `1px solid ${theme.border}`,
            borderRadius: theme.radiusControl,
            padding: '14px',
            fontFamily: theme.fontFamilyMono,
            fontSize: '12.5px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '10px',
              borderBottom: `1px solid ${theme.border}`,
              paddingBottom: '6px',
            }}
          >
            <span style={{ fontSize: '11px', fontWeight: 700, color: theme.muted, letterSpacing: '0.5px' }}>
              3. RESPONSE
            </span>

            {response?.status ? (
              <span
                data-testid="response-status-badge"
                className={`pill ${is2xx ? 'pill-success' : 'pill-danger'}`}
                style={{
                  fontSize: '11.5px',
                  fontWeight: 700,
                  padding: '2px 8px',
                  background: is2xx ? 'var(--success-tint, #E7F8EF)' : 'var(--danger-tint, #FDECEC)',
                  color: is2xx ? theme.success : theme.danger,
                }}
              >
                {response.status} {response.statusText || (is2xx ? 'OK' : 'Error')}
              </span>
            ) : (
              <span style={{ fontSize: '11px', color: theme.muted }}>Awaiting probe</span>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', color: theme.ink }}>
            {isLoading ? (
              <div style={{ color: theme.accent, padding: '10px 0' }}>
                Routing request through gateway-core...
              </div>
            ) : response ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ color: theme.muted }}>Latency: </span>
                  <span style={{ fontWeight: 600 }}>{response.durationMs ?? 0}ms</span>
                </div>

                {response.error && (
                  <div style={{ color: theme.danger, fontSize: '11.5px' }}>
                    {response.error}
                  </div>
                )}

                {(response.body || response.rawBody) && (
                  <div style={{ marginTop: '4px' }}>
                    <span style={{ color: theme.muted, fontSize: '11px' }}>Payload Preview:</span>
                    <pre
                      style={{
                        background: '#FFFFFF',
                        border: `1px solid ${theme.border}`,
                        borderRadius: '4px',
                        padding: '6px 8px',
                        fontSize: '11px',
                        maxHeight: '90px',
                        overflow: 'auto',
                        marginTop: '4px',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-all',
                      }}
                    >
                      {formatBodyPreview(response.body, response.rawBody)}
                    </pre>
                  </div>
                )}
              </>
            ) : (
              <div style={{ color: theme.muted, padding: '12px 0', fontSize: '12px' }}>
                Click <strong>Send Request</strong> to execute a live proxied call.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default EndpointSchematicCard;
