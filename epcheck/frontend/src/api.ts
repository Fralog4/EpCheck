const API_BASE = 'http://localhost:8080/api';

export interface IngestionReport {
  fileHash: string;
  filename: string;
  status: string;
  detectedType: string;
  extractedPersonCount: number;
  extractedOrgCount: number;
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
  strengthScore: number;
}

export interface NetworkReportDTO {
  targetName: string;
  connectedPersons: ConnectedPersonDTO[];
  connectionCount: number;
}

export interface GraphNodeDTO {
  id: string;
  label: string;
  type: 'PERSON' | 'DOCUMENT' | 'ORGANIZATION';
  riskScore: number | null;
}

export interface GraphEdgeDTO {
  source: string;
  target: string;
  label: string;
}

export interface FullGraphDTO {
  nodes: GraphNodeDTO[];
  edges: GraphEdgeDTO[];
  personCount: number;
  documentCount: number;
  organizationCount: number;
}

export interface TimelineEventDTO {
  date: string | null;
  documentName: string;
  pageNumber: number;
  snippet: string;
  sentiment: string | null;
}

export interface TimelineDTO {
  targetName: string;
  events: TimelineEventDTO[];
  eventCount: number;
}

export interface AliasSuggestionDTO {
  rawName: string;
  suggestedCanonical: string;
  confidence: number;
  reason: string;
}

export interface AliasReportDTO {
  suggestions: AliasSuggestionDTO[];
  totalCount: number;
}

// ── API Functions ──

export async function ingestDocument(file: File): Promise<IngestionReport> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${API_BASE}/ingest`, { method: 'POST', body: formData });
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

export async function fetchFullGraph(): Promise<FullGraphDTO> {
  const res = await fetch(`${API_BASE}/graph`);
  if (!res.ok) throw new Error(`Graph fetch failed: ${res.statusText}`);
  return res.json();
}

export async function fetchTimeline(name: string): Promise<TimelineDTO> {
  const res = await fetch(`${API_BASE}/timeline/${encodeURIComponent(name)}`);
  if (!res.ok) throw new Error(`Timeline fetch failed: ${res.statusText}`);
  return res.json();
}

export async function fetchAliases(): Promise<AliasReportDTO> {
  const res = await fetch(`${API_BASE}/aliases`);
  if (!res.ok) throw new Error(`Aliases fetch failed: ${res.statusText}`);
  return res.json();
}
