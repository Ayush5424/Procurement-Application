import React, { useState, useEffect } from 'react';
import { api } from '../api';
import {
  RequirementGroup,
  RequirementDetail,
  Contract,
  AuditRecord,
  ExtractedContract
} from '../types';
import {
  CheckCircle,
  AlertTriangle,
  Upload,
  Download,
  FileText,
  Filter,
  Eye,
  ArrowRight,
  ShieldAlert,
  Calendar,
  Building,
  Check,
  X,
  RefreshCw,
  Search
} from 'lucide-react';

export const BuyerWorkspace: React.FC = () => {
  const [requirements, setRequirements] = useState<RequirementGroup[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Selected requirement for detail modal
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [detail, setDetail] = useState<RequirementDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);

  // Modals
  const [showBatchModal, setShowBatchModal] = useState<boolean>(false);
  const [batchFile, setBatchFile] = useState<File | null>(null);
  const [batchLoading, setBatchLoading] = useState<boolean>(false);
  const [batchResultMsg, setBatchResultMsg] = useState<string | null>(null);

  const [showContractModal, setShowContractModal] = useState<boolean>(false);
  const [contractFile, setContractFile] = useState<File | null>(null);
  const [extractedContract, setExtractedContract] = useState<ExtractedContract | null>(null);
  const [contractLoading, setContractLoading] = useState<boolean>(false);

  const [exportDate, setExportDate] = useState<string>('2026-11-01');

  // Override Form
  const [showOverrideForm, setShowOverrideForm] = useState<boolean>(false);
  const [overrideReason, setOverrideReason] = useState<string>('');
  const [overrideDecision, setOverrideDecision] = useState<string>('PO');
  const [overrideRoute, setOverrideRoute] = useState<string>('SPOT_PO');
  const [overrideSupplierId, setOverrideSupplierId] = useState<string>('');
  const [overrideUnitPrice, setOverrideUnitPrice] = useState<string>('');

  const loadRequirements = async () => {
    setLoading(true);
    try {
      const data = await api.getRequirements(statusFilter === 'ALL' ? undefined : statusFilter);
      setRequirements(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRequirements();
  }, [statusFilter]);

  const openDetail = async (groupId: string) => {
    setSelectedGroupId(groupId);
    setDetailLoading(true);
    setShowOverrideForm(false);
    try {
      const d = await api.getRequirementDetail(groupId);
      setDetail(d);
      setOverrideDecision(d.requirement.decision);
      setOverrideRoute(d.requirement.route);
      setOverrideSupplierId(d.requirement.selectedSupplierId || '');
      setOverrideUnitPrice(d.requirement.unitPrice ? String(d.requirement.unitPrice) : '');
    } catch (err) {
      console.error(err);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleApprove = async (groupId: string) => {
    try {
      const updated = await api.approveRequirement(groupId, 'Buyer (Procurement)');
      setRequirements(prev => prev.map(r => r.groupId === groupId ? updated : r));
      if (detail && detail.requirement.groupId === groupId) {
        setDetail({ ...detail, requirement: updated });
      }
    } catch (err) {
      alert('Failed to approve requirement');
    }
  };

  const handleOverrideSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedGroupId || !overrideReason.trim()) return;

    try {
      const updated = await api.overrideRequirement(selectedGroupId, {
        actor: 'Buyer (Procurement)',
        overrideReason: overrideReason.trim(),
        newDecision: overrideDecision,
        newRoute: overrideRoute,
        newSupplierId: overrideSupplierId.trim() || undefined,
        newUnitPrice: overrideUnitPrice ? parseFloat(overrideUnitPrice) : undefined
      });

      setRequirements(prev => prev.map(r => r.groupId === selectedGroupId ? updated : r));
      setShowOverrideForm(false);
      setOverrideReason('');
      openDetail(selectedGroupId);
    } catch (err) {
      alert('Failed to override decision');
    }
  };

  const handleProcessBatch = async () => {
    if (!batchFile) return;
    setBatchLoading(true);
    setBatchResultMsg(null);
    try {
      const rows = await api.processBatchCsv(batchFile, '5.0');
      setBatchResultMsg(`Successfully processed ${rows.length} PR lines and consolidated requirements!`);
      loadRequirements();
    } catch (err: any) {
      setBatchResultMsg('Failed to process batch CSV: ' + err.message);
    } finally {
      setBatchLoading(false);
    }
  };

  const handleExportDecisionsCsv = async () => {
    if (!batchFile) return;
    try {
      const blob = await api.exportBatchDecisionsCsv(batchFile, '5.0');
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'decisions.csv';
      a.click();
    } catch (err) {
      alert('Failed to export decisions CSV');
    }
  };

  const handleExportSuppliers = async () => {
    try {
      const blob = await api.exportPreferredSuppliersCsv(exportDate);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `preferred_suppliers_${exportDate}.csv`;
      a.click();
    } catch (err) {
      alert('Failed to export preferred suppliers CSV');
    }
  };

  const handleContractPdfUpload = async () => {
    if (!contractFile) return;
    setContractLoading(true);
    try {
      const extracted = await api.uploadContractPdf(contractFile);
      setExtractedContract(extracted);
    } catch (err: any) {
      alert('Failed to extract contract: ' + err.message);
    } finally {
      setContractLoading(false);
    }
  };

  const handleActivateContract = async () => {
    if (!extractedContract) return;
    try {
      await api.activateContract(extractedContract);
      alert(`Contract ${extractedContract.contractId} activated successfully!`);
      setShowContractModal(false);
      setExtractedContract(null);
      setContractFile(null);
    } catch (err) {
      alert('Failed to activate contract');
    }
  };

  const filteredRequirements = requirements.filter(r => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      r.groupId.toLowerCase().includes(q) ||
      r.resolvedItemCode.toLowerCase().includes(q) ||
      r.plant.toLowerCase().includes(q) ||
      (r.selectedSupplierId && r.selectedSupplierId.toLowerCase().includes(q)) ||
      r.route.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6">
      {/* Top action bar */}
      <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200 flex flex-wrap justify-between items-center gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Buyer Workspace</h2>
          <p className="text-xs text-slate-500">Review, approve, or override procurement decisions under policy rules.</p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <button
            onClick={() => setShowBatchModal(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition shadow-sm"
          >
            <Upload size={14} /> Process Batch CSV
          </button>

          <button
            onClick={() => setShowContractModal(true)}
            className="bg-indigo-50 border border-indigo-200 hover:bg-indigo-100 text-indigo-700 px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
          >
            <FileText size={14} /> Add PDF Contract
          </button>

          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-lg">
            <input
              type="date"
              value={exportDate}
              onChange={e => setExportDate(e.target.value)}
              className="bg-white text-xs border border-slate-300 rounded px-2 py-1 outline-none text-slate-700"
            />
            <button
              onClick={handleExportSuppliers}
              className="bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 px-2.5 py-1 rounded text-xs font-medium flex items-center gap-1 transition"
              title="Export Top 3 Preferred Suppliers as of selected date"
            >
              <Download size={13} /> Export Suppliers CSV
            </button>
          </div>

          <button
            onClick={loadRequirements}
            className="p-2 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition"
            title="Refresh list"
          >
            <RefreshCw size={16} />
          </button>
        </div>
      </div>

      {/* Filter and search bar */}
      <div className="bg-white p-4 rounded-xl shadow-sm border border-slate-200 flex flex-wrap justify-between items-center gap-3">
        <div className="flex items-center gap-2">
          <Filter size={15} className="text-slate-400" />
          <span className="text-xs font-medium text-slate-600">Status:</span>
          {['ALL', 'PENDING_REVIEW', 'APPROVED', 'OVERRIDDEN'].map(st => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={`px-3 py-1 text-xs rounded-full font-medium transition ${
                statusFilter === st
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 hover:bg-slate-200 text-slate-600'
              }`}
            >
              {st.replace('_', ' ')}
            </button>
          ))}
        </div>

        <div className="relative">
          <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            placeholder="Search group, item, plant, route..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="pl-9 pr-3 py-1.5 text-xs border border-slate-300 rounded-lg outline-none focus:border-blue-500 w-64"
          />
        </div>
      </div>

      {/* Requirements Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold uppercase tracking-wider">
                <th className="py-3 px-4">Group ID</th>
                <th className="py-3 px-4">Item Code</th>
                <th className="py-3 px-4">Plant</th>
                <th className="py-3 px-4">Total Qty</th>
                <th className="py-3 px-4">Req. Value (₹)</th>
                <th className="py-3 px-4">Decision</th>
                <th className="py-3 px-4">Route</th>
                <th className="py-3 px-4">Supplier / Sourcing</th>
                <th className="py-3 px-4">PO ID</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {loading ? (
                <tr>
                  <td colSpan={11} className="py-8 text-center text-slate-400">Loading requirements...</td>
                </tr>
              ) : filteredRequirements.length === 0 ? (
                <tr>
                  <td colSpan={11} className="py-12 text-center text-slate-400">
                    No requirements found. Click "Process Batch CSV" above to upload PRs!
                  </td>
                </tr>
              ) : (
                filteredRequirements.map(req => (
                  <tr key={req.groupId} className="hover:bg-slate-50/80 transition">
                    <td className="py-3 px-4 font-bold text-blue-600 cursor-pointer" onClick={() => openDetail(req.groupId)}>
                      {req.groupId}
                    </td>
                    <td className="py-3 px-4 font-medium text-slate-800">
                      {req.resolvedItemCode}
                    </td>
                    <td className="py-3 px-4">
                      <span className="bg-slate-100 text-slate-700 px-2 py-0.5 rounded font-mono text-[11px]">
                        {req.plant}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-medium">{req.totalQuantity}</td>
                    <td className="py-3 px-4 font-semibold text-slate-800">
                      ₹{req.requirementValue?.toLocaleString('en-IN')}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                        req.decision === 'PO' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                      }`}>
                        {req.decision}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="font-mono text-[11px] text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded">
                        {req.route}
                      </span>
                    </td>
                    <td className="py-3 px-4 max-w-xs truncate" title={req.selectedSupplierId || req.suggestedSupplierIds}>
                      {req.selectedSupplierId ? (
                        <span className="font-semibold text-emerald-700">{req.selectedSupplierId} (₹{req.unitPrice})</span>
                      ) : req.suggestedSupplierIds ? (
                        <span className="text-amber-700 text-[11px]">{req.suggestedSupplierIds}</span>
                      ) : (
                        <span className="text-slate-400">None</span>
                      )}
                    </td>
                    <td className="py-3 px-4 font-mono font-medium text-indigo-600">
                      {req.poId || '—'}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                        req.status === 'APPROVED' || req.status === 'PO_CREATED' || req.status === 'SOURCING_CREATED'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : req.status === 'OVERRIDDEN'
                          ? 'bg-purple-50 text-purple-700 border border-purple-200'
                          : 'bg-amber-50 text-amber-700 border border-amber-200'
                      }`}>
                        {req.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right space-x-1">
                      <button
                        onClick={() => openDetail(req.groupId)}
                        className="bg-slate-100 hover:bg-slate-200 text-slate-700 px-2.5 py-1 rounded text-xs transition"
                        title="View Full Detail"
                      >
                        <Eye size={12} className="inline mr-1" /> View
                      </button>
                      {req.status === 'PENDING_REVIEW' && (
                        <button
                          onClick={() => handleApprove(req.groupId)}
                          className="bg-emerald-600 hover:bg-emerald-700 text-white px-2.5 py-1 rounded text-xs font-semibold transition"
                          title="One-click Approve"
                        >
                          Approve
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Requirement Detail Drawer / Modal */}
      {selectedGroupId && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-50 flex justify-end">
          <div className="bg-white w-full max-w-2xl h-full shadow-2xl flex flex-col overflow-hidden animate-in slide-in-from-right duration-200">
            {/* Modal Header */}
            <div className="p-5 bg-slate-50 border-b border-slate-200 flex justify-between items-center">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-bold text-lg text-slate-800">{selectedGroupId}</h3>
                  <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                    detail?.requirement.decision === 'PO' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                  }`}>
                    {detail?.requirement.decision}
                  </span>
                  <span className="text-xs bg-slate-200 text-slate-700 px-2 py-0.5 rounded font-mono">
                    {detail?.requirement.route}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  Item: <span className="font-semibold text-slate-700">{detail?.requirement.resolvedItemCode}</span> · Plant: {detail?.requirement.plant}
                </p>
              </div>
              <button
                onClick={() => setSelectedGroupId(null)}
                className="p-2 text-slate-400 hover:text-slate-700 rounded-lg"
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 text-xs text-slate-700">
              {detailLoading ? (
                <div className="py-12 text-center text-slate-400">Loading requirement breakdown...</div>
              ) : detail ? (
                <>
                  {/* Decision & Valuation Summary Card */}
                  <div className="bg-blue-50/60 border border-blue-200 rounded-xl p-4 space-y-2">
                    <h4 className="font-bold text-blue-900 text-sm flex items-center gap-1.5">
                      <CheckCircle size={16} className="text-blue-600" /> Procurement Decision Details
                    </h4>
                    <p className="text-xs text-slate-700">
                      <span className="font-semibold">Reason:</span> {detail.requirement.reason}
                    </p>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-blue-100 text-[11px]">
                      <div>
                        <span className="text-slate-500 block">Total Quantity</span>
                        <span className="font-bold text-slate-800">{detail.requirement.totalQuantity}</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Unit Price</span>
                        <span className="font-bold text-slate-800">₹{detail.requirement.unitPrice}</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Total Value</span>
                        <span className="font-bold text-blue-700">₹{detail.requirement.requirementValue?.toLocaleString('en-IN')}</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">PO Consolidation</span>
                        <span className="font-bold text-indigo-700">{detail.requirement.poId || 'None'}</span>
                      </div>
                    </div>
                    {detail.requirement.valuationExplanation && (
                      <div className="text-[11px] text-slate-500 bg-white p-2 rounded border border-blue-100">
                        {detail.requirement.valuationExplanation}
                      </div>
                    )}
                  </div>

                  {/* Clubbed PR Lines */}
                  <div className="space-y-2">
                    <h4 className="font-bold text-slate-800 text-xs uppercase tracking-wider">
                      Original PR Lines Clubbed in this Requirement ({detail.mappedPrLines.length})
                    </h4>
                    <div className="bg-slate-50 border border-slate-200 rounded-lg overflow-hidden">
                      <table className="w-full text-left text-xs">
                        <thead className="bg-slate-100 border-b border-slate-200 font-semibold text-slate-600">
                          <tr>
                            <th className="py-2 px-3">PR Number</th>
                            <th className="py-2 px-3">Line #</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {detail.mappedPrLines.map(line => (
                            <tr key={line.id}>
                              <td className="py-2 px-3 font-semibold text-blue-600">{line.prNo}</td>
                              <td className="py-2 px-3">{line.prLine}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Preferred Supplier Ranking Breakdown */}
                  {detail.preferredSuppliers && detail.preferredSuppliers.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="font-bold text-slate-800 text-xs uppercase tracking-wider">
                        Preferred Supplier Ranking & Reference Price
                      </h4>
                      <div className="bg-slate-50 border border-slate-200 rounded-lg overflow-hidden">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-slate-100 border-b border-slate-200 font-semibold text-slate-600">
                            <tr>
                              <th className="py-2 px-3">Rank</th>
                              <th className="py-2 px-3">Supplier</th>
                              <th className="py-2 px-3">Score</th>
                              <th className="py-2 px-3">POs (548d)</th>
                              <th className="py-2 px-3">Contract</th>
                              <th className="py-2 px-3">On-Time</th>
                              <th className="py-2 px-3">Quality</th>
                              <th className="py-2 px-3">Ref. Price</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            {detail.preferredSuppliers.map(s => (
                              <tr key={s.supplier.supplierId} className={s.rank === 1 ? 'bg-emerald-50/50' : ''}>
                                <td className="py-2 px-3 font-bold text-slate-800">#{s.rank}</td>
                                <td className="py-2 px-3 font-medium">
                                  {s.supplier.name} <span className="text-slate-400 text-[10px]">({s.supplier.supplierId})</span>
                                </td>
                                <td className="py-2 px-3 font-semibold text-blue-700">{s.totalScore.toFixed(1)}</td>
                                <td className="py-2 px-3">{s.poCount}</td>
                                <td className="py-2 px-3">{s.hasContract ? 'Yes' : 'No'}</td>
                                <td className="py-2 px-3">{s.onTimePct}%</td>
                                <td className="py-2 px-3">{s.qualityRating} / 5</td>
                                <td className="py-2 px-3 font-semibold text-emerald-700">₹{s.referencePrice || 'N/A'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {/* Audit Trail */}
                  {detail.auditRecords && detail.auditRecords.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="font-bold text-slate-800 text-xs uppercase tracking-wider">
                        Action & Audit Trail
                      </h4>
                      <div className="space-y-2">
                        {detail.auditRecords.map(a => (
                          <div key={a.id} className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs">
                            <div className="flex justify-between font-semibold text-slate-800">
                              <span>Action: {a.action} by {a.actor}</span>
                              <span className="text-slate-400 text-[10px]">{a.timestamp}</span>
                            </div>
                            <p className="text-slate-600 mt-1">{a.overrideReason}</p>
                            {a.newDecision && (
                              <div className="mt-1 text-[11px] text-blue-700 font-medium">
                                Outcome: {a.newDecision} · Route: {a.newRoute} · Supplier: {a.newSupplierId || 'N/A'}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Override Form */}
                  {showOverrideForm ? (
                    <form onSubmit={handleOverrideSubmit} className="bg-purple-50/60 border border-purple-200 rounded-xl p-4 space-y-3">
                      <div className="flex justify-between items-center">
                        <h4 className="font-bold text-purple-900 text-sm">Override Decision</h4>
                        <button type="button" onClick={() => setShowOverrideForm(false)} className="text-xs text-slate-500 hover:text-slate-800">Cancel</button>
                      </div>

                      <div>
                        <label className="block text-[11px] font-semibold text-slate-700 mb-1">
                          Written Reason for Override (Mandatory)
                        </label>
                        <textarea
                          required
                          value={overrideReason}
                          onChange={e => setOverrideReason(e.target.value)}
                          placeholder="State the commercial or operational justification for overriding this policy decision..."
                          rows={2}
                          className="w-full text-xs p-2.5 border border-slate-300 rounded-lg outline-none focus:border-purple-500 bg-white"
                        />
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-xs">
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-700 mb-1">New Decision</label>
                          <select
                            value={overrideDecision}
                            onChange={e => setOverrideDecision(e.target.value)}
                            className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none"
                          >
                            <option value="PO">PO</option>
                            <option value="SOURCING_REQUEST">SOURCING_REQUEST</option>
                          </select>
                        </div>
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-700 mb-1">New Route</label>
                          <select
                            value={overrideRoute}
                            onChange={e => setOverrideRoute(e.target.value)}
                            className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none"
                          >
                            <option value="SPOT_PO">SPOT_PO</option>
                            <option value="EMERGENCY_PO">EMERGENCY_PO</option>
                            <option value="CONTRACT_PO">CONTRACT_PO</option>
                            <option value="RFQ">RFQ</option>
                            <option value="RFP">RFP</option>
                            <option value="CAPEX_SOURCING">CAPEX_SOURCING</option>
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-xs">
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-700 mb-1">Supplier ID (if PO)</label>
                          <input
                            type="text"
                            value={overrideSupplierId}
                            onChange={e => setOverrideSupplierId(e.target.value)}
                            placeholder="e.g. S001"
                            className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none"
                          />
                        </div>
                        <div>
                          <label className="block text-[11px] font-semibold text-slate-700 mb-1">Unit Price (₹)</label>
                          <input
                            type="number"
                            step="0.01"
                            value={overrideUnitPrice}
                            onChange={e => setOverrideUnitPrice(e.target.value)}
                            placeholder="e.g. 500"
                            className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none"
                          />
                        </div>
                      </div>

                      <button
                        type="submit"
                        className="w-full bg-purple-600 hover:bg-purple-700 text-white font-semibold py-2 rounded-lg text-xs transition"
                      >
                        Submit & Save Override
                      </button>
                    </form>
                  ) : (
                    <div className="flex gap-2 pt-4 border-t border-slate-200">
                      <button
                        onClick={() => handleApprove(detail.requirement.groupId)}
                        className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2.5 rounded-lg text-xs flex items-center justify-center gap-1.5 transition shadow-sm"
                      >
                        <Check size={16} /> Approve System Decision
                      </button>
                      <button
                        onClick={() => setShowOverrideForm(true)}
                        className="px-4 bg-slate-100 hover:bg-purple-50 hover:text-purple-700 text-slate-700 font-semibold py-2.5 rounded-lg text-xs transition border border-slate-300"
                      >
                        Override...
                      </button>
                    </div>
                  )}
                </>
              ) : null}
            </div>
          </div>
        </div>
      )}

      {/* Batch Processing Modal */}
      {showBatchModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full p-6 space-y-4">
            <div className="flex justify-between items-center border-b border-slate-100 pb-3">
              <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                <Upload size={18} className="text-blue-600" /> Process PR Batch CSV
              </h3>
              <button onClick={() => setShowBatchModal(false)} className="text-slate-400 hover:text-slate-700">
                <X size={18} />
              </button>
            </div>

            <p className="text-xs text-slate-600">
              Upload a PR batch CSV (such as <code className="bg-slate-100 px-1 py-0.5 rounded text-blue-600 font-mono">sample_pr_batch.csv</code>).
              The decision engine will club requirements, value them, check contract ceilings, evaluate policy rules, consolidate POs, and record results.
            </p>

            <div className="border-2 border-dashed border-slate-300 rounded-xl p-6 text-center hover:border-blue-500 transition cursor-pointer">
              <input
                type="file"
                accept=".csv"
                onChange={e => setBatchFile(e.target.files?.[0] || null)}
                className="w-full text-xs"
              />
              {batchFile && (
                <div className="mt-2 text-xs font-semibold text-emerald-600">
                  Selected: {batchFile.name} ({(batchFile.size / 1024).toFixed(1)} KB)
                </div>
              )}
            </div>

            {batchResultMsg && (
              <div className={`p-3 rounded-lg text-xs font-medium ${
                batchResultMsg.includes('Successfully') ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' : 'bg-rose-50 text-rose-800 border border-rose-200'
              }`}>
                {batchResultMsg}
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <button
                disabled={!batchFile || batchLoading}
                onClick={handleProcessBatch}
                className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white py-2 rounded-lg text-xs font-semibold transition"
              >
                {batchLoading ? 'Processing Batch...' : 'Process Batch'}
              </button>
              <button
                disabled={!batchFile}
                onClick={handleExportDecisionsCsv}
                className="bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-300 text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
                title="Download decisions.csv directly"
              >
                <Download size={14} /> Export CSV
              </button>
            </div>
          </div>
        </div>
      )}

      {/* PDF Contract Upload Modal */}
      {showContractModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-xl w-full p-6 space-y-4">
            <div className="flex justify-between items-center border-b border-slate-100 pb-3">
              <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                <FileText size={18} className="text-indigo-600" /> Ingest Signed Contract PDF
              </h3>
              <button onClick={() => setShowContractModal(false)} className="text-slate-400 hover:text-slate-700">
                <X size={18} />
              </button>
            </div>

            <p className="text-xs text-slate-600">
              Upload a signed contract PDF (e.g. <code className="bg-slate-100 px-1 py-0.5 rounded font-mono text-indigo-600">RC-2026-031.pdf</code>).
              Apache PDFBox will extract contract terms and line items for buyer review before activation.
            </p>

            <div className="border-2 border-dashed border-slate-300 rounded-xl p-4 text-center">
              <input
                type="file"
                accept=".pdf"
                onChange={e => setContractFile(e.target.files?.[0] || null)}
                className="w-full text-xs"
              />
              <button
                disabled={!contractFile || contractLoading}
                onClick={handleContractPdfUpload}
                className="mt-3 bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 text-white px-4 py-1.5 rounded-lg text-xs font-semibold transition"
              >
                {contractLoading ? 'Extracting with PDFBox...' : 'Extract Contract Details'}
              </button>
            </div>

            {extractedContract && (
              <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3 text-xs">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-slate-800 text-sm">Contract: {extractedContract.contractId}</span>
                  <span className="bg-emerald-100 text-emerald-800 font-semibold px-2 py-0.5 rounded text-[10px]">Valid Extraction</span>
                </div>
                <div className="grid grid-cols-2 gap-2 text-slate-600 text-[11px]">
                  <div>Supplier: <span className="font-semibold text-slate-800">{extractedContract.supplierName} ({extractedContract.supplierId})</span></div>
                  <div>Ceiling: <span className="font-semibold text-slate-800">INR {extractedContract.valueCap?.toLocaleString('en-IN')}</span></div>
                  <div>Valid: <span className="font-semibold text-slate-800">{extractedContract.startDate} to {extractedContract.endDate}</span></div>
                  <div>Starting Consumed: <span className="font-semibold text-slate-800">INR 0 (New PDF)</span></div>
                </div>

                <div className="border-t border-slate-200 pt-2">
                  <div className="font-semibold text-slate-700 mb-1">Schedule A: Item Rates ({extractedContract.lines.length} lines)</div>
                  <div className="max-h-36 overflow-y-auto space-y-1">
                    {extractedContract.lines.map((l, i) => (
                      <div key={i} className="flex justify-between bg-white p-1.5 rounded border border-slate-200 text-[11px]">
                        <span><b className="text-indigo-600">{l.itemCode}</b> — {l.description}</span>
                        <span className="font-semibold text-slate-800">₹{l.rate} / {l.uom}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <button
                  onClick={handleActivateContract}
                  className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 rounded-lg text-xs transition"
                >
                  Confirm & Activate Contract in System
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
