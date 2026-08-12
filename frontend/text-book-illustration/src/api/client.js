const BASE_URL = '/api/v1';

/**
 * Shared fetch request wrapper for the Book Illustration Studio backend API.
 * @param {string} path - Endpoint path (e.g. '/identity')
 * @param {RequestInit} [options] - Fetch options
 * @returns {Promise<any>} Parsed JSON response
 */
export async function request(path, options = {}) {
  const normalizedPath = path.startsWith('/api/v1')
    ? path
    : `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;

  const headers = { ...options.headers };

  // Set default JSON Content-Type unless body is FormData
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(normalizedPath, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorData;
    try {
      errorData = await response.json();
    } catch {
      // Failed to parse JSON error body
    }
    const errorMessage = errorData?.message || response.statusText || `Request failed with status ${response.status}`;
    const error = new Error(errorMessage);
    error.status = response.status;
    error.data = errorData;
    throw error;
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return null;
  }

  return await response.json();
}

export async function postIdentity({ email, name }) {
  return await request('/identity', {
    method: 'POST',
    body: JSON.stringify({ email, name }),
  });
}
