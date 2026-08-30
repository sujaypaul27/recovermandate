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
  ShieldCheck,
  Zap,
  Keyboard,
} from "lucide-react";
import { searchGlobal } from "../lib/api";

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigate: (tabId: string) => void;
  onOpenShortcuts?: () => void;
}

export function CommandPalette({
  isOpen,
  onClose,
  onNavigate,
  onOpenShortcuts,
}: CommandPaletteProps) {
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
    }, 250);

    return () => clearTimeout(timeout);
  }, [query]);

  if (!isOpen) return null;

  const quickNav = [
    { id: "dashboard", label: "Overview & ROI Dashboard", icon: LayoutDashboard, tag: "1" },
    { id: "mandates", label: "Failed Mandates & Retries Log", icon: FileX2, tag: "2" },
    { id: "approvals", label: "AI Approval Queue & Dunning", icon: CheckSquare, tag: "3" },
    { id: "audit", label: "Cryptographic Audit Trail", icon: List, tag: "4" },
  ];

  const getItemIcon = (type: string) => {
    if (type === "AUDIT_LOG") return <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />;
    if (type === "RECOVERY_ACTION") return <Zap className="w-4 h-4 text-amber-400 shrink-0" />;
    return <CreditCard className="w-4 h-4 text-[#3395FF] shrink-0" />;
  };

  const getItemBadge = (item: any) => {
    if (item.type === "AUDIT_LOG") return "Audit";
    if (item.subtitle && item.subtitle.includes("@")) return "Customer";
    if (item.title && item.title.startsWith("sub_")) return "Subscription";
    return "Payment";
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 sm:px-6">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-slate-950/75 backdrop-blur-md"
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
              placeholder="Search payments, emails, subscriptions, or jump to view... (ESC to exit)"
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
                  Navigation Views
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
                      <kbd className="text-[10px] font-mono font-bold text-slate-400 bg-slate-800 px-2 py-0.5 rounded border border-slate-700">
                        {item.tag}
                      </kbd>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Search Results */}
            {query && (
              <div>
                <div className="flex items-center justify-between px-3 mb-2">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Deep Search Results {loading ? "(Searching...)" : `(${results.length})`}
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Payment ID · Email · Subscriptions</span>
                </div>

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
                    className="w-full flex items-center justify-between p-3 rounded-xl bg-slate-800/40 hover:bg-slate-800/80 border border-slate-800 transition-colors text-left group mb-1.5"
                  >
                    <div className="flex items-center gap-3 truncate">
                      {getItemIcon(item.type)}
                      <div className="truncate">
                        <div className="flex items-center gap-2">
                          <p className="text-xs font-bold text-slate-200 group-hover:text-white truncate">
                            {item.title || item.razorpayPaymentId || item.id}
                          </p>
                          <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-slate-700/60 text-slate-300 border border-slate-600/50">
                            {getItemBadge(item)}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 group-hover:text-slate-300 truncate mt-0.5">
                          {item.subtitle || item.eventType || item.action}
                        </p>
                      </div>
                    </div>
                    <ArrowRight className="w-4 h-4 text-slate-500 group-hover:text-[#3395FF] group-hover:translate-x-0.5 transition-all shrink-0 ml-2" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Footer bar */}
          <div className="px-4 py-2.5 bg-slate-950/60 border-t border-slate-800 text-[11px] text-slate-400 flex items-center justify-between font-mono">
            <span className="flex items-center gap-1.5">
              <span>Shortcuts:</span>
              <button
                onClick={() => {
                  onClose();
                  if (onOpenShortcuts) onOpenShortcuts();
                }}
                className="text-[#3395FF] hover:underline flex items-center gap-1 font-semibold"
              >
                <Keyboard className="w-3 h-3" /> Press <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-white font-bold">?</kbd>
              </button>
            </span>
            <span>RecoverMandate AI</span>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
