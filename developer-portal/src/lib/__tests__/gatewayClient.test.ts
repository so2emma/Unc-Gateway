import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  sendGatewayRequest,
  resolveGatewayBaseUrl,
  gatewayClient,
} from '../gatewayClient';

describe('gatewayClient', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('resolveGatewayBaseUrl', () => {
    it('prefers explicit baseUrl option when provided', () => {
      const url = resolveGatewayBaseUrl({ path: '/demo', baseUrl: 'http://custom-gw:8080/' });
      expect(url).toBe('http://custom-gw:8080');
    });

    it('returns /api/gateway in browser/jsdom environment when no option provided', () => {
      const url = resolveGatewayBaseUrl({ path: '/demo' });
      expect(url).toBe('/api/gateway');
    });
  });

  describe('sendGatewayRequest', () => {
    it('successfully sends GET request with API key header and parses JSON response', async () => {
      const mockPayload = { message: 'hello from mock upstream', status: 'ok' };
      mockFetch.mockResolvedValueOnce({
        status: 200,
        statusText: 'OK',
        text: async () => JSON.stringify(mockPayload),
        headers: new Headers({
          'content-type': 'application/json',
          'x-request-id': 'req-123',
        }),
      });

      const res = await sendGatewayRequest({
        path: '/proxy/users',
        apiKey: 'unc_key_live_test_123',
        tenantId: 'tenant-abc',
        baseUrl: 'http://localhost:8080',
        fetchFn: mockFetch,
      });

      expect(res.status).toBe(200);
      expect(res.isSuccess).toBe(true);
      expect(res.data).toEqual(mockPayload);
      expect(res.headers['x-request-id']).toBe('req-123');
      expect(res.durationMs).toBeGreaterThanOrEqual(0);

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/proxy/users');
      expect(init.method).toBe('GET');
      expect(init.headers['X-Api-Key']).toBe('unc_key_live_test_123');
      expect(init.headers['X-Tenant-Id']).toBe('tenant-abc');
    });

    it('handles 401 Unauthorized response from gateway key-auth filter', async () => {
      mockFetch.mockResolvedValueOnce({
        status: 401,
        statusText: 'Unauthorized',
        text: async () => '',
        headers: new Headers(),
      });

      const res = await sendGatewayRequest({
        path: '/proxy/secure',
        apiKey: 'unc_key_revoked',
        baseUrl: 'http://localhost:8080',
        fetchFn: mockFetch,
      });

      expect(res.status).toBe(401);
      expect(res.isSuccess).toBe(false);
      expect(res.statusText).toBe('Unauthorized');
    });

    it('handles POST request with JSON payload', async () => {
      const mockPayload = { success: true };
      mockFetch.mockResolvedValueOnce({
        status: 201,
        statusText: 'Created',
        text: async () => JSON.stringify(mockPayload),
        headers: new Headers(),
      });

      const res = await sendGatewayRequest({
        path: 'items',
        method: 'POST',
        body: { name: 'widget' },
        apiKey: 'key-1',
        baseUrl: 'http://localhost:8080',
        fetchFn: mockFetch,
      });

      expect(res.status).toBe(201);
      expect(res.isSuccess).toBe(true);
      expect(res.data).toEqual(mockPayload);

      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/items');
      expect(init.method).toBe('POST');
      expect(init.headers['Content-Type']).toBe('application/json');
      expect(init.body).toBe(JSON.stringify({ name: 'widget' }));
    });

    it('handles network failure gracefully without throwing', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Connection refused'));

      const res = await sendGatewayRequest({
        path: '/down',
        baseUrl: 'http://localhost:8080',
        fetchFn: mockFetch,
      });

      expect(res.status).toBe(0);
      expect(res.isSuccess).toBe(false);
      expect(res.statusText).toBe('Network Error');
      expect(res.error).toBe('Connection refused');
    });
  });
});
