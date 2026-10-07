import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL, stubApi } from '../../services/registryApi';
import StubForm from './StubForm';
import StubVersionForm from './StubVersionForm';

const StubsTab = () => {
  const [stubs, setStubs] = useState([]);
  const [selectedStub, setSelectedStub] = useState(null);
  const [versions, setVersions] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [showVersionForm, setShowVersionForm] = useState(false);
  const [editStub, setEditStub] = useState(null);
  const [editVersion, setEditVersion] = useState(null);
  const [filter, setFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('All');
  const navigate = useNavigate();

  const loadStubs = async () => {
    try {
      const res = await stubApi.getAll();
      setStubs(res.data);
      setSelectedStub(prev => prev ? (res.data.find(s => s.id === prev.id) || prev) : null);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    loadStubs();
    const interval = setInterval(loadStubs, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (selectedStub) {
      loadVersions(selectedStub.id);
    } else {
      setVersions([]);
    }
  }, [selectedStub]);

  const loadVersions = async (stubId) => {
    try {
      const res = await stubApi.getVersions(stubId);
      setVersions(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const handleToggle = async (id, e) => {
    e.stopPropagation();
    try {
      await stubApi.toggle(id);
      loadStubs();
    } catch (err) {
      console.error(err);
    }
  };

  const handleActivateVersion = async (versionId) => {
    try {
      await stubApi.activateVersion(selectedStub.id, versionId);
      loadStubs();
      loadVersions(selectedStub.id);
    } catch (err) {
      console.error(err);
    }
  };

  const handleClearAll = async () => {
    if (window.confirm('Are you sure you want to delete all stubs? This cannot be undone.')) {
      try {
        await stubApi.deleteAll();
        setStubs([]);
        setSelectedStub(null);
        setVersions([]);
      } catch (err) {
        console.error(err);
        alert('Failed to clear stubs');
      }
    }
  };

  const filteredStubs = stubs.filter(s => {
    if (categoryFilter !== 'All' && s.category !== categoryFilter) return false;
    if (filter) {
      const f = filter.toLowerCase();
      return (s.name && s.name.toLowerCase().includes(f)) ||
             (s.endpoint && s.endpoint.toLowerCase().includes(f)) ||
             (s.baseUrl && s.baseUrl.toLowerCase().includes(f));
    }
    return true;
  });
  const mockUrl = selectedStub
    ? `${API_BASE_URL.replace(/\/+$/, '')}/mock/${encodeURIComponent(selectedStub.id)}`
    : '';

  return (
    <div className="fade-in stubs-layout">
      {/* Left Panel */}
      <div className="glass-panel stubs-panel" style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2>Stubs ({filteredStubs.length})</h2>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="btn" onClick={() => { setEditStub(null); setShowForm(true); }}>+ New Stub</button>
            <button className="btn-danger" style={{ padding: '6px 12px', fontSize: '0.85rem', borderRadius: '6px', border: '1px solid rgba(239, 68, 68, 0.2)', background: 'rgba(239, 68, 68, 0.15)', color: '#fca5a5' }} onClick={handleClearAll}>Clear All</button>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
          <input className="form-control" placeholder="Search stubs..." value={filter} onChange={e => setFilter(e.target.value)} />
          <select className="form-control" style={{ width: '120px' }} value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)}>
            <option>All</option><option>Claims</option><option>SBI</option><option>GW</option><option>Other</option>
          </select>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {filteredStubs.map(stub => (
            <div 
              key={stub.id} 
              onClick={() => setSelectedStub(stub)}
              style={{
                padding: '12px', background: selectedStub?.id === stub.id ? 'rgba(0,0,0,0.05)' : 'transparent',
                border: '1px solid var(--border-color)', borderRadius: '8px', cursor: 'pointer',
                borderColor: selectedStub?.id === stub.id ? 'var(--primary)' : 'var(--border-color)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className={`badge badge-${stub.method === 'GET' ? 'info' : 'success'}`}>{stub.method}</span>
                  <span style={{ fontWeight: 500 }}>{stub.name}</span>
                </div>
                <button 
                  onClick={(e) => handleToggle(stub.id, e)}
                  className={`badge ${stub.enabled ? 'badge-success' : 'badge-warning'}`}
                  style={{ border: 'none', cursor: 'pointer' }}
                >
                  {stub.enabled ? 'Enabled' : 'Disabled'}
                </button>
              </div>
              {stub.baseUrl && <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Base URL: {stub.baseUrl}</div>}
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Endpoint: {stub.endpoint}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Right Panel */}
      <div className="glass-panel stubs-panel" style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {selectedStub ? (
          <div className="fade-in stubs-detail">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
              <div>
                <h2>{selectedStub.name}</h2>
                {selectedStub.baseUrl && <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>Base URL: {selectedStub.baseUrl}</div>}
                <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>Endpoint: {selectedStub.method} {selectedStub.endpoint}</div>
                {selectedStub.environment && <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Env: {selectedStub.environment}</div>}
                {selectedStub.description && <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Desc: {selectedStub.description}</div>}
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '12px' }}>
                  <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontWeight: 600 }}>Mock URL:</div>
                  <code style={{ fontSize: '0.75rem', background: 'rgba(0,0,0,0.1)', padding: '4px 8px', borderRadius: '4px', wordBreak: 'break-all' }}>
                    {mockUrl}
                  </code>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button 
                  className="btn-secondary" 
                  title="Copy Mock URL"
                  style={{ padding: '6px 12px', fontSize: '0.85rem', borderRadius: '6px', background: 'rgba(16, 185, 129, 0.1)', color: '#34d399', border: '1px solid rgba(16, 185, 129, 0.2)' }} 
                  onClick={() => navigator.clipboard.writeText(mockUrl)}>
                  🔗 Copy
                </button>
                <button 
                  className="btn-secondary" 
                  title="Test Stub in API Tester"
                  style={{ padding: '6px 12px', fontSize: '0.85rem', borderRadius: '6px', background: 'rgba(59, 130, 246, 0.1)', color: '#60a5fa', border: '1px solid rgba(59, 130, 246, 0.2)' }} 
                  onClick={() => navigate('/service-virtualization/api', { state: { request: { endpoint: selectedStub.endpoint, method: selectedStub.method, category: selectedStub.category, baseUrl: selectedStub.baseUrl, body: selectedStub.requestMatcher } } })}>
                  🧪 Test
                </button>
                <button 
                  className="btn-secondary" 
                  title="Edit Stub"
                  style={{ padding: '6px 12px', fontSize: '0.85rem', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.1)' }} 
                  onClick={() => { setEditStub(selectedStub); setShowForm(true); }}>
                  ✏️ Edit
                </button>
                <button 
                  className="btn-danger" 
                  title="Delete Stub"
                  style={{ padding: '6px 12px', fontSize: '0.85rem', borderRadius: '6px', border: '1px solid rgba(239, 68, 68, 0.2)', background: 'rgba(239, 68, 68, 0.15)', color: '#fca5a5' }} 
                  onClick={async () => {
                    if (window.confirm('Delete stub?')) {
                      await stubApi.delete(selectedStub.id);
                      setSelectedStub(null);
                      loadStubs();
                    }
                  }}>
                  🗑️ Delete
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', marginTop: '24px' }}>
              <h3 style={{ margin: 0 }}>Versions</h3>
              <button className="btn" style={{ padding: '6px 12px', fontSize: '0.85rem' }} onClick={() => { setEditVersion(null); setShowVersionForm(true); }}>+ New Version</button>
            </div>
            <div className="stubs-version-list">
              {versions.map(v => (
                <div key={v.versionId} style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '16px', background: v.active ? 'rgba(139, 92, 246, 0.05)' : 'transparent', borderColor: v.active ? 'var(--primary)' : 'var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <span className="badge badge-info">{v.version}</span>
                      <span className={`badge badge-${v.responseStatus >= 200 && v.responseStatus < 300 ? 'success' : 'danger'}`}>Status {v.responseStatus}</span>
                    </div>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <button className="btn-secondary" title="Edit Version" style={{ padding: '4px 8px', fontSize: '0.8rem', border: '1px solid rgba(0,0,0,0.1)' }} onClick={() => { setEditVersion(v); setShowVersionForm(true); }}>✏️</button>
                      <button className="btn-danger" title="Delete Version" disabled={v.active} style={{ padding: '4px 8px', fontSize: '0.8rem', border: '1px solid rgba(239, 68, 68, 0.2)', background: 'rgba(239, 68, 68, 0.15)', color: '#fca5a5', opacity: v.active ? 0.5 : 1, cursor: v.active ? 'not-allowed' : 'pointer' }} onClick={async () => {
                        if (window.confirm('Delete version?')) {
                          try {
                            await stubApi.deleteVersion(selectedStub.id, v.versionId);
                            loadVersions(selectedStub.id);
                          } catch (err) {
                            console.error(err);
                            alert("Failed to delete version");
                          }
                        }
                      }}>🗑️</button>
                      {v.active ? (
                        <button className="btn" style={{ background: 'var(--success)', padding: '4px 12px', fontSize: '0.8rem', cursor: 'default', opacity: 1 }} disabled>Active</button>
                      ) : (
                        <button className="btn-secondary" style={{ padding: '4px 12px', fontSize: '0.8rem' }} onClick={() => handleActivateVersion(v.versionId)}>Activate</button>
                      )}
                    </div>
                  </div>
                  {v.versionTag && <div style={{ marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-muted)' }}>Tag: {v.versionTag}</div>}
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '4px' }}>Response Body:</div>
                  <pre style={{ maxHeight: '100px', margin: 0 }}>{v.responseBody || 'No content'}</pre>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
            Select a stub to view details and versions.
          </div>
        )}
      </div>

      {showForm && (
        <StubForm 
          stub={editStub} 
          onClose={() => setShowForm(false)} 
          onSave={() => { setShowForm(false); loadStubs(); if(selectedStub) loadVersions(selectedStub.id); }} 
        />
      )}

      {showVersionForm && selectedStub && (
        <StubVersionForm
          stubId={selectedStub.id}
          version={editVersion}
          onClose={() => setShowVersionForm(false)}
          onSave={() => { setShowVersionForm(false); loadVersions(selectedStub.id); }}
        />
      )}
    </div>
  );
};

export default StubsTab;
