import React from 'react';

export function ChapterCard({ chapter }) {
  const { title, illustrationPrompt, illustrationImagePath, status } = chapter;

  const renderMedia = () => {
    if (status === 'DONE' && illustrationImagePath) {
      return (
        <img
          src={illustrationImagePath}
          alt={title || 'Chapter Illustration'}
          className="entity-card-image chapter"
          onError={(e) => {
            e.currentTarget.style.display = 'none';
            if (e.currentTarget.nextSibling) {
              e.currentTarget.nextSibling.style.display = 'flex';
            }
          }}
        />
      );
    }

    if (status === 'RUNNING') {
      return (
        <div className="entity-card-placeholder chapter">
          <div className="spinner" />
          <span>Generating illustration…</span>
        </div>
      );
    }

    if (status === 'FAIL') {
      return (
        <div className="entity-card-placeholder chapter error">
          <span>Failed to generate illustration</span>
        </div>
      );
    }

    return (
      <div className="entity-card-placeholder chapter">
        <span className="entity-badge">ILLUSTRATION PENDING</span>
      </div>
    );
  };

  return (
    <div className="entity-card chapter">
      <div className="entity-card-media chapter">
        {renderMedia()}
        {status === 'DONE' && illustrationImagePath && (
          <div className="entity-card-placeholder chapter" style={{ display: 'none' }}>
            <span className="entity-badge">IMAGE NOT AVAILABLE</span>
          </div>
        )}
      </div>
      <div className="entity-card-body">
        <div className="entity-card-title">{title || 'Chapter Illustration'}</div>
        {illustrationPrompt && <div className="entity-card-desc">{illustrationPrompt}</div>}
      </div>
    </div>
  );
}
