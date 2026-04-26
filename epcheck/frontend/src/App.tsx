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
            <h1>EpsteinLens</h1>
          </div>

          <NavLink
            to="/"
            end
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="nav-icon">📄</span>
            Upload
          </NavLink>

          <NavLink
            to="/analyze"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="nav-icon">🔍</span>
            Risk Analysis
          </NavLink>

          <NavLink
            to="/network"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="nav-icon">🕸️</span>
            Network Explorer
          </NavLink>

          <div style={{ flex: 1 }} />

          <div style={{
            padding: '12px 16px',
            fontSize: 11,
            color: 'var(--text-muted)',
            borderTop: '1px solid var(--border-subtle)',
          }}>
            OSINT Fact-Checking Tool<br />
            v0.1.0-alpha
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
