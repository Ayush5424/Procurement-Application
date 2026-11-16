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
