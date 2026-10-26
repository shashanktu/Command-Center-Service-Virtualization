import React, { useState, useEffect } from 'react';
import { stubApi } from '../../services/registryApi';
import ReactDOM from 'react-dom';
import { parseCurl } from '../../utils/curlParser';

const StubForm = ({ stub, onClose, onSave }) => {
  const [formData, setFormData] = useState({
    name: '', method: 'GET', endpoint: '', baseUrl: '', category: 'Other', environment: 'Dev', description: '',
    requestMatcher: '', responseStatus: 200, responseBody: '', responseHeaders: '{\n  "Content-Type": "application/json"\n}', delay: 0, enabled: true
  });
  const [curlInput, setCurlInput] = useState('');

  useEffect(() => {
    if (stub) setFormData(stub);
  }, [stub]);

  const handleCurlImport = () => {
    try {
      const parsed = parseCurl(curlInput);
      const baseUrl = parsed.url ? new URL(parsed.url).origin : '';
      setFormData(prev => ({
        ...prev,
        method: parsed.method || prev.method,
        endpoint: parsed.path || parsed.url || prev.endpoint,
        baseUrl: baseUrl || prev.baseUrl,
        requestMatcher: parsed.body || prev.requestMatcher,
        responseHeaders: Object.keys(parsed.headers).length > 0
          ? JSON.stringify(parsed.headers, null, 2)
          : prev.responseHeaders
      }));
      setCurlInput('');
    } catch (err) {
      alert("Could not parse cURL command");
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (stub && stub.id) {
        await stubApi.update(stub.id, formData);
      } else {
        await stubApi.create(formData);
      }
      onSave();
    } catch (err) {
      console.error(err);
      alert("Failed to save stub");
    }
  };

  return ReactDOM.createPortal(
    <div className="modal-overlay">
      <div className="modal-content fade-in" style={{ maxWidth: '800px' }}>
        <div className="modal-header">
          <h3>{stub ? 'Edit Stub' : 'Create Stub'}</h3>
          <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', fontSize: '1.5rem', cursor: 'pointer' }}>&times;</button>
        </div>
        <div style={{ padding: '24px 24px 0 24px' }}>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
            <input className="form-control" placeholder="Import from cURL (Paste command here)" value={curlInput} onChange={e => setCurlInput(e.target.value)} />
            <button type="button" className="btn-secondary" style={{ padding: '8px 16px' }} onClick={handleCurlImport}>Parse</button>
          </div>
          <hr style={{ borderColor: 'var(--border-color)', margin: '16px 0' }} />
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body grid-2">
            <div className="form-group">
              <label>Name</label>
              <input required className="form-control" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Base URL</label>
              <input className="form-control" value={formData.baseUrl} onChange={e => setFormData({...formData, baseUrl: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Endpoint</label>
              <input required className="form-control" value={formData.endpoint} onChange={e => setFormData({...formData, endpoint: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Method</label>
              <select className="form-control" value={formData.method} onChange={e => setFormData({...formData, method: e.target.value})}>
                <option>ANY</option><option>GET</option><option>POST</option><option>PUT</option><option>DELETE</option><option>PATCH</option>
              </select>
            </div>
            <div className="form-group">
              <label>Category</label>
              <select className="form-control" value={formData.category} onChange={e => setFormData({...formData, category: e.target.value})}>
                <option>Other</option><option>Claims</option><option>SBI</option><option>GW</option>
              </select>
            </div>
            <div className="form-group">
              <label>Environment</label>
              <input required className="form-control" value={formData.environment} onChange={e => setFormData({...formData, environment: e.target.value})} />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Description</label>
              <textarea className="form-control" value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} rows="2" />
            </div>
            {['POST', 'PUT', 'PATCH'].includes(formData.method) && (
              <div className="form-group" style={{ gridColumn: 'span 2' }}>
                <label>Expected Request Body (Optional matcher)</label>
                <textarea className="form-control" value={formData.requestMatcher || ''} onChange={e => setFormData({...formData, requestMatcher: e.target.value})} rows="3" placeholder='e.g. {"status": "active"}' />
              </div>
            )}
            <div className="form-group">
              <label>Response Status</label>
              <input type="number" required className="form-control" value={formData.responseStatus} onChange={e => setFormData({...formData, responseStatus: parseInt(e.target.value)})} />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Response Body</label>
              <textarea className="form-control" value={formData.responseBody || ''} onChange={e => setFormData({...formData, responseBody: e.target.value})} rows="4" />
            </div>
            <div className="form-group">
              <label>Response Headers (JSON)</label>
              <textarea className="form-control" value={formData.responseHeaders || '{\n  "Content-Type": "application/json"\n}'} onChange={e => setFormData({...formData, responseHeaders: e.target.value})} rows="2" />
            </div>
            <div className="form-group">
              <label>Delay (ms)</label>
              <input type="number" className="form-control" value={formData.delay} onChange={e => setFormData({...formData, delay: parseInt(e.target.value)})} />
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn">Save Stub</button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default StubForm;
