import { motion, AnimatePresence } from "framer-motion";
import { Keyboard, X, Sparkles } from "lucide-react";

interface KeyboardShortcutsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function KeyboardShortcutsModal({ isOpen, onClose }: KeyboardShortcutsModalProps) {
  if (!isOpen) return null;

  const isMac =
    typeof window !== "undefined" &&
    (/Mac|iPod|iPhone|iPad/.test(navigator.platform || "") || /Mac/i.test(navigator.userAgent || ""));

  const shortcutGroups = [
    {
      title: "Global Navigation",
      shortcuts: [
        {
          keys: [isMac ? "⌘" : "Ctrl", "K"],
          description: "Open Global Command Palette & Deep Search",
        },
        {
          keys: ["?"],
          description: "Toggle this Keyboard Shortcut Help Modal",
        },
        {
          keys: ["1"],
          description: "Switch to Overview & ROI Dashboard",
        },
        {
          keys: ["2"],
          description: "Switch to Failed Mandates Feed",
        },
        {
          keys: ["3"],
          description: "Switch to AI Approval Queue",
        },
        {
          keys: ["4"],
          description: "Switch to Cryptographic Audit Log",
        },
      ],
    },
    {
      title: "Operations & Actions",
      shortcuts: [
        {
          keys: ["B"],
          description: "Trigger 'Batch Approve Safe Mandates (< ₹2,500)'",
        },
        {
          keys: ["R"],
          description: "Refresh live telemetry, funnel metrics & queue",
        },
        {
          keys: ["Esc"],
          description: "Close active drawer, modal, or command palette",
        },
      ],
    },
  ];

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-slate-950/75 backdrop-blur-md"
        />

        {/* Modal Dialog */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          transition={{ type: "spring", stiffness: 350, damping: 28 }}
          className="relative w-full max-w-lg rounded-2xl bg-[#0C2340] border border-[#3395FF]/40 shadow-2xl overflow-hidden z-10 text-white"
        >
          {/* Header */}
          <div className="p-5 border-b border-slate-700/60 flex items-center justify-between bg-[#08182D]/90">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center shadow-lg shadow-[#3395FF]/20">
                <Keyboard className="w-5 h-5 text-[#3395FF]" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  Keyboard Shortcuts
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#3395FF]/20 text-[#93c5fd] border border-[#3395FF]/30">
                    Pro Ops
                  </span>
                </h3>
                <p className="text-xs text-slate-400">Power-user keyboard navigation for RecoverMandate</p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Content */}
          <div className="p-6 space-y-6 max-h-[70vh] overflow-y-auto">
            {shortcutGroups.map((group) => (
              <div key={group.title} className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-[#93c5fd]">
                  {group.title}
                </h4>
                <div className="space-y-2">
                  {group.shortcuts.map((sc, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between p-2.5 rounded-xl bg-[#02042B]/80 border border-slate-800 hover:border-slate-700 transition-colors"
                    >
                      <span className="text-xs text-slate-300 font-medium">{sc.description}</span>
                      <div className="flex items-center gap-1.5 shrink-0">
                        {sc.keys.map((k, ki) => (
                          <kbd
                            key={ki}
                            className="px-2 py-1 rounded bg-[#0C2340] border border-slate-700 text-xs font-mono font-bold text-white shadow-sm"
                          >
                            {k}
                          </kbd>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          {/* Footer */}
          <div className="p-4 bg-[#08182D] border-t border-slate-700/60 text-xs text-slate-400 flex items-center justify-between">
            <span className="flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-[#3395FF]" /> Press <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] font-mono text-white">?</kbd> anytime to open this guide
            </span>
            <button
              onClick={onClose}
              className="px-3 py-1.5 rounded-lg bg-[#3395FF] hover:bg-[#2582eb] text-white text-xs font-bold transition-colors"
            >
              Got it
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
