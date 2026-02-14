# EpsteinLens - OSINT Fact-Checking Backend

EpsteinLens is a backend system designed to ingest unstructured legal documents and structured flight logs, parsing them to build a Neo4j Knowledge Graph that determines the involvement level of specific individuals.

## 🆕 Latest Updates (v0.1.0-alpha)

- **Full Graph Implementation**: `Person`, `Document`, `FlightLog` nodes and rich relationships (`MENTIONED_IN`, `FLEW_ON`) are now fully modeled and operational using Spring Data Neo4j.
- **Async Ingestion Engine**: Implemented `DocumentIngestionService` using Kotlin Coroutines to handle PDF parsing and NER without blocking.
- **Risk Analysis Logic**: Added `AnalysisService` which categorizes individuals into **RED/ORANGE/YELLOW** risk levels based on flight logs and mention frequency/context.
- **REST API Exposed**: Endpoints for document upload (`/api/ingest`) and profile analysis (`/api/analyze/{name}`) are ready.
- **Swagger UI**: Integrated OpenAPI 3 documentation. Access interactive API docs at `/swagger-ui/index.html`.

## 🚧 Future Improvements

- **Optimization**: Refactor PDF ingestion to a single-pass extraction to reduce memory overhead on large files.
- **Data Consistency**: Implement batch saving for nodes/relationships to reduce database round-trips.
- **Reliability**: Add transactional boundaries or retry logic for the ingestion process.


## 🏗 Architecture & Tech Stack

- **Language**: Kotlin (JVM 21) - Chosen for null safety, conciseness, and Coroutines support.
- **Framework**: Spring Boot 3.4+ - Robust ecosystem for Web and Data.
- **Database**: Neo4j 5.x - Graph database is essential for modeling "Person -> Document" and "Person -> FlightLog" relationships properly ("Rich Relationships").
- **PDF Engine**: Apache PDFBox 3.0.1 - Reliable text extraction.
- **NLP**: Stanford CoreNLP - Used for Named Entity Recognition (NER) to extract Person entities. Configured efficiently to avoid loading unused models.
- **Async**: Kotlin Coroutines - Used for non-blocking I/O during heavy file ingestion and graph operations.

## 🧬 Domain Model (Graph Schema)

We favor **Rich Relationships** (Hyper-edges) to store context directly on the edge.

- **Nodes**:
  - `Person`: Represents an individual. Contains `normalized_name` for fuzzy matching and `risk_score`.
  - `Document`: Represents a source file (PDF).
  - `FlightLog`: Represents a flight record (or the group of records).

- **Relationships**:
  - `MENTIONED_IN`: `Person -> Document`. Stores `pageNumber` and `snippet` (context).
  - `FLEW_ON`: `Person -> FlightLog`. Stores `flightDate`, `aircraftId`, `origin`, `destination`.

## ⚙️ Key Components

### DocumentIngestionService
- **Async Ingestion**: Uses `suspend` functions and `Dispatchers.IO`.
- **Deduplication**: Checks SHA-256 hash of file content before processing.
- **Heuristic Classification**: Detects text patterns (e.g., "N908JE") to classify documents as `FLIGHT_LOG` or `DEPOSITION`.
- **NER Pipeline**: Extracts names and normalizes them (e.g., "Bill" -> "William").
- **Graph Persistence**: Upserts `Person` nodes and creates `MENTIONED_IN` / `FLEW_ON` relationships.

### AnalysisService (The "Brain")
- **Risk Algorithm**:
  - **RED**: User has > 0 `FLEW_ON` relationships.
  - **ORANGE**: User has > 5 mentions OR mentions contain keywords ("massage", "recruit").
  - **YELLOW**: Single mention, no flights.
- **Network Depth**: Uses Cypher queries to traverse the graph.

## 🚀 How to Run

1. **Prerequisites**: Neo4j running locally (port 7687).
2. **Configure**: Update `src/main/resources/application.properties` with Neo4j credentials.
3. **Run**: `./gradlew bootRun`
4. **Ingest**: POST PDF to `/api/ingest`.
5. **Analyze**: GET `/api/analyze/{name}`.

## 📝 Design Decisions

- **Why Neo4j OGM?**: Easier mapping of nodes/relationships to Kotlin data classes compared to raw Cypher drivers.
- **Why Stanford CoreNLP?**: Robust NER for English, though heavy. We applied optimization by disabling parse/sentiment annotators.
- **Why Coroutines?**: Ingestion is I/O intensive. Coroutines allow scaling processing without blocking threads.
