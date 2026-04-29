import { useState, useCallback } from 'react';
import cytoscape from 'cytoscape';
import CytoscapeComponent from 'react-cytoscapejs';
import { analyzeNetwork, type NetworkReportDTO, type ConnectedPersonDTO } from '../api';
import { useNavigate } from 'react-router-dom';

function getNodeColor(riskScore: number): string {
  if (riskScore >= 100) return '#ef4444';
  if (riskScore >= 75) return '#f97316';
  if (riskScore >= 25) return '#eab308';
  return '#22c55e';
}

function buildGraphElements(targetName: string, data: NetworkReportDTO) {
  const elements: cytoscape.ElementDefinition[] = [];

  // Target node (center)
  elements.push({
    data: {
      id: 'target',
      label: targetName,
      riskScore: -1,
    },
  });

  // Connected nodes
  data.connectedPersons.forEach((person: ConnectedPersonDTO, i: number) => {
    elements.push({
      data: {
        id: `person-${i}`,
        label: person.name,
        riskScore: person.riskScore,
        normalizedName: person.normalizedName,
      },
    });
    elements.push({
      data: {
        id: `edge-${i}`,
        source: 'target',
        target: `person-${i}`,
        strength: person.strengthScore || 1,
      },
    });
  });

  return elements;
}

const cytoscapeStylesheet: cytoscape.StylesheetCSS[] = [
  {
    selector: 'node',
    css: {
      'label': 'data(label)',
      'text-valign': 'bottom',
      'text-halign': 'center',
      'font-size': '11px',
      'color': '#f0f0f5',
      'text-margin-y': 8,
      'background-color': '#6366f1',
      'width': 40,
      'height': 40,
      'border-width': 2,
      'border-color': 'rgba(255,255,255,0.15)',
    },
  },
  {
    selector: 'node[riskScore >= 100]',
    css: { 'background-color': '#ef4444', 'width': 50, 'height': 50 },
  },
  {
    selector: 'node[riskScore >= 75][riskScore < 100]',
    css: { 'background-color': '#f97316', 'width': 45, 'height': 45 },
  },
  {
    selector: 'node[riskScore >= 25][riskScore < 75]',
    css: { 'background-color': '#eab308' },
  },
  {
    selector: 'node[riskScore >= 0][riskScore < 25]',
    css: { 'background-color': '#22c55e' },
  },
  {
    selector: '#target',
    css: {
      'background-color': '#6366f1',
      'width': 60,
      'height': 60,
      'border-width': 3,
      'border-color': '#a78bfa',
      'font-size': '13px',
    },
  },
  {
    selector: 'edge',
    css: {
      'width': 2,
      'line-color': 'rgba(99, 102, 241, 0.3)',
      'curve-style': 'bezier',
    },
  },
  {
    selector: 'edge[strength >= 3]',
    css: { 'width': 3, 'line-color': 'rgba(99, 102, 241, 0.5)' },
  },
  {
    selector: 'edge[strength >= 6]',
    css: { 'width': 5, 'line-color': 'rgba(99, 102, 241, 0.7)' },
  },
  {
    selector: 'edge[strength >= 10]',
    css: { 'width': 7, 'line-color': 'rgba(168, 85, 247, 0.7)' },
  },
];

export default function NetworkPage() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<NetworkReportDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await analyzeNetwork(query.trim());
      setData(result);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Network analysis failed');
    } finally {
      setLoading(false);
    }
  };

  const handleNodeClick = useCallback(
    (name: string) => {
      navigate(`/analyze?q=${encodeURIComponent(name)}`);
    },
    [navigate]
  );

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>🕸️ Network Explorer</h2>
        <p>Explore connections between persons via shared documents</p>
      </div>

      <form className="search-bar" onSubmit={handleSearch}>
        <input
          className="search-input"
          placeholder="Enter person name to explore their network..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn btn-primary" type="submit" disabled={loading || !query.trim()}>
          {loading ? <><div className="spinner" /> Mapping...</> : '🕸️ Explore'}
        </button>
      </form>

      {error && (
        <div className="card" style={{ borderColor: 'var(--risk-red)' }}>
          <p style={{ color: 'var(--risk-red)' }}>⚠️ {error}</p>
        </div>
      )}

      {data && (
        <div className="fade-in">
          <div className="network-stats">
            <div className="network-stat">
              <div className="stat-number">{data.connectionCount}</div>
              <div className="stat-desc">Connected Persons</div>
            </div>
            <div className="network-stat">
              <div className="stat-number" style={{ color: 'var(--risk-red)' }}>
                {data.connectedPersons.filter((p) => p.riskScore >= 75).length}
              </div>
              <div className="stat-desc">High Risk</div>
            </div>
          </div>

          <div className="graph-container">
            <CytoscapeComponent
              elements={buildGraphElements(query, data)}
              stylesheet={cytoscapeStylesheet}
              layout={{ name: 'cose', animate: true, animationDuration: 800 }}
              style={{ width: '100%', height: '100%' }}
              cy={(cy: cytoscape.Core) => {
                cy.on('tap', 'node', (evt: cytoscape.EventObject) => {
                  const nodeData = evt.target.data();
                  if (nodeData.id !== 'target' && nodeData.label) {
                    handleNodeClick(nodeData.label);
                  }
                });
              }}
            />
            <div className="graph-legend">
              <div className="legend-item">
                <div className="legend-dot" style={{ background: '#6366f1' }} />
                Target
              </div>
              <div className="legend-item">
                <div className="legend-dot" style={{ background: '#ef4444' }} />
                Red Risk
              </div>
              <div className="legend-item">
                <div className="legend-dot" style={{ background: '#f97316' }} />
                Orange
              </div>
              <div className="legend-item">
                <div className="legend-dot" style={{ background: '#eab308' }} />
                Yellow
              </div>
              <div className="legend-item">
                <div className="legend-dot" style={{ background: '#22c55e' }} />
                Green
              </div>
            </div>
          </div>

          {/* Connected Persons List */}
          <div className="card" style={{ marginTop: 24 }}>
            <h3 style={{ marginBottom: 12 }}>
              Connected Persons ({data.connectedPersons.length})
            </h3>
            <div className="evidence-list">
              {data.connectedPersons.map((person, i) => (
                <div
                  key={i}
                  className="evidence-item"
                  style={{ cursor: 'pointer' }}
                  onClick={() => handleNodeClick(person.name)}
                >
                  <div className="evidence-meta">
                    <div
                      className="source"
                      style={{ color: getNodeColor(person.riskScore) }}
                    >
                      Risk: {person.riskScore}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 2 }}>
                      Strength: {person.strengthScore.toFixed(1)}
                    </div>
                  </div>
                  <div className="evidence-snippet" style={{ fontWeight: 500, color: 'var(--text-primary)' }}>
                    {person.name}
                    <span style={{ color: 'var(--text-muted)', fontSize: 12, marginLeft: 8 }}>
                      ({person.normalizedName})
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {!data && !error && !loading && (
        <div className="empty-state">
          <div className="empty-icon">🕸️</div>
          <h3>Explore the Network</h3>
          <p>Enter a name to discover who shares documents with them</p>
        </div>
      )}
    </div>
  );
}
