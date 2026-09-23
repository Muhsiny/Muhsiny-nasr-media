const API = (import.meta.env.VITE_API_URL || 'https://nasr-media.com14.workers.dev').replace(/\/$/, '');

async function request(path, options = {}) {
  const headers = { accept: 'application/json', ...(options.headers || {}) };
  if (options.body && !headers['content-type']) headers['content-type'] = 'application/json';

  const res = await fetch(`${API}${path}`, { ...options, headers });
  const type = res.headers.get('content-type') || '';
  const data = type.includes('application/json') ? await res.json().catch(() => ({})) : { error: await res.text().catch(() => '') };

  if (!res.ok) {
    const error = new Error(data.error || `request_${res.status}`);
    error.status = res.status;
    throw error;
  }
  return data;
}

const auth = (token) => token ? { authorization: `Bearer ${token}` } : {};

export const newsroom = {
  health: () => request('/api/health'),
  agents: () => request('/api/agents'),
  articles: (params = '') => request(`/api/articles${params}`),
  article: (id) => request(`/api/articles/${encodeURIComponent(id)}`),
  reviewQueue: (token) => request('/api/articles?status=review', { headers: auth(token) }),
  create: (payload, token) => request('/api/articles', { method: 'POST', headers: auth(token), body: JSON.stringify(payload) }),
  update: (id, payload, token) => request(`/api/articles/${encodeURIComponent(id)}`, { method: 'PATCH', headers: auth(token), body: JSON.stringify(payload) }),
  process: (id, token) => request(`/api/articles/${encodeURIComponent(id)}/process`, { method: 'POST', headers: auth(token) }),
  approve: (payload, token) => request('/api/approvals', { method: 'POST', headers: auth(token), body: JSON.stringify(payload) }),
  pipeline: (articleId, token) => request(`/api/pipeline?articleId=${encodeURIComponent(articleId)}`, { headers: auth(token) })
};
