import { describe, it, expect, vi } from 'vitest';
import {
  adminApiClient,
  AdminApiError,
  listServices,
  getService,
  createService,
  updateService,
  deleteService,
  listRoutes,
  getRoute,
  createRoute,
  updateRoute,
  deleteRoute,
  listConsumers,
  getConsumer,
  createConsumer,
  updateConsumer,
  deleteConsumer,
  listPluginConfigs,
  getPluginConfig,
  createPluginConfig,
  updatePluginConfig,
  deletePluginConfig,
} from '../adminApiClient';

describe('adminApiClient', () => {
  const sampleService = {
    id: 's-1',
    name: 'order-service',
    url: 'http://order-backend:8080',
    connectTimeout: 5000,
    readTimeout: 40000,
  };

  const sampleRoute = {
    id: 'r-1',
    serviceId: 's-1',
    name: 'order-route',
    paths: '/api/orders',
    stripPath: true,
  };

  const sampleConsumer = {
    id: 'c-1',
    name: 'retail-client',
    username: 'retail-client',
    email: 'client@retail.com',
  };

  const samplePlugin = {
    id: 'p-1',
    name: 'rate-limit',
    ordering: 5,
    enabled: true,
    config: { minute: 60 },
  };

  it('handles services CRUD successfully', async () => {
    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';

      if (urlStr.endsWith('/api/admin/services') && method === 'GET') {
        return { ok: true, status: 200, json: async () => [sampleService] } as Response;
      }
      if (urlStr.endsWith('/api/admin/services/s-1') && method === 'GET') {
        return { ok: true, status: 200, json: async () => sampleService } as Response;
      }
      if (urlStr.endsWith('/api/admin/services') && method === 'POST') {
        return { ok: true, status: 201, json: async () => sampleService } as Response;
      }
      if (urlStr.endsWith('/api/admin/services/s-1') && method === 'PUT') {
        return { ok: true, status: 200, json: async () => ({ ...sampleService, name: 'order-svc-updated' }) } as Response;
      }
      if (urlStr.endsWith('/api/admin/services/s-1') && method === 'DELETE') {
        return { ok: true, status: 204 } as Response;
      }
      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const opts = { baseUrl: 'http://localhost:8081', fetchFn: mockFetch as any };

    const services = await listServices(opts);
    expect(services).toEqual([sampleService]);

    const service = await getService('s-1', opts);
    expect(service).toEqual(sampleService);

    const created = await createService({ name: 'order-service', url: 'http://order-backend:8080' }, opts);
    expect(created).toEqual(sampleService);

    const updated = await updateService('s-1', { name: 'order-svc-updated' }, opts);
    expect(updated.name).toBe('order-svc-updated');

    await expect(deleteService('s-1', opts)).resolves.toBeUndefined();
  });

  it('handles routes CRUD successfully', async () => {
    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';

      if (urlStr.endsWith('/api/admin/routes') && method === 'GET') {
        return { ok: true, status: 200, json: async () => [sampleRoute] } as Response;
      }
      if (urlStr.endsWith('/api/admin/routes/r-1') && method === 'GET') {
        return { ok: true, status: 200, json: async () => sampleRoute } as Response;
      }
      if (urlStr.endsWith('/api/admin/routes') && method === 'POST') {
        return { ok: true, status: 201, json: async () => sampleRoute } as Response;
      }
      if (urlStr.endsWith('/api/admin/routes/r-1') && method === 'PUT') {
        return { ok: true, status: 200, json: async () => ({ ...sampleRoute, paths: '/api/v2/orders' }) } as Response;
      }
      if (urlStr.endsWith('/api/admin/routes/r-1') && method === 'DELETE') {
        return { ok: true, status: 204 } as Response;
      }
      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const opts = { baseUrl: 'http://localhost:8081', fetchFn: mockFetch as any };

    const routes = await listRoutes(opts);
    expect(routes).toEqual([sampleRoute]);

    const route = await getRoute('r-1', opts);
    expect(route).toEqual(sampleRoute);

    const created = await createRoute({ serviceId: 's-1', name: 'order-route', paths: '/api/orders' }, opts);
    expect(created).toEqual(sampleRoute);

    const updated = await updateRoute('r-1', { paths: '/api/v2/orders' }, opts);
    expect(updated.paths).toBe('/api/v2/orders');

    await expect(deleteRoute('r-1', opts)).resolves.toBeUndefined();
  });

  it('handles consumers and plugin configs CRUD successfully', async () => {
    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';

      if (urlStr.endsWith('/api/admin/consumers') && method === 'GET') {
        return { ok: true, status: 200, json: async () => [sampleConsumer] } as Response;
      }
      if (urlStr.endsWith('/api/admin/consumers/c-1') && method === 'GET') {
        return { ok: true, status: 200, json: async () => sampleConsumer } as Response;
      }
      if (urlStr.endsWith('/api/admin/consumers') && method === 'POST') {
        return { ok: true, status: 201, json: async () => sampleConsumer } as Response;
      }
      if (urlStr.endsWith('/api/admin/consumers/c-1') && method === 'PUT') {
        return { ok: true, status: 200, json: async () => ({ ...sampleConsumer, name: 'retail-updated' }) } as Response;
      }
      if (urlStr.endsWith('/api/admin/consumers/c-1') && method === 'DELETE') {
        return { ok: true, status: 204 } as Response;
      }

      if (urlStr.endsWith('/api/admin/plugin-configs') && method === 'GET') {
        return { ok: true, status: 200, json: async () => [samplePlugin] } as Response;
      }
      if (urlStr.endsWith('/api/admin/plugin-configs/p-1') && method === 'GET') {
        return { ok: true, status: 200, json: async () => samplePlugin } as Response;
      }
      if (urlStr.endsWith('/api/admin/plugin-configs') && method === 'POST') {
        return { ok: true, status: 201, json: async () => samplePlugin } as Response;
      }
      if (urlStr.endsWith('/api/admin/plugin-configs/p-1') && method === 'PUT') {
        return { ok: true, status: 200, json: async () => ({ ...samplePlugin, ordering: 10 }) } as Response;
      }
      if (urlStr.endsWith('/api/admin/plugin-configs/p-1') && method === 'DELETE') {
        return { ok: true, status: 204 } as Response;
      }

      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const opts = { baseUrl: 'http://localhost:8081', fetchFn: mockFetch as any };

    const consumers = await listConsumers(opts);
    expect(consumers).toEqual([sampleConsumer]);

    const consumer = await getConsumer('c-1', opts);
    expect(consumer).toEqual(sampleConsumer);

    const createdC = await createConsumer({ name: 'retail-client' }, opts);
    expect(createdC).toEqual(sampleConsumer);

    const updatedC = await updateConsumer('c-1', { name: 'retail-updated' }, opts);
    expect(updatedC.name).toBe('retail-updated');

    await expect(deleteConsumer('c-1', opts)).resolves.toBeUndefined();

    const plugins = await listPluginConfigs(opts);
    expect(plugins).toEqual([samplePlugin]);

    const plugin = await getPluginConfig('p-1', opts);
    expect(plugin).toEqual(samplePlugin);

    const createdP = await createPluginConfig({ name: 'rate-limit' }, opts);
    expect(createdP).toEqual(samplePlugin);

    const updatedP = await updatePluginConfig('p-1', { ordering: 10 }, opts);
    expect(updatedP.ordering).toBe(10);

    await expect(deletePluginConfig('p-1', opts)).resolves.toBeUndefined();
  });

  it('throws AdminApiError when response is not ok', async () => {
    const mockFetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ message: 'Validation failed: name is required' }),
    } as Response));

    await expect(
      createService({ name: '' }, { baseUrl: 'http://localhost:8081', fetchFn: mockFetch as any })
    ).rejects.toThrow(AdminApiError);
  });
});
