import axios from 'axios';

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  'https://vam-service-virtualisation-ftada5d6eqgzaphb.eastus-01.azurewebsites.net/api';

const api = axios.create({
  baseURL: BASE_URL
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  register: (data) => api.post('/auth/register', data),
  verify: () => api.get('/auth/verify-token'),
  logout: () => {
    localStorage.removeItem('token');
    return api.post('/auth/logout');
  }
};

export const registryApi = {
  getAll: (env, category) => api.get('/registry', { params: { env, category } }),
  getById: (id) => api.get(`/registry/${id}`),
  create: (data) => api.post('/registry', data),
  update: (id, data) => api.patch(`/registry/${id}`, data),
  delete: (id) => api.delete(`/registry/${id}`),
};

export const healthApi = {
  check: (data) => api.post('/health-check', data)
};

export const settingsApi = {
  getAll: () => api.get('/settings'),
  update: (key, value) => api.put(`/settings/${key}`, { value })
};

export const requestApi = {
  getAll: () => api.get('/recorded-requests'),
  delete: (id) => api.delete(`/requests/${id}`),
  deleteAll: () => api.delete('/requests')
};

export const stubApi = {
  getAll: () => api.get('/stubs'),
  create: (data) => api.post('/stubs', data),
  update: (id, data) => api.put(`/stubs/${id}`, data),
  delete: (id) => api.delete(`/stubs/${id}`),
  deleteAll: () => api.delete('/stubs'),
  toggle: (id) => api.post(`/stubs/${id}/toggle`),
  getVersions: (id) => api.get(`/stubs/${id}/versions`),
  createVersion: (id, data) => api.post(`/stubs/${id}/versions`, data),
  updateVersion: (stubId, versionId, data) => api.put(`/stubs/${stubId}/versions/${versionId}`, data),
  deleteVersion: (stubId, versionId) => api.delete(`/stubs/${stubId}/versions/${versionId}`),
  activateVersion: (stubId, versionId) => api.post(`/stubs/${stubId}/versions/${versionId}/activate`),
};

export default api;
