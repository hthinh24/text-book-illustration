import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Button } from './Button';

export function Nav() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = () => {
    logout();
    navigate('/');
  };

  const initial = user?.name ? user.name.charAt(0).toUpperCase() : '?';

  return (
    <header className="gd-header">
      <Link to="/projects" className="gd-header-brand">
        <span className="gd-header-title">Book Illustration Studio</span>
        <span className="gd-header-badge">Projects</span>
      </Link>

      {user && (
        <div className="gd-user-menu">
          <div className="gd-avatar" title={user.name}>
            {initial}
          </div>
          <span className="gd-user-name">{user.name}</span>
          <Button variant="ghost" size="sm" onClick={handleSignOut}>
            Sign out
          </Button>
        </div>
      )}
    </header>
  );
}
