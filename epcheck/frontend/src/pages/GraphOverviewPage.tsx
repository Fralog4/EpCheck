import { useState, useEffect, useCallback } from 'react';
import cytoscape from 'cytoscape';
import CytoscapeComponent from 'react-cytoscapejs';
import { fetchFullGraph, type FullGraphDTO } from '../api';
import { useNavigate } from 'react-router-dom';

function buildElements(data: FullGraphDTO) {
  const elements: cytoscape.ElementDefinition[] = [];
  data.nodes.forEach(node => {
    elements.push({
      data: {
        id: node.id,
        label: node.label.length > 20 ? node.label.substring(0, 18) + '…' : node.label,
        fullLabel: node.label,
        nodeType: node.type,
        riskScore: node.riskScore ?? -1,
      },
    });
  });
  data.edges.forEach((edge, i) => {
    elements.push({
      data: { id: `e-${i}`, source: edge.source, target: edge.target },
    });
  });
  return elements;
}

const stylesheet: cytoscape.StylesheetCSS[] = [
  {
    selector: 'node[nodeType="PERSON"]',
    css: {
      'label': 'data(label)', 'text-valign': 'bottom', 'text-halign': 'center',
      'font-size': '10px', 'color': '#f0f0f5', 'text-margin-y': 6,
      'background-color': '#6366f1', 'width': 36, 'height': 36,
      'border-width': 2, 'border-color': 'rgba(255,255,255,0.12)',
    },
  },
  {
    selector: 'node[nodeType="DOCUMENT"]',
    css: {
      'label': 'data(label)', 'text-valign': 'bottom', 'text-halign': 'center',
      'font-size': '9px', 'color': '#aaa', 'text-margin-y': 6,
      'background-color': '#334155', 'width': 28, 'height': 28,
      'shape': 'round-rectangle', 'border-width': 1,
      'border-color': 'rgba(255,255,255,0.06)',
    },
  },
  {
    selector: 'node[nodeType="ORGANIZATION"]',
    css: {
      'label': 'data(label)', 'text-valign': 'bottom', 'text-halign': 'center',
      'font-size': '9px', 'color': '#aaa', 'text-margin-y': 6,
      'background-color': '#0d9488', 'width': 32, 'height': 32,
      'shape': 'diamond', 'border-width': 2,
      'border-color': 'rgba(13,148,136,0.3)',
    },
  },
  { selector: 'node[riskScore >= 100]', css: { 'background-color': '#ef4444', 'width': 46, 'height': 46 } },
  { selector: 'node[riskScore >= 75][riskScore < 100]', css: { 'background-color': '#f97316', 'width': 42, 'height': 42 } },
  { selector: 'node[riskScore >= 25][riskScore < 75]', css: { 'background-color': '#eab308' } },
  { selector: 'node[riskScore >= 0][riskScore < 25]', css: { 'background-color': '#22c55e' } },
  {
    selector: 'edge',
    css: { 'width': 1.5, 'line-color': 'rgba(99,102,241,0.2)', 'curve-style': 'bezier' },
  },
];

export default function GraphOverviewPage() {
  const [data, setData] = useState<FullGraphDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchFullGraph()
      .then(setData)
      .catch(e => setError(e instanceof Error ? e.message : 'Failed to load graph'))
      .finally(() => setLoading(false));
  }, []);

  const handleNodeClick = useCallback(
    (nodeData: { nodeType: string; fullLabel: string }) => {
      if (nodeData.nodeType === 'PERSON') {
        navigate(`/analyze?q=${encodeURIComponent(nodeData.fullLabel)}`);
      }
    },
    [navigate]
  );

  if (loading) {
    return (
      <div className="fade-in">
        <div className="page-header">
          <h2>🗺️ Graph Overview</h2>
          <p>Full knowledge graph visualization</p>
        </div>
        <div className="loading-spinner"><div className="spinner" /> Loading graph data...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="fade-in">
        <div className="page-header">
          <h2>🗺️ Graph Overview</h2>
        </div>
        <div className="card" style={{ borderColor: 'var(--risk-red)' }}>
          <p style={{ color: 'var(--risk-red)' }}>⚠️ {error}</p>
        </div>
      </div>
    );
  }

  if (!data || data.nodes.length === 0) {
    return (
      <div className="fade-in">
        <div className="page-header">
          <h2>🗺️ Graph Overview</h2>
          <p>Full knowledge graph visualization</p>
        </div>
        <div className="empty-state">
          <div className="empty-icon">🗺️</div>
          <h3>No data yet</h3>
          <p>Upload documents to populate the knowledge graph</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>🗺️ Graph Overview</h2>
        <p>Full knowledge graph — click a person node to view their risk profile</p>
      </div>

      <div className="graph-overview-stats">
        <div className="network-stat">
          <div className="stat-number">{data.personCount}</div>
          <div className="stat-desc">Persons</div>
        </div>
        <div className="network-stat">
          <div className="stat-number">{data.organizationCount}</div>
          <div className="stat-desc">Organizations</div>
        </div>
        <div className="network-stat">
          <div className="stat-number">{data.documentCount}</div>
          <div className="stat-desc">Documents</div>
        </div>
        <div className="network-stat">
          <div className="stat-number">{data.edges.length}</div>
          <div className="stat-desc">Connections</div>
        </div>
      </div>

      <div className="graph-container" style={{ height: 600 }}>
        <CytoscapeComponent
          elements={buildElements(data)}
          stylesheet={stylesheet}
          layout={{ name: 'cose', animate: true, animationDuration: 1200, nodeRepulsion: () => 8000, idealEdgeLength: () => 120 } as cytoscape.LayoutOptions}
          style={{ width: '100%', height: '100%' }}
          cy={(cy: cytoscape.Core) => {
            cy.on('tap', 'node', (evt: cytoscape.EventObject) => {
              handleNodeClick(evt.target.data());
            });
          }}
        />
        <div className="graph-legend">
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#6366f1' }} /> Person
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#334155', borderRadius: 3 }} /> Document
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#0d9488', transform: 'rotate(45deg)', borderRadius: 2 }} /> Organization
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#ef4444' }} /> Red
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#f97316' }} /> Orange
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#eab308' }} /> Yellow
          </div>
          <div className="legend-item">
            <div className="legend-dot" style={{ background: '#22c55e' }} /> Green
          </div>
        </div>
      </div>
    </div>
  );
}
