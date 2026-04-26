import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import UploadPage from './pages/UploadPage';
import AnalyzePage from './pages/AnalyzePage';
import NetworkPage from './pages/NetworkPage';

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-layout">
        {/* Sidebar */}
        <aside className="sidebar">
          <div className="sidebar-brand">
            <div className="brand-icon">🔬</div>
            <div>
              <h1>EpsteinLens</h1>
              <span style={{
                fontSize: 10,
                color: 'var(--text-muted)',
                fontWeight: 500,
                letterSpacing: '0.5px',
                textTransform: 'uppercase' as const,
              }}>OSINT Intelligence</span>
            </div>
          </div>

          <div style={{ fontSize: 10, color: 'var(--text-muted)', padding: '0 16px', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '1px', fontWeight: 600 }}>
            Workspace
          </div>

          <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span className="nav-icon">📄</span>
            <span>Upload</span>
          </NavLink>

          <NavLink to="/analyze" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span className="nav-icon">🔍</span>
            <span>Risk Analysis</span>
          </NavLink>

          <NavLink to="/network" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span className="nav-icon">🕸️</span>
            <span>Network Explorer</span>
          </NavLink>

          <div style={{ flex: 1 }} />

          <div style={{
            padding: '14px 16px',
            fontSize: 10,
            color: 'var(--text-muted)',
            borderTop: '1px solid var(--border-subtle)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <span style={{ fontWeight: 500 }}>v0.1.0-alpha</span>
            <span style={{
              padding: '2px 8px',
              background: 'var(--risk-green-bg)',
              color: 'var(--risk-green)',
              borderRadius: 100,
              fontSize: 9,
              fontWeight: 700,
            }}>ONLINE</span>
          </div>
        </aside>

        {/* Main Content */}
        <main className="main-content">
          <Routes>
            <Route path="/" element={<UploadPage />} />
            <Route path="/analyze" element={<AnalyzePage />} />
            <Route path="/network" element={<NetworkPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
