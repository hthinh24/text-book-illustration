import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getProjects } from '../api/client';
import { ProjectCard } from '../components/ProjectCard';
import { Button } from '../components/Button';

export function ProjectListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [projects, setProjects] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchProjects = useCallback(async () => {
    if (!user?.userId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await getProjects(user.userId);
      setProjects(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load projects. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }, [user?.userId]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Your projects</h1>
        <Button
          variant="primary"
          onClick={() => navigate('/projects/new')}
        >
          + New project
        </Button>
      </div>

      {isLoading && (
        <div style={{ textAlign: 'center', padding: 'var(--sp-6)', color: 'var(--fg-2)' }}>
          Loading projects…
        </div>
      )}

      {!isLoading && error && (
        <div className="gd-error-banner" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>{error}</span>
          <Button variant="secondary" size="sm" onClick={fetchProjects}>
            Retry
          </Button>
        </div>
      )}

      {!isLoading && !error && projects.length === 0 && (
        <div className="empty-state">
          <div className="empty-title">No projects yet</div>
          <div className="empty-desc">Create your first book illustration project to get started.</div>
          <Button variant="primary" onClick={() => navigate('/projects/new')}>
            + New project
          </Button>
        </div>
      )}

      {!isLoading && !error && projects.length > 0 && (
        <div className="project-list">
          {projects.map((project) => {
            const targetId = project.projectId || project.id;
            return (
              <ProjectCard
                key={targetId}
                project={project}
                onClick={() => navigate(`/projects/${targetId}`)}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
