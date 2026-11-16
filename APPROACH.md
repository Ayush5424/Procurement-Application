# Technical Approach & Architecture (APPROACH.md)

**Project:** Cantill.ai — Policy-Driven PR-to-PO Procurement Engine  
**Author:** Senior Full-Stack Engineering Team  

---

## 1. System Architecture

The application is structured as a clean, modular full-stack web application designed for correctness, deterministic decision-making, explainability, and zero code-modification policy updates.

```
+-------------------------------------------------------------------------+
|                        React + Vite + Tailwind CSS                      |
|      [Employee Chat]        [Buyer Workspace]      [Policy Management]  |
+------------------------------------+------------------------------------+
                                     | REST APIs / JSON / Multipart
+------------------------------------v------------------------------------+
|                    Spring Boot 3.3.x Backend (Java 21)                  |
|                                                                         |
|  [Controllers]                                                          |
|    - EmployeeChatController        - BuyerWorkspaceController           |
|    - BatchProcessingController     - PolicyManagementController         |
|                                                                         |
|  [Core Services & Domain Engine]                                        |
|    - ItemMatchingService (Rule-based & Token-Similarity Matcher)        |
|    - ClubbingService (14-day creation / 7-day need-by boundary)         |
|    - ValuationService (Rate Contract -> PO History -> Std Price)        |
|    - ContractEngineService (Live Ceiling Tracking & PDF Extraction)     |
|    - SupplierRankingService (Composite Scoring Model)                   |
|    - ProcurementDecisionEngine (Generic Policy Rule Evaluator)          |
|    - PoConsolidationService (24-hour window PO clustering)              |
|    - MasterDataService (Repeatable CSV Master Ingestion)                |
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
- **Disambiguation:** If multiple catalog items match, presents clickable candidate choices.
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

## 5. AI Tools & Functionality Disclosure

### AI Coding Tools Used During Development
- Antigravity AI agent assisted in codebase scaffolding, writing Flyway migrations, implementing test cases for worked examples, and creating documentation.

### AI Functionality Inside the Application
- The application itself **does not** depend on paid external LLMs, OpenAI, Claude, Gemini, or Ollama APIs.
- All procurement decisions, item matching, entity extraction, supplier ranking, and rule evaluation are entirely deterministic, explainable, and executed locally in Java.
- This guarantees zero hallucination, strict auditability for compliance, and reproducibility required for enterprise procurement operations.
