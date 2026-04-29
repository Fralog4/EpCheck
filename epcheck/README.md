# EpsteinLens — OSINT Fact-Checking Backend

EpsteinLens is a backend system that ingests unstructured legal documents (depositions, emails) and structured flight logs, extracting named entities via NLP to build a **Neo4j Knowledge Graph**. The graph is then analyzed to determine the involvement level of specific individuals through a risk-classification algorithm.

---

## 🆕 Latest Updates (v0.1.0-alpha)

- **Frontend Dashboard** — Premium dark-themed SPA built with **Vite + React + TypeScript + Cytoscape.js**. Three views: Document Upload (drag & drop), Risk Analysis (search + risk card + evidence list), Network Explorer (force-directed graph visualization with click-to-navigate).
- **Network Depth Analysis** — New `GET /api/network/{name}` endpoint explores N-hop connections between persons via shared documents using a Cypher multi-hop query.
- **Risk Score Persistence** — `AnalysisService` now writes back the computed risk score to `Person.riskScore` in Neo4j. Also uses `NameNormalizationService` for consistent name lookup.
- **Email Chain Classification** — `classifyPageText()` now detects email chains by checking for ≥2 email header keywords (`From:`, `To:`, `Subject:`, `Cc:`, `Sent:`).
- **NER Confidence Scoring** — `NERService` now returns confidence scores from CoreNLP. Entities below threshold (default 0.60, configurable) are filtered out, reducing noise.
- **OCR for Scanned PDFs** — New `OcrService` wraps Tess4J (Tesseract OCR). When `PDFTextStripper` yields <20 chars on a page, the page is rendered at 300 DPI and passed to Tesseract. OCR'd text flows through the same NER/classification pipeline. Configured via `application.properties`.
- **Smarter Name Normalization** — New `NameNormalizationService` replaces the hardcoded 2-entry alias map with: (1) a curated alias database of 12 key individuals (~50 variations), and (2) Jaro-Winkler fuzzy matching (0.90 threshold) for typos and OCR artifacts. Zero external dependencies.
- **Snippet Sentiment Classification** — New `SentimentClassifierService` classifies the ~200-char context around each entity mention as `ACCUSATORY`, `EXCULPATORY`, or `INFORMATIONAL` using domain-specific keyword matching. Replaces the `"NEUTRAL"` placeholder on `MENTIONED_IN` relationships. Zero-dependency (no heavy CoreNLP sentiment model).
- **Flight Log Parsing** — New `FlightLogParserService` extracts dates (ISO/US formats), FAA N-number aircraft IDs, and origin/destination from flight log text via regex. Replaces all `LocalDate.now()` and `"Unknown"` placeholders in `GraphPersistenceService`.
- **Transactional Persistence + Retry** — Extracted graph writes into `GraphPersistenceService` with `@Transactional` (atomic writes — all succeed or all roll back) and Spring Boot 4.0 native `@Retryable` (3 retries with exponential backoff for transient Neo4j failures).
- **Batch Graph Persistence** — Replaced per-entity `save()` calls with batch operations: single `findAllByNormalizedNameIn()` fetch, in-memory Person map, and `saveAll()` for FlightLogs and Persons. Reduces ~3N DB round-trips to ~3 total.
- **Single-Pass PDF Extraction** — Refactored `DocumentIngestionService` to load the PDF only once. Text extraction, NER, and classification now happen in a single page-by-page pass via `PageExtractionResult` objects, with graph persistence deferred until after the PDF is closed — significantly reducing memory overhead on large files.
- **Full Graph Implementation** — `Person`, `Document`, `FlightLog` nodes and rich relationships (`MENTIONED_IN`, `FLEW_ON`) are fully modeled and operational using Spring Data Neo4j.
- **Async Ingestion Engine** — `DocumentIngestionService` uses Kotlin Coroutines (`suspend` + `Dispatchers.IO`) to handle PDF parsing and NER without blocking.
- **Risk Analysis Logic** — `AnalysisService` categorizes individuals into **RED / ORANGE / YELLOW / GREEN** risk levels based on flight logs, mention frequency, and keyword context.
- **Batch Ingestion API** — New `POST /api/ingest/batch` endpoint accepting a ZIP file containing multiple PDFs, processing them sequentially and returning a detailed `BatchIngestionReport`.
- **Temporal Analysis & Alias Auto-Discovery** — Added Timeline tracking to see when an entity appears over time, and an Alias suggestion engine detecting shared surnames and proximity names during ingestion.
- **Relationship Strength Scoring** — Edges in the knowledge graph now have a computed strength score based on co-occurrence, shared flights, and sentiment overlap, visualized in the Network Explorer.
- **Input Validation & Sanitization** — Added strict MIME type and Magic Byte checking for uploaded files, a 500-page limit per document, and text sanitization to protect the NER engine from malicious or malformed characters.
- **Global Error Handling** — Added a Spring `@ControllerAdvice` global exception handler returning a standardized `ErrorDTO` for all API errors.
- **Monitoring & Rate Limiting** — Added Spring Boot Actuator with Prometheus metrics (custom metrics for ingestion and NER), visualized via Grafana. Added an IP-based sliding-window `RateLimitFilter`.
- **Production Docker & CI/CD** — Multi-stage Dockerfile, complete GitHub Actions workflow (lint → test → build → push), and a robust `compose.yaml` with profiles (`dev`, `staging`, `prod`).
- **Event-Driven Ingestion (Kafka)** — Moved from blocking HTTP requests to async message-driven ingestion. Added `POST /api/ingest/async` returning a `jobId` immediately (202 Accepted), with background processing by a Kafka consumer and status polling via `GET /api/ingest/status/{jobId}`.
- **Neo4j Graph Data Science (GDS)** — Integrated the GDS plugin to run advanced network algorithms. Exposed `GET /api/gds/pagerank` (to find central/influential figures) and `GET /api/gds/communities` (Louvain modularity to detect hidden syndicates).
- **REST API** — Endpoints for document upload (`POST /api/ingest`) and profile analysis (`GET /api/analyze/{name}`) are exposed.
- **Swagger UI** — Integrated OpenAPI 3 documentation, accessible at `/swagger-ui/index.html`.
## 🗺️ Roadmap

### ✅ Completed

- [x] OCR for Scanned PDFs (Tess4J, 300 DPI fallback)
- [x] Flight Log Parsing (regex: dates, aircraft IDs, origin/destination)
- [x] Sentiment Analysis (keyword-based: ACCUSATORY / EXCULPATORY / INFORMATIONAL)
- [x] Smarter Name Normalization (alias DB + Jaro-Winkler fuzzy matching)
- [x] Network Depth Analysis (`GET /api/network/{name}` + multi-hop Cypher)
- [x] Email Chain Classification (≥2 header keywords per page)
- [x] Risk Score Persistence (write-back to `Person.riskScore`)
- [x] NER Confidence Scoring (configurable threshold filter)
- [x] Frontend Dashboard (Vite + React + TypeScript + Cytoscape.js)

---

### 🔴 Priority — Production Hardening

- [ ] **Unit & Integration Tests** — Add tests for `DocumentIngestionService`, `GraphPersistenceService`, `AnalysisService`, `NERService`, and the REST controller. Target ≥80% service-layer coverage.
- [ ] **API Security** — Add Spring Security with JWT or API key authentication. Protect `POST /api/ingest` (write) and make `GET` endpoints optionally public.
- [x] **Input Validation & Sanitization** — Validate uploaded file MIME type (reject non-PDF), enforce max page count, sanitize extracted text before NER to prevent injection.
- [x] **Error Handling & API Responses** — Standardize error responses (`ErrorDTO` with code/message/timestamp), add global `@ControllerAdvice` exception handler.

### 🟡 Priority — Frontend Enhancements

- [x] **Dark/Light Theme Toggle** — Theme switcher in the sidebar. Preference stored in `localStorage`.
- [x] **Real-time Ingestion Progress** — Step-by-step progress indicator (Uploading → Extracting Text → Running NER → Persisting Graph).
- [x] **Evidence Highlighting** — Matched entity names highlighted in evidence snippets with accent color.
- [x] **Graph Overview Page** — Full knowledge graph visualization of all persons/documents. New `GET /api/graph` backend endpoint.
- [x] **Export to PDF/CSV** — "Export Report" buttons on Risk Analysis page (CSV download + PDF print).
- [x] **Responsive Layout** — Mobile/tablet breakpoints. Sidebar collapses to icon-only on tablet, drawer on mobile.

### 🟠 Priority — Intelligence Pipeline

- [x] **Organization Entity Extraction** — Extend NER to extract `ORGANIZATION` entities (companies, foundations) alongside `PERSON`.
- [x] **Temporal Analysis** — Timeline view showing when a person appears across documents over time.
- [x] **Relationship Strength Scoring** — Weight connections by co-occurrence frequency, shared flight count, and sentiment overlap.
- [x] **Alias Auto-Discovery** — Use NER context to suggest potential aliases (e.g., "Bill" near "Clinton" → link to canonical name).
- [x] **Multi-Language OCR** — Support French, Spanish, and other languages in Tesseract for international documents.

### 🟢 Priority — Scalability & DevOps

- [x] **Production Docker Compose** — Multi-stage Dockerfile, health checks, Neo4j volume mounts, and environment-based config profiles (`dev`, `staging`, `prod`).
- [x] **CI/CD Pipeline** — GitHub Actions workflow: lint → test → build → Docker push.
- [x] **Batch Ingestion API** — `POST /api/ingest/batch` endpoint accepting a ZIP of multiple PDFs for bulk processing.
- [x] **Monitoring & Observability** — Spring Boot Actuator + Prometheus metrics + Grafana dashboard for ingestion throughput and NER latency.
- [x] **Rate Limiting** — Add rate limiting on ingestion endpoint to prevent abuse (e.g., 10 uploads/minute per API key).

---

## 🏗 Architecture & Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Language** | Kotlin (JVM 21) | Null safety, conciseness, first-class Coroutines support |
| **Framework** | Spring Boot 4.0 | Robust ecosystem for Web + Data |
| **Database** | Neo4j 5.x | Graph model is essential for `Person → Document` and `Person → FlightLog` rich relationships |
| **PDF Engine** | Apache PDFBox 3.0.1 | Reliable text extraction, page-by-page streaming |
| **NLP** | Stanford CoreNLP 4.5.5 | Named Entity Recognition (PERSON). Optimized config: `tokenize,ssplit,pos,lemma,ner` only |
| **OCR** | Tess4J 5.18.0 (Tesseract) | Scanned PDF fallback. 300 DPI rendering + LSTM engine for accurate text extraction |
| **Async** | Kotlin Coroutines | Non-blocking I/O during heavy file ingestion and graph operations |
| **Resilience** | Spring Boot 4.0 native `@Retryable` | Built-in retry with exponential backoff for transient failures |
| **API Docs** | springdoc-openapi 2.3.0 | Auto-generated Swagger UI from controller annotations |

## 🧬 Domain Model (Graph Schema)

We use **Rich Relationships** (relationship properties) to store context directly on the edge.

```
(:Person)-[:MENTIONED_IN {pageNumber, snippet, sentiment}]->(:Document)
(:Person)-[:FLEW_ON {flightDate, aircraftId, origin, destination}]->(:FlightLog)
```

### Nodes

| Node | Key Properties | Description |
|---|---|---|
| `Person` | `name`, `normalized_name`, `risk_score` | An individual. `normalized_name` enables fuzzy matching (e.g. "Bill Clinton" → "william jefferson clinton") |
| `Document` | `fileHash` (SHA-256, PK), `filename`, `ingestionDate`, `docType` | A source PDF. `docType`: `DEPOSITION`, `FLIGHT_LOG`, or `EMAIL_CHAIN` |
| `FlightLog` | `flightDate`, `aircraftId`, `origin`, `destination` | A flight record extracted from flight log documents |

### Relationships

| Relationship | Direction | Properties | Description |
|---|---|---|---|
| `MENTIONED_IN` | `Person → Document` | `pageNumber`, `snippet`, `sentiment` | Links a person to the document page where they appear |
| `FLEW_ON` | `Person → FlightLog` | `flightDate`, `aircraftId`, `origin`, `destination` | Links a person to a flight record |

## ⚙️ Key Components

### DocumentIngestionService (Orchestrator)

Orchestrates the ingestion pipeline. Processes uploaded PDFs in a **single pass**:

1. **SHA-256 Deduplication** — Skips files already ingested (hash used as Document PK).
2. **Single-Pass Page Iteration** — One `Loader.loadPDF()` call, iterating page by page:
   - Extracts text via `PDFTextStripper`
   - Runs NER via `NERService` → collects `PageExtractionResult` objects
   - Classifies document incrementally (checks for `N908JE`, `Cessna` keywords — short-circuits on first match)
3. **Delegates Persistence** — PDF is closed (memory freed), then calls `GraphPersistenceService.persistIngestionResults()`

### GraphPersistenceService (Persistence Layer)

Handles all Neo4j writes with reliability guarantees:

- **`@Transactional`** — All writes (Document, FlightLogs, Persons + relationships) happen atomically. If any write fails, everything rolls back.
- **`@Retryable`** — Transient Neo4j errors (connection timeouts, leader elections) trigger up to 3 retries with exponential backoff (500ms → 1s → 2s, max 5s).
- **Batch Operations** — Single `findAllByNormalizedNameIn()` fetch, in-memory Person map, and `saveAll()` calls. Reduces ~3N DB round-trips to ~3 total.

### NERService

Wraps Stanford CoreNLP with an optimized pipeline (`tokenize,ssplit,pos,lemma,ner`). Disables `parse` and `sentiment` annotators to minimize memory usage. Filters for `PERSON` entity type only.

### AnalysisService

The risk classification engine. Algorithm:

| Risk Level | Condition | Score |
|---|---|---|
| 🔴 **RED** | Has ≥ 1 `FLEW_ON` relationship | 100 |
| 🟠 **ORANGE** | Has > 5 mentions OR snippets contain keywords (`"massage"`, `"recruit"`) | 75 |
| 🟡 **YELLOW** | Found in graph but doesn't meet RED/ORANGE criteria | 25 |
| 🟢 **GREEN** | Not found in graph | 0 |

Returns a `RiskReportDTO` with sorted evidence items (document mentions + flight records).

## 📂 Project Structure

```
epcheck/
├── compose.yaml                          # Docker Compose — Neo4j container
├── build.gradle.kts                      # Gradle build (Kotlin DSL)
├── generate_pdf.py                       # Utility script for test PDF generation
└── src/main/kotlin/com/source/epcheck/
    ├── EpcheckApplication.kt             # Spring Boot entry point
    ├── config/
    │   ├── AppConfig.kt                  # OpenAPI bean + @EnableResilientMethods
    │   ├── IngestionMetrics.kt           # Custom Micrometer metrics
    │   ├── KafkaConfig.kt                # Topic and broker configuration
    │   └── RateLimitFilter.kt            # IP-based rate limiter
    ├── controller/
    │   ├── EpsteinLensController.kt      # REST endpoints (Sync/Async ingest, analyze, etc.)
    │   ├── GdsController.kt              # REST endpoints for PageRank and Louvain communities
    │   └── GlobalExceptionHandler.kt     # @ControllerAdvice for global error routing
    ├── domain/
    │   ├── Document.kt                   # Document node + DocType enum
    │   ├── Person.kt                     # Person node with relationship mappings
    │   ├── FlightLog.kt                  # FlightLog node
    │   ├── MentionedIn.kt                # MENTIONED_IN relationship properties
    │   └── FlewOn.kt                     # FLEW_ON relationship properties
    ├── dto/
    │   ├── DTOs.kt                       # IngestionReport, RiskReportDTO, EvidenceItem, Timeline, Batch
    │   └── ErrorDTO.kt                   # Standardized error response format
    ├── repository/
    │   ├── DocumentRepository.kt         # Neo4jRepository<Document, String>
    │   ├── PersonRepository.kt           # Includes findByNormalizedName + batch fetch
    │   └── FlightLogRepository.kt        # Neo4jRepository<FlightLog, Long>
    └── service/
        ├── DocumentIngestionService.kt   # Extraction orchestrator (PDF + NER + OCR)
        ├── GraphPersistenceService.kt    # @Transactional + @Retryable graph writes
        ├── OcrService.kt                 # Tess4J Tesseract OCR wrapper
        ├── FlightLogParserService.kt     # Regex-based flight data extraction
        ├── SentimentClassifierService.kt # Keyword-based snippet sentiment
        ├── NameNormalizationService.kt   # Alias DB + Jaro-Winkler fuzzy matching
        ├── AliasDiscoveryService.kt      # Proximity and surname alias suggestion
        ├── NERService.kt                 # Stanford CoreNLP wrapper (PERSON, ORGANIZATION)
        ├── AnalysisService.kt            # Risk classification logic, relationship strength
        ├── GdsService.kt                 # Executes Neo4j Graph Data Science algorithms
        ├── IngestionProducer.kt          # Kafka producer for async ingestion jobs
        ├── IngestionConsumer.kt          # Kafka consumer for background processing
        ├── JobStatusService.kt           # Tracks async job states
        └── Exceptions.kt                 # Domain exceptions for validation failures
```

## 🚀 How to Run

EpsteinLens uses **Docker Compose Profiles** to manage different environments. Ensure you have **Docker Desktop** or **Rancher Desktop** running before starting.

### 🛠 Option 1: Development Mode (Recommended)
Use this if you want to run the Spring Boot application locally (for fast debugging/logs) and only the database in Docker.

1.  **Start Neo4j**:
    ```bash
    docker compose -f epcheck/compose.yaml --profile dev up -d
    ```
2.  **Run the App**:
    ```bash
    cd epcheck
    ./gradlew bootRun
    ```
    *Access Swagger UI at: `http://localhost:8080/swagger-ui/index.html`*

---

### 📦 Option 2: Full Stack (Staging)
Use this to run everything (App + Neo4j + Kafka) inside Docker.

1.  **Start Everything**:
    ```bash
    docker compose -f epcheck/compose.yaml --profile staging up -d --build
    ```
    *This will build the application image and start all services.*

---

### 📂 Option 3: Production
Optimized stack with monitoring (Prometheus + Grafana).

```bash
docker compose -f epcheck/compose.yaml --profile prod up -d
```

---

### 🔧 Troubleshooting (Windows)

**Error: `error during connect: ... elevated privileges to connect`**
This usually means the Docker daemon is not reachable.
1.  **Check Docker**: Ensure Docker Desktop or Rancher Desktop is **running**.
2.  **Permissions**: Open your terminal (PowerShell/CMD) as **Administrator**.
3.  **Socket Path**: If using Rancher Desktop, ensure the "Docker Compatibility" mode is enabled in settings.

### 🔌 API Usage Examples

```bash
# Ingest a PDF document (Sync)
curl -X POST http://localhost:8080/api/ingest -F "file=@document.pdf"

# Ingest a PDF document (Async - Kafka)
curl -X POST http://localhost:8080/api/ingest/async -F "file=@document.pdf"

# Analyze a person's risk profile
curl http://localhost:8080/api/analyze/john%20doe
```

### Configuration

Default settings in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `spring.neo4j.uri` | `bolt://localhost:7687` | Neo4j Bolt URI |
| `spring.neo4j.authentication.username` | `neo4j` | Neo4j username |
| `spring.neo4j.authentication.password` | `notverysecret` | Neo4j password |
| `ocr.tessdata-path` | *(empty)* | Path to tessdata directory (blank = Tess4J bundled) |
| `ocr.language` | `eng` | Tesseract OCR language |
| `ner.confidence-threshold` | `0.60` | Minimum NER confidence score (0.0–1.0) |
| `spring.servlet.multipart.max-file-size` | `50MB` | Max upload size |

```bash
# Ingest a PDF document
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@document.pdf"

# Analyze a person's risk profile
curl http://localhost:8080/api/analyze/john%20doe
```

## 📝 Design Decisions

- **Why Neo4j + Spring Data Neo4j?** — Graph model naturally represents person-document-flight relationships. SDN provides clean OGM mapping to Kotlin data classes with `@RelationshipProperties` for rich edges.
- **Why Stanford CoreNLP?** — Robust NER for English. Heavy (~4GB models), but optimized by disabling `parse`/`sentiment` annotators and setting `ner.applyFineGrained=false`.
- **Why Coroutines over Threads?** — PDF ingestion is I/O-intensive. Coroutines allow scaling without blocking threads, with `Dispatchers.IO` for file and DB operations.
- **Why Single-Pass?** — Avoids loading the PDF DOM twice, cutting memory in half for large files. Classification is done incrementally during the page iteration.
- **Why separate GraphPersistenceService?** — `@Transactional` and `@Retryable` work via Spring AOP proxy interception, which requires *external* method calls. Extracting persistence into a separate `@Service` ensures the AOP proxy is invoked correctly, while keeping `DocumentIngestionService` focused on extraction logic.