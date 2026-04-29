import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import UploadPage from './pages/UploadPage';
import AnalyzePage from './pages/AnalyzePage';
import NetworkPage from './pages/NetworkPage';
import GraphOverviewPage from './pages/GraphOverviewPage';
import TimelinePage from './pages/TimelinePage';

export default function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>(() =>
    (localStorage.getItem('theme') as 'dark' | 'light') || 'dark'
  );
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  const closeSidebar = () => setSidebarOpen(false);

  return (
    <BrowserRouter>
      <div className="app-layout">
        {/* Mobile hamburger */}
        <button className="sidebar-toggle" onClick={() => setSidebarOpen(!sidebarOpen)}>
          {sidebarOpen ? '✕' : '☰'}
        </button>

        {/* Mobile overlay */}
        <div
          className={`sidebar-overlay ${sidebarOpen ? 'visible' : ''}`}
          onClick={closeSidebar}
        />

        {/* Sidebar */}
        <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
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

          <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} onClick={closeSidebar}>
            <span className="nav-icon">📄</span>
            <span>Upload</span>
          </NavLink>

          <NavLink to="/analyze" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} onClick={closeSidebar}>
            <span className="nav-icon">🔍</span>
            <span>Risk Analysis</span>
          </NavLink>

          <NavLink to="/network" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} onClick={closeSidebar}>
            <span className="nav-icon">🕸️</span>
            <span>Network Explorer</span>
          </NavLink>

          <NavLink to="/graph" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} onClick={closeSidebar}>
            <span className="nav-icon">🗺️</span>
            <span>Graph Overview</span>
          </NavLink>

          <NavLink to="/timeline" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} onClick={closeSidebar}>
            <span className="nav-icon">⏳</span>
            <span>Timeline</span>
          </NavLink>

          <div style={{ flex: 1 }} />

          {/* Theme Toggle */}
          <button className="theme-toggle" onClick={toggleTheme}>
            <span className="toggle-icon">{theme === 'dark' ? '☀️' : '🌙'}</span>
            <span>{theme === 'dark' ? 'Light Mode' : 'Dark Mode'}</span>
          </button>

          <div style={{
            padding: '14px 16px',
            fontSize: 10,
            color: 'var(--text-muted)',
            borderTop: '1px solid var(--border-subtle)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 8,
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
            <Route path="/graph" element={<GraphOverviewPage />} />
            <Route path="/timeline" element={<TimelinePage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
