import React from 'react';

export function CharacterCard({ character }) {
  const { name, imagePrompt, portraitImagePath, status } = character;

  const renderMedia = () => {
    if (status === 'DONE' && portraitImagePath) {
      return (
        <img
          src={portraitImagePath}
          alt={name}
          className="entity-card-image"
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
        <div className="entity-card-placeholder">
          <div className="spinner" />
          <span>Generating portrait…</span>
        </div>
      );
    }

    if (status === 'FAIL') {
      return (
        <div className="entity-card-placeholder error">
          <span>Failed to generate portrait</span>
        </div>
      );
    }

    return (
      <div className="entity-card-placeholder">
        <span className="entity-badge">PORTRAIT PENDING</span>
      </div>
    );
  };

  return (
    <div className="entity-card">
      <div className="entity-card-media">
        {renderMedia()}
        {status === 'DONE' && portraitImagePath && (
          <div className="entity-card-placeholder" style={{ display: 'none' }}>
            <span className="entity-badge">IMAGE NOT AVAILABLE</span>
          </div>
        )}
      </div>
      <div className="entity-card-body">
        <div className="entity-card-title">{name}</div>
        {imagePrompt && <div className="entity-card-desc">{imagePrompt}</div>}
      </div>
    </div>
  );
}
