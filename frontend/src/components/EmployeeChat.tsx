import React, { useState, useEffect, useRef } from 'react';
import { api } from '../api';
import { ChatMessage, Item, ChatResponse } from '../types';
import {
  Send,
  Bot,
  User,
  CheckCircle2,
  AlertCircle,
  Sparkles,
  Building2,
  Calendar,
  Package,
  RefreshCw
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
          content: 'Hello! I am your Cantill Procurement Assistant. I help you raise purchase requisitions through conversation.\n\nTell me what you need, for which plant, and by when. For example:\n- *"Need 200 nitrile gloves for Chakan by next Friday"*\n- *"Require 15 MS sheet 3mm for Pune by 15 Nov"*\n- *"Urgent need: 2380 gearbox oil seals for Bhosari"*'
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
    } catch (err: any) {
      setMessages(prev => [
        ...prev,
        { role: 'ASSISTANT', content: 'Sorry, I encountered an issue communicating with the procurement server. Please try again.' }
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
        content: 'Chat session reset. What item or service do you need to requisition today?'
      }
    ]);
  };

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-w-5xl mx-auto bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex justify-between items-center">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold">
            <Bot size={20} />
          </div>
          <div>
            <h2 className="font-semibold text-slate-800">Employee Procurement Assistant</h2>
            <p className="text-xs text-slate-500">Conversational Purchase Requisition Intake · Corvo Policy v5.0</p>
          </div>
        </div>
        <button
          onClick={resetChat}
          className="px-3 py-1.5 text-xs text-slate-600 hover:text-slate-900 hover:bg-slate-200/60 rounded-md flex items-center gap-1.5 transition"
        >
          <RefreshCw size={14} /> New Request
        </button>
      </div>

      {/* Suggested prompts */}
      <div className="px-6 py-2 bg-slate-50/70 border-b border-slate-100 flex items-center gap-2 overflow-x-auto text-xs">
        <span className="text-slate-400 flex items-center gap-1 font-medium whitespace-nowrap">
          <Sparkles size={13} className="text-amber-500" /> Quick examples:
        </span>
        <button
          onClick={() => handleQuickPrompt("Need 200 nitrile gloves for Chakan by next Friday")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-600 px-2.5 py-1 rounded-full whitespace-nowrap transition"
        >
          200 nitrile gloves (Chakan)
        </button>
        <button
          onClick={() => handleQuickPrompt("Need 15 MS sheet 3mm for Bhosari plant by 2026-11-15")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-600 px-2.5 py-1 rounded-full whitespace-nowrap transition"
        >
          15 MS sheet 3mm (PUN1)
        </button>
        <button
          onClick={() => handleQuickPrompt("Urgent: need 2380 gearbox oil seals 45x62x8 for Pune by Nov 25")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-600 px-2.5 py-1 rounded-full whitespace-nowrap transition"
        >
          Urgent: 2380 oil seals
        </button>
        <button
          onClick={() => handleQuickPrompt("Need 1 Drinking water purifier 50 LPH for Pune plant")}
          className="bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-600 px-2.5 py-1 rounded-full whitespace-nowrap transition"
        >
          Drinking water purifier (New Item)
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
              className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-white font-medium text-xs ${
                m.role === 'USER' ? 'bg-indigo-600' : 'bg-blue-600'
              }`}
            >
              {m.role === 'USER' ? <User size={16} /> : <Bot size={16} />}
            </div>

            <div
              className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-line ${
                m.role === 'USER'
                  ? 'bg-blue-600 text-white rounded-tr-none'
                  : 'bg-slate-100 text-slate-800 rounded-tl-none border border-slate-200/60'
              }`}
            >
              {m.content}
            </div>
          </div>
        ))}

        {/* Candidate items clarification card */}
        {candidates.length > 0 && (
          <div className="ml-11 max-w-xl bg-blue-50/70 border border-blue-200 rounded-xl p-4 space-y-2">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-blue-900">
              <Package size={15} /> Select the exact matching item:
            </div>
            <div className="space-y-1.5">
              {candidates.map(item => (
                <div
                  key={item.itemCode}
                  onClick={() => handleSelectCandidate(item)}
                  className="bg-white p-2.5 rounded-lg border border-slate-200 hover:border-blue-500 hover:shadow-sm cursor-pointer transition flex justify-between items-center text-xs"
                >
                  <div>
                    <span className="font-semibold text-blue-700">{item.itemCode}</span> — {item.description}
                    <div className="text-slate-400 text-[11px]">{item.category} · UoM: {item.uom}</div>
                  </div>
                  <div className="font-medium text-slate-700">₹{item.standardPrice}</div>
                </div>
              ))}
              <div
                onClick={() => handleSend("This is a new item not listed in the catalog")}
                className="bg-white p-2 rounded-lg border border-dashed border-slate-300 hover:border-slate-500 hover:bg-slate-50 text-slate-600 cursor-pointer text-xs text-center transition"
              >
                None of the above (Classify as New Item)
              </div>
            </div>
          </div>
        )}

        {/* Confirmation prompt buttons */}
        {lastResponse?.status === 'CONFIRMATION_PENDING' && (
          <div className="ml-11 flex gap-2">
            <button
              onClick={() => handleSend("Yes, please proceed and create the PR")}
              className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow-sm transition"
            >
              <CheckCircle2 size={15} /> Yes, Confirm & Submit PR
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
          <div className="flex items-center gap-2 text-slate-400 text-xs ml-11">
            <Bot size={16} className="animate-pulse text-blue-500" />
            <span>Assistant is thinking...</span>
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
            placeholder="Type your purchase request (e.g. 'Need 200 nitrile gloves for Chakan by next Friday')..."
            disabled={loading}
            className="flex-1 bg-white border border-slate-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 rounded-lg px-4 py-2.5 text-sm outline-none transition disabled:bg-slate-100"
          />
          <button
            type="submit"
            disabled={loading || !input.trim()}
            className="bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white px-5 py-2.5 rounded-lg text-sm font-medium flex items-center gap-1.5 transition shrink-0"
          >
            <Send size={16} /> Send
          </button>
        </form>
      </div>
    </div>
  );
};
