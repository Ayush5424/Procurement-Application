import React, { useState, useEffect } from 'react';
import { api } from '../api';
import { PolicyVersion, PolicyRule, PolicyChangeProposal } from '../types';
import {
  ShieldCheck,
  FileText,
  Upload,
  Calendar,
  CheckCircle2,
  AlertCircle,
  ListOrdered,
  X
} from 'lucide-react';

export const PolicyManagement: React.FC = () => {
  const [policies, setPolicies] = useState<PolicyVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<string>('5.0');
  const [rules, setRules] = useState<PolicyRule[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Notification
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Amendment upload / text
  const [amendmentPdf, setAmendmentPdf] = useState<File | null>(null);
  const [amendmentText, setAmendmentText] = useState<string>('');
  const [parsing, setParsing] = useState<boolean>(false);
  const [proposal, setProposal] = useState<PolicyChangeProposal | null>(null);

  // Activation state
  const [effectiveDate, setEffectiveDate] = useState<string>('2026-11-19');
  const [activating, setActivating] = useState<boolean>(false);
  const [activationSuccess, setActivationSuccess] = useState<string | null>(null);

  const showNotification = (type: 'success' | 'error', message: string) => {
    setNotification({ type, message });
    setTimeout(() => {
      setNotification(prev => (prev?.message === message ? null : prev));
    }, 5000);
  };

  const loadPolicies = async () => {
    setLoading(true);
    try {
      const data = await api.getPolicies();
      setPolicies(data);
      if (data.length > 0 && !selectedVersion) {
        setSelectedVersion(data[0].versionNumber);
      }
    } catch {
      showNotification('error', 'Unable to load policy versions.');
    } finally {
      setLoading(false);
    }
  };

  const loadRules = async (ver: string) => {
    try {
      const r = await api.getPolicyRules(ver);
      setRules(r);
    } catch {
      showNotification('error', `Unable to load rules for Policy v${ver}.`);
    }
  };

  useEffect(() => {
    loadPolicies();
  }, []);

  useEffect(() => {
    if (selectedVersion) {
      loadRules(selectedVersion);
    }
  }, [selectedVersion]);

  const handleParsePdf = async () => {
    if (!amendmentPdf) return;
    setParsing(true);
    setProposal(null);
    setActivationSuccess(null);
    try {
      const prop = await api.uploadPolicyAmendmentPdf(amendmentPdf);
      setProposal(prop);
      if (prop.effectiveDate) {
        setEffectiveDate(prop.effectiveDate);
      }
    } catch {
      showNotification('error', 'Unable to extract policy amendment from the provided PDF.');
    } finally {
      setParsing(false);
    }
  };

  const handleParseText = async () => {
    if (!amendmentText.trim()) return;
    setParsing(true);
    setProposal(null);
    setActivationSuccess(null);
    try {
      const prop = await api.parsePolicyAmendmentText(amendmentText);
      setProposal(prop);
      if (prop.effectiveDate) {
        setEffectiveDate(prop.effectiveDate);
      }
    } catch {
      showNotification('error', 'Unable to parse policy amendment text.');
    } finally {
      setParsing(false);
    }
  };

  const handleActivateNewVersion = async () => {
    if (!proposal) return;
    setActivating(true);
    try {
      const activated = await api.activatePolicyVersion({
        versionNumber: proposal.newVersionNumber,
        effectiveDate: effectiveDate,
        description: proposal.title,
        rules: proposal.proposedRules
      });
      setActivationSuccess(`Policy Version ${activated.versionNumber} successfully activated effective ${effectiveDate}.`);
      loadPolicies();
      setSelectedVersion(activated.versionNumber);
      setProposal(null);
      setAmendmentPdf(null);
      setAmendmentText('');
    } catch {
      showNotification('error', 'Failed to activate policy version.');
    } finally {
      setActivating(false);
    }
  };

  /**
   * Translates internal condition strings into clean, human-readable criteria.
   */
  const formatConditions = (jsonStr: string) => {
    try {
      const obj = JSON.parse(jsonStr);
      const badges: string[] = [];
      if (obj.itemType) badges.push(`Item Type: ${obj.itemType}`);
      if (obj.category) badges.push(`Category: ${obj.category}`);
      if (obj.contractEligible) badges.push('Requires Active Rate Contract with Remaining Ceiling');
      if (obj.urgency) badges.push(`Urgency: ${obj.urgency}`);
      if (obj.maxValue) badges.push(`Max Value: ₹${obj.maxValue.toLocaleString('en-IN')}`);
      if (obj.minValue) badges.push(`Min Value: ₹${obj.minValue.toLocaleString('en-IN')}`);
      if (obj.requiresPastPurchase365d) badges.push('Requires Purchase History within 365 Days');
      return badges.length > 0 ? badges.join(' · ') : 'Standard Policy Criteria';
    } catch {
      return 'Standard Policy Criteria';
    }
  };

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {notification && (
        <div
          className={`p-3 rounded-lg text-xs font-medium flex items-center justify-between border ${
            notification.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-rose-50 text-rose-800 border-rose-200'
          }`}
        >
          <div className="flex items-center gap-2">
            {notification.type === 'success' ? (
              <CheckCircle2 size={16} className="text-emerald-600" />
            ) : (
              <AlertCircle size={16} className="text-rose-600" />
            )}
            <span>{notification.message}</span>
          </div>
          <button
            onClick={() => setNotification(null)}
            className="text-slate-400 hover:text-slate-600"
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-xl shadow-xs border border-slate-200">
        <h2 className="text-base sm:text-lg font-bold text-slate-800">Procurement Policy Governance</h2>
        <p className="text-xs text-slate-500 mt-0.5">
          View active procurement policy rules, approval thresholds, and route allocations. Upload signed amendments to update thresholds across the organization.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left column: Policy Versions and Rules Table */}
        <div className="lg:col-span-2 space-y-5">
          {/* Version Selector Bar */}
          <div className="bg-white p-3.5 sm:p-4 rounded-xl shadow-xs border border-slate-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
              <span className="text-xs font-semibold text-slate-600">Active Versions:</span>
              {policies.map(p => (
                <button
                  key={p.versionNumber}
                  onClick={() => setSelectedVersion(p.versionNumber)}
                  className={`px-2.5 sm:px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                    selectedVersion === p.versionNumber
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                  }`}
                >
                  <ShieldCheck size={14} /> Version {p.versionNumber}
                  <span className="text-[10px] opacity-80 font-normal">({p.effectiveDate})</span>
                </button>
              ))}
            </div>

            <span className="text-xs text-slate-500">
              Viewing rules for <b className="text-slate-800">Version {selectedVersion}</b>
            </span>
          </div>

          {/* Rules Table */}
          <div className="bg-white rounded-xl shadow-xs border border-slate-200 overflow-hidden">
            <div className="px-4 sm:px-5 py-3 sm:py-3.5 bg-slate-50 border-b border-slate-200 font-semibold text-xs text-slate-700 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-1 sm:gap-0">
              <span className="flex items-center gap-1.5">
                <ListOrdered size={15} className="text-blue-600" /> Evaluation Hierarchy & Rules
              </span>
              <span className="text-slate-400 font-normal text-[11px] sm:text-xs">Evaluated in sequential priority</span>
            </div>

            <div className="divide-y divide-slate-100 text-xs">
              {rules.map(rule => (
                <div key={rule.ruleCode} className="p-3.5 sm:p-4 hover:bg-slate-50/70 transition flex items-start gap-3 sm:gap-4">
                  <div className="w-7 h-7 rounded-full bg-slate-100 text-blue-700 font-bold flex items-center justify-center shrink-0 text-xs">
                    {rule.priority}
                  </div>

                  <div className="flex-1 space-y-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
                      <span className="font-semibold text-slate-800 text-xs sm:text-sm">Clause {rule.ruleCode} — {rule.name}</span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                        rule.outcome === 'PO' ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' : 'bg-amber-50 text-amber-800 border border-amber-200'
                      }`}>
                        {rule.outcome}
                      </span>
                      <span className="font-mono text-[10px] sm:text-[11px] bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded">
                        {rule.route}
                      </span>
                    </div>

                    <p className="text-slate-600 text-xs leading-relaxed break-words">{rule.description}</p>

                    <div className="flex flex-wrap items-center gap-2 sm:gap-3 text-[11px] text-slate-500 pt-1.5">
                      <span className="bg-slate-50 text-slate-600 px-2 py-0.5 rounded border border-slate-200 break-words">
                        {formatConditions(rule.conditionsJson)}
                      </span>
                      {rule.supplierSuggestionCount ? (
                        <span className="text-indigo-700 font-medium">Requirement: {rule.supplierSuggestionCount} supplier quotes</span>
                      ) : null}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right column: Policy Amendment Upload */}
        <div className="space-y-5">
          {/* Upload Amendment Card */}
          <div className="bg-white p-4 sm:p-5 rounded-xl shadow-xs border border-slate-200 space-y-4">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
              <Upload size={15} className="text-blue-600" /> Upload Policy Amendment PDF
            </h3>
            <p className="text-xs text-slate-500 leading-relaxed">
              Upload a signed procurement policy amendment PDF. The system will extract updated clauses and rule thresholds for review.
            </p>

            <div className="border-2 border-dashed border-slate-200 rounded-xl p-3.5 sm:p-4 text-center">
              <input
                type="file"
                accept=".pdf"
                onChange={e => setAmendmentPdf(e.target.files?.[0] || null)}
                className="w-full text-xs text-slate-700"
              />
              <button
                disabled={!amendmentPdf || parsing}
                onClick={handleParsePdf}
                className="mt-3 w-full bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white py-2 rounded-lg text-xs font-semibold transition"
              >
                {parsing ? 'Analyzing Document...' : 'Extract & Preview Amendment'}
              </button>
            </div>

            {/* Guided Text Input Alternative */}
            <div className="border-t border-slate-100 pt-3 space-y-2">
              <span className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                <FileText size={14} className="text-slate-500" /> Or enter amendment text directly:
              </span>
              <textarea
                value={amendmentText}
                onChange={e => setAmendmentText(e.target.value)}
                placeholder="e.g. Policy Amendment v5.1&#10;Effective Date: 2026-11-19&#10;Spot PO limit increased to INR 3,00,000 for standard items."
                rows={3}
                className="w-full text-xs p-2.5 border border-slate-300 rounded-lg outline-none focus:border-blue-500 bg-slate-50 text-slate-800"
              />
              <button
                disabled={!amendmentText.trim() || parsing}
                onClick={handleParseText}
                className="w-full bg-slate-100 hover:bg-slate-200 text-slate-700 py-2 rounded-lg text-xs font-semibold transition"
              >
                Parse Amendment Text
              </button>
            </div>
          </div>

          {/* Success Banner */}
          {activationSuccess && (
            <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 font-semibold flex items-center gap-2">
              <CheckCircle2 size={16} className="text-emerald-600 shrink-0" />
              <span>{activationSuccess}</span>
            </div>
          )}

          {/* Proposal Preview & Activation Card */}
          {proposal && (
            <div className="bg-slate-50 border border-slate-300 rounded-xl p-4 sm:p-5 space-y-4 animate-in fade-in duration-200">
              <div className="flex justify-between items-center">
                <span className="font-bold text-slate-800 text-sm">Proposed Version: v{proposal.newVersionNumber}</span>
                <span className="bg-blue-100 text-blue-800 text-[10px] font-semibold px-2 py-0.5 rounded">Ready for Review</span>
              </div>

              <div className="text-xs text-slate-700 bg-white p-3 sm:p-3.5 rounded-lg border border-slate-200 leading-relaxed whitespace-pre-line break-words">
                {proposal.plainLanguageExplanation}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                  <Calendar size={13} /> Effective Date:
                </label>
                <input
                  type="date"
                  value={effectiveDate}
                  onChange={e => setEffectiveDate(e.target.value)}
                  className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none text-slate-800"
                />
              </div>

              <button
                disabled={activating}
                onClick={handleActivateNewVersion}
                className="w-full bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-300 text-white font-semibold py-2.5 rounded-lg text-xs transition shadow-xs flex items-center justify-center gap-1.5"
              >
                <CheckCircle2 size={15} /> {activating ? 'Activating Policy...' : 'Approve & Activate Policy Version'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
