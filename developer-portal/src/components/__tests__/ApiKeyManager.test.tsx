import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ApiKeyManager } from '../ApiKeyManager';
import { ConsumerKey } from '@/lib/adminApiClient';
import { theme } from '@/styles/theme';

describe('ApiKeyManager Component', () => {
  const stubKeys: ConsumerKey[] = [
    {
      id: 'key-uuid-1',
      consumerId: 'cons-1',
      name: 'Production Key',
      keyPrefix: 'unc_key_11112222',
      keyHash: 'hash-abc',
      status: 'ACTIVE',
      createdAt: '2026-10-01T12:00:00Z',
    },
    {
      id: 'key-uuid-2',
      consumerId: 'cons-1',
      name: 'Staging Key',
      keyPrefix: 'unc_key_33334444',
      keyHash: 'hash-def',
      status: 'ACTIVE',
      createdAt: '2026-10-02T15:30:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders with a stubbed set of keys and displays each key masked (never raw value)', () => {
    render(<ApiKeyManager initialKeys={stubKeys} consumerId="cons-1" />);

    expect(screen.getByText('Production Key')).toBeInTheDocument();
    expect(screen.getByText('Staging Key')).toBeInTheDocument();

    const maskedDisplays = screen.getAllByTestId('masked-key');
    expect(maskedDisplays).toHaveLength(2);

    // Each key is masked with bullet points and prefix
    expect(maskedDisplays[0].textContent).toContain('unc_key_11112222••••••••••••••••');
    expect(maskedDisplays[1].textContent).toContain('unc_key_33334444••••••••••••••••');

    // Assert raw key is not present in document
    expect(screen.queryByText(/unc_key_11112222fullsecret/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/unc_key_33334444fullsecret/i)).not.toBeInTheDocument();

    // Verify Ignite styling tokens
    const issueBtn = screen.getByTestId('issue-key-btn');
    expect(issueBtn.style.backgroundColor).toBe('rgb(255, 107, 53)'); // theme.accent #FF6B35
  });

  it('clicking "issue new key" triggers exactly one call to key-issuance method', async () => {
    const mockIssue = vi.fn().mockResolvedValue({
      id: 'key-uuid-new',
      consumerId: 'cons-1',
      name: 'cli-tool',
      keyPrefix: 'unc_key_99998888',
      keyHash: 'hash-new',
      status: 'ACTIVE',
      key: 'unc_key_99998888rawsecretvaluehere',
      createdAt: '2026-10-03T20:00:00Z',
    });

    render(
      <ApiKeyManager
        initialKeys={stubKeys}
        consumerId="cons-1"
        onIssueKey={mockIssue}
      />
    );

    const input = screen.getByPlaceholderText(/key label/i);
    fireEvent.change(input, { target: { value: 'cli-tool' } });

    const issueBtn = screen.getByTestId('issue-key-btn');
    fireEvent.click(issueBtn);

    await waitFor(() => {
      expect(mockIssue).toHaveBeenCalledTimes(1);
    });
    expect(mockIssue).toHaveBeenCalledWith('cli-tool');
  });

  it('issuing a key appends newly returned masked key to list and never renders raw value after initial issuance response is dismissed', async () => {
    const rawKey = 'unc_key_99998888rawsecretvaluehere';
    const mockIssue = vi.fn().mockResolvedValue({
      id: 'key-uuid-new',
      consumerId: 'cons-1',
      name: 'new-key',
      keyPrefix: 'unc_key_99998888',
      keyHash: 'hash-new',
      status: 'ACTIVE',
      key: rawKey,
      createdAt: '2026-10-03T20:00:00Z',
    });

    render(
      <ApiKeyManager
        initialKeys={stubKeys}
        consumerId="cons-1"
        onIssueKey={mockIssue}
      />
    );

    const issueBtn = screen.getByTestId('issue-key-btn');
    fireEvent.click(issueBtn);

    // Initial issuance toast/modal renders with raw key
    expect(await screen.findByTestId('new-key-toast')).toBeInTheDocument();
    expect(screen.getByTestId('raw-key-value')).toHaveTextContent(rawKey);

    // List shows 3 keys now, all masked
    const maskedDisplays = screen.getAllByTestId('masked-key');
    expect(maskedDisplays).toHaveLength(3);
    expect(maskedDisplays[0].textContent).toContain('unc_key_99998888••••••••••••••••');

    // Dismiss the one-time raw key toast
    const dismissBtn = screen.getByTestId('dismiss-key-toast-btn');
    fireEvent.click(dismissBtn);

    // After dismissal, toast is gone and raw key value is nowhere in the document
    expect(screen.queryByTestId('new-key-toast')).not.toBeInTheDocument();
    expect(screen.queryByText(rawKey)).not.toBeInTheDocument();

    // Masked representation remains in the list
    expect(screen.getByText('new-key')).toBeInTheDocument();
    expect(screen.getAllByTestId('masked-key')[0].textContent).toContain('unc_key_99998888••••••••••••••••');
  });

  it('revoking a key removes it from displayed list and calls revocation client method exactly once with keyId', async () => {
    const mockRevoke = vi.fn().mockResolvedValue(undefined);

    render(
      <ApiKeyManager
        initialKeys={stubKeys}
        consumerId="cons-1"
        onRevokeKey={mockRevoke}
      />
    );

    expect(screen.getByText('Production Key')).toBeInTheDocument();
    expect(screen.getByText('Staging Key')).toBeInTheDocument();

    const revokeBtn1 = screen.getByTestId('revoke-key-btn-key-uuid-1');
    fireEvent.click(revokeBtn1);

    await waitFor(() => {
      expect(mockRevoke).toHaveBeenCalledTimes(1);
    });
    expect(mockRevoke).toHaveBeenCalledWith('key-uuid-1');

    // Key-1 should be removed from displayed list
    await waitFor(() => {
      expect(screen.queryByText('Production Key')).not.toBeInTheDocument();
    });
    // Key-2 should still remain
    expect(screen.getByText('Staging Key')).toBeInTheDocument();
    expect(screen.getAllByTestId('masked-key')).toHaveLength(1);
  });
});
