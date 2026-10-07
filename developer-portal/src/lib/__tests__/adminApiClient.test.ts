import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createConsumer,
  listConsumers,
  getConsumer,
  adminApiClient,
  AdminApiError,
} from '../adminApiClient';

describe('adminApiClient', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('createConsumer', () => {
    it('successfully posts to /api/admin/consumers and returns created consumer', async () => {
      const mockCreated = {
        id: '11111111-2222-3333-4444-555555555555',
        username: 'Ada Lovelace',
        name: 'Ada Lovelace',
        email: 'ada@acme.dev',
        organization: 'Acme Corp',
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: async () => mockCreated,
      });

      const result = await createConsumer(
        {
          name: 'Ada Lovelace',
          email: 'ada@acme.dev',
          organization: 'Acme Corp',
        },
        {
          baseUrl: 'http://localhost:8081',
          tenantId: 'tenant-123',
          apiKey: 'key-abc',
          fetchFn: mockFetch,
        }
      );

      expect(result).toEqual(mockCreated);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8081/api/admin/consumers');
      expect(options.method).toBe('POST');
      expect(options.headers).toEqual({
        'Content-Type': 'application/json',
        'X-Tenant-Id': 'tenant-123',
        'X-Api-Key': 'key-abc',
      });
      const parsedBody = JSON.parse(options.body);
      expect(parsedBody).toEqual({
        name: 'Ada Lovelace',
        username: 'Ada Lovelace',
        email: 'ada@acme.dev',
        organization: 'Acme Corp',
        tenantId: 'tenant-123',
      });
    });

    it('throws AdminApiError with message on server error', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: async () => ({ message: 'Consumer name is required' }),
      });

      await expect(
        createConsumer(
          { name: '' },
          { baseUrl: 'http://localhost:8081', fetchFn: mockFetch }
        )
      ).rejects.toThrow('Consumer name is required');
    });

    it('handles non-JSON error response gracefully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => {
          throw new Error('Not JSON');
        },
      });

      await expect(
        createConsumer(
          { name: 'Ada' },
          { baseUrl: 'http://localhost:8081', fetchFn: mockFetch }
        )
      ).rejects.toThrow('Failed to create consumer: HTTP 500');
    });
  });

  describe('listConsumers and getConsumer', () => {
    it('fetches consumer list', async () => {
      const consumers = [
        { id: '1', username: 'alice', email: 'alice@example.com' },
      ];
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => consumers,
      });

      const res = await listConsumers({
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });
      expect(res).toEqual(consumers);
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers',
        expect.objectContaining({ method: 'GET' })
      );
    });

    it('fetches single consumer by id', async () => {
      const consumer = { id: 'uuid-1', username: 'bob' };
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => consumer,
      });

      const res = await getConsumer('uuid-1', {
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });
      expect(res).toEqual(consumer);
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers/uuid-1',
        expect.objectContaining({ method: 'GET' })
      );
    });
  });

  describe('consumer key management', () => {
    it('issues a new consumer key via POST /api/admin/consumers/{id}/keys', async () => {
      const mockKey = {
        id: 'key-123',
        consumerId: 'cons-456',
        name: 'default',
        keyPrefix: 'unc_key_abcd1234',
        keyHash: 'hash-abc',
        status: 'ACTIVE',
        key: 'unc_key_abcd1234fullsecretvalue',
        createdAt: '2026-10-01T00:00:00Z',
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: async () => mockKey,
      });

      const result = await adminApiClient.issueConsumerKey(
        'cons-456',
        { name: 'ci-key' },
        {
          baseUrl: 'http://localhost:8081',
          tenantId: 'tenant-1',
          apiKey: 'sec-key',
          fetchFn: mockFetch,
        }
      );

      expect(result).toEqual(mockKey);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, opts] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8081/api/admin/consumers/cons-456/keys');
      expect(opts.method).toBe('POST');
      expect(opts.headers['X-Tenant-Id']).toBe('tenant-1');
      expect(opts.headers['X-Api-Key']).toBe('sec-key');
      expect(JSON.parse(opts.body)).toEqual({ name: 'ci-key' });
    });

    it('lists consumer keys via GET /api/admin/consumers/{id}/keys', async () => {
      const mockKeys = [
        {
          id: 'key-1',
          consumerId: 'cons-456',
          name: 'prod',
          keyPrefix: 'unc_key_1111',
          status: 'ACTIVE',
        },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => mockKeys,
      });

      const result = await adminApiClient.listConsumerKeys('cons-456', {
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });

      expect(result).toEqual(mockKeys);
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers/cons-456/keys',
        expect.objectContaining({ method: 'GET' })
      );
    });

    it('revokes a consumer key via DELETE /api/admin/consumers/{id}/keys/{keyId}', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
      });

      await adminApiClient.revokeConsumerKey('cons-456', 'key-1', {
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers/cons-456/keys/key-1',
        expect.objectContaining({ method: 'DELETE' })
      );
    });
  });

  describe('routes and services listing', () => {
    it('lists routes via GET /api/admin/routes', async () => {
      const mockRoutes = [
        { id: 'r1', name: 'demo-route', paths: '/demo' },
      ];
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => mockRoutes,
      });

      const res = await adminApiClient.listRoutes({
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });
      expect(res).toEqual(mockRoutes);
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/routes',
        expect.objectContaining({ method: 'GET' })
      );
    });

    it('lists services via GET /api/admin/services', async () => {
      const mockServices = [
        { id: 's1', name: 'demo-svc', url: 'http://upstream:9090' },
      ];
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => mockServices,
      });

      const res = await adminApiClient.listServices({
        baseUrl: 'http://localhost:8081',
        fetchFn: mockFetch,
      });
      expect(res).toEqual(mockServices);
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/services',
        expect.objectContaining({ method: 'GET' })
      );
    });
  });
});

