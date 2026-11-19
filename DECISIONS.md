# Business Decisions, Assumptions & Edge Cases (DECISIONS.md)

**Project:** Cantill.ai — Policy-Driven PR-to-PO Procurement Engine  
**Organization:** Corvo Engineering Pvt Ltd  
**Policy Version:** 5.0 (and versioned amendments)  
**Author:** Senior Full-Stack Engineering Team  

---

## 1. Executive Summary

This document records all architectural and business assumptions, ambiguities observed in the task data and policy text, the exact preferred supplier ranking algorithm, contract handling rules, and edge case resolutions implemented in the application.

---

## 2. Ambiguities & Business Decisions

### 2.1 Preferred Supplier Ranking Formula & Weights

#### Policy Text (Clause 6.1 & 6.2)
> "Every Active supplier that has either (a) at least one PO for the item in the 548 days before the date, or (b) a contract covering the item that started on or before the date (even if it has since expired)... Procurement looks at: how often we have bought the item from them, how recently, whether they have held a contract for the item, and their on-time delivery and quality record. Procurement leaves the exact weighting to the system design, but it must be consistent, explainable to a buyer, and recorded."

#### Observed Data from Worked Examples (Page 5)
1. **ITM-1048 (Nitrile coated gloves) as of 1 Nov 2026:**
   - **S029:** POs: 7, Recency: 1.0, Contract: YES, On-Time: 81%, Quality: 3.1 $\rightarrow$ **Rank 1**
   - **S030:** POs: 4, Recency: 1.0, Contract: NO, On-Time: 72%, Quality: 3.4 $\rightarrow$ **Rank 2**
   - **S031 (Inactive):** Excluded entirely.
2. **ITM-1036 (Pneumatic cylinder 50x100) as of 1 Nov 2026:**
   - **S023:** POs: 2, Recency: 1.0, Contract: NO, On-Time: 74%, Quality: 3.9 $\rightarrow$ **Rank 1**
   - **S022:** POs: 2, Recency: 0.5, Contract: NO, On-Time: 77%, Quality: 3.5 $\rightarrow$ **Rank 2**
   - **S024:** POs: 1, Recency: 0.0, Contract: NO, On-Time: 89%, Quality: 4.1 $\rightarrow$ **Rank 3**
3. **ITM-1016 (MIG wire ER70S-6 1.2mm) as of 1 Nov 2026:**
   - **S009:** POs: 1, Recency: 0.5, Contract: YES, On-Time: 83%, Quality: 4.6 $\rightarrow$ **Rank 1**
   - **S010:** POs: 1, Recency: 0.5, Contract: NO, On-Time: 80%, Quality: 3.9 $\rightarrow$ **Rank 2**

#### Ranking Algorithm Implemented
To ensure 100% determinism, explainability, and reproducibility matching the worked examples, we formulated the composite scoring model:

$$\text{Score} = (w_{po} \cdot \text{PO\_Factor}) + (w_{rec} \cdot \text{Recency}) + (w_{con} \cdot \text{Contract}) + (w_{ot} \cdot \text{OnTime\_Factor}) + (w_{qual} \cdot \text{Quality\_Factor})$$

Where weights sum to 100:
- **Purchase Frequency ($w_{po} = 25$):** $\text{PO\_Factor} = \min\left(\frac{\text{PO Count}}{5}, 1.0\right) \times 25$
- **Purchase Recency ($w_{rec} = 25$):** Defined per policy guidance:
  - Last PO within 90 days: $1.0 \times 25 = 25$
  - Last PO within 91 to 365 days: $0.5 \times 25 = 12.5$
  - Older than 365 days or no PO: $0.0 \times 25 = 0$
- **Contract History ($w_{con} = 20$):** $1.0 \times 20 = 20$ if the supplier held any contract covering the item starting on or before evaluation date, else 0.
- **On-Time Delivery Performance ($w_{ot} = 15$):** $\left(\frac{\text{on\_time\_pct}}{100}\right) \times 15$
- **Quality Rating Performance ($w_{qual} = 15$):** $\left(\frac{\text{quality\_rating}}{5.0}\right) \times 15$

#### Tie-Breaking Order
If two suppliers obtain identical total scores, ties are broken deterministically by:
1. Higher Quality Rating
2. Higher On-Time Delivery Percentage
3. Lexicographical `supplier_id` (e.g., S001 before S002).

---

### 2.2 Sourcing Suggestions Fallback

#### What was noticed
For RFQ (requires 3 suggestions), RFP (requires 5), or Capex (requires 5), preferred supplier lists often contain fewer than 3 or 5 eligible suppliers.
#### What was decided
Per Clause 6.4:
1. Start with the preferred supplier list in ranking order.
2. If fewer suppliers than needed are available, top up with other **Active** suppliers listing the item's category in the supplier master.
3. Exclude Blocked and Inactive suppliers.
4. Additional suppliers are ranked by:
   $$\text{FallbackScore} = \left(\frac{\text{Quality Rating}}{5.0} \times 50\right) + \text{On-Time Delivery Pct}$$
5. New items (`NEW`) receive zero supplier suggestions (buyer sources manually).

---

### 2.3 Rate Contracts & Ceiling Consumption

#### What was noticed
1. Contracts in `contract_register.csv` reflect consumption as of 31 October 2026.
2. Two contracts existed only in PDF format in `contracts/pdf`:
   - `RC-2026-031` (Pneumatic Point, S021, valid from 2026-11-15, INR 20,00,000 ceiling)
   - `RC-2026-033` (Infoline Systems, S035, valid from 2026-11-20, INR 50,00,000 ceiling)
3. Clause 4.2 states: "Consumed values in the register are as at its last update; contracts signed after that update start at zero."
4. When multiple contracts apply to an item (e.g. Hex bolts covered by both `RC-2026-015` and `RC-2026-013`):
   - Choose the lowest-rate contract that still has sufficient ceiling room.
   - If lower-rate `RC-2026-015` ceiling is exhausted, fall back to next lowest-rate `RC-2026-013`.
   - When all applicable contracts are out of ceiling, value requirement at lowest applicable contract rate and route to RFQ/RFP.

---

### 2.4 PR Clubbing Boundaries

#### What was noticed
PR lines arrive with timestamps and need-by dates. Clause 2.2 specifies:
- Same item and same plant.
- Created within 14 calendar days of the requirement's first line ($0 \le \text{days} \le 14$).
- Need-by date within 7 days either side of the first line's need-by date ($-7 \le \text{days} \le +7$).
- Joins the earliest qualifying open requirement.
- New items (`NEW`) are never clubbed.

#### Edge Cases Handled:
- **Same item, different plants (Worked Example 6):** `PR-26-00106/1` (PUN1) and `PR-26-00103/1` (CHK1) are separated into two distinct requirement groups.
- **Emergency Propagation (Clause 2.3):** If any PR line in a clubbed requirement is marked `Emergency`, the entire requirement inherits `Emergency` status.

---

### 2.5 PO Consolidation

#### What was noticed
Clause 7.1 & 7.2:
- Requirements that end in a PO are combined into one PO when they share:
  1. Same supplier
  2. Same plant
  3. Same procurement route (e.g., `CONTRACT_PO`, `SPOT_PO`, `EMERGENCY_PO`)
  4. Raised within 24 hours of the first requirement on that PO (measured from creation timestamp of the first line of each requirement).
- Sourcing requests (`RFQ`, `RFP`, `CAPEX_SOURCING`, `NEW_ITEM_SOURCING`) are **never** consolidated into POs.
- Consolidated PO IDs (`PO-0001`, `PO-0002`...) are shared across all PR lines belonging to the consolidated requirements.

---

### 2.6 Unprocessed Lines Handling

If an input PR line contains malformed dates, negative quantities, or cannot be parsed, the engine marks the line as `NOT_PROCESSED` with a detailed explanation in `reason`, satisfying Section 12 requirements.

---

### 2.7 Deployment Architecture Decision: Combined Web Service vs. Separate Static Site

#### What was noticed
Render supports deploying frontend and backend either as two distinct services (a Static Site for React and a Web Service for Spring Boot) or as a single combined Docker Web Service.

#### What was decided
We selected **ONE combined Web Service** on Render:
1. **Single Public URL:** Eliminates CORS configurations, cross-origin preflight requests, and cookie/session domain issues.
2. **Simplified Operational Footprint:** One unified build and health check (`/api/health`) across the entire stack.
3. **Atomic Deployments:** Frontend assets and backend API contracts are packaged together in the same container, eliminating version drift between client and server.
4. **Zero-Lag Routing:** API requests route internally over `localhost` rather than crossing external public network boundaries.

---

### 2.8 Confidential Master Data & Deployment-Safe Runtime Ingestion

#### What was noticed
1. Assignment instructions strictly prohibit committing proprietary or confidential task datasets and PDFs (`task_kit/`) to public GitHub repositories.
2. Simply removing `COPY task_kit` from Dockerfile allows Render Docker builds to pass, but leaves a freshly provisioned cloud database empty—breaking item resolution, supplier ranking, and contract matching during evaluation.
3. Introducing synthetic or mock data is unacceptable as it violates Corvo Procurement Policy consistency.

#### What was decided
We designed a four-tier deployment-safe master data strategy:
1. **Repository Security:** `task_kit/`, `.zip`, and local database files are excluded from Git (`.gitignore`) and Docker build contexts (`.dockerignore`).
2. **Stream & ZIP Ingestion:** Extended `MasterDataService` to accept stream readers and standard ZIP packages (`importFromZip`), parsing CSVs in logical sequence (plants $\rightarrow$ items $\rightarrow$ suppliers $\rightarrow$ po_history $\rightarrow$ contracts $\rightarrow$ contract_lines).
3. **In-Browser Master Data Management:** In Buyer Workspace, a Master Data status inspector displays real-time record counts. If the database is uninitialized, an alert banner enables the user to upload the task kit ZIP or CSVs directly through the UI.
4. **Encrypted Environment Variable Option:** Supported an optional `MASTER_DATA_BASE64` variable in `render.yaml` (stored privately in Render) for automated zero-touch seeding on startup.
5. **Persistent Neon PostgreSQL:** Data ingested once into Neon persists permanently across redeployments, container rebuilds, and scale-to-zero cycles.
6. **Local Developer Workflow Preserved:** `MasterDataService.autoImportIfDataAvailable()` continues to auto-discover local `task_kit` directory paths, ensuring local test suites run with 100% test passing rates.

---

### 2.9 Neon Serverless PostgreSQL Connection Normalization

#### What was noticed
Neon connection strings copied from the Neon console frequently use direct URI formats (e.g. `postgresql://user:pass@ep-xyz.aws.neon.tech/neondb?sslmode=require`), whereas standard PostgreSQL JDBC drivers require the `jdbc:postgresql://host:port/db` scheme and fail if passed raw URIs.

#### What was decided
Implemented custom connection normalizer in `DataSourceConfig.java`:
1. Detects `postgres://` and `postgresql://` URI prefixes.
2. Extracts user credentials (`userInfo`) and passes them cleanly to `DataSourceProperties`.
3. Reconstructs valid JDBC connection URLs with SSL parameters intact.
4. Falls back cleanly to standard JDBC strings, ensuring flawless connection regardless of which format the user pastes into Render environment variables.

---

### 2.10 Client-Side SPA Routing & REST API Isolation

#### What was noticed
When hosting a React Single Page Application (SPA) inside Spring Boot, direct navigation to client routes (e.g., `/buyer`, `/policy`, `/chat`) or browser page refreshes will request those paths from the Spring Boot web server, which typically results in 404 Not Found.

#### What was decided
Configured a custom `PathResourceResolver` in `WebConfig.java`:
1. **API Protection:** Never intercepts requests beginning with `/api/` or `api/`, ensuring all REST controllers handle API calls and return appropriate JSON error payloads for non-existent endpoints.
2. **Static Asset Pass-Through:** Serves existing static files (`assets/*.js`, `assets/*.css`, images) directly from `/static/`. Missing static assets with extensions return standard 404s.
3. **Client-Side Forwarding:** All HTML navigation routes without file extensions are forwarded to `/static/index.html`, allowing React Router / view state machines to mount and render seamlessly.

---

### 2.11 RFQ vs. RFP Threshold Discrepancy Resolution

#### What was noticed
1. A summary table in the general task brief suggested an RFQ threshold of ₹5,00,000.
2. In contrast, the formal governing policy document (**Corvo Procurement Policy v5.0**, Clauses 5.5 and 5.6) explicitly states:
   - **Clause 5.5 (RFQ):** *"For any requirement whose value is below ₹10,00,000... Procurement solicits quotes from three suppliers."*
   - **Clause 5.6 (RFP):** *"For any requirement whose value is ₹10,00,000 or more, Procurement conducts a formal Request for Proposal with at least five suppliers."*
3. Furthermore, Worked Examples (Worked Examples 12 and 14) consistently confirm ₹10,00,000 as the boundary dividing RFQ from RFP.

#### What was decided
The formal **Corvo Procurement Policy v5.0** is the binding source of truth. The engine strictly implements:
- Requirement value $< \text{INR 10,00,000}$: Route to `RFQ` with 3 suggested suppliers.
- Requirement value $\ge \text{INR 10,00,000}$: Route to `RFP` with 5 suggested suppliers.

---

### 2.12 Zero Code-Change Policy Amendment Guarantee

#### What was noticed
Ground Rule 5 states: *"No code changes after the build deadline. The amendment must work through your app's own policy-update feature. We check the commit history."*

#### What was decided
1. Procurement rules and thresholds are modeled as relational entities in PostgreSQL (`policy_versions` and `policy_rules`), evaluated dynamically by `ProcurementDecisionEngine`.
2. When the v5.1 amendment is uploaded via the UI or API:
   - Apache PDFBox extracts the text and parses modified thresholds (e.g., Spot PO limit increases to ₹3,00,000).
   - The user reviews the proposal and clicks "Approve & Activate".
   - Version 5.1 is created, and future requirements are evaluated against the new thresholds immediately.
3. Zero Java or TypeScript files are modified during or after policy activation. This is verified by automated test `PolicyAmendmentTest.java`.
4. Between 10:00 AM and 12:00 noon on 19 Nov (the blind-test window), only the three requested output CSVs are committed to `submission/` (`Ayush_Abhinav__run1.csv`, `Ayush_Abhinav__run2.csv`, `Ayush_Abhinav__preferred.csv`). No application code commits will be made.

---

### 2.13 Blind Test Execution & Policy Amendment No. 1 (v5.1) Record

#### Context & Ground Rules
On November 19, 2026, the blind test kit was provided containing:
1. `hidden_pr_batch.csv`: 190 purchase requisition lines raised between 23 Nov and 11 Dec 2026.
2. `Corvo_Policy_Amendment_1_v5.1.pdf`: Amendment No. 1 to the procurement policy, effective 1 December 2026.

Per the strict evaluation ground rules:
- **Zero code changes** were made to the application codebase (no backend Java or frontend TypeScript modifications).
- All evaluations, policy activations, and batch exports were executed directly on the live deployed web service (`https://cantill-procurement.onrender.com`).

#### Amendment No. 1 Changes Implemented via App Policy Management
1. **Clause A1 (Packaging Spot Limit):**
   - For items in the *Packaging* category, the Spot PO threshold in rule 5.4 was reduced from ₹2,00,000 to ₹1,00,000.
   - Packaging requirements $\ge \text{INR 1,00,000}$ are strictly routed to sourcing (`RFQ` or `RFP`).
2. **Clause A2 (RFP Threshold):**
   - The boundary between `RFQ` and formal `RFP` (rules 5.5 and 5.6) was reduced from ₹10,00,000 to ₹8,00,000.
   - Sourcing requirements $< \text{INR 8,00,000}$ route to `RFQ` (3 suppliers); $\ge \text{INR 8,00,000}$ route to `RFP` (5 suppliers).
3. **Clause A3 (New Rule 5.2A — Chakan Electrical Safety):**
   - At the Chakan plant (`CHK1`), any requirement in the *Electrical Spares* category worth ₹50,000 or more must be sourced (`RFQ`, or `RFP` if $\ge \text{INR 8,00,000}$), even if it would otherwise qualify for an emergency or spot PO.
   - An applicable rate contract PO (rule 5.2) remains permitted and takes precedence.

#### Output Deliverables in `submission/`
1. `Ayush_Abhinav__run1.csv`: Processed 190 PR lines under Policy Version 5.0.
2. `Ayush_Abhinav__run2.csv`: Processed 190 PR lines after activating Policy Amendment No. 1 (Version 5.1). Key shifts observed:
   - Packaging items between ₹1,00,000 and ₹2,00,000 (e.g., ITM-1042 Wooden Pallet PR-26-00671..673) shifted from Spot PO to RFQ sourcing.
   - Sourcing requirements between ₹8,00,000 and ₹10,00,000 (e.g., ITM-1066 PR-26-00667) shifted from RFQ to RFP.
   - CHK1 Electrical Spares $\ge \text{INR 50,000}$ without contracts (e.g., ITM-1029 PR-26-00676..677) routed to RFQ under Rule 5.2A.
3. `Ayush_Abhinav__preferred.csv`: Complete top 3 preferred suppliers list evaluated as of 2026-12-01 across catalog items (`item_code,rank,supplier_id,reference_price`).

#### Edge Case & Boundary Audits Recorded
1. **PR-26-00666 (ITM-1011):** Initial valuation against an expired/exhausted contract rate (₹5.75) was under ₹2,00,000, but upon allocating preferred supplier S006 at ₹6.64, the final requirement value is ₹2,18,323.20. Under Clause 5.4, any requirement $\ge \text{INR 2,00,000}$ must not be issued as a Spot PO; it is routed to `RFQ` sourcing.
2. **PR-26-00674 (ITM-1034):** The raw batch row specified a quantity of 0 with a unit price of ₹40,929.88. Evaluating group value as ₹0.00 was identified as an edge case anomaly; the requirement value is properly reconciled to ₹40,929.88 (unit price basis) qualifying for Spot PO.
3. **Threshold Explanation Consistency in v5.1:** In `Ayush_Abhinav__run2.csv`, all sourcing decision explanations are synchronized with Amendment 1 thresholds (referencing ₹8L boundary rather than the v5.0 ₹10L threshold).
