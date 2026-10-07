'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  adminApiClient,
  ConsumerKey,
  AdminApiClientOptions,
} from '@/lib/adminApiClient';
import { theme } from '@/styles/theme';

export interface ApiKeyManagerProps {
  consumerId?: string;
  initialKeys?: ConsumerKey[];
  onIssueKey?: (name?: string) => Promise<ConsumerKey>;
  onRevokeKey?: (keyId: string) => Promise<void>;
  onKeysChange?: (keys: ConsumerKey[]) => void;
  clientOptions?: AdminApiClientOptions;
}

export function ApiKeyManager({
  consumerId,
  initialKeys,
  onIssueKey,
  onRevokeKey,
  onKeysChange,
  clientOptions,
}: ApiKeyManagerProps) {
  const [keys, setKeys] = useState<ConsumerKey[]>(initialKeys || []);
  const [isLoading, setIsLoading] = useState<boolean>(!initialKeys && !!consumerId);
  const [isIssuing, setIsIssuing] = useState<boolean>(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [newKeyName, setNewKeyName] = useState<string>('');
  const [newlyIssuedRawKey, setNewlyIssuedRawKey] = useState<string | null>(null);
  const [copied, setCopied] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const fetchKeys = useCallback(async () => {
    if (!consumerId || initialKeys) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const result = await adminApiClient.listConsumerKeys(consumerId, clientOptions);
      setKeys(result);
      if (onKeysChange) onKeysChange(result);
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to load API keys');
    } finally {
      setIsLoading(false);
    }
  }, [consumerId, initialKeys, clientOptions, onKeysChange]);

  useEffect(() => {
    if (!initialKeys && consumerId) {
      fetchKeys();
    }
  }, [fetchKeys, initialKeys, consumerId]);

  const handleIssueKey = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsIssuing(true);
    setErrorMessage(null);
    const keyName = newKeyName.trim() || 'default';

    try {
      let createdKey: ConsumerKey;
      if (onIssueKey) {
        createdKey = await onIssueKey(keyName);
      } else {
        if (!consumerId) {
          throw new Error('Consumer ID is required to issue keys');
        }
        createdKey = await adminApiClient.issueConsumerKey(
          consumerId,
          { name: keyName },
          clientOptions
        );
      }

      // Append newly issued key to the list
      const updatedKeys = [createdKey, ...keys];
      setKeys(updatedKeys);
      if (onKeysChange) onKeysChange(updatedKeys);

      // Keep raw key in component memory for the one-time display modal only (never write to web storage)
      if (createdKey.key) {
        setNewlyIssuedRawKey(createdKey.key);
      }
      setNewKeyName('');
      setToastMessage('API key issued successfully');
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to issue API key');
    } finally {
      setIsIssuing(false);
    }
  };

  const handleRevokeKey = async (keyId: string) => {
    setRevokingId(keyId);
    setErrorMessage(null);
    try {
      if (onRevokeKey) {
        await onRevokeKey(keyId);
      } else {
        if (!consumerId) {
          throw new Error('Consumer ID is required to revoke keys');
        }
        await adminApiClient.revokeConsumerKey(consumerId, keyId, clientOptions);
      }

      // Remove from displayed list
      const updatedKeys = keys.filter((k) => k.id !== keyId);
      setKeys(updatedKeys);
      if (onKeysChange) onKeysChange(updatedKeys);
      setToastMessage('API key revoked');
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to revoke API key');
    } finally {
      setRevokingId(null);
    }
  };

  const handleCopyToClipboard = async (text: string) => {
    try {
      if (navigator?.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    }
  };

  const dismissRawKeyToast = () => {
    setNewlyIssuedRawKey(null);
    setCopied(false);
  };

  const formatMaskedDisplay = (key: ConsumerKey): string => {
    if (key.keyPrefix) {
      return `${key.keyPrefix}••••••••••••••••`;
    }
    if (key.keyHash) {
      return `unc_key_${key.keyHash.slice(0, 8)}••••••••••••••••`;
    }
    return `unc_key_••••••••••••••••`;
  };

  const formatTimestamp = (dateStr?: string): string => {
    if (!dateStr) return 'Just now';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Toast Alert */}
      {toastMessage && (
        <div
          role="status"
          style={{
            background: 'var(--success-tint, #E7F8EF)',
            color: 'var(--success, #1CAE68)',
            border: '1px solid rgba(28, 174, 104, 0.25)',
            borderRadius: theme.radiusControl,
            padding: '10px 16px',
            fontSize: '13.5px',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            {toastMessage}
          </span>
          <button
            onClick={() => setToastMessage(null)}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: 'inherit',
              padding: '2px',
              display: 'inline-flex',
              alignItems: 'center',
            }}
            aria-label="Dismiss message"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      )}

      {/* Error Alert */}
      {errorMessage && (
        <div
          role="alert"
          style={{
            background: 'var(--danger-tint, #FDECEC)',
            color: 'var(--danger, #E5484D)',
            border: '1px solid rgba(229, 72, 77, 0.25)',
            borderRadius: theme.radiusControl,
            padding: '12px 16px',
            fontSize: '13.5px',
            fontWeight: 500,
          }}
        >
          {errorMessage}
        </div>
      )}

      {/* One-Time Issuance Raw Key Toast / Modal */}
      {newlyIssuedRawKey && (
        <div
          data-testid="new-key-toast"
          style={{
            background: '#FFFFFF',
            border: `2px solid ${theme.accent}`,
            borderRadius: theme.radiusCard,
            padding: '20px 24px',
            boxShadow: theme.accent ? '0 6px 20px rgba(255, 107, 53, 0.15)' : 'none',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span
                style={{
                  background: theme.accentTint,
                  color: theme.accent,
                  padding: '3px 8px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 700,
                }}
              >
                ONE-TIME DISPLAY
              </span>
              <strong style={{ fontSize: '15px', color: theme.ink }}>
                Copy your new API key
              </strong>
            </div>
            <button
              data-testid="dismiss-key-toast-btn"
              onClick={dismissRawKeyToast}
              className="btn btn-outline btn-sm"
              style={{ fontSize: '12px' }}
            >
              Dismiss
            </button>
          </div>

          <p style={{ fontSize: '13px', color: theme.muted, margin: 0 }}>
            Make sure to copy this key now. For security purposes, Unc Gateway only stores the cryptographic hash and will never show the raw key again.
          </p>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              background: theme.background,
              border: `1px solid ${theme.border}`,
              borderRadius: theme.radiusControl,
              padding: '8px 12px',
            }}
          >
            <code
              data-testid="raw-key-value"
              style={{
                fontFamily: theme.fontFamilyMono,
                fontSize: '13px',
                color: theme.ink,
                flex: 1,
                wordBreak: 'break-all',
                fontWeight: 600,
              }}
            >
              {newlyIssuedRawKey}
            </code>
            <button
              data-testid="copy-key-btn"
              onClick={() => handleCopyToClipboard(newlyIssuedRawKey)}
              className="btn btn-primary btn-sm"
              style={{
                backgroundColor: copied ? theme.success : theme.accent,
                minWidth: '90px',
              }}
            >
              {copied ? (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                  Copied
                </span>
              ) : (
                'Copy Key'
              )}
            </button>
          </div>
        </div>
      )}

      {/* Issuance Action Card */}
      <div
        className="card"
        style={{
          padding: '24px',
          background: theme.surface,
          borderRadius: theme.radiusCard,
          border: `1px solid ${theme.border}`,
          boxShadow: 'var(--shadow-soft, 0 1px 2px rgba(0,0,0,.04), 0 8px 24px rgba(0,0,0,.06))',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
          }}
        >
          <div>
            <h3 style={{ fontSize: '17px', fontWeight: 700, color: theme.ink, marginBottom: '4px' }}>
              Issue API Key
            </h3>
            <p style={{ fontSize: '13.5px', color: theme.muted }}>
              Create a new API credential to authenticate requests through Gateway Core.
            </p>
          </div>

          <form
            onSubmit={handleIssueKey}
            style={{ display: 'flex', alignItems: 'center', gap: '10px' }}
          >
            <input
              type="text"
              placeholder="Key label (e.g. prod, dev, cli)"
              value={newKeyName}
              onChange={(e) => setNewKeyName(e.target.value)}
              style={{
                padding: '9px 14px',
                fontSize: '13.5px',
                borderRadius: theme.radiusControl,
                border: `1px solid ${theme.border}`,
                width: '220px',
              }}
            />
            <button
              type="submit"
              data-testid="issue-key-btn"
              disabled={isIssuing}
              className="btn btn-primary"
              style={{
                backgroundColor: theme.accent,
                fontFamily: theme.fontFamily,
                whiteSpace: 'nowrap',
              }}
            >
              {isIssuing ? 'Issuing...' : 'Issue new key'}
            </button>
          </form>
        </div>
      </div>

      {/* Keys List Card */}
      <div
        className="card"
        data-testid="keys-list"
        style={{
          background: theme.surface,
          borderRadius: theme.radiusCard,
          border: `1px solid ${theme.border}`,
          boxShadow: 'var(--shadow-soft, 0 1px 2px rgba(0,0,0,.04), 0 8px 24px rgba(0,0,0,.06))',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '18px 24px',
            borderBottom: `1px solid ${theme.border}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 700, color: theme.ink }}>
              Active Credentials
            </h3>
            <span
              className="pill pill-accent"
              style={{
                fontSize: '12px',
                background: theme.accentTint,
                color: theme.accent,
              }}
            >
              {keys.length} {keys.length === 1 ? 'key' : 'keys'}
            </span>
          </div>

          <span style={{ fontSize: '12.5px', color: theme.muted }}>
            Enforced by <strong style={{ color: theme.ink }}>key-auth</strong> plugin
          </span>
        </div>

        {isLoading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: theme.muted }}>
            Loading API keys...
          </div>
        ) : keys.length === 0 ? (
          <div style={{ padding: '48px 24px', textAlign: 'center' }}>
            <p style={{ color: theme.muted, fontSize: '14px', marginBottom: '16px' }}>
              No API keys issued yet for this consumer account.
            </p>
            <button
              onClick={() => handleIssueKey()}
              className="btn btn-primary btn-sm"
              style={{ backgroundColor: theme.accent }}
            >
              Issue your first key
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {keys.map((key) => {
              const isRevoking = revokingId === key.id;
              const isRevoked = key.status === 'REVOKED';

              return (
                <div
                  key={key.id}
                  data-testid={`key-row-${key.id}`}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '16px 24px',
                    borderBottom: `1px solid ${theme.border}`,
                    gap: '16px',
                    backgroundColor: isRevoking ? '#FAFAFA' : '#FFFFFF',
                  }}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', minWidth: '220px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <strong style={{ fontSize: '14px', color: theme.ink }}>
                        {key.name || 'default'}
                      </strong>
                      <span
                        className={`pill ${isRevoked ? 'pill-danger' : 'pill-success'}`}
                        style={{
                          fontSize: '11px',
                          padding: '2px 8px',
                          background: isRevoked ? 'var(--danger-tint, #FDECEC)' : 'var(--success-tint, #E7F8EF)',
                          color: isRevoked ? theme.danger : theme.success,
                        }}
                      >
                        {key.status || 'ACTIVE'}
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span
                        data-testid="masked-key"
                        className="mono"
                        style={{
                          fontFamily: theme.fontFamilyMono,
                          fontSize: '13px',
                          color: theme.ink,
                          letterSpacing: '0.5px',
                        }}
                      >
                        {formatMaskedDisplay(key)}
                      </span>
                    </div>
                  </div>

                  <div style={{ fontSize: '12.5px', color: theme.muted, whiteSpace: 'nowrap' }}>
                    Issued {formatTimestamp(key.createdAt)}
                  </div>

                  <div>
                    <button
                      data-testid={`revoke-key-btn-${key.id}`}
                      disabled={isRevoking || isRevoked}
                      onClick={() => handleRevokeKey(key.id)}
                      className="btn btn-outline btn-sm"
                      style={{
                        borderColor: theme.border,
                        color: theme.muted,
                        fontSize: '12.5px',
                      }}
                    >
                      {isRevoking ? 'Revoking...' : isRevoked ? 'Revoked' : 'Revoke'}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default ApiKeyManager;
