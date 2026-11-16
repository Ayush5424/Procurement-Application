# Cantill.ai — Policy-Driven PR-to-PO Procurement Engine

> A full-stack, policy-driven procurement application designed for Corvo Engineering Pvt Ltd. Translates purchase requisitions into purchase orders or sourcing requests strictly following **Corvo Procurement Policy v5.0** and dynamic policy amendments.

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Technology Stack](#technology-stack)
3. [Key Features](#key-features)
   - [Employee Chat (PR Creation)](#1-employee-chat-pr-creation)
   - [Buyer Workspace & Decision Engine](#2-buyer-workspace--decision-engine)
   - [Policy Management & PDF Amendment](#3-policy-management--pdf-amendment)
4. [Procurement Decision Engine & Rules](#procurement-decision-engine--rules)
5. [Local Setup & Running](#local-setup--running)
   - [Option A: Running with Docker Compose (Recommended)](#option-a-running-with-docker-compose-recommended)
   - [Option B: Running Locally (Backend + Frontend)](#option-b-running-locally-backend--frontend)
6. [API Endpoints](#api-endpoints)
7. [Running Tests](#running-tests)
8. [Production Deployment](#production-deployment)
9. [Architecture & Decision Documentation](#architecture--decision-documentation)

---

## System Architecture

Cantill.ai is built as a single, modular Spring Boot 3 backend paired with a high-performance React + TypeScript frontend, backed by PostgreSQL.

```
+-------------------------------------------------------------------------+
|                        React + Vite + Tailwind CSS                      |
|      [Employee Chat]        [Buyer Workspace]      [Policy Management]  |
+------------------------------------+------------------------------------+
                                     | REST APIs (JSON / Multipart / CSV)
+------------------------------------v------------------------------------+
|                    Spring Boot 3.3.x Backend (Java 21)                  |
|                                                                         |
|  [REST Controllers]                                                     |
|    - EmployeeChatController        - BuyerWorkspaceController           |
|    - BatchProcessingController     - PolicyManagementController         |
|                                                                         |
|  [Core Services & Domain Engine]                                        |
|    - ItemMatchingService (Rule-based & Token-Similarity Matcher)        |
|    - ClubbingService (14-day creation / 7-day need-by boundary)         |
|    - ValuationService (Contract Rate -> PO History -> Std Price)        |
|    - ContractEngineService (Live Ceiling Tracking & PDF Extraction)     |
|    - SupplierRankingService (Composite Scoring Model)                   |
|    - ProcurementDecisionEngine (Generic Policy Rule Evaluator)          |
|    - PoConsolidationService (24-hour window PO clustering)              |
|    - MasterDataService (Repeatable CSV Ingestion)                       |
|    - ContractPdfService (Apache PDFBox Contract Ingestion)              |
|                                                                         |
|  [Spring Data JPA Repositories & Relational Mapping]                    |
+------------------------------------+------------------------------------+
                                     |
+------------------------------------v------------------------------------+
|                      PostgreSQL / Flyway Migrations                     |
|  - Items, Suppliers, Plants, PO History, Contracts, Contract Lines      |
|  - Purchase Requisitions, PR Lines, Requirements, PO Records            |
|  - Policy Versions, Policy Rules, Audit Records, Chat Sessions          |
+-------------------------------------------------------------------------+
```

---

## Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.5, Spring Web, Spring Data JPA, Hibernate, Bean Validation, Apache Commons CSV, Apache PDFBox, Flyway, Maven.
- **Frontend:** React 18, TypeScript, Vite, Tailwind CSS, Lucide React icons.
- **Database:** PostgreSQL 16 (or Neon Serverless PostgreSQL in production; embedded H2 fallback for testing).
- **Zero External AI / LLM APIs:** All natural language understanding, entity extraction, item resolution, supplier ranking, and rule evaluations run deterministically inside Java — guaranteeing 100% explainability, auditability, and zero hallucination.

---

## Key Features

### 1. Employee Chat (PR Creation)
- **Natural Language Intake:** Requester types conversational messages (e.g., *"Need 200 nitrile gloves for Chakan by next Friday"*).
- **Deterministic Java NLP:** Identifies items against the catalog, extracts quantities, plants (`CHK1` / `PUN1`), urgency (`Normal` / `Emergency`), and need-by dates (ISO dates or relative phrasing like *"tomorrow"*, *"by next Friday"*).
- **Clarification & Disambiguation:** Asks targeted follow-ups when details are missing or multiple catalog items match.
- **New Item Handling:** Classifies unknown items cleanly as `NEW` without inventing item codes; prompts requester for an estimated unit price.
- **Confirmation Flow:** Renders a structured request summary before persisting the Purchase Requisition.

### 2. Buyer Workspace & Decision Engine
- **Batch CSV Upload:** Upload any PR batch CSV and process it end-to-end through the Corvo Procurement Decision Engine.
- **Explainable Decisions:** Every line details its clubbed requirement ID, valuation breakdown, applicable contract, route selected, and audit trail reason.
- **PO Consolidation:** Groups eligible PO requirements (same supplier, plant, route within 24 hours) to minimize operational overhead.
- **Supplier Ranking & Export:** Computes deterministic composite supplier rankings ($w_{po}: 25\%$, $w_{rec}: 25\%$, $w_{con}: 20\%$, $w_{ot}: 15\%$, $w_{qual}: 15\%$) for every item as of 1 Nov 2026, exportable directly as CSV.
- **PDF Contract Extraction:** Upload signed contract PDFs (including contracts missing from register such as `RC-2026-031` and `RC-2026-033`). Extracts supplier, dates, ceiling, and line items with approval workflow before activation.

### 3. Policy Management & PDF Amendment
- **Policy as Data:** Rules and thresholds are stored as database entities rather than hardcoded Java conditionals.
- **PDF Amendment Extraction:** Upload policy amendment PDFs (e.g., Corvo Procurement Policy v5.1). Automatically extracts changed thresholds (e.g., Spot PO limit increases) using Apache PDFBox.
- **Zero Code Modification:** Activating a new policy version instantly applies new rules to future requirements without restarting or recompiling the application.

---

## Procurement Decision Engine & Rules

Evaluation strictly follows **Corvo Procurement Policy v5.0** in order:

| Step | Rule | Condition | Decision | Route | Supplier Sourcing |
|---|---|---|---|---|---|
| **1** | **Capex** | Item type is `Capex` | `SOURCING_REQUEST` | `CAPEX_SOURCING` | Suggest 5 suppliers; Capex approval required |
| **2** | **Contract PO** | Eligible active contract with remaining ceiling $\ge$ requirement value | `PURCHASE_ORDER` | `CONTRACT_PO` | Issue PO to contract supplier at contract rate |
| **3** | **Emergency PO** | Requisition marked `Emergency` and value $\le$ ₹5,00,000 | `PURCHASE_ORDER` | `EMERGENCY_PO` | Rank 1 preferred supplier; emergency approval required |
| **4** | **Spot PO** | Normal urgency, value $\le$ ₹2,00,000 (or ₹3,00,000 in v5.1) AND purchased in last 365 days | `PURCHASE_ORDER` | `SPOT_PO` | Lowest-price supplier from recent PO history |
| **5** | **RFQ** | Normal urgency, value between Spot limit and ₹25,00,000 (or ceiling exhausted) | `SOURCING_REQUEST` | `RFQ` | Suggest 3 suppliers from preferred ranking |
| **6** | **RFP** | Normal urgency, value $>$ ₹25,00,000 | `SOURCING_REQUEST` | `RFP` | Suggest 5 suppliers; formal RFP process |

### Clubbing Rules (Clause 2.2)
- Same resolved item code and same plant.
- Created within 14 calendar days of the requirement's first PR line ($0 \le \Delta t \le 14$).
- Need-by date within $\pm 7$ calendar days of the requirement's first line need-by date.
- Joins the earliest qualifying open requirement.
- New items (`NEW`) are never clubbed.

---

## Local Setup & Running

### Prerequisites
- Git
- Java 21 JDK
- Maven 3.9+
- Node.js 18+ and npm
- PostgreSQL 16 (or Docker)

---

### Option A: Running with Docker Compose (Recommended)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Ayush5424/Procurement-Application.git
   cd Procurement-Application
   ```

2. **Start the complete stack:**
   ```bash
   docker-compose up --build
   ```

3. **Access the application:**
   - Frontend UI: `http://localhost:3000`
   - Backend REST APIs: `http://localhost:8080`
   - PostgreSQL Database: `localhost:5432` (db: `cantill_procurement`, user: `cantill`, password: `cantill_password`)

---

### Option B: Running Locally (Backend + Frontend)

#### 1. Setup Database
Ensure PostgreSQL is running locally, then create the database:
```sql
CREATE DATABASE cantill_procurement;
CREATE USER cantill WITH ENCRYPTED PASSWORD 'cantill_password';
GRANT ALL PRIVILEGES ON DATABASE cantill_procurement TO cantill;
```

#### 2. Start Spring Boot Backend
```bash
cd backend
mvn clean spring-boot:run
```
*Note: Flyway migrations run automatically on startup to seed schemas, master data, policy v5.0 rules, and registered contracts.*

The backend runs at `http://localhost:8080`.

#### 3. Start React Frontend
```bash
cd frontend
npm install
npm run dev
```

The frontend runs at `http://localhost:5173` (configured with Vite proxy to forward `/api` requests to `http://localhost:8080`).

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat/message` | Conversational PR intake; parses message and manages session state |
| `POST` | `/api/chat/confirm` | Confirms draft requisition and persists PR into database |
| `POST` | `/api/chat/reset` | Clears conversation state and restarts chat session |
| `POST` | `/api/process-batch` | Uploads PR batch CSV; executes clubbing, valuation, and policy routing |
| `GET` | `/api/buyer/dashboard` | Returns all requirements, POs, and sourcing requests |
| `GET` | `/api/buyer/export-decisions` | Exports batch decisions as standard CSV |
| `GET` | `/api/buyer/export-preferred-suppliers` | Exports preferred supplier rankings as of 1 Nov 2026 as CSV |
| `GET` | `/api/buyer/contracts` | Lists all active contracts and remaining ceiling balances |
| `POST` | `/api/buyer/contracts/upload-pdf` | Extracts contract details from signed PDF via PDFBox |
| `POST` | `/api/buyer/contracts/confirm-pdf` | Approves and registers an extracted PDF contract |
| `GET` | `/api/policies` | Lists all procurement policy versions and active status |
| `POST` | `/api/policies/upload-amendment` | Parses policy amendment PDF and returns extracted changes |
| `POST` | `/api/policies/{version}/activate` | Activates target policy version immediately |

---

## Running Tests

Automated test suites validate all 14 worked examples, composite supplier rankings, dynamic policy amendments, and conversational intake:

```bash
cd backend
mvn test
```

### Test Coverage Highlights
- `MasterDataServiceTest`: Validates ingestion of items, suppliers, PO history, plants, contracts, and initial v5.0 policy rules.
- `DecisionEngineWorkedExamplesTest`:
  - Validates exact preferred supplier rankings for `ITM-1048` (Gloves), `ITM-1036` (Pneumatic cylinder), and `ITM-1016` (MIG wire).
  - Validates batch processing of sample batch lines matching Worked Examples 1, 2, 7, 8, 12, 13.
- `PolicyAmendmentTest`: Validates that activating policy v5.1 changes procurement route for ₹2.6L requisition from `RFQ` to `SPOT_PO` with zero Java code modifications.
- `EmployeeChatServiceTest`: Validates end-to-end multi-turn conversational intake, entity extraction, disambiguation, and confirmation.

---

## Production Deployment

### Backend Deployment (Render)
1. Link GitHub repository `Ayush5424/Procurement-Application` to Render.
2. The included [`render.yaml`](render.yaml) automatically provisions:
   - Web service using `backend/Dockerfile` on Java 21.
   - Connected Neon PostgreSQL instance.
   - Environment variables: `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

### Frontend Deployment (Vercel / Render)
1. Set Root Directory to `frontend`.
2. Build Command: `npm run build`
3. Output Directory: `dist`
4. Set Environment Variable: `VITE_API_BASE_URL` pointing to the deployed backend URL (e.g. `https://procurement-application-api.onrender.com`).

---

## Architecture & Decision Documentation

- [APPROACH.md](APPROACH.md) — Technical approach, architecture diagrams, deterministic Java NLP, and AI tools disclosure.
- [DECISIONS.md](DECISIONS.md) — Business decisions, ambiguities resolved, edge cases, ranking weights, and contract ceiling handling.
