import { describe, it, expect, vi } from 'vitest';
import { createConsumer, listConsumers, getConsumer, Consumer } from '@/lib/adminApiClient';

describe('Consumer Signup Integration', () => {
  it('submits consumer creation, receives 201 Created, and verifies consumer in follow-up list', async () => {
    const consumersDb: Consumer[] = [];

    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';

      if (urlStr.endsWith('/api/admin/consumers') && method === 'POST') {
        const body = JSON.parse(init?.body as string);
        const newConsumer: Consumer = {
          id: 'generated-uuid-777',
          username: body.name,
          name: body.name,
          email: body.email,
          organization: body.organization,
          tenantId: (init?.headers as Record<string, string>)?.['X-Tenant-Id'],
          createdAt: new Date().toISOString(),
        };
        consumersDb.push(newConsumer);
        return {
          ok: true,
          status: 201,
          json: async () => newConsumer,
        } as Response;
      }

      if (urlStr.endsWith('/api/admin/consumers') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => consumersDb,
        } as Response;
      }

      if (urlStr.includes('/api/admin/consumers/') && method === 'GET') {
        const id = urlStr.split('/').pop();
        const found = consumersDb.find((c) => c.id === id);
        if (found) {
          return {
            ok: true,
            status: 200,
            json: async () => found,
          } as Response;
        }
        return {
          ok: false,
          status: 404,
          json: async () => ({ message: 'Consumer not found' }),
        } as Response;
      }

      return {
        ok: false,
        status: 404,
        json: async () => ({ message: 'Not found' }),
      } as Response;
    });

    // 1. Submit signup form payload to create consumer
    const created = await createConsumer(
      {
        name: 'Ada Lovelace',
        email: 'ada@acme.dev',
        organization: 'Acme Corp',
      },
      {
        baseUrl: 'http://localhost:8081',
        tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        apiKey: 'unc_live_sec_1234567890abcdef',
        fetchFn: mockFetch as any,
      }
    );

    expect(created).toBeDefined();
    expect(created.id).toBe('generated-uuid-777');
    expect(created.name).toBe('Ada Lovelace');
    expect(created.email).toBe('ada@acme.dev');
    expect(created.organization).toBe('Acme Corp');

    // 2. Follow-up GET /api/admin/consumers confirms new consumer appears
    const allConsumers = await listConsumers({
      baseUrl: 'http://localhost:8081',
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      apiKey: 'unc_live_sec_1234567890abcdef',
      fetchFn: mockFetch as any,
    });

    expect(allConsumers).toHaveLength(1);
    expect(allConsumers[0].id).toBe('generated-uuid-777');
    expect(allConsumers[0].name).toBe('Ada Lovelace');

    // 3. Follow-up GET /api/admin/consumers/{id} returns the specific consumer
    const fetched = await getConsumer('generated-uuid-777', {
      baseUrl: 'http://localhost:8081',
      tenantId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      apiKey: 'unc_live_sec_1234567890abcdef',
      fetchFn: mockFetch as any,
    });

    expect(fetched.id).toBe('generated-uuid-777');
    expect(fetched.email).toBe('ada@acme.dev');
  });
});
