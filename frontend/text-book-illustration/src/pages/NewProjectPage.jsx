import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { initProject } from '../api/client';
import { Field } from '../components/Field';
import { Button } from '../components/Button';

export function NewProjectPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [activeTab, setActiveTab] = useState('text'); // 'text' | 'file'
  const [text, setText] = useState('');
  const [file, setFile] = useState(null);

  const [fieldErrors, setFieldErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setFieldErrors({});
    if (tab === 'text') {
      setFile(null);
    } else {
      setText('');
    }
  };

  const validate = () => {
    const errors = {};
    if (!title.trim()) {
      errors.title = 'Project title is required';
    }

    if (activeTab === 'text' && !text.trim()) {
      errors.text = 'Book text is required';
    }

    if (activeTab === 'file' && !file) {
      errors.file = 'Please select a .txt file';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError(null);

    if (!validate()) {
      return;
    }

    setIsLoading(true);
    try {
      const response = await initProject({
        userId: user.userId,
        title: title.trim(),
        text: activeTab === 'text' ? text.trim() : undefined,
        file: activeTab === 'file' ? file : undefined,
      });

      const projectId = response.projectId || response.id;
      navigate(`/projects/${projectId}`);
    } catch (err) {
      setApiError(err.message || 'Failed to create project. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '640px', margin: '0 auto' }}>
      <Link to="/projects" className="back-link">
        ← Back to projects
      </Link>

      <h1 className="page-title" style={{ marginBottom: 'var(--sp-6)' }}>
        New project
      </h1>

      {apiError && <div className="gd-error-banner" role="alert">{apiError}</div>}

      <div className="gd-card">
        <form onSubmit={handleSubmit} noValidate>
          <Field
            id="project-title"
            label="Project title"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Alice's Adventures in Wonderland"
            error={fieldErrors.title}
            disabled={isLoading}
          />

          <div style={{ marginBottom: 'var(--sp-4)' }}>
            <label style={{ display: 'block', fontSize: '12px', fontWeight: 'var(--fw-semi)', color: 'var(--grad-ink)', marginBottom: '6px' }}>
              Book content <span className="req">*</span>
            </label>

            <div className="tab-group" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'text'}
                className={`tab-btn ${activeTab === 'text' ? 'active' : ''}`}
                onClick={() => handleTabChange('text')}
                disabled={isLoading}
              >
                Paste text
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'file'}
                className={`tab-btn ${activeTab === 'file' ? 'active' : ''}`}
                onClick={() => handleTabChange('file')}
                disabled={isLoading}
              >
                Upload .txt file
              </button>
            </div>

            {activeTab === 'text' ? (
              <Field
                id="book-text"
                multiline
                rows={8}
                value={text}
                onChange={(e) => {
                  setText(e.target.value);
                  if (fieldErrors.text) setFieldErrors((prev) => ({ ...prev, text: null }));
                }}
                placeholder="Paste the book text here..."
                error={fieldErrors.text}
                disabled={isLoading}
              />
            ) : (
              <div>
                <div className="file-upload-zone">
                  <label htmlFor="book-file" className="file-label" style={{ width: '100%', padding: '0px' }}>
                    {file ? 'Change file' : 'Choose a .txt file'}
                  </label>
                  <input
                    id="book-file"
                    type="file"
                    accept=".txt"
                    disabled={isLoading}
                    onChange={(e) => {
                      const selected = e.target.files[0] || null;
                      setFile(selected);
                      if (fieldErrors.file) setFieldErrors((prev) => ({ ...prev, file: null }));
                    }}
                  />
                  {file && <div className="file-name">Selected: {file.name}</div>}
                </div>
                {fieldErrors.file && <div className="gd-field-error" style={{ marginTop: 'var(--sp-1)' }}>{fieldErrors.file}</div>}
              </div>
            )}
          </div>

          <Button
            type="submit"
            variant="primary"
            disabled={isLoading}
            style={{ width: '100%', marginTop: 'var(--sp-4)' }}
          >
            {isLoading ? 'Creating…' : 'Create Project →'}
          </Button>
        </form>
      </div>
    </div>
  );
}
