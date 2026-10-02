import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SignupForm } from '../SignupForm';
import { theme } from '@/styles/theme';

describe('SignupForm Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders form with Ignite theme styling tokens', () => {
    render(<SignupForm />);

    expect(screen.getByText('Create your developer account')).toBeInTheDocument();
    expect(screen.getByText('Self-serve onboarding')).toBeInTheDocument();

    const submitBtn = screen.getByRole('button', { name: /create account/i });
    expect(submitBtn).toBeInTheDocument();

    // Verify Ignite theme attributes (accent color #FF6B35 and Inter font)
    expect(submitBtn.style.backgroundColor).toBe('rgb(255, 107, 53)'); // #FF6B35 in rgb
    expect(submitBtn.style.fontFamily).toBe(theme.fontFamily);
  });

  it('blocks submission and displays validation errors when required fields are empty', async () => {
    const mockSubmit = vi.fn();
    render(<SignupForm onSubmitHandler={mockSubmit} />);

    const submitBtn = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByText('Full name is required')).toBeInTheDocument();
    expect(await screen.findByText('Work email is required')).toBeInTheDocument();
    expect(mockSubmit).not.toHaveBeenCalled();
  });

  it('validates email format before submission', async () => {
    const mockSubmit = vi.fn();
    render(<SignupForm onSubmitHandler={mockSubmit} />);

    fireEvent.change(screen.getByLabelText(/full name/i), {
      target: { value: 'Ada Lovelace' },
    });
    fireEvent.change(screen.getByLabelText(/work email/i), {
      target: { value: 'not-an-email' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Please enter a valid email address')).toBeInTheDocument();
    expect(mockSubmit).not.toHaveBeenCalled();
  });

  it('calls submit handler exactly once with entered form values on valid submission', async () => {
    const mockSubmit = vi.fn().mockResolvedValue({
      id: 'consumer-uuid-123',
      name: 'Ada Lovelace',
      username: 'Ada Lovelace',
      email: 'ada@acme.dev',
      organization: 'Acme Corp',
    });

    render(<SignupForm onSubmitHandler={mockSubmit} defaultTenantId="tenant-xyz" />);

    fireEvent.change(screen.getByLabelText(/full name/i), {
      target: { value: 'Ada Lovelace' },
    });
    fireEvent.change(screen.getByLabelText(/work email/i), {
      target: { value: 'ada@acme.dev' },
    });
    fireEvent.change(screen.getByLabelText(/organization/i), {
      target: { value: 'Acme Corp' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(mockSubmit).toHaveBeenCalledTimes(1);
    });

    expect(mockSubmit).toHaveBeenCalledWith({
      name: 'Ada Lovelace',
      email: 'ada@acme.dev',
      organization: 'Acme Corp',
      tenantId: 'tenant-xyz',
    });
  });

  it('renders confirmation view with success accent when submission succeeds', async () => {
    const mockSubmit = vi.fn().mockResolvedValue({
      id: 'consumer-uuid-456',
      name: 'Grace Hopper',
      email: 'grace@navy.gov',
      organization: 'US Navy',
    });

    render(<SignupForm onSubmitHandler={mockSubmit} />);

    fireEvent.change(screen.getByLabelText(/full name/i), {
      target: { value: 'Grace Hopper' },
    });
    fireEvent.change(screen.getByLabelText(/work email/i), {
      target: { value: 'grace@navy.gov' },
    });
    fireEvent.change(screen.getByLabelText(/organization/i), {
      target: { value: 'US Navy' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByTestId('signup-success-view')).toBeInTheDocument();
    expect(screen.getByText("You're all set, Grace Hopper")).toBeInTheDocument();
    expect(
      screen.getByText(/Your consumer account was created. Head to/i)
    ).toBeInTheDocument();

    const keysLink = screen.getByRole('link', { name: /go to api keys/i });
    expect(keysLink).toHaveAttribute('href', '/keys?consumerId=consumer-uuid-456');
  });

  it('displays inline error alert using danger tokens when submission fails', async () => {
    const mockSubmit = vi.fn().mockRejectedValue(new Error('Consumer already exists'));

    render(<SignupForm onSubmitHandler={mockSubmit} />);

    fireEvent.change(screen.getByLabelText(/full name/i), {
      target: { value: 'Ada Lovelace' },
    });
    fireEvent.change(screen.getByLabelText(/work email/i), {
      target: { value: 'ada@acme.dev' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    const errorAlert = await screen.findByRole('alert');
    expect(errorAlert).toBeInTheDocument();
    expect(errorAlert).toHaveTextContent('Consumer already exists');
    expect(errorAlert.style.color).toBe('rgb(229, 72, 77)'); // #E5484D danger color
  });
});
