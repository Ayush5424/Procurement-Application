import React, { useState } from 'react';
import { EmployeeChat } from './components/EmployeeChat';
import { BuyerWorkspace } from './components/BuyerWorkspace';
import { PolicyManagement } from './components/PolicyManagement';
import {
  MessageSquare,
  Briefcase,
  Shield,
  Building2
} from 'lucide-react';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'chat' | 'buyer' | 'policy'>('chat');

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
      {/* Top Navigation Bar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-2.5 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row justify-between py-2.5 md:py-0 md:h-16 items-center gap-2 md:gap-0">
            {/* Brand */}
            <div className="flex items-center justify-between w-full md:w-auto">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 md:w-9 md:h-9 rounded-lg bg-blue-700 flex items-center justify-center text-white font-bold text-base md:text-lg tracking-wider shadow-sm flex-shrink-0">
                  C
                </div>
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="font-bold text-base md:text-lg text-slate-900 tracking-tight whitespace-nowrap">Cantill Procurement</span>
                    <span className="bg-slate-100 text-slate-700 text-[9px] md:text-[10px] font-semibold px-1.5 md:px-2 py-0.5 rounded uppercase tracking-wider border border-slate-200">
                      Enterprise
                    </span>
                  </div>
                  <p className="text-[10px] md:text-[11px] text-slate-500 font-medium">Corvo Engineering Pvt Ltd</p>
                </div>
              </div>

              {/* Operating Plants (Mobile Compact) */}
              <div className="flex md:hidden items-center gap-1 text-[10px] text-slate-500 bg-slate-50 px-2 py-1 rounded border border-slate-100">
                <Building2 size={12} className="text-slate-400 flex-shrink-0" />
                <span>PUN1 · CHK1</span>
              </div>
            </div>

            {/* Navigation Tabs */}
            <nav className="flex space-x-1 bg-slate-100 p-1 rounded-lg w-full md:w-auto justify-center overflow-x-auto">
              <button
                onClick={() => setActiveTab('chat')}
                className={`flex items-center justify-center gap-1.5 px-2.5 sm:px-3.5 py-1.5 rounded-md text-xs font-semibold transition whitespace-nowrap flex-1 md:flex-none ${
                  activeTab === 'chat'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <MessageSquare size={14} className="flex-shrink-0" />
                <span className="hidden sm:inline">Requisition Intake</span>
                <span className="sm:hidden">Intake</span>
              </button>

              <button
                onClick={() => setActiveTab('buyer')}
                className={`flex items-center justify-center gap-1.5 px-2.5 sm:px-3.5 py-1.5 rounded-md text-xs font-semibold transition whitespace-nowrap flex-1 md:flex-none ${
                  activeTab === 'buyer'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Briefcase size={14} className="flex-shrink-0" />
                <span className="hidden sm:inline">Buyer Workspace</span>
                <span className="sm:hidden">Buyer</span>
              </button>

              <button
                onClick={() => setActiveTab('policy')}
                className={`flex items-center justify-center gap-1.5 px-2.5 sm:px-3.5 py-1.5 rounded-md text-xs font-semibold transition whitespace-nowrap flex-1 md:flex-none ${
                  activeTab === 'policy'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Shield size={14} className="flex-shrink-0" />
                <span className="hidden sm:inline">Policy Governance</span>
                <span className="sm:hidden">Policy</span>
              </button>
            </nav>

            {/* Operating Plants (Desktop) */}
            <div className="hidden lg:flex items-center gap-2 text-xs text-slate-500">
              <Building2 size={14} className="text-slate-400 flex-shrink-0" />
              <span>Locations: Bhosari (PUN1) · Chakan (CHK1)</span>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-2 sm:px-6 lg:px-8 py-3 sm:py-6">
        {activeTab === 'chat' && <EmployeeChat />}
        {activeTab === 'buyer' && <BuyerWorkspace />}
        {activeTab === 'policy' && <PolicyManagement />}
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-slate-200 py-3 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row justify-between items-center gap-1.5 sm:gap-0">
          <span>Corvo Engineering Pvt Ltd · Corporate Procurement System</span>
          <span>Policy v5.0 Active</span>
        </div>
      </footer>
    </div>
  );
};

export default App;
