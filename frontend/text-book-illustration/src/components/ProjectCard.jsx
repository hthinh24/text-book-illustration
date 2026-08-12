import React from 'react';
import { completedStepCount, STEPS } from '../utils/pipelineSteps';

export function ProjectCard({ project, onClick }) {
  const filledCount = completedStepCount(project.step, project.stepStatus);
  const formattedDate = project.createdAt
    ? new Date(project.createdAt).toLocaleDateString()
    : '';

  const renderStatusPill = () => {
    switch (project.status) {
      case 'DONE':
        return <span className="gd-pill ink">Done</span>;
      case 'IN_PROGRESS':
        return (
          <span className="gd-pill">
            <span className="dot-pulse" />
            In progress
          </span>
        );
      case 'DRAFT':
      default:
        return <span className="gd-pill gray">Draft</span>;
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (onClick) onClick();
    }
  };

  return (
    <div
      className="project-row"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      aria-label={`Project: ${project.title}`}
    >
      <div className="project-info">
        <div className="project-title">{project.title}</div>
        <div className="project-meta">
          Created {formattedDate}
          {filledCount === 5 ? ' · All 5 steps complete' : ` · ${filledCount}/5 steps complete`}
        </div>
      </div>

      <div className="project-status-group">
        <div className="progress-bar" title={`${filledCount} of 5 steps completed`}>
          {STEPS.map((_, index) => (
            <div
              key={index}
              className={`progress-segment ${index < filledCount ? 'filled' : ''}`}
            />
          ))}
        </div>
        {renderStatusPill()}
      </div>
    </div>
  );
}
