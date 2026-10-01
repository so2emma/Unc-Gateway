export interface Consumer {
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

export interface CreateConsumerInput {
  name: string;
  email?: string;
  organization?: string;
  customId?: string;
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
  // In browser, relative URL works through Next.js rewrite
  if (typeof window !== 'undefined') {
    return '';
  }
  return 'http://localhost:8081';
}

function resolveTenantId(options?: AdminApiClientOptions, input?: CreateConsumerInput): string | undefined {
  if (input?.tenantId) return input.tenantId;
  if (options?.tenantId) return options.tenantId;
  if (typeof window !== 'undefined') {
    const stored = window.localStorage.getItem('unc_tenant_id');
    if (stored) return stored;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_TENANT_ID) {
    return process.env.NEXT_PUBLIC_TENANT_ID;
  }
  return undefined;
}

function resolveApiKey(options?: AdminApiClientOptions): string | undefined {
  if (options?.apiKey) return options.apiKey;
  if (typeof window !== 'undefined') {
    const stored = window.localStorage.getItem('unc_api_key');
    if (stored) return stored;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_ADMIN_API_KEY) {
    return process.env.NEXT_PUBLIC_ADMIN_API_KEY;
  }
  return undefined;
}

export async function createConsumer(
  input: CreateConsumerInput,
  options?: AdminApiClientOptions
): Promise<Consumer> {
  const baseUrl = resolveBaseUrl(options);
  const url = `${baseUrl}/api/admin/consumers`;
  const fetcher = options?.fetchFn || fetch;

  const tenantId = resolveTenantId(options, input);
  const apiKey = resolveApiKey(options);

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  if (apiKey) {
    headers['X-Api-Key'] = apiKey;
  }

  const payload: Record<string, any> = {
    name: input.name,
    username: input.name,
  };
  if (input.email) payload.email = input.email;
  if (input.organization) payload.organization = input.organization;
  if (input.customId) payload.customId = input.customId;
  if (tenantId) payload.tenantId = tenantId;

  const response = await fetcher(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMessage = `Failed to create consumer: HTTP ${response.status}`;
    let errorData: any = null;
    try {
      errorData = await response.json();
      if (errorData && typeof errorData.message === 'string') {
        errorMessage = errorData.message;
      }
    } catch {
      // Non-JSON response
    }
    throw new AdminApiError(errorMessage, response.status, errorData);
  }

  const created: Consumer = await response.json();
  return created;
}

export async function listConsumers(options?: AdminApiClientOptions): Promise<Consumer[]> {
  const baseUrl = resolveBaseUrl(options);
  const url = `${baseUrl}/api/admin/consumers`;
  const fetcher = options?.fetchFn || fetch;

  const tenantId = resolveTenantId(options);
  const apiKey = resolveApiKey(options);

  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };

  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  if (apiKey) {
    headers['X-Api-Key'] = apiKey;
  }

  const response = await fetcher(url, {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    let errorMessage = `Failed to list consumers: HTTP ${response.status}`;
    let errorData: any = null;
    try {
      errorData = await response.json();
      if (errorData && typeof errorData.message === 'string') {
        errorMessage = errorData.message;
      }
    } catch {
      // Non-JSON response
    }
    throw new AdminApiError(errorMessage, response.status, errorData);
  }

  return response.json();
}

export async function getConsumer(id: string, options?: AdminApiClientOptions): Promise<Consumer> {
  const baseUrl = resolveBaseUrl(options);
  const url = `${baseUrl}/api/admin/consumers/${id}`;
  const fetcher = options?.fetchFn || fetch;

  const tenantId = resolveTenantId(options);
  const apiKey = resolveApiKey(options);

  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };

  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  if (apiKey) {
    headers['X-Api-Key'] = apiKey;
  }

  const response = await fetcher(url, {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    let errorMessage = `Failed to get consumer ${id}: HTTP ${response.status}`;
    let errorData: any = null;
    try {
      errorData = await response.json();
      if (errorData && typeof errorData.message === 'string') {
        errorMessage = errorData.message;
      }
    } catch {
      // Non-JSON response
    }
    throw new AdminApiError(errorMessage, response.status, errorData);
  }

  return response.json();
}

export const adminApiClient = {
  createConsumer,
  listConsumers,
  getConsumer,
};

export default adminApiClient;
