import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  getProjectDetail,
  triggerStyle,
  triggerCharacter,
  triggerPortraits,
  triggerChapters,
  triggerIllustrations,
  retryStep,
} from '../api/client';
import { getActionableStep, isProjectComplete } from '../utils/pipelineSteps';
import { usePolling } from '../hooks/usePolling';
import { Stepper } from '../components/Stepper';
import { StepPanel } from '../components/StepPanel';
import { BookTextModal } from '../components/BookTextModal';
import { CharacterCard } from '../components/CharacterCard';
import { ChapterCard } from '../components/ChapterCard';
import { Button } from '../components/Button';

export function ProjectDetailPage() {
  const { id } = useParams();

  const [project, setProject] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [retryNotice, setRetryNotice] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!id) return;
    try {
      const data = await getProjectDetail(id);
      setProject(data);
      setError(null);
    } catch (err) {
      setError(err.message || 'Failed to load project details.');
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const actionableStep = project
    ? getActionableStep(project.step, project.stepStatus)
    : { stepName: 'STYLE', stepStatus: 'PENDING' };

  const complete = project ? isProjectComplete(project.step, project.stepStatus) : false;
  const isRunning = project?.stepStatus === 'RUNNING' || actionableStep.stepStatus === 'RUNNING';

  // Fix 2: Enable polling for any open project while not complete.
  // Fast poll (2.5s) when RUNNING, background sync poll (3.5s) when PENDING/FAIL to sync across browser tabs
  usePolling(fetchDetail, {
    intervalMs: isRunning ? 2500 : 3500,
    enabled: !!project && !complete,
  });

  const handleTriggerStyle = async (userStyle) => {
    setIsActionLoading(true);
    setRetryNotice(null);
    try {
      const updated = await triggerStyle(id, userStyle);
      setProject(updated);
    } catch (err) {
      setError(err.message || 'Failed to trigger style generation.');
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleTriggerStep = async (stepName) => {
    setIsActionLoading(true);
    setRetryNotice(null);
    try {
      let updated;
      switch (stepName) {
        case 'CHARACTER':
          updated = await triggerCharacter(id);
          break;
        case 'PORTRAIT':
          updated = await triggerPortraits(id);
          break;
        case 'CHAPTER':
          updated = await triggerChapters(id);
          break;
        case 'ILLUSTRATION':
          updated = await triggerIllustrations(id);
          break;
        default:
          break;
      }
      if (updated) {
        setProject(updated);
      }
    } catch (err) {
      setError(err.message || `Failed to trigger ${stepName.toLowerCase()} step.`);
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleRetry = async () => {
    setIsActionLoading(true);
    setRetryNotice(null);
    try {
      const result = await retryStep(id);
      const { project: freshProject, retryReason } = result || {};
      if (retryReason === 'STUCK_TIMEOUT') {
        setRetryNotice('Step was stuck and has been reset to retry.');
      } else if (retryReason === 'RETRY_EXHAUSTED') {
        setRetryNotice('RETRY_EXHAUSTED: Retry limit reached for this step.');
      } else {
        setRetryNotice('Step failed and has been reset to retry.');
      }
      if (freshProject) {
        setProject(freshProject);
      } else {
        await fetchDetail();
      }
    } catch (err) {
      if (err.message && err.message.includes('RETRY_EXHAUSTED')) {
        setRetryNotice('RETRY_EXHAUSTED: Retry limit reached for this step.');
      } else {
        setError(err.message || 'Failed to retry step.');
      }
    } finally {
      setIsActionLoading(false);
    }
  };

  if (isLoading) {
    return <div style={{ color: 'var(--fg-2)', textAlign: 'center', padding: 'var(--sp-8)' }}>Loading project details…</div>;
  }

  if (error && !project) {
    return (
      <div>
        <Link to="/projects" className="back-link">
          ← Back to projects
        </Link>
        <div className="gd-error-banner" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 'var(--sp-4)' }}>
          <span>{error}</span>
          <Button variant="secondary" size="sm" onClick={fetchDetail}>
            Retry
          </Button>
        </div>
      </div>
    );
  }

  if (!project) return null;

  const formattedDate = project.createdAt ? new Date(project.createdAt).toLocaleDateString() : '';
  const hasCharacters = Array.isArray(project.characters) && project.characters.length > 0;
  const hasChapters = Array.isArray(project.chapters) && project.chapters.length > 0;

  return (
    <div>
      <Link to="/projects" className="back-link">
        ← Back to projects
      </Link>

      <div style={{ marginBottom: 'var(--sp-5)' }}>
        <h1 className="page-title">{project.title}</h1>
        <div className="meta">Created {formattedDate}</div>
      </div>

      <Stepper currentStep={actionableStep.stepName} stepStatus={actionableStep.stepStatus} />

      <div className="detail-grid">
        <StepPanel
          project={project}
          actionableStep={actionableStep}
          onTriggerStyle={handleTriggerStyle}
          onTriggerStep={handleTriggerStep}
          onRetry={handleRetry}
          retryNotice={retryNotice}
          isActionLoading={isActionLoading}
        />

        <div className="side-panel-card">
          {project.style && (
            <div className="side-panel-section">
              <div className="side-panel-title">Style</div>
              <div className="side-panel-text">{project.style}</div>
            </div>
          )}

          <div className="side-panel-section">
            <div className="side-panel-title">Book Text</div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsModalOpen(true)}
              style={{ fontWeight: 'var(--fw-semi)' }}
            >
              Read full text →
            </Button>
          </div>
        </div>
      </div>

      {hasChapters && (
        <div className="entity-section">
          <h3 className="entity-section-title">Chapters ({project.chapters.length})</h3>
          <div className="entity-grid">
            {project.chapters.map((ch) => (
              <ChapterCard key={ch.id} chapter={ch} />
            ))}
          </div>
        </div>
      )}

      {hasCharacters && (
        <div className="entity-section">
          <h3 className="entity-section-title">Characters ({project.characters.length})</h3>
          <div className="entity-grid">
            {project.characters.map((char) => (
              <CharacterCard key={char.id} character={char} />
            ))}
          </div>
        </div>
      )}

      <BookTextModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        projectId={id}
        title={project.title}
      />
    </div>
  );
}
