import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthProvider } from '../../context/AuthContext';
import { AuthPage } from '../AuthPage';
import { postIdentity } from '../../api/client';

vi.mock('../../api/client', () => ({
  postIdentity: vi.fn(),
}));

function renderAuthPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<AuthPage />} />
          <Route path="/projects" element={<div>Projects Stub Page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('AuthPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows validation errors on submit with blank fields and does not call API', async () => {
    renderAuthPage();

    const submitBtn = screen.getByRole('button', { name: /continue/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByText('Full name is required')).toBeInTheDocument();
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(postIdentity).not.toHaveBeenCalled();
  });

  it('renders inline error message when API rejects with an error', async () => {
    postIdentity.mockRejectedValueOnce(new Error('Invalid email domain'));

    renderAuthPage();

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/email/i);
    const submitBtn = screen.getByRole('button', { name: /continue/i });

    fireEvent.change(nameInput, { target: { value: 'Jane Doe' } });
    fireEvent.change(emailInput, { target: { value: 'jane@invalid.com' } });
    fireEvent.click(submitBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email domain');
    expect(postIdentity).toHaveBeenCalledWith({ email: 'jane@invalid.com', name: 'Jane Doe' });
  });

  it('calls login and navigates to /projects when submission is valid', async () => {
    postIdentity.mockResolvedValueOnce({
      userId: 'user-123',
      name: 'Jane Doe',
    });

    renderAuthPage();

    const nameInput = screen.getByLabelText(/full name/i);
    const emailInput = screen.getByLabelText(/email/i);
    const submitBtn = screen.getByRole('button', { name: /continue/i });

    fireEvent.change(nameInput, { target: { value: 'Jane Doe' } });
    fireEvent.change(emailInput, { target: { value: 'jane@example.com' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Projects Stub Page')).toBeInTheDocument();
    });

    expect(postIdentity).toHaveBeenCalledWith({ email: 'jane@example.com', name: 'Jane Doe' });
    expect(JSON.parse(localStorage.getItem('gd_user'))).toEqual({
      userId: 'user-123',
      name: 'Jane Doe',
      email: 'jane@example.com',
    });
  });
});
