import React, { useState } from 'react';
import { Field } from './Field';
import { Button } from './Button';
import { isProjectComplete } from '../utils/pipelineSteps';

const ACTION_LABELS = {
  STYLE: 'Style',
  CHARACTER: 'Characters',
  PORTRAIT: 'Portraits',
  CHAPTER: 'Chapters',
  ILLUSTRATION: 'Illustrations',
};

export function StepPanel({
  project,
  actionableStep,
  onTriggerStyle,
  onTriggerStep,
  onRetry,
  retryNotice,
  isRetryExhausted = false,
  isActionLoading,
}) {
  const [styleInput, setStyleInput] = useState('');

  const { stepName, stepStatus } = actionableStep;
  const complete = isProjectComplete(project.step, project.stepStatus);

  const renderButtonSpinner = () => (
    <span
      className="spinner"
      style={{
        width: '14px',
        height: '14px',
        borderWidth: '2px',
        borderColor: 'rgba(255, 255, 255, 0.4)',
        borderTopColor: '#ffffff',
        marginRight: '6px',
      }}
    />
  );

  if (complete) {
    return (
      <div className="gd-card step-panel">
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
          <div className="gd-num-square done">✓</div>
          <div>
            <div style={{ fontWeight: 'var(--fw-bold)', fontSize: '15px', color: 'var(--fg-1)' }}>
              All 5 steps complete — nothing left to generate.
            </div>
            <div style={{ fontSize: '13px', color: 'var(--fg-2)', marginTop: '4px' }}>
              This project is done. Reopen it any time; nothing here regenerates automatically.
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Handle FAIL state
  if (project.stepStatus === 'FAIL') {
    const exhausted = isRetryExhausted || retryNotice?.includes('RETRY_EXHAUSTED');

    return (
      <div className="gd-card step-panel">
        {retryNotice && (
          <div className="gd-error-banner" style={{ marginBottom: 'var(--sp-3)' }}>
            {retryNotice.replace('RETRY_EXHAUSTED: ', '')}
          </div>
        )}
        <div style={{ fontWeight: 'var(--fw-bold)', fontSize: '15px', color: '#C62828', marginBottom: '8px' }}>
          Step failed: {ACTION_LABELS[stepName] || stepName}
        </div>
        <p style={{ fontSize: '14px', color: 'var(--fg-1)', marginBottom: 'var(--sp-4)' }}>
          {project.errorMessage || 'An error occurred while generating this step.'}
        </p>

        {exhausted ? (
          <div style={{ fontSize: '13px', color: 'var(--fg-2)', background: 'var(--bg-3)', padding: '10px 14px', borderRadius: 'var(--r-2)' }}>
            This step has failed too many times and can't be retried automatically.
          </div>
        ) : (
          <Button
            variant="primary"
            disabled={isActionLoading}
            onClick={onRetry}
          >
            {isActionLoading && renderButtonSpinner()}
            {isActionLoading ? 'Retrying…' : 'Retry Step →'}
          </Button>
        )}
      </div>
    );
  }

  // Handle RUNNING state
  if (stepStatus === 'RUNNING' || project.stepStatus === 'RUNNING') {
    return (
      <div className="gd-card step-panel">
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
          <div className="spinner" />
          <div>
            <div style={{ fontWeight: 'var(--fw-bold)', fontSize: '15px', color: 'var(--fg-1)' }}>
              Generating {ACTION_LABELS[stepName] || stepName}…
            </div>
            <div style={{ fontSize: '13px', color: 'var(--fg-2)', marginTop: '4px' }}>
              Gemini is working on your request. This page polls automatically until complete.
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Handle PENDING state
  return (
    <div className="gd-card step-panel">
      <div style={{ fontSize: '14px', color: 'var(--fg-2)', marginBottom: 'var(--sp-4)' }}>
        Ready for the next step: <strong>{ACTION_LABELS[stepName] || stepName}</strong>.
      </div>

      {stepName === 'STYLE' ? (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            onTriggerStyle(styleInput);
          }}
        >
          <Field
            id="style-input"
            label="Art style (optional)"
            value={styleInput}
            onChange={(e) => setStyleInput(e.target.value)}
            placeholder="Leave blank to let Gemini choose a style based on your book"
            disabled={isActionLoading}
          />
          <Button
            type="submit"
            variant="primary"
            disabled={isActionLoading}
            style={{ marginTop: 'var(--sp-2)' }}
          >
            {isActionLoading && renderButtonSpinner()}
            {isActionLoading ? 'Generating…' : 'Generate Style →'}
          </Button>
        </form>
      ) : (
        <div>
          <Button
            variant="primary"
            disabled={isActionLoading}
            onClick={() => onTriggerStep(stepName)}
          >
            {isActionLoading && renderButtonSpinner()}
            {isActionLoading ? 'Generating…' : `Generate ${ACTION_LABELS[stepName]} →`}
          </Button>
        </div>
      )}
    </div>
  );
}
