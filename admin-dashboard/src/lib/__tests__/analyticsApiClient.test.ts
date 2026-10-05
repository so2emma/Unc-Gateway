import { describe, it, expect, vi } from 'vitest';
import {
  analyticsApiClient,
  AnalyticsApiError,
  getTrafficPulse,
  getLatencyMetrics,
  resolveAnalyticsBaseUrl,
  resolveTenantId,
} from '../analyticsApiClient';

describe('analyticsApiClient', () => {
  const sampleBuckets = [
    { bucketStart: '2026-10-05T12:00:00Z', requestCount: 15 },
    { bucketStart: '2026-10-05T12:00:01Z', requestCount: 22 },
  ];

  const sampleLatency = {
    p95: 42.5,
    p99: 185.0,
  };

  it('fetches traffic pulse successfully with X-Tenant-Id header and limit query param', async () => {
    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';
      const headers = (init?.headers as Record<string, string>) || {};

      expect(headers['X-Tenant-Id']).toBe('tenant-a');
      expect(headers['Accept']).toBe('application/json');

      if (urlStr.includes('/api/analytics/traffic-pulse?limit=30') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => ({ buckets: sampleBuckets }),
        } as Response;
      }
      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const opts = { baseUrl: 'http://localhost:8083', tenantId: 'tenant-a', limit: 30, fetchFn: mockFetch as any };
    const res = await getTrafficPulse(opts);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(res.buckets).toEqual(sampleBuckets);
  });

  it('normalizes alternative bucket payload shapes (entries, data, raw arrays)', async () => {
    const mockFetch = vi.fn(async () => {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          entries: [
            { start: '2026-10-05T12:00:00Z', count: 10 },
            { timestamp: '2026-10-05T12:00:01Z', requests: 25 },
          ],
        }),
      } as Response;
    });

    const res = await getTrafficPulse({ baseUrl: 'http://localhost:8083', fetchFn: mockFetch as any });
    expect(res.buckets).toHaveLength(2);
    expect(res.buckets[0]).toEqual({ bucketStart: '2026-10-05T12:00:00Z', requestCount: 10 });
    expect(res.buckets[1]).toEqual({ bucketStart: '2026-10-05T12:00:01Z', requestCount: 25 });
  });

  it('fetches latency metrics successfully with p95 and p99 values', async () => {
    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';
      const headers = (init?.headers as Record<string, string>) || {};

      expect(headers['X-Tenant-Id']).toBe('tenant-test');

      if (urlStr.endsWith('/api/analytics/metrics/latency') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => sampleLatency,
        } as Response;
      }
      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const opts = { baseUrl: 'http://localhost:8083', tenantId: 'tenant-test', fetchFn: mockFetch as any };
    const res = await getLatencyMetrics(opts);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(res).toEqual(sampleLatency);
  });

  it('throws AnalyticsApiError on HTTP error status code', async () => {
    const mockFetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ message: 'X-Tenant-Id header is required' }),
    } as Response));

    await expect(
      getTrafficPulse({ baseUrl: 'http://localhost:8083', fetchFn: mockFetch as any })
    ).rejects.toThrow(AnalyticsApiError);
  });

  it('correctly exports analyticsApiClient object with convenience methods', async () => {
    expect(analyticsApiClient.getTrafficPulse).toBeDefined();
    expect(analyticsApiClient.getLatencyMetrics).toBeDefined();
  });
});
