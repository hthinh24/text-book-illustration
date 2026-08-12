import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Field } from '../components/Field';
import { Button } from '../components/Button';

export function AuthPage() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const validate = () => {
    const errors = {};
    if (!name.trim()) {
      errors.name = 'Full name is required';
    }
    if (!email.trim()) {
      errors.email = 'Email is required';
    } else if (!email.includes('@')) {
      errors.email = 'Please enter a valid email address';
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
      await login(name.trim(), email.trim());
      navigate('/projects');
    } catch (err) {
      setApiError(err.message || 'Failed to sign in. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand-header">
          <h1 className="brand-title">Book Illustration Studio</h1>
          <p className="brand-subtitle">
            Enter your details to start or resume an illustration project.
          </p>
        </div>

        {apiError && <div className="gd-error-banner" role="alert">{apiError}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <Field
            id="auth-name"
            label="Full name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Mira Hassan"
            error={fieldErrors.name}
            disabled={isLoading}
          />

          <Field
            id="auth-email"
            label="Email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="mira@example.com"
            error={fieldErrors.email}
            disabled={isLoading}
          />

          <Button
            type="submit"
            variant="primary"
            disabled={isLoading}
            style={{ marginTop: 'var(--sp-4)' }}
          >
            {isLoading ? 'Signing in…' : 'Continue →'}
          </Button>
        </form>

        <p className="auth-note">
          No password — this is a lightweight identity check. Using an email that already has projects resumes them exactly where you left off.
        </p>
      </div>
    </div>
  );
}
