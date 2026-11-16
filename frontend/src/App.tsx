import React, { useState } from 'react';
import { EmployeeChat } from './components/EmployeeChat';
import { BuyerWorkspace } from './components/BuyerWorkspace';
import { PolicyManagement } from './components/PolicyManagement';
import {
  MessageSquare,
  Briefcase,
  Shield,
  Layers,
  Building2,
  ExternalLink
} from 'lucide-react';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'chat' | 'buyer' | 'policy'>('chat');

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            {/* Logo and Brand */}
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-700 to-indigo-600 flex items-center justify-center text-white shadow-sm font-bold text-lg tracking-wider">
                C
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="font-extrabold text-lg text-slate-900 tracking-tight">Cantill.ai</h1>
                  <span className="bg-blue-100 text-blue-800 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider">
                    PR-to-PO
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 font-medium">Corvo Procurement Engine · Policy v5.0</p>
              </div>
            </div>

            {/* Navigation Tabs */}
            <nav className="flex space-x-1 sm:space-x-2 bg-slate-100 p-1 rounded-xl">
              <button
                onClick={() => setActiveTab('chat')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-bold transition ${
                  activeTab === 'chat'
                    ? 'bg-white text-blue-600 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <MessageSquare size={15} />
                <span>Employee Chat</span>
              </button>

              <button
                onClick={() => setActiveTab('buyer')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-bold transition ${
                  activeTab === 'buyer'
                    ? 'bg-white text-blue-600 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Briefcase size={15} />
                <span>Buyer Workspace</span>
              </button>

              <button
                onClick={() => setActiveTab('policy')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-bold transition ${
                  activeTab === 'policy'
                    ? 'bg-white text-blue-600 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Shield size={15} />
                <span>Policy Management</span>
              </button>
            </nav>

            {/* Plants badge */}
            <div className="hidden md:flex items-center gap-2 text-xs text-slate-500">
              <Building2 size={14} className="text-slate-400" />
              <span>PUN1 (Bhosari) · CHK1 (Chakan)</span>
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
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row justify-between items-center gap-2">
          <span>Cantill.ai Full-stack Implementation · Corvo Engineering Pvt Ltd</span>
          <a
            href="https://github.com/Ayush5424/Procurement-Application"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1 text-blue-600 hover:underline"
          >
            <span>GitHub Repository: Ayush5424/Procurement-Application</span>
            <ExternalLink size={12} />
          </a>
        </div>
      </footer>
    </div>
  );
};

export default App;
