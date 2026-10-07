import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ConsumersPluginConfigsGrid } from '../ConsumersPluginConfigsGrid';
import { ConsumerItem, PluginConfigItem, adminApiClient } from '@/lib/adminApiClient';

describe('ConsumersPluginConfigsGrid Component', () => {
  const seededConsumers: ConsumerItem[] = [
    {
      id: 'cons-1',
      username: 'ada-lovelace',
      name: 'Ada Lovelace',
      email: 'ada@unc.dev',
      organization: 'Acme Computing',
      customId: 'ext_ada_001',
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  const seededPlugins: PluginConfigItem[] = [
    {
      id: 'plug-1',
      name: 'key-auth',
      ordering: 1,
      enabled: true,
      config: { keyNames: ['apikey'] },
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  let mockClient: typeof adminApiClient;

  beforeEach(() => {
    mockClient = {
      listServices: vi.fn(),
      getService: vi.fn(),
      createService: vi.fn(),
      updateService: vi.fn(),
      deleteService: vi.fn(),
      listRoutes: vi.fn(),
      getRoute: vi.fn(),
      createRoute: vi.fn(),
      updateRoute: vi.fn(),
      deleteRoute: vi.fn(),
      listConsumers: vi.fn().mockResolvedValue(seededConsumers),
      getConsumer: vi.fn(),
      createConsumer: vi.fn().mockResolvedValue({
        id: 'cons-2',
        username: 'alan-turing',
        name: 'Alan Turing',
        email: 'alan@unc.dev',
        organization: 'Bletchley Park',
        customId: 'ext_alan_002',
      }),
      updateConsumer: vi.fn().mockResolvedValue({
        id: 'cons-1',
        username: 'ada-lovelace',
        name: 'Countess Ada Lovelace',
        email: 'ada@unc.dev',
      }),
      deleteConsumer: vi.fn().mockResolvedValue(undefined),
      listPluginConfigs: vi.fn().mockResolvedValue(seededPlugins),
      getPluginConfig: vi.fn(),
      createPluginConfig: vi.fn().mockResolvedValue({
        id: 'plug-2',
        name: 'rate-limit',
        ordering: 2,
        enabled: true,
      }),
      updatePluginConfig: vi.fn().mockResolvedValue({
        id: 'plug-1',
        name: 'key-auth',
        ordering: 10,
        enabled: true,
      }),
      deletePluginConfig: vi.fn().mockResolvedValue(undefined),
    };
  });

  it('renders consumers in dense table and supports creating a new consumer', async () => {
    render(
      <ConsumersPluginConfigsGrid
        initialConsumers={seededConsumers}
        initialPluginConfigs={seededPlugins}
        client={mockClient}
      />
    );

    // Verify consumer row rendered
    const consumerRow = screen.getByTestId('consumer-row-cons-1');
    expect(consumerRow).toBeInTheDocument();
    expect(consumerRow).toHaveTextContent('Ada Lovelace');
    expect(consumerRow).toHaveTextContent('ada@unc.dev');

    // Click Create Consumer
    const createBtn = screen.getByTestId('create-consumer-btn');
    fireEvent.click(createBtn);

    // Fill form
    fireEvent.change(screen.getByTestId('input-consumer-name'), {
      target: { value: 'Alan Turing' },
    });
    fireEvent.change(screen.getByTestId('input-consumer-email'), {
      target: { value: 'alan@unc.dev' },
    });
    fireEvent.change(screen.getByTestId('input-consumer-org'), {
      target: { value: 'Bletchley Park' },
    });

    // Submit
    fireEvent.click(screen.getByTestId('submit-consumer-btn'));

    await waitFor(() => {
      expect(mockClient.createConsumer).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.createConsumer).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Alan Turing',
        email: 'alan@unc.dev',
        organization: 'Bletchley Park',
      })
    );
  });

  it('verifies edit and delete consumer handlers', async () => {
    render(
      <ConsumersPluginConfigsGrid
        initialConsumers={seededConsumers}
        initialPluginConfigs={seededPlugins}
        client={mockClient}
      />
    );

    // Edit consumer
    const editBtn = screen.getByTestId('edit-consumer-cons-1');
    fireEvent.click(editBtn);

    fireEvent.change(screen.getByTestId('input-consumer-name'), {
      target: { value: 'Countess Ada Lovelace' },
    });

    fireEvent.click(screen.getByTestId('submit-consumer-btn'));

    await waitFor(() => {
      expect(mockClient.updateConsumer).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.updateConsumer).toHaveBeenCalledWith(
      'cons-1',
      expect.objectContaining({
        name: 'Countess Ada Lovelace',
      })
    );

    // Delete consumer
    const deleteBtn = screen.getByTestId('delete-consumer-cons-1');
    fireEvent.click(deleteBtn);

    fireEvent.click(screen.getByTestId('confirm-delete-btn'));

    await waitFor(() => {
      expect(mockClient.deleteConsumer).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.deleteConsumer).toHaveBeenCalledWith('cons-1');
  });

  it('switches to plugin configs tab and displays plugin rows with actions', async () => {
    render(
      <ConsumersPluginConfigsGrid
        initialConsumers={seededConsumers}
        initialPluginConfigs={seededPlugins}
        client={mockClient}
      />
    );

    // Switch tab
    fireEvent.click(screen.getByTestId('tab-plugin-configs'));

    const pluginRow = await screen.findByTestId('plugin-row-plug-1');
    expect(pluginRow).toBeInTheDocument();
    expect(pluginRow).toHaveTextContent('key-auth');
    expect(pluginRow).toHaveTextContent('Active');

    // Delete plugin
    const deleteBtn = screen.getByTestId('delete-plugin-plug-1');
    fireEvent.click(deleteBtn);

    fireEvent.click(screen.getByTestId('confirm-delete-btn'));

    await waitFor(() => {
      expect(mockClient.deletePluginConfig).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.deletePluginConfig).toHaveBeenCalledWith('plug-1');
  });
});
