-- V1__init_schema.sql: Initial schema for Cantill.ai Procurement Application

CREATE TABLE IF NOT EXISTS plants (
    code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS items (
    item_code VARCHAR(50) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    uom VARCHAR(20) NOT NULL,
    category VARCHAR(100) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    standard_price NUMERIC(15, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    categories VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    on_time_pct NUMERIC(5, 2) NOT NULL,
    quality_rating NUMERIC(3, 1) NOT NULL
);

CREATE TABLE IF NOT EXISTS po_history (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(50) NOT NULL,
    po_date DATE NOT NULL,
    supplier_id VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    quantity NUMERIC(15, 2) NOT NULL,
    uom VARCHAR(20) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    plant VARCHAR(10) NOT NULL,
    delivered_on_time BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_po_history_item_date ON po_history(item_code, po_date);
CREATE INDEX IF NOT EXISTS idx_po_history_supplier ON po_history(supplier_id);

CREATE TABLE IF NOT EXISTS contracts (
    contract_id VARCHAR(50) PRIMARY KEY,
    supplier_id VARCHAR(50) NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    value_cap NUMERIC(15, 2) NOT NULL,
    consumed_value NUMERIC(15, 2) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'REGISTER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS contract_lines (
    id BIGSERIAL PRIMARY KEY,
    contract_id VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    uom VARCHAR(20) NOT NULL,
    rate NUMERIC(15, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contract_lines_item ON contract_lines(item_code);
CREATE INDEX IF NOT EXISTS idx_contract_lines_contract ON contract_lines(contract_id);

CREATE TABLE IF NOT EXISTS purchase_requisitions (
    pr_no VARCHAR(50) PRIMARY KEY,
    requester VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    plant VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pr_lines (
    id BIGSERIAL PRIMARY KEY,
    pr_no VARCHAR(50) NOT NULL,
    pr_line INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    plant VARCHAR(10) NOT NULL,
    item_code VARCHAR(50),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(15, 2) NOT NULL,
    uom VARCHAR(20),
    estimated_unit_price NUMERIC(15, 2),
    need_by_date DATE NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_pr_lines_pr_no ON pr_lines(pr_no, pr_line);

CREATE TABLE IF NOT EXISTS requirement_groups (
    group_id VARCHAR(50) PRIMARY KEY,
    resolved_item_code VARCHAR(50) NOT NULL,
    plant VARCHAR(10) NOT NULL,
    first_pr_created_at TIMESTAMP NOT NULL,
    first_pr_need_by_date DATE NOT NULL,
    total_quantity NUMERIC(15, 2) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    requirement_value NUMERIC(15, 2) NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    decision VARCHAR(50) NOT NULL,
    route VARCHAR(50) NOT NULL,
    selected_supplier_id VARCHAR(50),
    contract_id_used VARCHAR(50),
    historical_po_used VARCHAR(50),
    suggested_supplier_ids VARCHAR(500),
    po_id VARCHAR(50),
    policy_version VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    ranking_explanation TEXT,
    valuation_explanation TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW'
);

CREATE TABLE IF NOT EXISTS requirement_pr_lines (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    pr_no VARCHAR(50) NOT NULL,
    pr_line INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_req_pr_lines ON requirement_pr_lines(group_id);

CREATE TABLE IF NOT EXISTS policy_versions (
    id BIGSERIAL PRIMARY KEY,
    version_number VARCHAR(50) UNIQUE NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS policy_rules (
    id BIGSERIAL PRIMARY KEY,
    policy_version_number VARCHAR(50) NOT NULL,
    priority INTEGER NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    outcome VARCHAR(50) NOT NULL,
    route VARCHAR(50) NOT NULL,
    conditions_json TEXT NOT NULL,
    supplier_suggestion_count INTEGER DEFAULT 0,
    requires_capex_approval BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS audit_records (
    id BIGSERIAL PRIMARY KEY,
    requirement_id VARCHAR(50) NOT NULL,
    original_decision VARCHAR(50) NOT NULL,
    original_route VARCHAR(50) NOT NULL,
    original_supplier_id VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    override_reason TEXT,
    new_decision VARCHAR(50),
    new_route VARCHAR(50),
    new_supplier_id VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id VARCHAR(50) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    draft_item_code VARCHAR(50),
    draft_description VARCHAR(255),
    draft_quantity NUMERIC(15, 2),
    draft_uom VARCHAR(20),
    draft_plant VARCHAR(10),
    draft_need_by_date DATE,
    draft_urgency VARCHAR(20),
    draft_estimated_unit_price NUMERIC(15, 2),
    created_pr_no VARCHAR(50),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
