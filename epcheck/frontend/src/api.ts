const API_BASE = 'http://localhost:8080/api';

export interface IngestionReport {
  fileHash: string;
  filename: string;
  status: string;
  detectedType: string;
  extractedPersonCount: number;
}

export interface EvidenceItem {
  date: string | null;
  sourcePdf: string;
  page: number;
  snippet: string;
  sentiment: string | null;
}

export interface RiskReportDTO {
  targetName: string;
  status: 'RED' | 'ORANGE' | 'YELLOW' | 'GREEN';
  evidence: EvidenceItem[];
  flightCount: number;
  riskScore: number;
}

export interface ConnectedPersonDTO {
  name: string;
  normalizedName: string;
  riskScore: number;
}

export interface NetworkReportDTO {
  targetName: string;
  connectedPersons: ConnectedPersonDTO[];
  connectionCount: number;
}

export async function ingestDocument(file: File): Promise<IngestionReport> {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${API_BASE}/ingest`, {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) throw new Error(`Ingestion failed: ${res.statusText}`);
  return res.json();
}

export async function analyzeProfile(name: string): Promise<RiskReportDTO> {
  const res = await fetch(`${API_BASE}/analyze/${encodeURIComponent(name)}`);
  if (!res.ok) throw new Error(`Analysis failed: ${res.statusText}`);
  return res.json();
}

export async function analyzeNetwork(name: string): Promise<NetworkReportDTO> {
  const res = await fetch(`${API_BASE}/network/${encodeURIComponent(name)}`);
  if (!res.ok) throw new Error(`Network analysis failed: ${res.statusText}`);
  return res.json();
}
