export interface Item {
  itemCode: string;
  description: string;
  uom: string;
  category: string;
  itemType: string;
  standardPrice: number;
}

export interface Supplier {
  supplierId: string;
  name: string;
  city: string;
  categories: string;
  status: string;
  onTimePct: number;
  qualityRating: number;
}

export interface Contract {
  contractId: string;
  supplierId: string;
  supplierName: string;
  startDate: string;
  endDate: string;
  valueCap: number;
  consumedValue: number;
  sourceType: string;
  isActive: boolean;
}

export interface ContractLine {
  id?: number;
  contractId: string;
  itemCode: string;
  description: string;
  uom: string;
  rate: number;
}

export interface RequirementGroup {
  groupId: string;
  resolvedItemCode: string;
  plant: string;
  firstPrCreatedAt: string;
  firstPrNeedByDate: string;
  totalQuantity: number;
  unitPrice: number;
  requirementValue: number;
  urgency: string;
  decision: string;
  route: string;
  selectedSupplierId?: string;
  contractIdUsed?: string;
  historicalPoUsed?: string;
  suggestedSupplierIds?: string;
  poId?: string;
  policyVersion: string;
  reason: string;
  rankingExplanation?: string;
  valuationExplanation?: string;
  status: string;
}

export interface RequirementPrLine {
  id: number;
  groupId: string;
  prNo: string;
  prLine: number;
}

export interface AuditRecord {
  id: number;
  requirementId: string;
  originalDecision: string;
  originalRoute: string;
  originalSupplierId?: string;
  action: string;
  actor: string;
  timestamp: string;
  overrideReason?: string;
  newDecision?: string;
  newRoute?: string;
  newSupplierId?: string;
}

export interface RankedSupplier {
  rank: number;
  supplier: Supplier;
  totalScore: number;
  poCount: number;
  recency: number;
  hasContract: boolean;
  onTimePct: number;
  qualityRating: number;
  referencePrice?: number;
  priceSource: string;
  explanation: string;
}

export interface RequirementDetail {
  requirement: RequirementGroup;
  mappedPrLines: RequirementPrLine[];
  auditRecords: AuditRecord[];
  preferredSuppliers: RankedSupplier[];
}

export interface PolicyVersion {
  id: number;
  versionNumber: string;
  effectiveDate: string;
  status: string;
  description: string;
  createdAt: string;
}

export interface PolicyRule {
  id?: number;
  policyVersionNumber: string;
  priority: number;
  ruleCode: string;
  name: string;
  description: string;
  outcome: string;
  route: string;
  conditionsJson: string;
  supplierSuggestionCount?: number;
  requiresCapexApproval?: boolean;
}

export interface PolicyChangeProposal {
  newVersionNumber: string;
  effectiveDate: string;
  title: string;
  plainLanguageExplanation: string;
  proposedRules: PolicyRule[];
  detectedModifications: string[];
  rawText: string;
  requiresManualReview: boolean;
}

export interface ExtractedContract {
  contractId: string;
  supplierId: string;
  supplierName: string;
  startDate: string;
  endDate: string;
  valueCap: number;
  consumedValue: number;
  lines: ContractLine[];
  rawText: string;
  isValid: boolean;
}

export interface ChatMessage {
  id?: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  timestamp?: string;
}

export interface ChatResponse {
  sessionId: string;
  reply: string;
  status: string;
  session: any;
  candidateItems: Item[];
  createdPr?: any;
}
