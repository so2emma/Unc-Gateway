import { describe, it, expect, vi } from 'vitest';
import {
  issueConsumerKey,
  listConsumerKeys,
  revokeConsumerKey,
  ConsumerKey,
} from '@/lib/adminApiClient';
import { sendGatewayRequest } from '@/lib/gatewayClient';

describe('Consumer Key Management Integration', () => {
  it('issues a key, verifies subsequent GET returns metadata, executes proxy call, and revokes key', async () => {
    // In-memory keys repository simulating admin-api database state
    const keysDb: ConsumerKey[] = [];

    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = (init?.method || 'GET').toUpperCase();

      // POST /api/admin/consumers/{id}/keys
      if (urlStr.includes('/api/admin/consumers/') && urlStr.endsWith('/keys') && method === 'POST') {
        const parts = urlStr.split('/consumers/')[1].split('/keys')[0];
        const consumerId = decodeURIComponent(parts);
        const body = init?.body ? JSON.parse(init.body as string) : {};

        const rawKey = 'unc_key_' + Math.random().toString(16).substring(2, 18);
        const keyPrefix = rawKey.substring(0, 16);
        const newKey: ConsumerKey = {
          id: 'key-' + Math.random().toString(36).substring(2, 9),
          consumerId,
          name: body.name || 'default',
          keyPrefix,
          keyHash: 'hash-' + Math.random().toString(16).substring(2, 10),
          status: 'ACTIVE',
          key: rawKey,
          createdAt: new Date().toISOString(),
        };

        keysDb.push(newKey);

        return {
          ok: true,
          status: 201,
          json: async () => newKey,
          text: async () => JSON.stringify(newKey),
          headers: new Headers({ 'content-type': 'application/json' }),
        } as Response;
      }

      // GET /api/admin/consumers/{id}/keys
      if (urlStr.includes('/api/admin/consumers/') && urlStr.endsWith('/keys') && method === 'GET') {
        const parts = urlStr.split('/consumers/')[1].split('/keys')[0];
        const consumerId = decodeURIComponent(parts);

        // Strip raw key in list response, keep only active keys
        const consumerKeys = keysDb
          .filter((k) => k.consumerId === consumerId && k.status === 'ACTIVE')
          .map((k) => {
            const { key: _, ...meta } = k;
            return meta as ConsumerKey;
          });

        return {
          ok: true,
          status: 200,
          json: async () => consumerKeys,
          text: async () => JSON.stringify(consumerKeys),
          headers: new Headers({ 'content-type': 'application/json' }),
        } as Response;
      }

      // DELETE /api/admin/consumers/{id}/keys/{keyId}
      if (urlStr.includes('/api/admin/consumers/') && method === 'DELETE') {
        const urlParts = urlStr.split('/');
        const keyId = urlParts[urlParts.length - 1];
        const keyObj = keysDb.find((k) => k.id === keyId);
        if (keyObj) {
          keyObj.status = 'REVOKED';
          keyObj.revokedAt = new Date().toISOString();
        }

        return {
          ok: true,
          status: 204,
          text: async () => '',
          headers: new Headers(),
        } as Response;
      }

      // Gateway Core proxy simulator
      if (urlStr.includes('/demo-milestone')) {
        const reqHeaders = (init?.headers || {}) as Record<string, string>;
        const providedKey = reqHeaders['X-Api-Key'];
        const matchingKey = keysDb.find((k) => k.key === providedKey && k.status === 'ACTIVE');

        if (matchingKey) {
          const respBody = JSON.stringify({
            message: 'Hello from mock upstream',
            consumerId: matchingKey.consumerId,
          });
          return {
            ok: true,
            status: 200,
            statusText: 'OK',
            text: async () => respBody,
            json: async () => JSON.parse(respBody),
            headers: new Headers({
              'content-type': 'application/json',
              'x-gateway-route': 'milestone-route',
            }),
          } as Response;
        }

        return {
          ok: false,
          status: 401,
          statusText: 'Unauthorized',
          text: async () => JSON.stringify({ message: 'Unauthorized: valid API key required' }),
          json: async () => ({ message: 'Unauthorized: valid API key required' }),
          headers: new Headers({ 'content-type': 'application/json' }),
        } as Response;
      }

      return {
        ok: false,
        status: 404,
        text: async () => 'Not found',
        headers: new Headers(),
      } as Response;
    });

    const testConsumerId = 'consumer-integration-42';

    // 1. Issue an API key for the consumer
    const issuedKey = await issueConsumerKey(
      testConsumerId,
      { name: 'ci-integration-key' },
      {
        baseUrl: 'http://localhost:8081',
        tenantId: 'tenant-t1',
        apiKey: 'admin-root-key',
        fetchFn: mockFetch as any,
      }
    );

    expect(issuedKey).toBeDefined();
    expect(issuedKey.id).toBeDefined();
    expect(issuedKey.name).toBe('ci-integration-key');
    expect(issuedKey.key).toBeDefined(); // Raw key returned on issuance
    expect(issuedKey.key?.startsWith('unc_key_')).toBe(true);
    expect(issuedKey.status).toBe('ACTIVE');

    // 2. Follow-up GET confirms newly issued key appears in active list (without raw key)
    const activeKeys = await listConsumerKeys(testConsumerId, {
      baseUrl: 'http://localhost:8081',
      tenantId: 'tenant-t1',
      apiKey: 'admin-root-key',
      fetchFn: mockFetch as any,
    });

    expect(activeKeys).toHaveLength(1);
    expect(activeKeys[0].id).toBe(issuedKey.id);
    expect(activeKeys[0].keyPrefix).toBe(issuedKey.keyPrefix);
    expect((activeKeys[0] as any).key).toBeUndefined(); // Raw key must not be returned on list

    // 3. Send a demo proxied request carrying this valid key through Gateway Core
    const proxySuccess = await sendGatewayRequest({
      path: '/demo-milestone',
      apiKey: issuedKey.key,
      tenantId: 'tenant-t1',
      baseUrl: 'http://localhost:8080',
      fetchFn: mockFetch as any,
    });

    expect(proxySuccess.status).toBe(200);
    expect(proxySuccess.isSuccess).toBe(true);
    expect(proxySuccess.data).toEqual({
      message: 'Hello from mock upstream',
      consumerId: testConsumerId,
    });

    // 4. Revoke the API key
    await revokeConsumerKey(testConsumerId, issuedKey.id, {
      baseUrl: 'http://localhost:8081',
      tenantId: 'tenant-t1',
      apiKey: 'admin-root-key',
      fetchFn: mockFetch as any,
    });

    // 5. Follow-up GET confirms the key is no longer in the active key list
    const keysAfterRevocation = await listConsumerKeys(testConsumerId, {
      baseUrl: 'http://localhost:8081',
      tenantId: 'tenant-t1',
      apiKey: 'admin-root-key',
      fetchFn: mockFetch as any,
    });

    expect(keysAfterRevocation).toHaveLength(0);

    // 6. Request through Gateway Core with revoked key now fails with HTTP 401
    const proxyUnauthorized = await sendGatewayRequest({
      path: '/demo-milestone',
      apiKey: issuedKey.key,
      tenantId: 'tenant-t1',
      baseUrl: 'http://localhost:8080',
      fetchFn: mockFetch as any,
    });

    expect(proxyUnauthorized.status).toBe(401);
    expect(proxyUnauthorized.isSuccess).toBe(false);
  });
});
