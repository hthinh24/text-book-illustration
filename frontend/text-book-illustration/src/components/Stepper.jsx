import React from 'react';
import { STEPS, isProjectComplete } from '../utils/pipelineSteps';

const STEP_LABELS = {
  STYLE: 'Style',
  CHARACTER: 'Characters',
  PORTRAIT: 'Portraits',
  CHAPTER: 'Chapters',
  ILLUSTRATION: 'Illustrations',
};

export function Stepper({ currentStep, stepStatus }) {
  const actionableIdx = STEPS.indexOf(currentStep);
  const complete = isProjectComplete(currentStep, stepStatus);

  return (
    <div className="stepper-container">
      {STEPS.map((stepKey, index) => {
        const isDone = complete || index < actionableIdx;
        const isCurrent = !complete && index === actionableIdx;
        const isRunning = isCurrent && stepStatus === 'RUNNING';

        let numSquareClass = 'gd-num-square';
        if (isDone) {
          numSquareClass += ' done';
        } else if (!isCurrent) {
          numSquareClass += ' gray';
        }

        return (
          <React.Fragment key={stepKey}>
            {index > 0 && (
              <div className={`stepper-line ${index <= (complete ? STEPS.length : actionableIdx) ? 'filled' : ''}`} />
            )}
            <div className={`stepper-item ${isCurrent ? 'active' : ''}`}>
              <div className={numSquareClass}>
                {isDone ? '✓' : index + 1}
              </div>
              <span className={`stepper-label ${isCurrent ? 'active' : ''} ${isDone ? 'done' : ''}`}>
                {STEP_LABELS[stepKey] || stepKey}
                {isRunning && <span className="dot-pulse-inline" />}
              </span>
            </div>
          </React.Fragment>
        );
      })}
    </div>
  );
}
