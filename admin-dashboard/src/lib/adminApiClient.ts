export interface ServiceItem {
  id: string;
  tenantId?: string;
  name: string;
  url?: string;
  upstreamUrl?: string;
  connectTimeout?: number;
  readTimeout?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateServiceInput {
  name: string;
  url?: string;
  upstreamUrl?: string;
  connectTimeout?: number;
  readTimeout?: number;
  tenantId?: string;
}

export interface RouteItem {
  id: string;
  tenantId?: string;
  serviceId?: string;
  name: string;
  paths: string;
  path?: string;
  methods?: string | null;
  protocols?: string | null;
  stripPath?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateRouteInput {
  serviceId: string;
  name: string;
  paths?: string;
  path?: string;
  methods?: string;
  protocols?: string;
  stripPath?: boolean;
  tenantId?: string;
}

export interface ConsumerItem {
  id: string;
  tenantId?: string;
  username: string;
  name?: string;
  customId?: string | null;
  email?: string | null;
  organization?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export type Consumer = ConsumerItem;

export interface CreateConsumerInput {
  name: string;
  username?: string;
  email?: string;
  organization?: string;
  customId?: string;
  tenantId?: string;
}

export interface PluginConfigItem {
  id: string;
  tenantId?: string;
  serviceId?: string | null;
  routeId?: string | null;
  consumerId?: string | null;
  name: string;
  pluginName?: string;
  ordering?: number;
  order?: number;
  enabled?: boolean;
  config?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

export type PluginConfig = PluginConfigItem;

export interface CreatePluginConfigInput {
  name: string;
  pluginName?: string;
  serviceId?: string | null;
  routeId?: string | null;
  consumerId?: string | null;
  ordering?: number;
  order?: number;
  enabled?: boolean;
  config?: Record<string, any>;
  tenantId?: string;
}

export interface AdminApiClientOptions {
  baseUrl?: string;
  tenantId?: string;
  apiKey?: string;
  fetchFn?: typeof fetch;
}

export class AdminApiError extends Error {
  status: number;
  data?: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'AdminApiError';
    this.status = status;
    this.data = data;
  }
}

function resolveBaseUrl(options?: AdminApiClientOptions): string {
  if (options?.baseUrl !== undefined) {
    return options.baseUrl.replace(/\/+$/, '');
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_ADMIN_API_URL) {
    return process.env.NEXT_PUBLIC_ADMIN_API_URL.replace(/\/+$/, '');
  }
  // In browser, relative URL works through Next.js API proxy route
  if (typeof window !== 'undefined') {
    return '';
  }
  return 'http://localhost:8081';
}

function resolveTenantId(options?: AdminApiClientOptions): string | undefined {
  if (options?.tenantId) return options.tenantId;
  if (typeof window !== 'undefined') {
    const sessionVal = window.sessionStorage?.getItem('unc_tenant_id');
    if (sessionVal) return sessionVal;
    const localVal = window.localStorage?.getItem('unc_tenant_id');
    if (localVal) return localVal;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_TENANT_ID) {
    return process.env.NEXT_PUBLIC_TENANT_ID;
  }
  return undefined;
}

function resolveApiKey(options?: AdminApiClientOptions): string | undefined {
  if (options?.apiKey) return options.apiKey;
  if (typeof window !== 'undefined') {
    const sessionVal = window.sessionStorage?.getItem('unc_api_key');
    if (sessionVal) return sessionVal;
    const localVal = window.localStorage?.getItem('unc_api_key');
    if (localVal) return localVal;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_ADMIN_API_KEY) {
    return process.env.NEXT_PUBLIC_ADMIN_API_KEY;
  }
  return undefined;
}

function buildHeaders(options?: AdminApiClientOptions, contentType = 'application/json'): Record<string, string> {
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };
  if (contentType) {
    headers['Content-Type'] = contentType;
  }
  const tenantId = resolveTenantId(options);
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  const apiKey = resolveApiKey(options);
  if (apiKey) {
    headers['X-Api-Key'] = apiKey;
  }
  return headers;
}

async function handleResponse<T>(res: Response, fallbackMessage: string): Promise<T> {
  if (!res.ok) {
    let errorMessage = `${fallbackMessage}: HTTP ${res.status}`;
    let errorData: any = null;
    try {
      errorData = await res.json();
      if (errorData && typeof errorData.message === 'string' && errorData.message.trim()) {
        errorMessage = errorData.message;
      }
    } catch {
      // Non-JSON response
    }
    throw new AdminApiError(errorMessage, res.status, errorData);
  }
  if (res.status === 204) {
    return undefined as unknown as T;
  }
  return res.json();
}

// ==================== Services ====================

export async function listServices(options?: AdminApiClientOptions): Promise<ServiceItem[]> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/services`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<ServiceItem[]>(res, 'Failed to list services');
}

export async function getService(id: string, options?: AdminApiClientOptions): Promise<ServiceItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/services/${encodeURIComponent(id)}`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<ServiceItem>(res, `Failed to get service ${id}`);
}

export async function createService(input: CreateServiceInput, options?: AdminApiClientOptions): Promise<ServiceItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = {
    name: input.name,
    url: input.url || input.upstreamUrl,
    upstreamUrl: input.upstreamUrl || input.url,
  };
  if (input.connectTimeout !== undefined) payload.connectTimeout = input.connectTimeout;
  if (input.readTimeout !== undefined) payload.readTimeout = input.readTimeout;
  if (input.tenantId) payload.tenantId = input.tenantId;

  const res = await fetcher(`${baseUrl}/api/admin/services`, {
    method: 'POST',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<ServiceItem>(res, 'Failed to create service');
}

export async function updateService(
  id: string,
  input: Partial<CreateServiceInput>,
  options?: AdminApiClientOptions
): Promise<ServiceItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = { ...input };
  if (input.upstreamUrl && !input.url) payload.url = input.upstreamUrl;
  if (input.url && !input.upstreamUrl) payload.upstreamUrl = input.url;

  const res = await fetcher(`${baseUrl}/api/admin/services/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<ServiceItem>(res, `Failed to update service ${id}`);
}

export async function deleteService(id: string, options?: AdminApiClientOptions): Promise<void> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/services/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<void>(res, `Failed to delete service ${id}`);
}

// ==================== Routes ====================

export async function listRoutes(options?: AdminApiClientOptions): Promise<RouteItem[]> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/routes`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<RouteItem[]>(res, 'Failed to list routes');
}

export async function getRoute(id: string, options?: AdminApiClientOptions): Promise<RouteItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/routes/${encodeURIComponent(id)}`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<RouteItem>(res, `Failed to get route ${id}`);
}

export async function createRoute(input: CreateRouteInput, options?: AdminApiClientOptions): Promise<RouteItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = {
    serviceId: input.serviceId,
    name: input.name,
    paths: input.paths || input.path,
    path: input.path || input.paths,
  };
  if (input.methods !== undefined) payload.methods = input.methods;
  if (input.protocols !== undefined) payload.protocols = input.protocols;
  if (input.stripPath !== undefined) payload.stripPath = input.stripPath;
  if (input.tenantId) payload.tenantId = input.tenantId;

  const res = await fetcher(`${baseUrl}/api/admin/routes`, {
    method: 'POST',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<RouteItem>(res, 'Failed to create route');
}

export async function updateRoute(
  id: string,
  input: Partial<CreateRouteInput>,
  options?: AdminApiClientOptions
): Promise<RouteItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = { ...input };
  if (input.path && !input.paths) payload.paths = input.path;
  if (input.paths && !input.path) payload.path = input.paths;

  const res = await fetcher(`${baseUrl}/api/admin/routes/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<RouteItem>(res, `Failed to update route ${id}`);
}

export async function deleteRoute(id: string, options?: AdminApiClientOptions): Promise<void> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/routes/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<void>(res, `Failed to delete route ${id}`);
}

// ==================== Consumers ====================

export async function listConsumers(options?: AdminApiClientOptions): Promise<ConsumerItem[]> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/consumers`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<ConsumerItem[]>(res, 'Failed to list consumers');
}

export async function getConsumer(id: string, options?: AdminApiClientOptions): Promise<ConsumerItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/consumers/${encodeURIComponent(id)}`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<ConsumerItem>(res, `Failed to get consumer ${id}`);
}

export async function createConsumer(
  input: CreateConsumerInput,
  options?: AdminApiClientOptions
): Promise<ConsumerItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = {
    name: input.name || input.username,
    username: input.username || input.name,
  };
  if (input.email) payload.email = input.email;
  if (input.organization) payload.organization = input.organization;
  if (input.customId) payload.customId = input.customId;
  if (input.tenantId) payload.tenantId = input.tenantId;

  const res = await fetcher(`${baseUrl}/api/admin/consumers`, {
    method: 'POST',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<ConsumerItem>(res, 'Failed to create consumer');
}

export async function updateConsumer(
  id: string,
  input: Partial<CreateConsumerInput>,
  options?: AdminApiClientOptions
): Promise<ConsumerItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = { ...input };
  if (input.name && !input.username) payload.username = input.name;
  if (input.username && !input.name) payload.name = input.username;

  const res = await fetcher(`${baseUrl}/api/admin/consumers/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<ConsumerItem>(res, `Failed to update consumer ${id}`);
}

export async function deleteConsumer(id: string, options?: AdminApiClientOptions): Promise<void> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/consumers/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<void>(res, `Failed to delete consumer ${id}`);
}

// ==================== Plugin Configs ====================

export async function listPluginConfigs(options?: AdminApiClientOptions): Promise<PluginConfigItem[]> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/plugin-configs`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<PluginConfigItem[]>(res, 'Failed to list plugin configs');
}

export async function getPluginConfig(id: string, options?: AdminApiClientOptions): Promise<PluginConfigItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/plugin-configs/${encodeURIComponent(id)}`, {
    method: 'GET',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<PluginConfigItem>(res, `Failed to get plugin config ${id}`);
}

export async function createPluginConfig(
  input: CreatePluginConfigInput,
  options?: AdminApiClientOptions
): Promise<PluginConfigItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = {
    name: input.name || input.pluginName,
    pluginName: input.pluginName || input.name,
    serviceId: input.serviceId || null,
    routeId: input.routeId || null,
    consumerId: input.consumerId || null,
    ordering: input.ordering !== undefined ? input.ordering : (input.order !== undefined ? input.order : 0),
    order: input.order !== undefined ? input.order : (input.ordering !== undefined ? input.ordering : 0),
    enabled: input.enabled !== undefined ? input.enabled : true,
    config: input.config || {},
  };
  if (input.tenantId) payload.tenantId = input.tenantId;

  const res = await fetcher(`${baseUrl}/api/admin/plugin-configs`, {
    method: 'POST',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<PluginConfigItem>(res, 'Failed to create plugin config');
}

export async function updatePluginConfig(
  id: string,
  input: Partial<CreatePluginConfigInput>,
  options?: AdminApiClientOptions
): Promise<PluginConfigItem> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const payload: Record<string, any> = { ...input };
  if (input.name && !input.pluginName) payload.pluginName = input.name;
  if (input.pluginName && !input.name) payload.name = input.pluginName;
  if (input.ordering !== undefined && input.order === undefined) payload.order = input.ordering;
  if (input.order !== undefined && input.ordering === undefined) payload.ordering = input.order;

  const res = await fetcher(`${baseUrl}/api/admin/plugin-configs/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: buildHeaders(options),
    body: JSON.stringify(payload),
  });
  return handleResponse<PluginConfigItem>(res, `Failed to update plugin config ${id}`);
}

export async function deletePluginConfig(id: string, options?: AdminApiClientOptions): Promise<void> {
  const baseUrl = resolveBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const res = await fetcher(`${baseUrl}/api/admin/plugin-configs/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: buildHeaders(options, ''),
  });
  return handleResponse<void>(res, `Failed to delete plugin config ${id}`);
}

export const adminApiClient = {
  // Services
  listServices,
  getService,
  createService,
  updateService,
  deleteService,
  // Routes
  listRoutes,
  getRoute,
  createRoute,
  updateRoute,
  deleteRoute,
  // Consumers
  listConsumers,
  getConsumer,
  createConsumer,
  updateConsumer,
  deleteConsumer,
  // Plugin Configs
  listPluginConfigs,
  getPluginConfig,
  createPluginConfig,
  updatePluginConfig,
  deletePluginConfig,
};

export default adminApiClient;
