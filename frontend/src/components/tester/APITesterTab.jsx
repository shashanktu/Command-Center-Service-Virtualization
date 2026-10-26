import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import api, { settingsApi } from '../../services/registryApi';
import { parseCurl } from '../../utils/curlParser';

const APITesterTab = () => {
  const [baseUrl, setBaseUrl] = useState('');
  const [method, setMethod] = useState('GET');
  const [category, setCategory] = useState('Other');
  const [endpoint, setEndpoint] = useState('/');
  const [body, setBody] = useState('');
  const [headers, setHeaders] = useState('{\n  "Content-Type": "application/json"\n}');
  const [response, setResponse] = useState(null);
  const [loading, setLoading] = useState(false);
  const [curlInput, setCurlInput] = useState('');
  const [showNoStubModal, setShowNoStubModal] = useState(false);
  const [pendingRequestData, setPendingRequestData] = useState(null);

  const location = useLocation();

  const handleCurlImport = () => {
    try {
      const parsed = parseCurl(curlInput);
      if (parsed.method) setMethod(parsed.method);
      if (parsed.path || parsed.url) setEndpoint(parsed.path || parsed.url);
      if (parsed.host) {
        const protocol = parsed.url.startsWith('https') ? 'https://' : 'http://';
        setBaseUrl(protocol + parsed.host);
      }
      if (Object.keys(parsed.headers).length > 0) {
        setHeaders(JSON.stringify(parsed.headers, null, 2));
      }
      if (parsed.body) {
        setBody(parsed.body);
      }
      setCurlInput('');
    } catch (err) {
      alert("Could not parse cURL command");
    }
  };

  useEffect(() => {
    if (location.state?.request) {
      const req = location.state.request;
      
      let targetBaseUrl = req.baseUrl || req.url || '';
      let targetEndpoint = req.endpoint || '/';

      try {
        if (targetBaseUrl.includes('http')) {
          const urlObj = new URL(targetBaseUrl);
          targetBaseUrl = urlObj.origin;
        }
        if (req.url && req.url.includes('http')) {
          const fullUrlObj = new URL(req.url);
          targetEndpoint = fullUrlObj.pathname + fullUrlObj.search;
        }
      } catch (e) {
        // Leave as is if parsing fails
      }

      setBaseUrl(targetBaseUrl);
      setMethod(req.method || 'GET');
      setCategory(req.category || 'Other');
      setEndpoint(targetEndpoint);
      setBody(req.healthCheckBody || req.body || '');
      
      const h = req.healthCheckHeaders || req.headers || req.responseHeaders;
      if (h && h !== '{}') {
        try {
          JSON.parse(h);
          setHeaders(h);
        } catch (e) {
          // leave default
        }
      }
    }
  }, [location.state]);

  useEffect(() => {
    settingsApi.getAll().then(res => {
      const defaultTargetUrl = res.data.targetUrl || '';
      setBaseUrl(prev => prev || defaultTargetUrl);
    }).catch(e => {
      console.error("Failed to fetch settings", e);
    });
  }, []);

  const handleSend = async (e) => {
    e.preventDefault();
    const targetBaseUrl = baseUrl.trim();
    const targetEndpoint = endpoint.trim();
    if (!targetBaseUrl || !targetEndpoint) {
      alert("Base URL and endpoint are required");
      return;
    }

    setLoading(true);
    setResponse(null);
    
    let parsedHeaders = {};
    try {
      parsedHeaders = JSON.parse(headers || '{}');
    } catch (err) {
      alert("Invalid JSON in headers");
      setLoading(false);
      return;
    }

    parsedHeaders['X-Target-Host'] = targetBaseUrl;
    parsedHeaders['X-Api-Category'] = category;

    let requestData = {
      url: '/proxy-request',
      method,
      headers: parsedHeaders,
      params: { endpoint: targetEndpoint },
      data: method !== 'GET' ? body : undefined
    };

    try {
      let start = Date.now();
      try {
        const res = await api.request(requestData);
        const time = Date.now() - start;
        setResponse({
          status: res.status,
          time,
          source: res.headers['x-response-source'] || 'live-api',
          data: res.data,
          headers: res.headers
        });
      } catch (err) {
        if (err.response && err.response.headers['x-no-stub-found'] === 'true') {
          setPendingRequestData(requestData);
          setShowNoStubModal(true);
          return;
        }
        
        setResponse({
          status: err.response?.status || 'Network Error',
          error: err.message,
          data: err.response?.data
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fade-in">
      <h1>API Tester</h1>
      <div className="grid-2" style={{ alignItems: 'start' }}>
        <div className="glass-panel">
          <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
            <input className="form-control" placeholder="Import from cURL (Paste command here)" value={curlInput} onChange={e => setCurlInput(e.target.value)} />
            <button type="button" className="btn-secondary" style={{ padding: '8px 16px' }} onClick={handleCurlImport}>Parse</button>
          </div>
          <form onSubmit={handleSend}>
            <div className="form-group">
              <label>Base URL (Upstream Domain)</label>
              <input className="form-control" placeholder="https://api.example.com" value={baseUrl} onChange={e => setBaseUrl(e.target.value)} required />
            </div>
            <div className="form-group">
              <label>Endpoint Path</label>
              <input className="form-control" placeholder="/v1/users" value={endpoint} onChange={e => setEndpoint(e.target.value)} required />
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>HTTP Method</label>
                <select className="form-control" value={method} onChange={e => setMethod(e.target.value)}>
                  <option>GET</option><option>POST</option><option>PUT</option><option>DELETE</option><option>PATCH</option>
                </select>
              </div>
              <div className="form-group">
                <label>Category</label>
                <select className="form-control" value={category} onChange={e => setCategory(e.target.value)}>
                  <option>Other</option><option>Claims</option><option>SBI</option><option>GW</option>
                </select>
              </div>
            </div>
            <div className="form-group">
              <label>Request Headers (JSON)</label>
              <textarea className="form-control" value={headers} onChange={e => setHeaders(e.target.value)} rows="3" />
            </div>
            {method !== 'GET' && (
              <div className="form-group">
                <label>Request Body</label>
                <textarea className="form-control" value={body} onChange={e => setBody(e.target.value)} rows="5" />
              </div>
            )}
            
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', marginTop: '24px' }}>
              <button type="submit" className="btn" disabled={loading}>
                {loading ? 'Sending...' : 'Send Request'}
              </button>
            </div>
          </form>
        </div>

        <div className="glass-panel" style={{ display: 'flex', flexDirection: 'column' }}>
          <h3 style={{ marginBottom: '16px' }}>Response</h3>
          {response ? (
            <div className="fade-in" style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <span className={`badge badge-${(response.status >= 200 && response.status < 300) ? 'success' : 'danger'}`}>
                  {response.status}
                </span>
                {response.time && <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>{response.time}ms</span>}
                {response.source && (
                  <span className={`badge badge-${response.source === 'stub' ? 'info' : 'primary'}`}>
                    {response.source.toUpperCase()}
                  </span>
                )}
              </div>
              
              {response.error && (
                <div style={{ padding: '12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: '6px' }}>
                  {response.error}
                </div>
              )}
              
              <div style={{ flex: 1, overflowY: 'auto' }}>
                <div style={{ color: 'var(--text-muted)', marginBottom: '8px', fontSize: '0.9rem' }}>Data</div>
                <pre style={{ margin: 0 }}>
                  {typeof response.data === 'object' ? JSON.stringify(response.data, null, 2) : response.data || 'No Content'}
                </pre>
              </div>
            </div>
          ) : (
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
              Send a request to view the response here.
            </div>
          )}
        </div>
      </div>

      {showNoStubModal && (
        <div className="modal-overlay fade-in" style={{ zIndex: 9999 }}>
          <div className="modal-content glass-panel" style={{ maxWidth: '450px', padding: 0 }}>
            <div className="modal-header">
              <h3 style={{ margin: 0 }}>No Stub Available</h3>
            </div>
            <div className="modal-body" style={{ textAlign: 'center' }}>
              <p style={{ color: 'var(--text-muted)', lineHeight: '1.5' }}>
                We don't have a stub available for this API. Do you want to hit the real-time API?
              </p>
            </div>
            <div className="modal-footer" style={{ justifyContent: 'center' }}>
              <button 
                className="btn-secondary" 
                onClick={() => {
                  setShowNoStubModal(false);
                  setResponse({
                    status: 404,
                    error: "No stub available. Request cancelled.",
                    data: null
                  });
                }}
              >
                Cancel
              </button>
              <button 
                className="btn" 
                onClick={async () => {
                  setShowNoStubModal(false);
                  if (pendingRequestData) {
                    setLoading(true);
                    pendingRequestData.params.allowRealApi = true;
                    try {
                      const start = Date.now();
                      const retryRes = await api.request(pendingRequestData);
                      const time = Date.now() - start;
                      setResponse({
                        status: retryRes.status,
                        time,
                        source: retryRes.headers['x-response-source'] || 'live-api',
                        data: retryRes.data,
                        headers: retryRes.headers
                      });
                    } catch (retryErr) {
                      setResponse({
                        status: retryErr.response?.status || 'Network Error',
                        error: retryErr.message,
                        data: retryErr.response?.data
                      });
                    } finally {
                      setLoading(false);
                    }
                  }
                }}
              >
                Yes, hit real-time API
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default APITesterTab;
