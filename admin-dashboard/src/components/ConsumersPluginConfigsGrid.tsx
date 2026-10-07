'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  adminApiClient as defaultClient,
  ConsumerItem,
  PluginConfigItem,
  CreateConsumerInput,
  CreatePluginConfigInput,
} from '@/lib/adminApiClient';
import {
  PlusIcon,
  EditIcon,
  TrashIcon,
  RefreshIcon,
  SearchIcon,
  CloseIcon,
  CheckIcon,
  UsersIcon,
  CpuIcon,
  AlertTriangleIcon,
} from './Icons';

export interface ConsumersPluginConfigsGridProps {
  initialConsumers?: ConsumerItem[];
  initialPluginConfigs?: PluginConfigItem[];
  client?: typeof defaultClient;
}

export const ConsumersPluginConfigsGrid: React.FC<ConsumersPluginConfigsGridProps> = ({
  initialConsumers,
  initialPluginConfigs,
  client = defaultClient,
}) => {
  const [activeTab, setActiveTab] = useState<'consumers' | 'plugin-configs'>('consumers');
  const [consumers, setConsumers] = useState<ConsumerItem[]>(initialConsumers || []);
  const [pluginConfigs, setPluginConfigs] = useState<PluginConfigItem[]>(initialPluginConfigs || []);
  const [loading, setLoading] = useState<boolean>(!initialConsumers && !initialPluginConfigs);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Consumer modal state
  const [isConsumerModalOpen, setIsConsumerModalOpen] = useState<boolean>(false);
  const [editingConsumer, setEditingConsumer] = useState<ConsumerItem | null>(null);
  const [consumerFormData, setConsumerFormData] = useState<CreateConsumerInput>({
    name: '',
    email: '',
    organization: '',
    customId: '',
  });

  // Plugin config modal state
  const [isPluginModalOpen, setIsPluginModalOpen] = useState<boolean>(false);
  const [editingPlugin, setEditingPlugin] = useState<PluginConfigItem | null>(null);
  const [pluginFormData, setPluginFormData] = useState<{
    name: string;
    serviceId: string;
    routeId: string;
    consumerId: string;
    ordering: number;
    enabled: boolean;
    configJson: string;
  }>({
    name: '',
    serviceId: '',
    routeId: '',
    consumerId: '',
    ordering: 0,
    enabled: true,
    configJson: '{\n  \n}',
  });

  const [deleteConfirm, setDeleteConfirm] = useState<{
    type: 'consumer' | 'plugin-config';
    id: string;
    name: string;
  } | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMessage(null);
      const [fetchedConsumers, fetchedPlugins] = await Promise.all([
        client.listConsumers(),
        client.listPluginConfigs(),
      ]);
      setConsumers(fetchedConsumers || []);
      setPluginConfigs(fetchedPlugins || []);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to load consumers and plugin configs');
    } finally {
      setLoading(false);
    }
  }, [client]);

  useEffect(() => {
    if (!initialConsumers && !initialPluginConfigs) {
      loadData();
    }
  }, [initialConsumers, initialPluginConfigs, loadData]);

  const showNotification = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => setSuccessMessage(null), 3500);
  };

  // ==================== Consumer CRUD Handlers ====================

  const openCreateConsumer = () => {
    setEditingConsumer(null);
    setConsumerFormData({
      name: '',
      email: '',
      organization: '',
      customId: '',
    });
    setIsConsumerModalOpen(true);
  };

  const openEditConsumer = (c: ConsumerItem) => {
    setEditingConsumer(c);
    setConsumerFormData({
      name: c.name || c.username,
      email: c.email || '',
      organization: c.organization || '',
      customId: c.customId || '',
    });
    setIsConsumerModalOpen(true);
  };

  const handleSaveConsumer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!consumerFormData.name.trim()) return;

    try {
      if (editingConsumer) {
        const updated = await client.updateConsumer(editingConsumer.id, consumerFormData);
        setConsumers((prev) =>
          prev.map((c) => (c.id === editingConsumer.id ? { ...c, ...updated } : c))
        );
        showNotification(`Consumer "${consumerFormData.name}" updated`);
      } else {
        const created = await client.createConsumer(consumerFormData);
        setConsumers((prev) => [...prev, created]);
        showNotification(`Consumer "${consumerFormData.name}" created`);
      }
      setIsConsumerModalOpen(false);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to save consumer');
    }
  };

  const handleDeleteConsumer = async (id: string) => {
    try {
      await client.deleteConsumer(id);
      setConsumers((prev) => prev.filter((c) => c.id !== id));
      setDeleteConfirm(null);
      showNotification('Consumer deleted successfully');
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to delete consumer');
    }
  };

  // ==================== Plugin Config CRUD Handlers ====================

  const openCreatePlugin = () => {
    setEditingPlugin(null);
    setPluginFormData({
      name: 'key-auth',
      serviceId: '',
      routeId: '',
      consumerId: '',
      ordering: 0,
      enabled: true,
      configJson: '{\n  \n}',
    });
    setIsPluginModalOpen(true);
  };

  const openEditPlugin = (p: PluginConfigItem) => {
    setEditingPlugin(p);
    setPluginFormData({
      name: p.name || p.pluginName || '',
      serviceId: p.serviceId || '',
      routeId: p.routeId || '',
      consumerId: p.consumerId || '',
      ordering: p.ordering ?? p.order ?? 0,
      enabled: p.enabled ?? true,
      configJson: JSON.stringify(p.config || {}, null, 2),
    });
    setIsPluginModalOpen(true);
  };

  const handleSavePlugin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pluginFormData.name.trim()) return;

    let parsedConfig: Record<string, any> = {};
    if (pluginFormData.configJson.trim()) {
      try {
        parsedConfig = JSON.parse(pluginFormData.configJson);
      } catch (err) {
        setErrorMessage('Invalid JSON configuration syntax.');
        return;
      }
    }

    const payload: CreatePluginConfigInput = {
      name: pluginFormData.name,
      pluginName: pluginFormData.name,
      serviceId: pluginFormData.serviceId.trim() || null,
      routeId: pluginFormData.routeId.trim() || null,
      consumerId: pluginFormData.consumerId.trim() || null,
      ordering: pluginFormData.ordering,
      enabled: pluginFormData.enabled,
      config: parsedConfig,
    };

    try {
      if (editingPlugin) {
        const updated = await client.updatePluginConfig(editingPlugin.id, payload);
        setPluginConfigs((prev) =>
          prev.map((p) => (p.id === editingPlugin.id ? { ...p, ...updated } : p))
        );
        showNotification(`Plugin config "${payload.name}" updated`);
      } else {
        const created = await client.createPluginConfig(payload);
        setPluginConfigs((prev) => [...prev, created]);
        showNotification(`Plugin config "${payload.name}" created`);
      }
      setIsPluginModalOpen(false);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to save plugin config');
    }
  };

  const handleDeletePlugin = async (id: string) => {
    try {
      await client.deletePluginConfig(id);
      setPluginConfigs((prev) => prev.filter((p) => p.id !== id));
      setDeleteConfirm(null);
      showNotification('Plugin config deleted successfully');
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to delete plugin config');
    }
  };

  // Filtered lists
  const filteredConsumers = consumers.filter(
    (c) =>
      (c.name && c.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (c.username && c.username.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (c.email && c.email.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (c.organization && c.organization.toLowerCase().includes(searchQuery.toLowerCase())) ||
      c.id.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredPluginConfigs = pluginConfigs.filter(
    (p) =>
      (p.name && p.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (p.pluginName && p.pluginName.toLowerCase().includes(searchQuery.toLowerCase())) ||
      p.id.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div data-testid="consumers-plugin-configs-grid" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header and Actions Bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#F5F6F8', marginBottom: '4px' }}>
            Consumers & Plugin Configs
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            Dense operator management for API consumers, credentials, and middleware plugin pipelines
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button
            onClick={loadData}
            className="btn btn-secondary btn-sm"
            title="Refresh from Admin API"
            aria-label="Refresh grid"
          >
            <RefreshIcon size={14} />
            <span>Refresh</span>
          </button>

          {activeTab === 'consumers' ? (
            <button
              data-testid="create-consumer-btn"
              onClick={openCreateConsumer}
              className="btn btn-primary"
            >
              <PlusIcon size={15} />
              <span>Create Consumer</span>
            </button>
          ) : (
            <button
              data-testid="create-plugin-btn"
              onClick={openCreatePlugin}
              className="btn btn-primary"
            >
              <PlusIcon size={15} />
              <span>Create Plugin Config</span>
            </button>
          )}
        </div>
      </div>

      {/* Notifications Banner */}
      {errorMessage && (
        <div
          role="alert"
          style={{
            padding: '12px 16px',
            backgroundColor: 'var(--danger-tint)',
            border: '1px solid rgba(229, 72, 77, 0.3)',
            borderRadius: 'var(--radius-control)',
            color: 'var(--danger)',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangleIcon size={16} />
            <span>{errorMessage}</span>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="btn-icon btn-icon-danger"
            aria-label="Dismiss error"
          >
            <CloseIcon size={14} />
          </button>
        </div>
      )}

      {successMessage && (
        <div
          role="status"
          style={{
            padding: '12px 16px',
            backgroundColor: 'var(--success-tint)',
            border: '1px solid rgba(28, 174, 104, 0.3)',
            borderRadius: 'var(--radius-control)',
            color: 'var(--success)',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <CheckIcon size={16} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Segmented Controls & Search Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '16px',
          flexWrap: 'wrap',
        }}
      >
        {/* Tab Switcher */}
        <div
          style={{
            display: 'inline-flex',
            backgroundColor: 'var(--surface-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: 'var(--radius-control)',
            padding: '3px',
            gap: '2px',
          }}
        >
          <button
            data-testid="tab-consumers"
            onClick={() => setActiveTab('consumers')}
            style={{
              padding: '6px 14px',
              fontSize: '13px',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.15s ease',
              backgroundColor: activeTab === 'consumers' ? 'var(--surface-hover)' : 'transparent',
              color: activeTab === 'consumers' ? '#FFFFFF' : 'var(--text-muted)',
              borderBottom: activeTab === 'consumers' ? '2px solid var(--accent)' : '2px solid transparent',
            }}
          >
            <UsersIcon size={14} style={{ color: activeTab === 'consumers' ? 'var(--accent)' : 'inherit' }} />
            <span>Consumers</span>
            <span
              className="badge badge-muted mono"
              style={{ padding: '1px 6px', fontSize: '10.5px' }}
            >
              {consumers.length}
            </span>
          </button>

          <button
            data-testid="tab-plugin-configs"
            onClick={() => setActiveTab('plugin-configs')}
            style={{
              padding: '6px 14px',
              fontSize: '13px',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.15s ease',
              backgroundColor: activeTab === 'plugin-configs' ? 'var(--surface-hover)' : 'transparent',
              color: activeTab === 'plugin-configs' ? '#FFFFFF' : 'var(--text-muted)',
              borderBottom: activeTab === 'plugin-configs' ? '2px solid var(--accent)' : '2px solid transparent',
            }}
          >
            <CpuIcon size={14} style={{ color: activeTab === 'plugin-configs' ? 'var(--accent)' : 'inherit' }} />
            <span>Plugin Configs</span>
            <span
              className="badge badge-muted mono"
              style={{ padding: '1px 6px', fontSize: '10.5px' }}
            >
              {pluginConfigs.length}
            </span>
          </button>
        </div>

        {/* Filter input */}
        <div style={{ position: 'relative', width: '280px' }}>
          <SearchIcon
            size={14}
            style={{
              position: 'absolute',
              left: '10px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)',
            }}
          />
          <input
            type="text"
            className="input"
            placeholder={activeTab === 'consumers' ? 'Filter consumers...' : 'Filter plugins...'}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ paddingLeft: '32px', fontSize: '12.5px' }}
          />
        </div>
      </div>

      {/* Main Dense Table Grid */}
      <div className="dense-table-container">
        {activeTab === 'consumers' ? (
          <table className="dense-table" data-testid="consumers-table">
            <thead>
              <tr>
                <th style={{ width: '130px' }}>Consumer ID</th>
                <th style={{ width: '180px' }}>Name / Username</th>
                <th style={{ width: '220px' }}>Email</th>
                <th>Organization</th>
                <th style={{ width: '120px' }}>Custom ID</th>
                <th style={{ width: '130px' }}>Created</th>
                <th style={{ width: '90px', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredConsumers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty-cell">
                    <div className="table-empty-content">
                      {loading ? (
                        <>
                          <div className="table-empty-title">Fetching Consumers</div>
                          <div className="table-empty-desc">Loading consumer records from Admin API...</div>
                        </>
                      ) : searchQuery.trim() ? (
                        <>
                          <div className="table-empty-title">No matching consumers</div>
                          <div className="table-empty-desc">
                            No consumers matched &ldquo;{searchQuery}&rdquo;. Check your query or clear the filter.
                          </div>
                          <button
                            type="button"
                            onClick={() => setSearchQuery('')}
                            className="btn btn-secondary btn-sm"
                          >
                            Clear Filter
                          </button>
                        </>
                      ) : (
                        <>
                          <div className="table-empty-title">No consumers registered</div>
                          <div className="table-empty-desc">
                            Create an API consumer to issue API key credentials and bind policies.
                          </div>
                          <button
                            type="button"
                            onClick={openCreateConsumer}
                            className="btn btn-primary btn-sm"
                          >
                            <PlusIcon size={14} />
                            <span>Create Consumer</span>
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredConsumers.map((consumer) => (
                  <tr key={consumer.id} data-testid={`consumer-row-${consumer.id}`}>
                    <td className="mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }} title={consumer.id}>
                      {consumer.id.slice(0, 8)}...
                    </td>
                    <td style={{ fontWeight: 600, color: '#F5F6F8' }}>
                      {consumer.name || consumer.username}
                    </td>
                    <td style={{ color: 'var(--text-muted)' }}>
                      {consumer.email || '—'}
                    </td>
                    <td style={{ color: '#F5F6F8' }}>
                      {consumer.organization || '—'}
                    </td>
                    <td className="mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                      {consumer.customId || '—'}
                    </td>
                    <td style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {consumer.createdAt ? new Date(consumer.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <button
                          data-testid={`edit-consumer-${consumer.id}`}
                          onClick={() => openEditConsumer(consumer)}
                          className="btn-icon"
                          title="Edit Consumer"
                          aria-label={`Edit consumer ${consumer.name || consumer.username}`}
                        >
                          <EditIcon size={14} />
                        </button>
                        <button
                          data-testid={`delete-consumer-${consumer.id}`}
                          onClick={() =>
                            setDeleteConfirm({
                              type: 'consumer',
                              id: consumer.id,
                              name: consumer.name || consumer.username,
                            })
                          }
                          className="btn-icon btn-icon-danger"
                          title="Delete Consumer"
                          aria-label={`Delete consumer ${consumer.name || consumer.username}`}
                        >
                          <TrashIcon size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        ) : (
          <table className="dense-table" data-testid="plugins-table">
            <thead>
              <tr>
                <th style={{ width: '130px' }}>Plugin ID</th>
                <th style={{ width: '170px' }}>Plugin Name</th>
                <th style={{ width: '110px' }}>Status</th>
                <th style={{ width: '90px' }}>Ordering</th>
                <th>Target Scope</th>
                <th style={{ width: '130px' }}>Created</th>
                <th style={{ width: '90px', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredPluginConfigs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty-cell">
                    <div className="table-empty-content">
                      {loading ? (
                        <>
                          <div className="table-empty-title">Fetching Plugin Configs</div>
                          <div className="table-empty-desc">Loading plugin configurations from Admin API...</div>
                        </>
                      ) : searchQuery.trim() ? (
                        <>
                          <div className="table-empty-title">No matching plugin configs</div>
                          <div className="table-empty-desc">
                            No plugin configs matched &ldquo;{searchQuery}&rdquo;. Check your query or clear the filter.
                          </div>
                          <button
                            type="button"
                            onClick={() => setSearchQuery('')}
                            className="btn btn-secondary btn-sm"
                          >
                            Clear Filter
                          </button>
                        </>
                      ) : (
                        <>
                          <div className="table-empty-title">No plugin configurations active</div>
                          <div className="table-empty-desc">
                            Attach middleware plugin configurations such as key-auth or rate-limit to enforce gateway policies.
                          </div>
                          <button
                            type="button"
                            onClick={openCreatePlugin}
                            className="btn btn-primary btn-sm"
                          >
                            <PlusIcon size={14} />
                            <span>Attach Plugin Config</span>
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredPluginConfigs.map((plugin) => (
                  <tr key={plugin.id} data-testid={`plugin-row-${plugin.id}`}>
                    <td className="mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }} title={plugin.id}>
                      {plugin.id.slice(0, 8)}...
                    </td>
                    <td style={{ fontWeight: 600, color: 'var(--accent)' }}>
                      {plugin.name || plugin.pluginName}
                    </td>
                    <td>
                      <span
                        className={`badge ${plugin.enabled !== false ? 'badge-success' : 'badge-muted'}`}
                        style={{ fontSize: '10px' }}
                      >
                        {plugin.enabled !== false ? 'Active' : 'Disabled'}
                      </span>
                    </td>
                    <td className="mono" style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      #{plugin.ordering ?? plugin.order ?? 0}
                    </td>
                    <td style={{ fontSize: '11.5px' }}>
                      {plugin.routeId ? (
                        <span className="mono" style={{ color: 'var(--text-muted)' }}>
                          Route: {plugin.routeId.slice(0, 8)}...
                        </span>
                      ) : plugin.serviceId ? (
                        <span className="mono" style={{ color: 'var(--text-muted)' }}>
                          Service: {plugin.serviceId.slice(0, 8)}...
                        </span>
                      ) : plugin.consumerId ? (
                        <span className="mono" style={{ color: 'var(--text-muted)' }}>
                          Consumer: {plugin.consumerId.slice(0, 8)}...
                        </span>
                      ) : (
                        <span className="badge badge-accent" style={{ fontSize: '10px' }}>
                          Global
                        </span>
                      )}
                    </td>
                    <td style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {plugin.createdAt ? new Date(plugin.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <button
                          data-testid={`edit-plugin-${plugin.id}`}
                          onClick={() => openEditPlugin(plugin)}
                          className="btn-icon"
                          title="Edit Plugin Config"
                          aria-label={`Edit plugin config ${plugin.name || plugin.pluginName}`}
                        >
                          <EditIcon size={14} />
                        </button>
                        <button
                          data-testid={`delete-plugin-${plugin.id}`}
                          onClick={() =>
                            setDeleteConfirm({
                              type: 'plugin-config',
                              id: plugin.id,
                              name: plugin.name || plugin.pluginName || plugin.id,
                            })
                          }
                          className="btn-icon btn-icon-danger"
                          title="Delete Plugin Config"
                          aria-label={`Delete plugin config ${plugin.name || plugin.pluginName}`}
                        >
                          <TrashIcon size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* ==================== Consumer Create/Edit Modal ==================== */}
      {isConsumerModalOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#F5F6F8' }}>
                {editingConsumer ? `Edit Consumer: ${editingConsumer.name || editingConsumer.username}` : 'Create API Consumer'}
              </h2>
              <button
                onClick={() => setIsConsumerModalOpen(false)}
                className="btn-icon"
                aria-label="Close dialog"
              >
                <CloseIcon size={16} />
              </button>
            </div>
            <form onSubmit={handleSaveConsumer}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label className="label" htmlFor="consumer-name">Consumer Name / Username</label>
                  <input
                    id="consumer-name"
                    data-testid="input-consumer-name"
                    type="text"
                    className="input"
                    placeholder="e.g. billing-service or team-alpha"
                    value={consumerFormData.name}
                    onChange={(e) => setConsumerFormData({ ...consumerFormData, name: e.target.value })}
                    required
                  />
                </div>

                <div>
                  <label className="label" htmlFor="consumer-email">Contact Email</label>
                  <input
                    id="consumer-email"
                    data-testid="input-consumer-email"
                    type="email"
                    className="input"
                    placeholder="e.g. operator@domain.com"
                    value={consumerFormData.email || ''}
                    onChange={(e) => setConsumerFormData({ ...consumerFormData, email: e.target.value })}
                  />
                </div>

                <div>
                  <label className="label" htmlFor="consumer-org">Organization</label>
                  <input
                    id="consumer-org"
                    data-testid="input-consumer-org"
                    type="text"
                    className="input"
                    placeholder="e.g. Enterprise Operations"
                    value={consumerFormData.organization || ''}
                    onChange={(e) => setConsumerFormData({ ...consumerFormData, organization: e.target.value })}
                  />
                </div>

                <div>
                  <label className="label" htmlFor="consumer-custom-id">Custom External ID</label>
                  <input
                    id="consumer-custom-id"
                    data-testid="input-consumer-custom-id"
                    type="text"
                    className="input mono"
                    placeholder="e.g. ext-client-id"
                    value={consumerFormData.customId || ''}
                    onChange={(e) => setConsumerFormData({ ...consumerFormData, customId: e.target.value })}
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  onClick={() => setIsConsumerModalOpen(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  data-testid="submit-consumer-btn"
                  type="submit"
                  className="btn btn-primary"
                >
                  {editingConsumer ? 'Save Changes' : 'Create Consumer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== Plugin Config Create/Edit Modal ==================== */}
      {isPluginModalOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#F5F6F8' }}>
                {editingPlugin ? `Edit Plugin: ${editingPlugin.name || editingPlugin.pluginName}` : 'Attach Plugin Config'}
              </h2>
              <button
                onClick={() => setIsPluginModalOpen(false)}
                className="btn-icon"
                aria-label="Close dialog"
              >
                <CloseIcon size={16} />
              </button>
            </div>
            <form onSubmit={handleSavePlugin}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label className="label" htmlFor="plugin-name">Plugin Type / Name</label>
                  <select
                    id="plugin-name"
                    data-testid="select-plugin-name"
                    className="select"
                    value={pluginFormData.name}
                    onChange={(e) => setPluginFormData({ ...pluginFormData, name: e.target.value })}
                    required
                  >
                    <option value="key-auth">key-auth (API Key Authentication)</option>
                    <option value="rate-limit">rate-limit (Rate Limiting)</option>
                    <option value="jwt-auth">jwt-auth (JWT Validation)</option>
                    <option value="request-transformer">request-transformer (Header / Body Transform)</option>
                    <option value="circuit-breaker">circuit-breaker (Fault Tolerance)</option>
                    <option value="logging">logging (Structured Audit Logs)</option>
                  </select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label className="label" htmlFor="plugin-ordering">Execution Order</label>
                    <input
                      id="plugin-ordering"
                      data-testid="input-plugin-ordering"
                      type="number"
                      className="input mono"
                      value={pluginFormData.ordering}
                      onChange={(e) =>
                        setPluginFormData({ ...pluginFormData, ordering: parseInt(e.target.value) || 0 })
                      }
                    />
                  </div>
                  <div>
                    <label className="label" htmlFor="plugin-scope-target">Route ID (Optional)</label>
                    <input
                      id="plugin-scope-target"
                      data-testid="input-plugin-route-id"
                      type="text"
                      className="input mono"
                      placeholder="Blank for Global"
                      value={pluginFormData.routeId}
                      onChange={(e) => setPluginFormData({ ...pluginFormData, routeId: e.target.value })}
                    />
                  </div>
                </div>

                <div>
                  <label className="label" htmlFor="plugin-config-json">JSON Configuration</label>
                  <textarea
                    id="plugin-config-json"
                    data-testid="textarea-plugin-config"
                    className="textarea mono"
                    rows={5}
                    value={pluginFormData.configJson}
                    onChange={(e) => setPluginFormData({ ...pluginFormData, configJson: e.target.value })}
                    placeholder={`{\n  "keyNames": ["apikey", "x-api-key"]\n}`}
                  />
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <input
                    id="plugin-enabled"
                    data-testid="checkbox-plugin-enabled"
                    type="checkbox"
                    checked={pluginFormData.enabled}
                    onChange={(e) => setPluginFormData({ ...pluginFormData, enabled: e.target.checked })}
                    style={{ accentColor: 'var(--accent)', width: '16px', height: '16px' }}
                  />
                  <label htmlFor="plugin-enabled" style={{ fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                    Plugin enabled for matching requests
                  </label>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  onClick={() => setIsPluginModalOpen(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  data-testid="submit-plugin-btn"
                  type="submit"
                  className="btn btn-primary"
                >
                  {editingPlugin ? 'Save Changes' : 'Attach Plugin'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== Delete Confirmation Modal ==================== */}
      {deleteConfirm && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content" style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: 'var(--danger)' }}>
                Confirm Deletion
              </h2>
              <button onClick={() => setDeleteConfirm(null)} className="btn-icon" aria-label="Cancel deletion">
                <CloseIcon size={16} />
              </button>
            </div>
            <div className="modal-body">
              <p style={{ fontSize: '13.5px', color: 'var(--text-primary)', lineHeight: 1.5 }}>
                Are you sure you want to delete {deleteConfirm.type === 'consumer' ? 'consumer' : 'plugin config'}{' '}
                <strong style={{ color: '#FFFFFF' }}>&ldquo;{deleteConfirm.name}&rdquo;</strong>?
              </p>
              <p style={{ fontSize: '12.5px', color: 'var(--text-muted)', marginTop: '8px' }}>
                This action cannot be undone. Associated credentials and policies will be invalidated.
              </p>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                onClick={() => setDeleteConfirm(null)}
                className="btn btn-secondary"
              >
                Cancel
              </button>
              <button
                data-testid="confirm-delete-btn"
                type="button"
                onClick={() => {
                  if (deleteConfirm.type === 'consumer') {
                    handleDeleteConsumer(deleteConfirm.id);
                  } else {
                    handleDeletePlugin(deleteConfirm.id);
                  }
                }}
                className="btn btn-danger"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ConsumersPluginConfigsGrid;
