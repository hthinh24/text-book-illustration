import React from 'react';
import { Outlet } from 'react-router-dom';
import { Nav } from './Nav';

export function AppLayout({ children }) {
  return (
    <div className="app-container">
      <Nav />
      <main className="content-wrapper">
        {children || <Outlet />}
      </main>
    </div>
  );
}
