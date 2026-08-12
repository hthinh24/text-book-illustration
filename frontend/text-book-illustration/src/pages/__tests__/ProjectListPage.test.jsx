import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthContext } from '../../context/AuthContext';
import { ProjectListPage } from '../ProjectListPage';
import { getProjects } from '../../api/client';

vi.mock('../../api/client', () => ({
  getProjects: vi.fn(),
}));

const mockUser = {
  userId: 'user-uuid-123',
  name: 'Test User',
  email: 'test@example.com',
};

function renderProjectListPage(userContextValue = { user: mockUser }) {
  return render(
    <AuthContext.Provider value={userContextValue}>
      <MemoryRouter initialEntries={['/projects']}>
        <Routes>
          <Route path="/projects" element={<ProjectListPage />} />
          <Route path="/projects/new" element={<div>New Project Stub</div>} />
          <Route path="/projects/:id" element={<div>Detail Stub</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('ProjectListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty state when getProjects returns an empty array', async () => {
    getProjects.mockResolvedValueOnce([]);

    renderProjectListPage();

    expect(screen.getByText('Loading projects…')).toBeInTheDocument();
    expect(await screen.findByText('No projects yet')).toBeInTheDocument();
    expect(getProjects).toHaveBeenCalledWith('user-uuid-123');
  });

  it('renders project list items with correct status pills and progress segments', async () => {
    getProjects.mockResolvedValueOnce([
      { projectId: 'proj-1', title: 'Alice in Wonderland', createdAt: '2026-08-10T10:00:00Z', status: 'DRAFT', step: 'STYLE', stepStatus: 'PENDING' },
      { projectId: 'proj-2', title: 'Peter Pan', createdAt: '2026-08-11T12:00:00Z', status: 'DONE', step: 'ILLUSTRATION', stepStatus: 'SUCCESS' },
    ]);

    renderProjectListPage();

    const aliceRow = (await screen.findByText('Alice in Wonderland')).closest('.project-row');
    const peterRow = screen.getByText('Peter Pan').closest('.project-row');

    expect(aliceRow.querySelectorAll('.progress-segment.filled')).toHaveLength(0);
    expect(peterRow.querySelectorAll('.progress-segment.filled')).toHaveLength(5);
    expect(screen.getByText('Draft')).toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();
  });

  it('renders error banner with retry button on API failure', async () => {
    getProjects.mockRejectedValueOnce(new Error('Network failure'));

    renderProjectListPage();

    expect(await screen.findByText('Network failure')).toBeInTheDocument();

    getProjects.mockResolvedValueOnce([
      {
        projectId: 'proj-3',
        title: 'Retried Project',
        createdAt: '2026-08-12T10:00:00Z',
        status: 'DRAFT',
        step: 'STYLE',
        stepStatus: 'PENDING',
      },
    ]);

    const retryBtn = screen.getByRole('button', { name: /retry/i });
    fireEvent.click(retryBtn);

    expect(await screen.findByText('Retried Project')).toBeInTheDocument();
    expect(getProjects).toHaveBeenCalledTimes(2);
  });
});
