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

export async function getProjects(userId) {
  return await request(`/projects?userId=${encodeURIComponent(userId)}`, {
    method: 'GET',
  });
}

export async function initProject({ userId, title, text, file }) {
  const formData = new FormData();
  formData.append('userId', userId);
  formData.append('title', title);
  if (file) {
    formData.append('file', file);
  } else if (text) {
    formData.append('text', text);
  }

  return await request('/projects/init-project', {
    method: 'POST',
    body: formData,
  });
}

/**
 * Fetch full detail envelope of a project.
 * @param {string} projectId
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function getProjectDetail(projectId) {
  return await request(`/projects/${projectId}`, {
    method: 'GET',
  });
}

/**
 * Fetch raw book text content as text.
 * @param {string} projectId
 * @returns {Promise<string>} Raw text content
 */
export async function getBookText(projectId) {
  const response = await fetch(`/api/v1/projects/${projectId}/files/book-text`);
  if (!response.ok) {
    throw new Error('Failed to load book text');
  }
  return await response.text();
}

/**
 * Trigger Step 1: Style
 * @param {string} projectId
 * @param {string} [style]
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function triggerStyle(projectId, style) {
  return await request(`/projects/${projectId}/style`, {
    method: 'POST',
    body: JSON.stringify(style && style.trim() ? { style: style.trim() } : {}),
  });
}

/**
 * Trigger Step 2: Character
 * @param {string} projectId
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function triggerCharacter(projectId) {
  return await request(`/projects/${projectId}/character`, {
    method: 'POST',
  });
}

/**
 * Trigger Step 3: Portraits
 * @param {string} projectId
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function triggerPortraits(projectId) {
  return await request(`/projects/${projectId}/portraits`, {
    method: 'POST',
  });
}

/**
 * Trigger Step 4: Chapters
 * @param {string} projectId
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function triggerChapters(projectId) {
  return await request(`/projects/${projectId}/chapters`, {
    method: 'POST',
  });
}

/**
 * Trigger Step 5: Illustrations
 * @param {string} projectId
 * @returns {Promise<Object>} ProjectDetailResponse
 */
export async function triggerIllustrations(projectId) {
  return await request(`/projects/${projectId}/illustrations`, {
    method: 'POST',
  });
}

/**
 * Retry failed/stuck step
 * @param {string} projectId
 * @returns {Promise<{ project: Object, retryReason: string }>} RetryResponse
 */
export async function retryStep(projectId) {
  return await request(`/projects/${projectId}/retry`, {
    method: 'POST',
  });
}
