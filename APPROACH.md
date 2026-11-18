# Technical Approach & Architecture (APPROACH.md)

**Project:** Cantill.ai — Policy-Driven PR-to-PO Procurement Engine  
**Author:** Senior Full-Stack Engineering Team  

---

## 1. System Architecture

The application is structured as a clean, modular full-stack web application designed for correctness, deterministic decision-making, explainability, zero code-modification policy updates, and zero-headache cloud deployment.

In production on Render, the system runs as **ONE combined Web Service**, where Spring Boot 3.3.x (Java 21) serves both the compiled React + TypeScript Single Page Application (SPA) static bundle and all `/api/**` REST endpoints from a single public domain and port, backed by an external Neon Serverless PostgreSQL database.

```
                              Render Web Service (Single Public URL)
                             ┌─────────────────────────────────────────────────────────┐
                             │               Spring Boot 3.3.x (Java 21 JRE)           │
                             │                                                         │
Browser Client ─────────────►│ 1. GET /              ──► index.html                    │
(Single Public Origin)       │ 2. GET /buyer, /chat  ──► index.html (SPA Route Forward) │
                             │ 3. GET /assets/**     ──► Static CSS / JS Bundle        │
                             │ 4. ALL /api/**        ──► Spring MVC REST Controllers   │
                             │                                                         │
                             │ [REST Controllers]                                      │
                             │   - EmployeeChatController    - BuyerWorkspaceController│
                             │   - BatchProcessingController - PolicyManagementCtrl    │
                             │   - MasterDataController      - HealthController        │
                             │                                                         │
                             │ [Core Services & Domain Engine]                         │
                             │   - ItemMatchingService (Rule & Token Normalization)    │
                             │   - ClubbingService (14-day creation / 7-day need-by)   │
                             │   - ValuationService (Contract -> PO Hist -> Std Price) │
                             │   - ContractEngineService (Live Ceiling Tracking & PDF) │
                             │   - SupplierRankingService (Composite Scoring Model)    │
                             │   - ProcurementDecisionEngine (Dynamic Rule Evaluator)  │
                             │   - PoConsolidationService (24-hour window clustering)  │
                             │   - MasterDataService (Repeatable CSV/ZIP Ingestion)    │
                             │   - ContractPdfService (Apache PDFBox Contract Ingest)  │
                             └────────────────────────────┬────────────────────────────┘
                                                          │
                                                          ▼ (HikariCP / JDBC with SSL)
                                        Neon Serverless PostgreSQL (or local H2)
                             ┌─────────────────────────────────────────────────────────┐
                             │  - Items, Suppliers, Plants, PO History, Contracts      │
                             │  - Purchase Requisitions, PR Lines, Requirements, POs   │
                             │  - Policy Versions, Policy Rules, Audit Records, Chat   │
                             │  - Automated Flyway Migrations (V1__init_schema.sql)    │
                             └─────────────────────────────────────────────────────────┘
```

---

## 2. Procurement Decision Engine

The decision engine strictly enforces Corvo Procurement Policy v5.0 rules through a multi-stage evaluation pipeline:

1. **Item Resolution:** PR lines are matched against the item master using exact code lookups, description normalization, abbreviation expansion (e.g. `brg` $\rightarrow$ `bearing`, `hyd` $\rightarrow$ `hydraulic`), and tokenized similarity. Unmatched items are classified as `NEW` (`NEW_ITEM_SOURCING`).
2. **PR Clubbing:** Lines are grouped by `resolvedItemCode`, `plant`, creation window ($\le 14$ calendar days), and need-by window ($\pm 7$ days). New items are excluded from clubbing.
3. **Valuation:** Computes requirement value as $\text{Total Qty} \times \text{Unit Price}$ using standard `BigDecimal` arithmetic. Priority: applicable usable contract rate $\rightarrow$ lowest applicable contract rate (if ceilings exhausted) $\rightarrow$ most recent PO price within 365 days $\rightarrow$ item master standard price.
4. **Contract Eligibility & Live Ceiling Tracking:** Validates contract dates, item coverage, and active supplier status. Tracks cumulative consumed ceilings in order of requirement date.
5. **Preferred Supplier Ranking:** Deterministic 5-factor scoring model ($w_{po}: 25\%$, $w_{rec}: 25\%$, $w_{con}: 20\%$, $w_{ot}: 15\%$, $w_{qual}: 15\%$). Blocked and Inactive suppliers are unconditionally excluded.
6. **Policy Rule Evaluation:** Evaluates active policy rules by priority (Capex $\rightarrow$ Contract PO $\rightarrow$ Emergency PO $\rightarrow$ Spot PO $\rightarrow$ RFQ $\rightarrow$ RFP).
7. **PO Consolidation:** Clusters qualifying PO requirements sharing supplier, plant, route, and within 24 hours of first requirement.

---

## 3. Employee Chat Intake (Java-Based NLP)

Rather than using unreliable external LLMs or multi-field forms, employee intake is powered by a robust conversational NLP engine implemented in Java:
- **Intent and Entity Extraction:** Regex and token parsing extract item code, item description, quantity, plant (`CHK1` / `PUN1`), need-by date (supporting ISO dates and relative phrasing like *"by next Friday"* or *"tomorrow"*), and urgency (`Normal` / `Emergency`).
- **Conversational State Machine:** Tracks missing fields and maintains dialog state across messages (`ACTIVE`, `CLARIFICATION_NEEDED`, `CONFIRMATION_PENDING`, `COMPLETED`).
- **Disambiguation:** If multiple catalog items match, presents candidate choices.
- **New Item Handling:** Gracefully handles unknown items without inventing fake item codes, and prompts requester for an estimated unit price.
- **Explicit Confirmation:** Displays a structured summary card before writing the PR to the database.

---

## 4. Policy Management (Policy as Data)

All procurement policies and decision rules are stored as versioned entities in PostgreSQL rather than hardcoded Java conditionals:
- **Generic Condition Evaluator:** Rules store JSON-encoded criteria (`itemType`, `category`, `plant`, `urgency`, `maxValue`, `maxValueExclusive`, `minValue`, `contractEligible`, `requiresPastPurchase365d`).
- **PDF Amendment Extraction:** Apache PDFBox extracts raw text from uploaded amendment PDFs. The parser identifies changed thresholds (e.g., Spot PO limit increases, Emergency PO limits) and outputs a plain-language explanation of changes.
- **Effective Date Versioning:** Requirements are judged against the policy version active as of their requirement date.
- **Zero Code Changes:** Verified by automated tests, policy amendments alter procurement routes and decisions without recompiling or changing Java source code.

---

## 5. Deployment & Runtime Architecture

### 5.1 Single Combined Web Service on Render
- **Packaging:** React frontend production assets (`index.html`, `assets/`) are compiled and embedded directly into Spring Boot static resources (`/static/`).
- **Client-Side Routing:** Configured via `WebConfig.java` using Spring's `PathResourceResolver`:
  - Declines any requests under `/api/**` so that REST controllers handle API calls and return JSON 404s for invalid endpoints.
  - Serves static assets (`assets/**`) directly.
  - Forwards all SPA navigation paths (`/buyer`, `/chat`, `/policy`, browser refreshes) to `index.html`.
- **Single Public URL:** Eliminates CORS issues, cross-domain cookie complexities, and multiple cloud services.

### 5.2 Multi-Stage Docker Build
Root `Dockerfile` utilizes a 3-stage container build:
1. **Stage 1 (Node 20 Alpine):** Installs frontend dependencies and executes `tsc && vite build`.
2. **Stage 2 (Maven 3.9 + Eclipse Temurin 21 Alpine):** Copies built static assets into Spring Boot resources and packages the fat JAR (`mvn clean package -DskipTests`).
3. **Stage 3 (Eclipse Temurin 21 JRE Alpine):** Copies the fat JAR and submission directories into a hardened, minimal runtime image exposing port 8080.

### 5.3 External Neon PostgreSQL & Connection Normalization
- Configured via environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- **URL Normalizer:** `DataSourceConfig.java` parses standard Neon connection URIs (`postgresql://` or `postgres://`), extracts user credentials if embedded, and constructs valid JDBC connection URLs (`jdbc:postgresql://...`) with SSL support.
- **Connection Pool:** HikariCP tuned for serverless PostgreSQL (`maximum-pool-size: 10`, `idle-timeout: 300000ms`, `connection-timeout: 20000ms`).

### 5.4 Deployment-Safe Master Data Ingestion
To comply with data confidentiality rules without publishing proprietary task datasets to public repositories:
- `task_kit/` is strictly excluded from Git and Docker build contexts via `.gitignore` and `.dockerignore`.
- **Runtime Ingestion Methods:**
  1. **In-Browser UI:** Buyer Workspace provides a Master Data modal and status inspector showing live DB counts, allowing direct upload of the assignment ZIP package (`Cantill_FullStack_Task_Kit.zip`) or individual CSVs.
  2. **REST API:** `POST /api/master-data/upload` accepts ZIP archives or CSVs and ingests them via `MasterDataService.importFromZip()`.
  3. **Encrypted Env Var:** Optional `MASTER_DATA_BASE64` in Render auto-seeds the database on cold startup.
  4. **Persistence:** Data ingested once into Neon PostgreSQL persists permanently across container restarts and redeployments.
  5. **Local Compatibility:** Local development and unit tests continue to auto-import from local `task_kit` directory paths seamlessly.

---

## 6. Tools, Technologies & AI Disclosure ("Tell us what you used")

In compliance with Ground Rule 7 (*"AI tools are allowed, both for coding and inside your app (for chat and policy reading). Say what you used in APPROACH.md"*):

### 6.1 Full-Stack Technologies & Libraries Used
- **Backend Framework & Language:** Java 21, Spring Boot 3.3.5 (Spring Web, Spring Data JPA, Hibernate, Bean Validation).
- **Frontend Framework & Tooling:** React 18, TypeScript 5, Vite 5, Tailwind CSS 3, Lucide React (icons).
- **Document & Data Processing:**
  - `Apache Commons CSV 1.11.0` — Deterministic CSV parsing, sanitization, and export across RFC-4180 standard formats.
  - `Apache PDFBox 3.0.3` — Extraction of text, clauses, and structured terms from rate contract PDFs and policy amendment documents.
- **Database & Migration:**
  - `Neon Serverless PostgreSQL 16` — Primary cloud production relational database.
  - `Flyway Core & Flyway PostgreSQL 10.15.2` — Repeatable database migrations and schema versioning (`V1__init_schema.sql`).
  - `HikariCP` — High-performance JDBC connection pooling tuned for serverless PostgreSQL.
  - `H2 Database 2.2.x` — In-memory relational database fallback for automated unit testing.
- **Containerization & Hosting:**
  - `Docker` — Multi-stage containerization (`node:20-alpine`, `maven:3.9.9-eclipse-temurin-21-alpine`, `eclipse-temurin:21-jre-alpine`).
  - `Render` — Single unified Web Service hosting both static SPA assets and backend REST APIs.

### 6.2 AI Coding Tools Used During Development
- **Antigravity AI Agent (Google DeepMind):** Used as an autonomous engineering partner for boilerplate code scaffolding, constructing Flyway DDL schemas, implementing deterministic test assertions for the 14 worked examples, conducting frontend cleanup, and preparing cloud deployment blueprints.

### 6.3 In-App Intelligence & Processing Strategy
- **Zero Paid/External LLM Dependencies:** The production application **does not call external AI APIs** (e.g. OpenAI GPT-4, Anthropic Claude, Google Gemini, or Ollama).
- **Why Deterministic Java NLP Was Selected Over External LLMs:**
  1. **Strict Explainability & Zero Hallucination:** In enterprise procurement, a hallucinated supplier code, altered rate, or fabricated catalog item can cause legal and financial liability. Deterministic regex token parsing guarantees 100% reproducible entity extraction.
  2. **Predictable Cold-Start & Latency:** In-memory Java string analysis executes in sub-millisecond timeframes without external API rate limits, timeouts, or token costs.
  3. **Data Privacy & Compliance:** No company purchase requests or supplier pricing data leave the application boundary.
  4. **Dynamic Policy Updates Without Code:** Policy amendments are parsed by Apache PDFBox and stored as relational rules in PostgreSQL, allowing dynamic policy updates (e.g., v5.0 $\rightarrow$ v5.1) without touching Java code or relying on unpredictable LLM prompt engineering.
