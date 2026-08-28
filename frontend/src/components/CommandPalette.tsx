import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Search,
  LayoutDashboard,
  FileX2,
  CheckSquare,
  List,
  ArrowRight,
  X,
  CreditCard,
} from "lucide-react";
import { searchGlobal } from "../lib/api";

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigate: (tabId: string) => void;
}

export function CommandPalette({ isOpen, onClose, onNavigate }: CommandPaletteProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery("");
      setResults([]);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }

    setLoading(true);
    const timeout = setTimeout(async () => {
      try {
        const res = await searchGlobal(query);
        setResults(Array.isArray(res) ? res : []);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(timeout);
  }, [query]);

  if (!isOpen) return null;

  const quickNav = [
    { id: "dashboard", label: "Overview & ROI", icon: LayoutDashboard, tag: "Dashboard" },
    { id: "mandates", label: "Failed Mandates Log", icon: FileX2, tag: "Feed" },
    { id: "approvals", label: "AI Approval Queue", icon: CheckSquare, tag: "Actions" },
    { id: "audit", label: "Cryptographic Audit Trail", icon: List, tag: "Compliance" },
  ];

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 sm:px-6">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm"
        />

        {/* Modal Window */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: -20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: -20 }}
          transition={{ type: "spring", stiffness: 350, damping: 30 }}
          className="relative w-full max-w-xl rounded-2xl bg-slate-900/95 border border-slate-700/70 shadow-2xl overflow-hidden backdrop-blur-xl z-10 text-white"
        >
          {/* Header & Input */}
          <div className="flex items-center px-4 py-3.5 border-b border-slate-800 gap-3">
            <Search className="w-5 h-5 text-slate-400 shrink-0" />
            <input
              ref={inputRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search payments, audit logs, or jump to view... (ESC to exit)"
              className="w-full bg-transparent text-sm text-white placeholder:text-slate-500 focus:outline-none"
            />
            {query && (
              <button
                onClick={() => setQuery("")}
                className="text-slate-400 hover:text-white p-1 rounded-md"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Body Content */}
          <div className="max-h-80 overflow-y-auto p-3 space-y-4">
            {/* Quick Navigation Section */}
            {!query && (
              <div>
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 px-3 block mb-2">
                  Navigation Shortcuts
                </span>
                <div className="space-y-1">
                  {quickNav.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => {
                        onNavigate(item.id);
                        onClose();
                      }}
                      className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-left text-sm text-slate-300 hover:text-white hover:bg-slate-800/60 transition-colors group"
                    >
                      <div className="flex items-center gap-3">
                        <item.icon className="w-4 h-4 text-slate-400 group-hover:text-blue-400" />
                        <span>{item.label}</span>
                      </div>
                      <span className="text-[10px] font-mono font-medium text-slate-500 bg-slate-800 px-2 py-0.5 rounded border border-slate-700">
                        {item.tag}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Search Results */}
            {query && (
              <div>
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 px-3 block mb-2">
                  Search Results {loading ? "(Searching...)" : `(${results.length})`}
                </span>

                {results.length === 0 && !loading && (
                  <div className="text-center py-8 text-slate-500 text-sm">
                    No matching records found for "{query}".
                  </div>
                )}

                {results.map((item, index) => (
                  <button
                    key={index}
                    onClick={() => {
                      if (item.type === "PAYMENT_EVENT") onNavigate("mandates");
                      else if (item.type === "RECOVERY_ACTION") onNavigate("approvals");
                      else onNavigate("audit");
                      onClose();
                    }}
                    className="w-full flex items-center justify-between p-3 rounded-xl bg-slate-800/40 hover:bg-slate-800/80 border border-slate-800 transition-colors text-left"
                  >
                    <div className="flex items-center gap-3 truncate">
                      <CreditCard className="w-4 h-4 text-blue-400 shrink-0" />
                      <div className="truncate">
                        <p className="text-xs font-bold text-slate-200 truncate">{item.title || item.razorpayPaymentId || item.id}</p>
                        <p className="text-[11px] text-slate-400 truncate">{item.subtitle || item.eventType || item.action}</p>
                      </div>
                    </div>
                    <ArrowRight className="w-4 h-4 text-slate-500 shrink-0" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Footer bar */}
          <div className="px-4 py-2.5 bg-slate-950/60 border-t border-slate-800 text-[11px] text-slate-500 flex items-center justify-between font-mono">
            <span>Tip: Press <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300">Ctrl</kbd> + <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300">K</kbd> anywhere</span>
            <span>RecoverMandate AI</span>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
