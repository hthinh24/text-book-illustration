import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AuthContext } from '../../context/AuthContext';
import { ProjectDetailPage } from '../ProjectDetailPage';
import {
  getProjectDetail,
  triggerStyle,
  triggerCharacter,
  retryStep,
  getBookText,
} from '../../api/client';

vi.mock('../../api/client', () => ({
  getProjectDetail: vi.fn(),
  triggerStyle: vi.fn(),
  triggerCharacter: vi.fn(),
  triggerPortraits: vi.fn(),
  triggerChapters: vi.fn(),
  triggerIllustrations: vi.fn(),
  retryStep: vi.fn(),
  getBookText: vi.fn(),
}));

const mockUser = {
  userId: 'user-uuid-123',
  name: 'Test User',
  email: 'test@example.com',
};

function renderProjectDetailPage(userContextValue = { user: mockUser }) {
  return render(
    <AuthContext.Provider value={userContextValue}>
      <MemoryRouter initialEntries={['/projects/proj-123']}>
        <Routes>
          <Route path="/projects/:id" element={<ProjectDetailPage />} />
          <Route path="/projects" element={<div>Project List Stub</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('ProjectDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders style form and button when step is STYLE and PENDING', async () => {
    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'DRAFT',
      step: 'STYLE',
      stepStatus: 'PENDING',
      characters: [],
      chapters: [],
    });

    renderProjectDetailPage();

    expect(await screen.findByText('Alice in Wonderland')).toBeInTheDocument();
    expect(screen.getByLabelText(/art style/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /generate style/i })).toBeInTheDocument();
  });

  it('triggers style generation and polls until SUCCESS state', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });

    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'DRAFT',
      step: 'STYLE',
      stepStatus: 'PENDING',
      characters: [],
      chapters: [],
    });

    triggerStyle.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'IN_PROGRESS',
      step: 'STYLE',
      stepStatus: 'RUNNING',
      characters: [],
      chapters: [],
    });

    renderProjectDetailPage();

    // Wait for initial render
    await act(async () => {
      await Promise.resolve();
    });

    const generateBtn = screen.getByRole('button', { name: /generate style/i });
    
    await act(async () => {
      fireEvent.click(generateBtn);
    });

    expect(triggerStyle).toHaveBeenCalledWith('proj-123', '');

    // Next polling tick returns SUCCESS
    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'IN_PROGRESS',
      step: 'STYLE',
      stepStatus: 'SUCCESS',
      style: 'Watercolor digital illustration',
      characters: [],
      chapters: [],
    });

    // Advance timers by 2500ms for polling hook
    await act(async () => {
      vi.advanceTimersByTimeAsync(2600);
    });

    expect(getProjectDetail).toHaveBeenCalledTimes(2);
    expect(await screen.findByText('Watercolor digital illustration')).toBeInTheDocument();
  });

  it('renders error banner and calls retryStep on retry button click', async () => {
    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'IN_PROGRESS',
      step: 'CHARACTER',
      stepStatus: 'FAIL',
      errorMessage: 'Gemini rate limit exceeded',
      characters: [],
      chapters: [],
    });

    retryStep.mockResolvedValueOnce({
      project: {
        projectId: 'proj-123',
        title: 'Alice in Wonderland',
        createdAt: '2026-08-10T10:00:00Z',
        status: 'IN_PROGRESS',
        step: 'CHARACTER',
        stepStatus: 'PENDING',
        characters: [],
        chapters: [],
      },
      retryReason: 'FAILED',
    });

    renderProjectDetailPage();

    expect(await screen.findByText(/Gemini rate limit exceeded/i)).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: /retry step/i });
    fireEvent.click(retryBtn);

    expect(await screen.findByRole('button', { name: /generate characters/i })).toBeInTheDocument();
    expect(retryStep).toHaveBeenCalledWith('proj-123');
  });

  it('renders character cards in DONE and PENDING states', async () => {
    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'IN_PROGRESS',
      step: 'PORTRAIT',
      stepStatus: 'PENDING',
      characters: [
        {
          id: 'char-1',
          name: 'Alice',
          imagePrompt: 'Young girl in blue dress',
          portraitImagePath: '/data/portraits/2244b005-7fac-4941-9b97-b6c3053a07fa.png',
          status: 'DONE',
        },
        {
          id: 'char-2',
          name: 'Mad Hatter',
          imagePrompt: 'eccentric man with top hat',
          portraitImagePath: null,
          status: 'PENDING',
        },
      ],
      chapters: [],
    });

    renderProjectDetailPage();

    expect(await screen.findByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Mad Hatter')).toBeInTheDocument();
    expect(screen.getByText('PORTRAIT PENDING')).toBeInTheDocument();
    expect(screen.getByAltText('Alice')).toHaveAttribute('src', 'http://localhost:8080/data/portraits/2244b005-7fac-4941-9b97-b6c3053a07fa.png');
  });

  it('hides retry button and displays terminal notice when retry is exhausted', async () => {
    getProjectDetail.mockResolvedValueOnce({
      projectId: 'proj-123',
      title: 'Alice in Wonderland',
      createdAt: '2026-08-10T10:00:00Z',
      status: 'IN_PROGRESS',
      step: 'CHARACTER',
      stepStatus: 'FAIL',
      errorMessage: 'Gemini quota exceeded',
      characters: [],
      chapters: [],
    });

    retryStep.mockRejectedValueOnce(new Error('RETRY_EXHAUSTED: Retry limit reached for step CHARACTER (10/10).'));

    renderProjectDetailPage();

    expect(await screen.findByText(/Gemini quota exceeded/i)).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: /retry step/i });
    fireEvent.click(retryBtn);

    expect(await screen.findByText(/This step has failed too many times and can't be retried automatically/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry step/i })).not.toBeInTheDocument();
  });
});
