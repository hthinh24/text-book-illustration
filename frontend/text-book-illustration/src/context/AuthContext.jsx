import React, { createContext, useContext, useState } from 'react';
import { postIdentity } from '../api/client';

export const AuthContext = createContext(null);
const STORAGE_KEY = 'gd_user';

export function AuthProvider({ children }) {
  // Synchronous state initialization from localStorage to prevent flash of unauthenticated state
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const login = async (name, email) => {
    const response = await postIdentity({ email, name });
    const userData = {
      userId: response.userId,
      name: response.name,
      email,
    };
    setUser(userData);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(userData));
    } catch (err) {
      console.warn('Failed to save user session to localStorage', err);
    }
    return userData;
  };

  const logout = () => {
    setUser(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (err) {
      console.warn('Failed to remove user session from localStorage', err);
    }
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
