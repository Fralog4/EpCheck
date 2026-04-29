import React, { useState } from 'react';
import { analyzeProfile, type RiskReportDTO } from '../api';

const riskColors: Record<string, string> = {
  RED: 'var(--risk-red)',
  ORANGE: 'var(--risk-orange)',
  YELLOW: 'var(--risk-yellow)',
  GREEN: 'var(--risk-green)',
};

/** Wraps occurrences of `name` in the snippet with <mark> tags */
function highlightName(snippet: string, targetName: string): (string | React.JSX.Element)[] {
  if (!targetName) return [snippet];
  const parts: (string | React.JSX.Element)[] = [];
  const regex = new RegExp(`(${targetName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  while ((match = regex.exec(snippet)) !== null) {
    if (match.index > lastIndex) parts.push(snippet.slice(lastIndex, match.index));
    parts.push(<mark key={key++} className="highlight-entity">{match[1]}</mark>);
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < snippet.length) parts.push(snippet.slice(lastIndex));
  return parts.length ? parts : [snippet];
}

function exportCSV(report: RiskReportDTO) {
  const header = 'Source PDF,Page,Date,Snippet,Sentiment\n';
  const rows = report.evidence.map(e =>
    `"${e.sourcePdf}",${e.page},"${e.date || ''}","${e.snippet.replace(/"/g, '""')}","${e.sentiment || ''}"`
  ).join('\n');
  const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${report.targetName.replace(/\s+/g, '_')}_risk_report.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function exportPDF(report: RiskReportDTO) {
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8">
<title>Risk Report — ${report.targetName}</title>
<style>
  body { font-family: 'Segoe UI', sans-serif; padding: 40px; color: #1a1a2e; }
  h1 { font-size: 24px; margin-bottom: 4px; }
  .badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; }
  .RED { background: #fee2e2; color: #dc2626; }
  .ORANGE { background: #fff7ed; color: #ea580c; }
  .YELLOW { background: #fef9c3; color: #ca8a04; }
  .GREEN { background: #dcfce7; color: #16a34a; }
  table { width: 100%; border-collapse: collapse; margin-top: 24px; font-size: 13px; }
  th { text-align: left; padding: 8px; border-bottom: 2px solid #e5e5e5; color: #666; }
  td { padding: 8px; border-bottom: 1px solid #f0f0f0; }
  .meta { color: #888; font-size: 11px; margin-top: 8px; }
</style></head><body>
<h1>Risk Report: ${report.targetName}</h1>
<span class="badge ${report.status}">${report.status} RISK — Score: ${report.riskScore}/100</span>
<p class="meta">Flights: ${report.flightCount} | Evidence items: ${report.evidence.length} | Generated: ${new Date().toLocaleString()}</p>
<table><thead><tr><th>Source</th><th>Page</th><th>Date</th><th>Snippet</th><th>Sentiment</th></tr></thead>
<tbody>${report.evidence.map(e => `<tr><td>${e.sourcePdf}</td><td>${e.page}</td><td>${e.date || '-'}</td><td>${e.snippet}</td><td>${e.sentiment || '-'}</td></tr>`).join('')}</tbody></table>
</body></html>`;
  const w = window.open('', '_blank');
  if (w) { w.document.write(html); w.document.close(); w.print(); }
}

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

            {/* Export Toolbar */}
            <div className="export-toolbar">
              <button className="btn-secondary" onClick={() => exportCSV(report)}>
                📊 Export CSV
              </button>
              <button className="btn-secondary" onClick={() => exportPDF(report)}>
                📄 Export PDF
              </button>
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
                    {highlightName(item.snippet, report.targetName)}
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
