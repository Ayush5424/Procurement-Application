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
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            {/* Brand */}
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-blue-700 flex items-center justify-center text-white font-bold text-lg tracking-wider shadow-sm">
                C
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-bold text-lg text-slate-900 tracking-tight">Cantill Procurement</span>
                  <span className="bg-slate-100 text-slate-700 text-[10px] font-semibold px-2 py-0.5 rounded uppercase tracking-wider border border-slate-200">
                    Enterprise
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 font-medium">Corvo Engineering Pvt Ltd</p>
              </div>
            </div>

            {/* Navigation Tabs */}
            <nav className="flex space-x-1 bg-slate-100 p-1 rounded-lg">
              <button
                onClick={() => setActiveTab('chat')}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'chat'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <MessageSquare size={14} />
                <span>Requisition Intake</span>
              </button>

              <button
                onClick={() => setActiveTab('buyer')}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'buyer'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Briefcase size={14} />
                <span>Buyer Workspace</span>
              </button>

              <button
                onClick={() => setActiveTab('policy')}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'policy'
                    ? 'bg-white text-blue-700 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Shield size={14} />
                <span>Policy Governance</span>
              </button>
            </nav>

            {/* Operating Plants */}
            <div className="hidden md:flex items-center gap-2 text-xs text-slate-500">
              <Building2 size={14} className="text-slate-400" />
              <span>Locations: Bhosari (PUN1) · Chakan (CHK1)</span>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {activeTab === 'chat' && <EmployeeChat />}
        {activeTab === 'buyer' && <BuyerWorkspace />}
        {activeTab === 'policy' && <PolicyManagement />}
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-slate-200 py-3 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 flex justify-between items-center">
          <span>Corvo Engineering Pvt Ltd · Corporate Procurement System</span>
          <span>Policy v5.0 Active</span>
        </div>
      </footer>
    </div>
  );
};

export default App;
