import React, { useState, useEffect, useRef } from 'react';
import { api } from '../api';
import { ChatMessage, Item, ChatResponse } from '../types';
import {
  Send,
  User,
  CheckCircle2,
  Package,
  RefreshCw,
  Building2,
  HelpCircle
} from 'lucide-react';

export const EmployeeChat: React.FC = () => {
  const [sessionId, setSessionId] = useState<string>(() => 'chat-' + Math.random().toString(36).substring(2, 9));
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [candidates, setCandidates] = useState<Item[]>([]);
  const [lastResponse, setLastResponse] = useState<ChatResponse | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  // Initial welcome message
  useEffect(() => {
    if (messages.length === 0) {
      setMessages([
        {
          role: 'ASSISTANT',
          content: 'Welcome to Cantill Procurement. You can raise a purchase requisition by describing the required item, destination plant, and target delivery date.\n\nExamples:\n• Need 200 nitrile gloves for Chakan by next Friday\n• Require 15 MS sheet 3mm for Pune plant by 15 Nov\n• Urgent: 2380 gearbox oil seals for Bhosari'
        }
      ]);
    }
  }, []);

  const handleSend = async (messageText?: string) => {
    const textToSend = messageText || input;
    if (!textToSend.trim() || loading) return;

    const userMsg: ChatMessage = { role: 'USER', content: textToSend };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);
    setCandidates([]);

    try {
      const res = await api.sendChatMessage(sessionId, textToSend);
      setLastResponse(res);
      setSessionId(res.sessionId);
      if (res.candidateItems && res.candidateItems.length > 0) {
        setCandidates(res.candidateItems);
      }
      setMessages(prev => [...prev, { role: 'ASSISTANT', content: res.reply }]);
    } catch {
      setMessages(prev => [
        ...prev,
        { role: 'ASSISTANT', content: 'Unable to connect to the procurement service. Please try again.' }
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectCandidate = (item: Item) => {
    handleSend(`I need item ${item.itemCode}: ${item.description}`);
  };

  const handleQuickPrompt = (prompt: string) => {
    setInput(prompt);
    handleSend(prompt);
  };

  const resetChat = () => {
    const newSession = 'chat-' + Math.random().toString(36).substring(2, 9);
    setSessionId(newSession);
    setCandidates([]);
    setLastResponse(null);
    setMessages([
      {
        role: 'ASSISTANT',
        content: 'New requisition session started. What item or material do you need to requisition?'
      }
    ]);
  };

  /**
   * Cleans up and renders message content without raw Markdown artifacts.
   */
  const renderMessageContent = (content: string) => {
    // Split into lines
    const lines = content.split('\n');
    return lines.map((line, lineIdx) => {
      // Process bold markers (**text**) and clean stray asterisks
      const parts = line.split(/(\*\*.*?\*\*)/g);
      return (
        <div key={lineIdx} className={lineIdx > 0 ? 'mt-1.5' : ''}>
          {parts.map((part, partIdx) => {
            if (part.startsWith('**') && part.endsWith('**')) {
              return (
                <strong key={partIdx} className="font-semibold text-slate-900">
                  {part.slice(2, -2)}
                </strong>
              );
            }
            // Clean single asterisks or decorative marks
            const cleanText = part.replace(/\*/g, '');
            return <span key={partIdx}>{cleanText}</span>;
          })}
        </div>
      );
    });
  };

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-w-4xl mx-auto bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex justify-between items-center">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 rounded-lg bg-blue-700 text-white flex items-center justify-center font-bold text-xs">
            PR
          </div>
          <div>
            <h2 className="font-semibold text-slate-800 text-sm">Purchase Requisition Assistant</h2>
            <p className="text-xs text-slate-500">Conversational Requisition Intake</p>
          </div>
        </div>
        <button
          onClick={resetChat}
          className="px-3 py-1.5 text-xs text-slate-600 hover:text-slate-900 hover:bg-slate-200/60 rounded-md flex items-center gap-1.5 transition font-medium"
        >
          <RefreshCw size={13} /> New Requisition
        </button>
      </div>

      {/* Suggested prompts */}
      <div className="px-6 py-2 bg-slate-50/70 border-b border-slate-100 flex items-center gap-2 overflow-x-auto text-xs">
        <span className="text-slate-500 flex items-center gap-1 font-medium whitespace-nowrap">
          <Package size={13} className="text-slate-400" /> Sample requests:
        </span>
        <button
          onClick={() => handleQuickPrompt("Need 200 nitrile gloves for Chakan by next Friday")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-700 px-2.5 py-1 rounded-md whitespace-nowrap transition text-xs"
        >
          200 nitrile gloves (Chakan)
        </button>
        <button
          onClick={() => handleQuickPrompt("Need 15 MS sheet 3mm for Bhosari plant by 2026-11-15")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-700 px-2.5 py-1 rounded-md whitespace-nowrap transition text-xs"
        >
          15 MS sheet 3mm (PUN1)
        </button>
        <button
          onClick={() => handleQuickPrompt("Urgent: need 2380 gearbox oil seals 45x62x8 for Pune by Nov 25")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-700 px-2.5 py-1 rounded-md whitespace-nowrap transition text-xs"
        >
          Urgent: 2380 oil seals
        </button>
        <button
          onClick={() => handleQuickPrompt("Need 1 Drinking water purifier 50 LPH for Pune plant")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-700 px-2.5 py-1 rounded-md whitespace-nowrap transition text-xs"
        >
          Water purifier (New Item)
        </button>
      </div>

      {/* Chat Messages */}
      <div className="flex-1 p-6 overflow-y-auto space-y-4">
        {messages.map((m, idx) => (
          <div
            key={idx}
            className={`flex items-start gap-3 ${m.role === 'USER' ? 'flex-row-reverse' : ''}`}
          >
            <div
              className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 text-white font-medium text-xs ${
                m.role === 'USER' ? 'bg-indigo-600' : 'bg-slate-700'
              }`}
            >
              {m.role === 'USER' ? <User size={14} /> : <Building2 size={14} />}
            </div>

            <div
              className={`max-w-[80%] rounded-xl px-4 py-3 text-xs leading-relaxed ${
                m.role === 'USER'
                  ? 'bg-blue-600 text-white rounded-tr-none'
                  : 'bg-slate-100 text-slate-800 rounded-tl-none border border-slate-200'
              }`}
            >
              {renderMessageContent(m.content)}
            </div>
          </div>
        ))}

        {/* Candidate items clarification card */}
        {candidates.length > 0 && (
          <div className="ml-10 max-w-xl bg-slate-50 border border-slate-300 rounded-lg p-4 space-y-2.5">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-800">
              <HelpCircle size={14} className="text-blue-600" /> Multiple catalog items match. Please select the required item:
            </div>
            <div className="space-y-1.5">
              {candidates.map(item => (
                <div
                  key={item.itemCode}
                  onClick={() => handleSelectCandidate(item)}
                  className="bg-white p-2.5 rounded border border-slate-200 hover:border-blue-500 hover:shadow-xs cursor-pointer transition flex justify-between items-center text-xs"
                >
                  <div>
                    <span className="font-semibold text-blue-700">{item.itemCode}</span> — {item.description}
                    <div className="text-slate-500 text-[11px] mt-0.5">{item.category} · UoM: {item.uom}</div>
                  </div>
                  <div className="font-medium text-slate-700">₹{item.standardPrice.toLocaleString('en-IN')}</div>
                </div>
              ))}
              <div
                onClick={() => handleSend("This is a new item not listed in the catalog")}
                className="bg-white p-2 rounded border border-dashed border-slate-300 hover:border-slate-500 hover:bg-slate-50 text-slate-600 cursor-pointer text-xs text-center transition font-medium"
              >
                Item not in catalog (Proceed as New Item)
              </div>
            </div>
          </div>
        )}

        {/* Confirmation prompt buttons */}
        {lastResponse?.status === 'CONFIRMATION_PENDING' && (
          <div className="ml-10 flex gap-2">
            <button
              onClick={() => handleSend("Yes, please proceed and create the PR")}
              className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow-xs transition"
            >
              <CheckCircle2 size={14} /> Confirm & Submit Requisition
            </button>
            <button
              onClick={() => handleSend("No, I want to change something")}
              className="bg-slate-200 hover:bg-slate-300 text-slate-700 px-4 py-2 rounded-lg text-xs font-semibold transition"
            >
              Modify Details
            </button>
          </div>
        )}

        {/* Loading indicator */}
        {loading && (
          <div className="flex items-center gap-2 text-slate-500 text-xs ml-10">
            <RefreshCw size={13} className="animate-spin text-blue-600" />
            <span>Processing requisition details...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Form */}
      <div className="p-4 bg-slate-50 border-t border-slate-200">
        <form
          onSubmit={e => {
            e.preventDefault();
            handleSend();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Describe your requirement (e.g., 'Need 200 nitrile gloves for Chakan by next Friday')..."
            disabled={loading}
            className="flex-1 bg-white border border-slate-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 rounded-lg px-4 py-2.5 text-xs outline-none transition disabled:bg-slate-100 text-slate-800"
          />
          <button
            type="submit"
            disabled={loading || !input.trim()}
            className="bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white px-5 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition shrink-0"
          >
            <Send size={14} /> Submit
          </button>
        </form>
      </div>
    </div>
  );
};
