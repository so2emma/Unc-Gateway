'use client';

import React, { Suspense, useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import {
  adminApiClient,
  Consumer,
  RouteItem,
  ServiceItem,
} from '@/lib/adminApiClient';
import {
  gatewayClient,
  GatewayResponseResult,
} from '@/lib/gatewayClient';
import {
  EndpointSchematicCard,
  SchematicRouteData,
  SchematicResponseData,
} from '@/components/EndpointSchematicCard';
import { theme } from '@/styles/theme';

function EndpointsPageContent() {
  const searchParams = useSearchParams();
  const queryConsumerId = searchParams.get('consumerId');

  const [consumerId, setConsumerId] = useState<string>(queryConsumerId || '');
  const [consumer, setConsumer] = useState<Consumer | null>(null);
  const [customApiKey, setCustomApiKey] = useState<string>('');
  const [routes, setRoutes] = useState<RouteItem[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Map of routeId -> GatewayResponseResult
  const [responses, setResponses] = useState<Record<string, SchematicResponseData>>({});
  const [loadingRoutes, setLoadingRoutes] = useState<Record<string, boolean>>({});

  useEffect(() => {
    // Purge any lingering keys from web storage to ensure zero persistent secrets
    if (typeof window !== 'undefined') {
      try {
        const keysToPurge: string[] = [];
        for (let i = 0; i < window.localStorage.length; i++) {
          const k = window.localStorage.key(i);
          if (k && (k.startsWith('unc_raw_key_') || k.startsWith('unc_active_'))) {
            keysToPurge.push(k);
          }
        }
        keysToPurge.forEach((k) => window.localStorage.removeItem(k));
        window.sessionStorage.removeItem('unc_active_api_key');
        window.sessionStorage.removeItem('unc_last_issued_key');
      } catch {}
    }
  }, []);

  useEffect(() => {
    if (queryConsumerId) {
      setConsumerId(queryConsumerId);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('unc_consumer_id', queryConsumerId);
      }
    } else if (typeof window !== 'undefined') {
      const stored = window.localStorage.getItem('unc_consumer_id');
      if (stored) {
        setConsumerId(stored);
      }
    }
  }, [queryConsumerId]);

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      // 1. Load routes and services
      const [fetchedRoutes, fetchedServices] = await Promise.all([
        adminApiClient.listRoutes().catch(() => [] as RouteItem[]),
        adminApiClient.listServices().catch(() => [] as ServiceItem[]),
      ]);
      setRoutes(fetchedRoutes);
      setServices(fetchedServices);

      // 2. Load consumer details if consumerId is known
      let activeConsumerId = consumerId;
      if (!activeConsumerId) {
        const consumers = await adminApiClient.listConsumers().catch(() => [] as Consumer[]);
        if (consumers.length > 0) {
          activeConsumerId = consumers[0].id;
          setConsumerId(activeConsumerId);
          setConsumer(consumers[0]);
        }
      } else {
        const c = await adminApiClient.getConsumer(activeConsumerId).catch(() => null);
        if (c) setConsumer(c);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to load gateway topology');
    } finally {
      setLoading(false);
    }
  }, [consumerId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleTestRoute = async (route: RouteItem, service?: ServiceItem) => {
    const routeKey = route.id;
    setLoadingRoutes((prev) => ({ ...prev, [routeKey]: true }));

    // Use in-memory key entered by user; if empty, request is sent unauthenticated (tests 401)
    const apiKeyToSend = customApiKey.trim();

    try {
      const routePath = route.paths || route.path || '/';
      const result: GatewayResponseResult = await gatewayClient.sendGatewayRequest({
        path: routePath,
        method: route.methods ? route.methods.split(',')[0].trim() : 'GET',
        apiKey: apiKeyToSend,
        tenantId: route.tenantId || consumer?.tenantId,
      });

      setResponses((prev) => ({
        ...prev,
        [routeKey]: {
          status: result.status,
          statusText: result.statusText,
          headers: result.headers,
          body: result.data,
          rawBody: result.rawBody,
          durationMs: result.durationMs,
          error: result.error,
        },
      }));
    } catch (err: any) {
      setResponses((prev) => ({
        ...prev,
        [routeKey]: {
          status: 500,
          statusText: 'Internal Error',
          error: err?.message || 'Request failed',
          durationMs: 0,
        },
      }));
    } finally {
      setLoadingRoutes((prev) => ({ ...prev, [routeKey]: false }));
    }
  };

  const handleTestAll = async () => {
    for (const r of routes) {
      const s = services.find((svc) => svc.id === r.serviceId);
      await handleTestRoute(r, s);
    }
  };

  return (
    <div style={{ flex: 1, padding: '40px 0 60px' }}>
      <div className="container" style={{ maxWidth: '1040px' }}>
        {/* Top Header */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
            marginBottom: '28px',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
              <span
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                  color: theme.accent,
                }}
              >
                Live Topology
              </span>
              <span style={{ color: theme.border }}>/</span>
              <span style={{ fontSize: '12px', color: theme.muted }}>Dynamic Routing Schematics</span>
            </div>
            <h1
              style={{
                fontSize: '28px',
                fontWeight: 800,
                color: theme.ink,
                letterSpacing: '-0.5px',
              }}
            >
              Schematic Endpoint Cards
            </h1>
            <p style={{ color: theme.muted, fontSize: '15px', marginTop: '4px' }}>
              Inspect the end-to-end Request → Route → Response topology executed dynamically by Gateway Core.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {consumerId && (
              <Link
                href={`/keys?consumerId=${encodeURIComponent(consumerId)}`}
                className="btn btn-outline"
                style={{ fontSize: '13.5px' }}
              >
                Manage API Keys
              </Link>
            )}
            {routes.length > 0 && (
              <button
                onClick={handleTestAll}
                className="btn btn-primary"
                style={{
                  backgroundColor: theme.accent,
                  fontSize: '13.5px',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <svg
                  width="13"
                  height="13"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <polygon points="5 3 19 12 5 21 5 3" />
                </svg>
                <span>Probe All Routes</span>
              </button>
            )}
          </div>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div
            role="alert"
            style={{
              background: 'var(--danger-tint, #FDECEC)',
              color: 'var(--danger, #E5484D)',
              borderRadius: theme.radiusControl,
              padding: '12px 16px',
              marginBottom: '24px',
              fontSize: '14px',
            }}
          >
            {errorMessage}
          </div>
        )}

        {/* Control Bar: Active Consumer & Live Request Key */}
        <div
          className="card"
          style={{
            padding: '20px 24px',
            marginBottom: '28px',
            borderRadius: theme.radiusCard,
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            background: theme.surface,
            border: `1px solid ${theme.border}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '16px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <span
                style={{
                  fontWeight: 700,
                  fontSize: '13px',
                  color: theme.ink,
                }}
              >
                Active Consumer:
              </span>
              <span
                style={{
                  fontSize: '13.5px',
                  color: theme.ink,
                  background: theme.background,
                  padding: '4px 10px',
                  borderRadius: '6px',
                  border: `1px solid ${theme.border}`,
                }}
              >
                <strong>{consumer?.name || consumer?.username || 'Consumer'}</strong>{' '}
                <span className="mono" style={{ fontSize: '11.5px', color: theme.muted }}>
                  ({consumerId ? consumerId.slice(0, 8) + '...' : 'none'})
                </span>
              </span>
            </div>

            {consumerId && (
              <Link
                href={`/keys?consumerId=${encodeURIComponent(consumerId)}`}
                style={{
                  fontSize: '13px',
                  color: theme.accent,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                  textDecoration: 'none',
                  fontWeight: 500,
                }}
              >
                <span>Issue & manage keys</span>
                <span aria-hidden="true">&rarr;</span>
              </Link>
            )}
          </div>

          {/* Dedicated API Key Input Field (In-Memory Only) */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
              paddingTop: '16px',
              borderTop: `1px solid ${theme.border}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
              <label htmlFor="custom-api-key-input" style={{ fontSize: '13px', fontWeight: 700, color: theme.ink }}>
                API Key for Live Request (In-Memory Only):
              </label>
              {customApiKey && (
                <button
                  type="button"
                  onClick={() => setCustomApiKey('')}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: theme.muted,
                    fontSize: '12px',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  Clear key (test unauthenticated 401)
                </button>
              )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <input
                id="custom-api-key-input"
                data-testid="api-key-input"
                type="text"
                placeholder="Paste your raw secret key here (unc_key_...) to authenticate"
                value={customApiKey}
                onChange={(e) => setCustomApiKey(e.target.value)}
                className="mono"
                style={{
                  flex: 1,
                  minWidth: '280px',
                  padding: '9px 14px',
                  fontSize: '13px',
                  borderRadius: theme.radiusControl,
                  border: `1px solid ${customApiKey ? theme.accent : theme.border}`,
                  background: '#FFFFFF',
                  fontFamily: theme.fontFamilyMono,
                }}
              />
              <button
                type="button"
                onClick={async () => {
                  try {
                    const text = await navigator.clipboard.readText();
                    if (text && text.trim()) {
                      setCustomApiKey(text.trim());
                    }
                  } catch {}
                }}
                className="btn btn-outline btn-sm"
                style={{
                  fontSize: '12px',
                  padding: '8px 12px',
                  whiteSpace: 'nowrap',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <svg
                  width="13"
                  height="13"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
                <span>Paste from Clipboard</span>
              </button>
            </div>

            <div style={{ fontSize: '12px', color: theme.muted, marginTop: '2px' }}>
              {customApiKey ? (
                <span
                  style={{
                    color: theme.success,
                    fontWeight: 500,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px',
                  }}
                >
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                  <span>
                    In-memory key supplied: <code className="mono">{customApiKey.slice(0, 16)}••••••••</code> (will send via <code className="mono">X-Api-Key</code> header)
                  </span>
                </span>
              ) : (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                  <span>
                    <strong>Security Policy:</strong> Raw API keys are never cached in browser storage or saved in plaintext on the server.
                    Copy your secret key when issuing it on the{' '}
                    <Link href={`/keys?consumerId=${encodeURIComponent(consumerId)}`} style={{ color: theme.accent, textDecoration: 'underline' }}>
                      API Keys page
                    </Link>
                    , and paste it above to test authenticated 200 OK responses.
                  </span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* List of Endpoint Schematic Cards */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: theme.muted }}>
            Loading gateway routes and services...
          </div>
        ) : routes.length === 0 ? (
          <div
            className="card"
            style={{
              padding: '48px 24px',
              textAlign: 'center',
              borderRadius: theme.radiusCard,
            }}
          >
            <h3 style={{ fontSize: '16px', fontWeight: 700, color: theme.ink, marginBottom: '8px' }}>
              No Resolvable Routes Found
            </h3>
            <p style={{ color: theme.muted, fontSize: '14px', maxWidth: '440px', margin: '0 auto 20px' }}>
              No routes are registered for this tenant yet. Once routes are registered in the Admin API, dynamic routing schematics will appear here.
            </p>
          </div>
        ) : (
          <div>
            {routes.map((route) => {
              const matchedService = services.find((s) => s.id === route.serviceId);
              const routeData: SchematicRouteData = {
                routeId: route.id,
                routeName: route.name,
                routePath: route.paths || route.path || '/',
                serviceName: matchedService?.name || 'mock-upstream',
                serviceUrl: matchedService?.url || matchedService?.upstreamUrl || 'http://mock-upstream:9090',
                stripPath: route.stripPath !== false,
              };

              const effectiveKey = customApiKey.trim();

              return (
                <EndpointSchematicCard
                  key={route.id}
                  route={routeData}
                  request={{
                    method: route.methods ? route.methods.split(',')[0].trim() : 'GET',
                    path: route.paths || route.path || '/',
                    headers: {
                      'X-Api-Key': effectiveKey || '(none - unauthenticated)',
                    },
                  }}
                  response={responses[route.id]}
                  isLoading={!!loadingRoutes[route.id]}
                  onSendRequest={() => handleTestRoute(route, matchedService)}
                  selectedApiKey={effectiveKey}
                />
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default function EndpointsPage() {
  return (
    <Suspense
      fallback={
        <div style={{ padding: '60px', textAlign: 'center', color: theme.muted }}>
          Loading Endpoint Schematics...
        </div>
      }
    >
      <EndpointsPageContent />
    </Suspense>
  );
}
