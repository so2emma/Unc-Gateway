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
});
