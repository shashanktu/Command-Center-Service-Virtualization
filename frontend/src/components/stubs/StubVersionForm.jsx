import React, { useState } from 'react';
import { stubApi } from '../../services/registryApi';
import ReactDOM from 'react-dom';

const StubVersionForm = ({ stubId, version, onClose, onSave }) => {
  const [formData, setFormData] = useState(version || {
    version: '',
    versionTag: '',
    responseStatus: 200,
    responseBody: '',
    responseHeaders: '{\n  "Content-Type": "application/json"\n}'
  });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      // Validate headers JSON
      try {
        if (formData.responseHeaders) JSON.parse(formData.responseHeaders);
      } catch (err) {
        alert("Invalid JSON in Response Headers");
        setSaving(false);
        return;
      }

      if (version) {
        await stubApi.updateVersion(stubId, version.versionId, formData);
      } else {
        await stubApi.createVersion(stubId, formData);
      }
      onSave();
    } catch (err) {
      console.error(err);
      const responseMessage = typeof err.response?.data === 'string' ? err.response.data : '';
      const message = responseMessage || (err.response?.status === 403
        ? 'You do not have permission to create or update versions.'
        : 'Failed to save version. Check the backend response and try again.');
      alert(message);
    } finally {
      setSaving(false);
    }
  };

  return ReactDOM.createPortal(
    <div className="modal-overlay">
      <div className="modal-content fade-in" style={{ maxWidth: '600px' }}>
        <div className="modal-header">
          <h3>{version ? 'Edit Version' : 'Create New Version'}</h3>
          <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', fontSize: '1.5rem', cursor: 'pointer' }}>&times;</button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body grid-2">
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Version</label>
              <input
                className="form-control"
                placeholder="Leave blank to assign the next version automatically"
                value={formData.version || ''}
                onChange={e => setFormData({ ...formData, version: e.target.value })}
                readOnly={Boolean(version)}
              />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Version Tag / Description</label>
              <input className="form-control" placeholder="Error Scenario" value={formData.versionTag} onChange={e => setFormData({...formData, versionTag: e.target.value})} />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Response Status</label>
              <input type="number" required className="form-control" value={formData.responseStatus} onChange={e => setFormData({...formData, responseStatus: parseInt(e.target.value)})} />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Response Body</label>
              <textarea className="form-control" value={formData.responseBody || ''} onChange={e => setFormData({...formData, responseBody: e.target.value})} rows="5" />
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Response Headers (JSON)</label>
              <textarea className="form-control" value={formData.responseHeaders || ''} onChange={e => setFormData({...formData, responseHeaders: e.target.value})} rows="3" />
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>Cancel</button>
            <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving...' : (version ? 'Save Changes' : 'Create Version')}</button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default StubVersionForm;
