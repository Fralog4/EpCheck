import { useState, useRef, type DragEvent } from 'react';
import { ingestDocument, type IngestionReport } from '../api';

export default function UploadPage() {
  const [dragging, setDragging] = useState(false);
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState<IngestionReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleFile = async (file: File) => {
    setLoading(true);
    setError(null);
    setReport(null);
    try {
      const result = await ingestDocument(file);
      setReport(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  };

  const onFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>📄 Document Ingestion</h2>
        <p>Upload PDF documents to extract entities and build the Knowledge Graph</p>
      </div>

      <div
        className={`upload-zone ${dragging ? 'drag-over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => fileRef.current?.click()}
      >
        <span className="upload-icon">📎</span>
        <h3>{loading ? 'Processing document...' : 'Drop PDF here or click to browse'}</h3>
        <p>Supports depositions, flight logs, and email chains</p>
        {loading && (
          <div className="loading-spinner" style={{ marginTop: 20 }}>
            <div className="spinner" />
            Extracting entities with NER pipeline...
          </div>
        )}
      </div>

      <input
        ref={fileRef}
        type="file"
        accept=".pdf"
        style={{ display: 'none' }}
        onChange={onFileSelect}
      />

      {error && (
        <div className="card fade-in" style={{ marginTop: 24, borderColor: 'var(--risk-red)' }}>
          <p style={{ color: 'var(--risk-red)', fontSize: 13 }}>⚠️ {error}</p>
        </div>
      )}

      {report && (
        <div className="card fade-in" style={{ marginTop: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
            <span style={{
              width: 32, height: 32, borderRadius: 8,
              background: 'var(--risk-green-bg)', display: 'flex',
              alignItems: 'center', justifyContent: 'center', fontSize: 16,
            }}>✅</span>
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 700 }}>Ingestion Complete</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}>
                {report.filename}
              </p>
            </div>
          </div>
          <div className="report-card">
            <div className="report-stat">
              <div className="stat-value">{report.status}</div>
              <div className="stat-label">Status</div>
            </div>
            <div className="report-stat">
              <div className="stat-value">{report.detectedType}</div>
              <div className="stat-label">Doc Type</div>
            </div>
            <div className="report-stat">
              <div className="stat-value">{report.extractedPersonCount}</div>
              <div className="stat-label">Persons Found</div>
            </div>
            <div className="report-stat">
              <div className="stat-value" style={{ fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>
                {report.fileHash.substring(0, 12)}…
              </div>
              <div className="stat-label">SHA-256</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
