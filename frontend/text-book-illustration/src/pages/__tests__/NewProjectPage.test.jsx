import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthContext } from '../../context/AuthContext';
import { NewProjectPage } from '../NewProjectPage';
import { initProject } from '../../api/client';

vi.mock('../../api/client', () => ({
  initProject: vi.fn(),
}));

const mockUser = {
  userId: 'user-uuid-123',
  name: 'Test User',
  email: 'test@example.com',
};

function renderNewProjectPage(userContextValue = { user: mockUser }) {
  return render(
    <AuthContext.Provider value={userContextValue}>
      <MemoryRouter initialEntries={['/projects/new']}>
        <Routes>
          <Route path="/projects/new" element={<NewProjectPage />} />
          <Route path="/projects/:id" element={<div>Project Detail View</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('NewProjectPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows validation errors when submitting blank fields and does not call initProject', async () => {
    renderNewProjectPage();

    const submitBtn = screen.getByRole('button', { name: /create project/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByText('Project title is required')).toBeInTheDocument();
    expect(await screen.findByText('Book text is required')).toBeInTheDocument();
    expect(initProject).not.toHaveBeenCalled();
  });

  it('enforces input mode mutual exclusivity when switching tabs', async () => {
    renderNewProjectPage();

    const textarea = screen.getByPlaceholderText(/paste the book text here/i);
    fireEvent.change(textarea, { target: { value: 'Once upon a time...' } });
    expect(textarea.value).toBe('Once upon a time...');

    const fileTabBtn = screen.getByRole('tab', { name: /upload .txt file/i });
    fireEvent.click(fileTabBtn);

    expect(screen.queryByPlaceholderText(/paste the book text here/i)).not.toBeInTheDocument();
    expect(screen.getByText(/choose a .txt file/i)).toBeInTheDocument();

    const textTabBtn = screen.getByRole('tab', { name: /paste text/i });
    fireEvent.click(textTabBtn);

    const reOpenedTextarea = screen.getByPlaceholderText(/paste the book text here/i);
    expect(reOpenedTextarea.value).toBe('');
  });

  it('calls initProject with text content and navigates on success', async () => {
    initProject.mockResolvedValueOnce({
      projectId: 'proj-new-999',
      title: 'The Great Gatsby',
    });

    renderNewProjectPage();

    const titleInput = screen.getByLabelText(/project title/i);
    const textarea = screen.getByPlaceholderText(/paste the book text here/i);
    const submitBtn = screen.getByRole('button', { name: /create project/i });

    fireEvent.change(titleInput, { target: { value: 'The Great Gatsby' } });
    fireEvent.change(textarea, { target: { value: 'In my younger and more vulnerable years...' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Project Detail View')).toBeInTheDocument();
    });

    expect(initProject).toHaveBeenCalledWith({
      userId: 'user-uuid-123',
      title: 'The Great Gatsby',
      text: 'In my younger and more vulnerable years...',
      file: undefined,
    });
  });
});
