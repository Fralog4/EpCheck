import { useState } from 'react';
import { analyzeProfile, type RiskReportDTO } from '../api';

const riskColors: Record<string, string> = {
  RED: 'var(--risk-red)',
  ORANGE: 'var(--risk-orange)',
  YELLOW: 'var(--risk-yellow)',
  GREEN: 'var(--risk-green)',
};

export default function AnalyzePage() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState<RiskReportDTO | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await analyzeProfile(query.trim());
      setReport(result);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Analysis failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>🔍 Risk Analysis</h2>
        <p>Search for a person to view their risk profile and evidence</p>
      </div>

      <form className="search-bar" onSubmit={handleSearch}>
        <input
          className="search-input"
          placeholder="Enter person name (e.g. Bill Clinton, Prince Andrew)..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn btn-primary" type="submit" disabled={loading || !query.trim()}>
          {loading ? <><div className="spinner" /> Analyzing...</> : '🔍 Analyze'}
        </button>
      </form>

      {error && (
        <div className="card" style={{ borderColor: 'var(--risk-red)' }}>
          <p style={{ color: 'var(--risk-red)' }}>⚠️ {error}</p>
        </div>
      )}

      {report && (
        <div className="fade-in">
          {/* Risk Card */}
          <div className="card" style={{ marginBottom: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h3 style={{ fontSize: 22, marginBottom: 8 }}>{report.targetName}</h3>
                <span className={`risk-badge ${report.status.toLowerCase()}`}>
                  {report.status === 'RED' && '🔴'}
                  {report.status === 'ORANGE' && '🟠'}
                  {report.status === 'YELLOW' && '🟡'}
                  {report.status === 'GREEN' && '🟢'}
                  {' '}{report.status} RISK
                </span>
              </div>
              {report.flightCount > 0 && (
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--risk-red)' }}>
                    {report.flightCount}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                    Flights
                  </div>
                </div>
              )}
            </div>

            {/* Score Meter */}
            <div className="score-meter">
              <div className="score-meter-label">
                <span>Risk Score</span>
                <span style={{ fontWeight: 700, color: riskColors[report.status] }}>
                  {report.riskScore}/100
                </span>
              </div>
              <div className="score-meter-bar">
                <div
                  className="score-meter-fill"
                  style={{
                    width: `${report.riskScore}%`,
                    background: `linear-gradient(90deg, ${riskColors[report.status]}, ${riskColors[report.status]}88)`,
                  }}
                />
              </div>
            </div>
          </div>

          {/* Evidence */}
          <div className="card">
            <h3 style={{ marginBottom: 4 }}>📋 Evidence ({report.evidence.length} items)</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 12 }}>
              Document mentions and flight records
            </p>
            <div className="evidence-list">
              {report.evidence.map((item, i) => (
                <div key={i} className="evidence-item">
                  <div className="evidence-meta">
                    <div className="source">{item.sourcePdf.length > 20 
                      ? item.sourcePdf.substring(0, 20) + '...' 
                      : item.sourcePdf}</div>
                    {item.page > 0 && <div className="page">Page {item.page}</div>}
                    {item.date && <div className="page">{item.date}</div>}
                  </div>
                  <div className="evidence-snippet">
                    {item.snippet}
                    {item.sentiment && (
                      <span className={`sentiment-tag ${item.sentiment.toLowerCase()}`}>
                        {item.sentiment}
                      </span>
                    )}
                  </div>
                </div>
              ))}
              {report.evidence.length === 0 && (
                <div className="empty-state">
                  <p>No evidence found for this person</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {!report && !error && !loading && (
        <div className="empty-state">
          <div className="empty-icon">🕵️</div>
          <h3>Search for a person</h3>
          <p>Enter a name to view their risk profile and evidence trail</p>
        </div>
      )}
    </div>
  );
}
