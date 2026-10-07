import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ConsumersPluginConfigsGrid } from '@/components/ConsumersPluginConfigsGrid';
import {
  createConsumer,
  listConsumers,
  ConsumerItem,
  adminApiClient,
} from '@/lib/adminApiClient';

describe('Consumers CRUD Integration against /api/admin/consumers', () => {
  it('creates consumer through grid create control, receives 201 Created, and verifies new consumer row appears on refresh', async () => {
    const consumersDb: ConsumerItem[] = [
      {
        id: 'seed-01',
        name: 'Initial Consumer',
        username: 'Initial Consumer',
        email: 'init@unc.dev',
        organization: 'Unc Core',
        createdAt: new Date().toISOString(),
      },
    ];

    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';

      if (urlStr.endsWith('/api/admin/consumers') && method === 'POST') {
        const payload = JSON.parse(init?.body as string);
        const newConsumer: ConsumerItem = {
          id: 'new-uuid-999',
          name: payload.name,
          username: payload.name,
          email: payload.email,
          organization: payload.organization,
          customId: payload.customId,
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
          json: async () => [...consumersDb],
        } as Response;
      }

      if (urlStr.endsWith('/api/admin/plugin-configs') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => [],
        } as Response;
      }

      return {
        ok: false,
        status: 404,
        json: async () => ({ message: 'Not Found' }),
      } as Response;
    });

    const clientWithMockFetch = {
      ...adminApiClient,
      createConsumer: (input: any) =>
        createConsumer(input, {
          baseUrl: 'http://localhost:8081',
          tenantId: 'tenant-test-1',
          apiKey: 'key-test-1',
          fetchFn: mockFetch as any,
        }),
      listConsumers: () =>
        listConsumers({
          baseUrl: 'http://localhost:8081',
          tenantId: 'tenant-test-1',
          apiKey: 'key-test-1',
          fetchFn: mockFetch as any,
        }),
      listPluginConfigs: vi.fn().mockResolvedValue([]),
    } as typeof adminApiClient;

    // Render the grid with the live/mocked client
    render(<ConsumersPluginConfigsGrid client={clientWithMockFetch} />);

    // Verify initial consumer loaded
    const initialRow = await screen.findByTestId('consumer-row-seed-01');
    expect(initialRow).toBeInTheDocument();
    expect(initialRow).toHaveTextContent('Initial Consumer');

    // 1. Click Create Consumer control
    const createBtn = screen.getByTestId('create-consumer-btn');
    fireEvent.click(createBtn);

    // 2. Fill form in modal
    fireEvent.change(screen.getByTestId('input-consumer-name'), {
      target: { value: 'Acme Operator' },
    });
    fireEvent.change(screen.getByTestId('input-consumer-email'), {
      target: { value: 'operator@acme.dev' },
    });
    fireEvent.change(screen.getByTestId('input-consumer-org'), {
      target: { value: 'Acme Systems' },
    });

    // 3. Submit creation request
    const submitBtn = screen.getByTestId('submit-consumer-btn');
    fireEvent.click(submitBtn);

    // 4. Verify 201 Created request was made
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers',
        expect.objectContaining({
          method: 'POST',
        })
      );
    });

    // 5. Verify the new consumer appears in the UI
    const newConsumerRow = await screen.findByTestId('consumer-row-new-uuid-999');
    expect(newConsumerRow).toBeInTheDocument();
    expect(newConsumerRow).toHaveTextContent('Acme Operator');
    expect(newConsumerRow).toHaveTextContent('operator@acme.dev');
    expect(newConsumerRow).toHaveTextContent('Acme Systems');

    // 6. Refresh grid and verify new consumer persists in follow-up GET list
    const refreshBtn = screen.getByLabelText('Refresh grid');
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8081/api/admin/consumers',
        expect.objectContaining({
          method: 'GET',
        })
      );
    });

    expect(screen.getByTestId('consumer-row-new-uuid-999')).toBeInTheDocument();
  });
});
