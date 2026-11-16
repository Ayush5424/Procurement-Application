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
  Clock,
  Sparkles,
  ArrowRight,
  ListOrdered
} from 'lucide-react';

export const PolicyManagement: React.FC = () => {
  const [policies, setPolicies] = useState<PolicyVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<string>('5.0');
  const [rules, setRules] = useState<PolicyRule[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Amendment upload / text
  const [amendmentPdf, setAmendmentPdf] = useState<File | null>(null);
  const [amendmentText, setAmendmentText] = useState<string>('');
  const [parsing, setParsing] = useState<boolean>(false);
  const [proposal, setProposal] = useState<PolicyChangeProposal | null>(null);

  // Activation state
  const [effectiveDate, setEffectiveDate] = useState<string>('2026-11-19');
  const [activating, setActivating] = useState<boolean>(false);
  const [activationSuccess, setActivationSuccess] = useState<string | null>(null);

  const loadPolicies = async () => {
    setLoading(true);
    try {
      const data = await api.getPolicies();
      setPolicies(data);
      if (data.length > 0 && !selectedVersion) {
        setSelectedVersion(data[0].versionNumber);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const loadRules = async (ver: string) => {
    try {
      const r = await api.getPolicyRules(ver);
      setRules(r);
    } catch (err) {
      console.error(err);
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
    } catch (err: any) {
      alert('Failed to parse policy PDF: ' + err.message);
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
    } catch (err: any) {
      alert('Failed to parse amendment text: ' + err.message);
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
      setActivationSuccess(`Policy Version ${activated.versionNumber} successfully activated effective ${effectiveDate}!`);
      loadPolicies();
      setSelectedVersion(activated.versionNumber);
      setProposal(null);
      setAmendmentPdf(null);
      setAmendmentText('');
    } catch (err: any) {
      alert('Failed to activate policy: ' + err.message);
    } finally {
      setActivating(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200">
        <h2 className="text-xl font-bold text-slate-800">Policy Management — Policy as Data</h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Procurement policy thresholds, conditions, and rule priorities are stored as versioned data in PostgreSQL.
          Uploading and approving a policy amendment changes procurement decisions without Java code modifications.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left column: Policy Versions and Rules Table */}
        <div className="lg:col-span-2 space-y-5">
          {/* Version Selector Bar */}
          <div className="bg-white p-4 rounded-xl shadow-sm border border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-600">Active Versions:</span>
              {policies.map(p => (
                <button
                  key={p.versionNumber}
                  onClick={() => setSelectedVersion(p.versionNumber)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
                    selectedVersion === p.versionNumber
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                  }`}
                >
                  <ShieldCheck size={14} /> v{p.versionNumber}
                  <span className="text-[10px] opacity-80">({p.effectiveDate})</span>
                </button>
              ))}
            </div>

            <span className="text-xs text-slate-400">
              Showing rules for <b className="text-slate-800">Version {selectedVersion}</b>
            </span>
          </div>

          {/* Rules Table */}
          <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <div className="px-5 py-3.5 bg-slate-50 border-b border-slate-200 font-semibold text-xs text-slate-700 flex justify-between items-center">
              <span className="flex items-center gap-1.5">
                <ListOrdered size={15} className="text-blue-600" /> Ordered Policy Decision Rules
              </span>
              <span className="text-slate-400 font-normal">First matching rule determines outcome</span>
            </div>

            <div className="divide-y divide-slate-100 text-xs">
              {rules.map(rule => (
                <div key={rule.ruleCode} className="p-4 hover:bg-slate-50/70 transition flex items-start gap-4">
                  <div className="w-8 h-8 rounded-full bg-slate-100 text-blue-700 font-bold flex items-center justify-center shrink-0 text-xs">
                    {rule.priority}
                  </div>

                  <div className="flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-800 text-sm">Clause {rule.ruleCode} — {rule.name}</span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        rule.outcome === 'PO' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                      }`}>
                        {rule.outcome}
                      </span>
                      <span className="font-mono text-[11px] bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded">
                        {rule.route}
                      </span>
                    </div>

                    <p className="text-slate-600 text-xs">{rule.description}</p>

                    <div className="flex items-center gap-4 text-[11px] text-slate-400 pt-1">
                      <span>Conditions: <code className="bg-slate-100 text-slate-600 px-1 py-0.5 rounded">{rule.conditionsJson}</code></span>
                      {rule.supplierSuggestionCount ? (
                        <span className="text-indigo-600 font-medium">Suggestions: {rule.supplierSuggestionCount} suppliers</span>
                      ) : null}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right column: Policy Amendment Upload & Guided Change */}
        <div className="space-y-5">
          {/* Upload Amendment Card */}
          <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200 space-y-4">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
              <Upload size={16} className="text-blue-600" /> Upload Policy Amendment PDF
            </h3>
            <p className="text-xs text-slate-500">
              Upload an amendment PDF document (e.g. for the blind test). Apache PDFBox will extract and analyze the rule changes.
            </p>

            <div className="border-2 border-dashed border-slate-200 rounded-xl p-4 text-center">
              <input
                type="file"
                accept=".pdf"
                onChange={e => setAmendmentPdf(e.target.files?.[0] || null)}
                className="w-full text-xs"
              />
              <button
                disabled={!amendmentPdf || parsing}
                onClick={handleParsePdf}
                className="mt-3 w-full bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white py-2 rounded-lg text-xs font-semibold transition"
              >
                {parsing ? 'Extracting with PDFBox...' : 'Extract & Preview Amendment'}
              </button>
            </div>

            {/* Guided Text Input Alternative */}
            <div className="border-t border-slate-100 pt-3 space-y-2">
              <span className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                <Sparkles size={14} className="text-amber-500" /> Or describe change in text:
              </span>
              <textarea
                value={amendmentText}
                onChange={e => setAmendmentText(e.target.value)}
                placeholder="e.g. Policy Amendment v5.1&#10;Effective Date: 2026-11-19&#10;Spot PO limit increased to INR 3,00,000 for standard items."
                rows={3}
                className="w-full text-xs p-2.5 border border-slate-300 rounded-lg outline-none focus:border-blue-500 bg-slate-50"
              />
              <button
                disabled={!amendmentText.trim() || parsing}
                onClick={handleParseText}
                className="w-full bg-slate-100 hover:bg-slate-200 text-slate-700 py-2 rounded-lg text-xs font-semibold transition"
              >
                Parse Change Text
              </button>
            </div>
          </div>

          {/* Success Banner */}
          {activationSuccess && (
            <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 font-semibold flex items-center gap-2">
              <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
              <span>{activationSuccess}</span>
            </div>
          )}

          {/* Proposal Preview & Activation Card */}
          {proposal && (
            <div className="bg-blue-50/70 border border-blue-200 rounded-xl p-5 space-y-4 animate-in fade-in duration-200">
              <div className="flex justify-between items-center">
                <span className="font-bold text-blue-900 text-sm">Proposed Version: v{proposal.newVersionNumber}</span>
                <span className="bg-blue-200/60 text-blue-800 text-[10px] font-bold px-2 py-0.5 rounded">Ready for Review</span>
              </div>

              <div className="text-xs text-slate-700 whitespace-pre-line bg-white p-3.5 rounded-lg border border-blue-100 leading-relaxed">
                {proposal.plainLanguageExplanation}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                  <Calendar size={13} /> Set Effective Date:
                </label>
                <input
                  type="date"
                  value={effectiveDate}
                  onChange={e => setEffectiveDate(e.target.value)}
                  className="w-full text-xs p-2 border border-slate-300 rounded-lg bg-white outline-none"
                />
              </div>

              <button
                disabled={activating}
                onClick={handleActivateNewVersion}
                className="w-full bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-300 text-white font-bold py-2.5 rounded-lg text-xs transition shadow-sm flex items-center justify-center gap-1.5"
              >
                <CheckCircle2 size={16} /> {activating ? 'Activating Policy...' : 'Approve & Activate Policy Version'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
