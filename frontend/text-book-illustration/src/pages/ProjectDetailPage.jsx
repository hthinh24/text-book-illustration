import React from 'react';
import { useParams } from 'react-router-dom';

export function ProjectDetailPage() {
  const { id } = useParams();

  return (
    <div>
      <h2>Project Detail ({id})</h2>
      <p className="meta">Project detail pipeline view will be implemented in subsequent tasks.</p>
    </div>
  );
}
