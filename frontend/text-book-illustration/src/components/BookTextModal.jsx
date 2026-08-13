import React, { useState, useEffect } from 'react';
import { getBookText } from '../api/client';
import { Button } from './Button';

export function BookTextModal({ isOpen, onClose, projectId, title }) {
  const [text, setText] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (isOpen && projectId) {
      setIsLoading(true);
      setError(null);
      getBookText(projectId)
        .then((content) => setText(content))
        .catch((err) => setError(err.message || 'Failed to load book text'))
        .finally(() => setIsLoading(false));
    }
  }, [isOpen, projectId]);

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">Book text — {title}</h3>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <div className="modal-body">
          {isLoading && <div style={{ color: 'var(--fg-2)', textAlign: 'center', padding: 'var(--sp-5)' }}>Loading book text…</div>}
          {!isLoading && error && <div className="gd-error-banner">{error}</div>}
          {!isLoading && !error && <pre className="book-text-content">{text}</pre>}
        </div>

        <div className="modal-footer">
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </div>
  );
}
