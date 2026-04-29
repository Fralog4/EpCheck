import { useState } from 'react';
import { fetchTimeline, type TimelineDTO } from '../api';

export default function TimelinePage() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<TimelineDTO | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await fetchTimeline(query.trim());
      setData(result);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Timeline fetch failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>⏳ Temporal Analysis</h2>
        <p>View when a person appears across documents over time</p>
      </div>

      <form className="search-bar" onSubmit={handleSearch}>
        <input
          className="search-input"
          placeholder="Enter person name to view their timeline..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn btn-primary" type="submit" disabled={loading || !query.trim()}>
          {loading ? <><div className="spinner" /> Loading...</> : '⏳ View Timeline'}
        </button>
      </form>

      {error && (
        <div className="card" style={{ borderColor: 'var(--risk-red)' }}>
          <p style={{ color: 'var(--risk-red)' }}>⚠️ {error}</p>
        </div>
      )}

      {data && (
        <div className="fade-in">
          <div className="card" style={{ marginBottom: 24 }}>
            <h3 style={{ fontSize: 20, marginBottom: 4 }}>{data.targetName}</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              {data.eventCount} appearance{data.eventCount !== 1 ? 's' : ''} across documents
            </p>
          </div>

          {data.events.length > 0 ? (
            <div className="timeline">
              {data.events.map((event, i) => (
                <div key={i} className="timeline-event fade-in" style={{ animationDelay: `${i * 0.05}s` }}>
                  <div className="timeline-marker">
                    <div className="timeline-dot" />
                    {i < data.events.length - 1 && <div className="timeline-line" />}
                  </div>
                  <div className="timeline-content card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                      <div>
                        <div style={{ fontSize: 12, color: 'var(--accent-light)', fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>
                          {event.date || 'Unknown date'}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                          {event.documentName} — Page {event.pageNumber}
                        </div>
                      </div>
                      {event.sentiment && (
                        <span className={`sentiment-tag ${event.sentiment.toLowerCase()}`}>
                          {event.sentiment}
                        </span>
                      )}
                    </div>
                    <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                      {event.snippet}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-icon">📭</div>
              <h3>No timeline events</h3>
              <p>This person has no document appearances on record</p>
            </div>
          )}
        </div>
      )}

      {!data && !error && !loading && (
        <div className="empty-state">
          <div className="empty-icon">⏳</div>
          <h3>Temporal Analysis</h3>
          <p>Enter a name to view their chronological appearances across documents</p>
        </div>
      )}
    </div>
  );
}
