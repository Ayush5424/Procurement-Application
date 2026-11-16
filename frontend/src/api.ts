import {
  RequirementGroup,
  RequirementDetail,
  AuditRecord,
  Contract,
  PolicyVersion,
  PolicyRule,
  PolicyChangeProposal,
  ExtractedContract,
  ChatResponse,
  ChatMessage
} from './types';

const API_BASE = '/api';

export const api = {
  // Chat
  async sendChatMessage(sessionId: string, message: string): Promise<ChatResponse> {
    const res = await fetch(`${API_BASE}/chat/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, message })
    });
    if (!res.ok) throw new Error('Failed to send message');
    return res.json();
  },

  async getChatHistory(sessionId: string): Promise<ChatMessage[]> {
    const res = await fetch(`${API_BASE}/chat/sessions/${sessionId}/messages`);
    if (!res.ok) return [];
    return res.json();
  },

  // Requirements
  async getRequirements(status?: string): Promise<RequirementGroup[]> {
    const url = status ? `${API_BASE}/requirements?status=${encodeURIComponent(status)}` : `${API_BASE}/requirements`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to fetch requirements');
    return res.json();
  },

  async getRequirementDetail(groupId: string): Promise<RequirementDetail> {
    const res = await fetch(`${API_BASE}/requirements/${encodeURIComponent(groupId)}`);
    if (!res.ok) throw new Error('Failed to fetch requirement detail');
    return res.json();
  },

  async approveRequirement(groupId: string, actor = 'Buyer'): Promise<RequirementGroup> {
    const res = await fetch(`${API_BASE}/requirements/${encodeURIComponent(groupId)}/approve?actor=${encodeURIComponent(actor)}`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error('Failed to approve requirement');
    return res.json();
  },

  async overrideRequirement(groupId: string, payload: {
    actor: string;
    overrideReason: string;
    newDecision: string;
    newRoute: string;
    newSupplierId?: string;
    newUnitPrice?: number;
  }): Promise<RequirementGroup> {
    const res = await fetch(`${API_BASE}/requirements/${encodeURIComponent(groupId)}/override`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('Failed to override requirement');
    return res.json();
  },

  async getAuditTrail(): Promise<AuditRecord[]> {
    const res = await fetch(`${API_BASE}/audit-trail`);
    if (!res.ok) throw new Error('Failed to fetch audit records');
    return res.json();
  },

  // Batch Processing
  async processBatchCsv(file: File, policyVersion?: string): Promise<any[]> {
    const formData = new FormData();
    formData.append('file', file);
    const url = policyVersion ? `${API_BASE}/process-batch?policyVersion=${encodeURIComponent(policyVersion)}` : `${API_BASE}/process-batch`;
    const res = await fetch(url, {
      method: 'POST',
      body: formData
    });
    if (!res.ok) throw new Error('Failed to process batch CSV');
    return res.json();
  },

  async exportBatchDecisionsCsv(file: File, policyVersion?: string): Promise<Blob> {
    const formData = new FormData();
    formData.append('file', file);
    const url = policyVersion ? `${API_BASE}/process-batch/export?policyVersion=${encodeURIComponent(policyVersion)}` : `${API_BASE}/process-batch/export`;
    const res = await fetch(url, {
      method: 'POST',
      body: formData
    });
    if (!res.ok) throw new Error('Failed to export batch decisions CSV');
    return res.blob();
  },

  async exportPreferredSuppliersCsv(asOfDate?: string): Promise<Blob> {
    const url = asOfDate ? `${API_BASE}/preferred-suppliers/export?asOfDate=${encodeURIComponent(asOfDate)}` : `${API_BASE}/preferred-suppliers/export`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to export preferred suppliers CSV');
    return res.blob();
  },

  // Contracts
  async getContracts(): Promise<Contract[]> {
    const res = await fetch(`${API_BASE}/contracts`);
    if (!res.ok) throw new Error('Failed to fetch contracts');
    return res.json();
  },

  async uploadContractPdf(file: File): Promise<ExtractedContract> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/contracts/upload-pdf`, {
      method: 'POST',
      body: formData
    });
    if (!res.ok) throw new Error('Failed to extract contract from PDF');
    return res.json();
  },

  async activateContract(contractDto: ExtractedContract): Promise<Contract> {
    const res = await fetch(`${API_BASE}/contracts/activate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(contractDto)
    });
    if (!res.ok) throw new Error('Failed to activate contract');
    return res.json();
  },

  // Policies
  async getPolicies(): Promise<PolicyVersion[]> {
    const res = await fetch(`${API_BASE}/policies`);
    if (!res.ok) throw new Error('Failed to fetch policies');
    return res.json();
  },

  async getPolicyRules(version: string): Promise<PolicyRule[]> {
    const res = await fetch(`${API_BASE}/policies/${encodeURIComponent(version)}`);
    if (!res.ok) throw new Error('Failed to fetch policy rules');
    const data = await res.json();
    return data.rules || [];
  },

  async uploadPolicyAmendmentPdf(file: File): Promise<PolicyChangeProposal> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/policies/upload-amendment`, {
      method: 'POST',
      body: formData
    });
    if (!res.ok) throw new Error('Failed to parse amendment PDF');
    return res.json();
  },

  async parsePolicyAmendmentText(text: string): Promise<PolicyChangeProposal> {
    const res = await fetch(`${API_BASE}/policies/parse-text`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text })
    });
    if (!res.ok) throw new Error('Failed to parse amendment text');
    return res.json();
  },

  async activatePolicyVersion(payload: {
    versionNumber: string;
    effectiveDate: string;
    description: string;
    rules: PolicyRule[];
  }): Promise<PolicyVersion> {
    const res = await fetch(`${API_BASE}/policies/activate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('Failed to activate policy version');
    return res.json();
  }
};
