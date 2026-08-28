import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";
import {
  CheckCircle,
  XCircle,
  Bot,
  User,
  ShieldCheck,
  Sliders,
  Cpu,
} from "lucide-react";
import {
  fetchRecoveryActions,
  approveRecoveryAction,
  rejectRecoveryAction,
} from "../lib/api";

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

const TypewriterText = ({ text }: { text: string }) => {
  const [displayedText, setDisplayedText] = useState("");

  useEffect(() => {
    let i = 0;
    setDisplayedText("");
    const interval = setInterval(() => {
      setDisplayedText(text.substring(0, i));
      i += 3;
      if (i > text.length) clearInterval(interval);
    }, 8);
    return () => clearInterval(interval);
  }, [text]);

  return <span>{displayedText}</span>;
};

export function ApprovalQueuePage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { toast } = useToast();

  const load = () => {
    setLoading(true);
    fetchRecoveryActions(0, 50, "DRAFTED")
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [refreshTrigger]);

  const handleApprove = async (id: number) => {
    try {
      await approveRecoveryAction(id);
      toast({ title: "Approved", description: "Recovery action dispatched successfully." });
      load();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load recovery actions: {error}</p>
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between sm:items-end gap-3 mb-2">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-white">Action Required</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Review AI-drafted communications and adjust tone parameters before dispatch.
          </p>
        </div>
      </div>

      {loading ? (
        <div className="space-y-4">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="skeleton-shimmer h-64 w-full rounded-2xl" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
          <CheckCircle className="w-12 h-12 text-emerald-500 mb-2" />
          <p className="font-bold text-lg text-slate-900 dark:text-white">Inbox Zero!</p>
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400 max-w-sm">
            No pending approvals. Go grab a coffee.
          </p>
        </div>
      ) : (
        data.content.map((action: any) => (
          <motion.div key={action.id} variants={fadeUp}>
            <ApprovalCard action={action} onApprove={() => handleApprove(action.id)} onReload={load} />
          </motion.div>
        ))
      )}
    </motion.div>
  );
}

function ApprovalCard({
  action,
  onApprove,
  onReload,
}: {
  action: any;
  onApprove: () => void;
  onReload: () => void;
}) {
  const [rejectReason, setRejectReason] = useState("");
  const [isRejecting, setIsRejecting] = useState(false);
  const [toneSetting, setToneSetting] = useState<"urgent" | "standard" | "gentle">("standard");
  const { toast } = useToast();

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      toast({ title: "Required", description: "Please provide a reason to train the AI.", variant: "destructive" });
      return;
    }
    try {
      await rejectRecoveryAction(action.id, rejectReason);
      toast({ title: "Rejected", description: "Feedback sent to model." });
      onReload();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  const isHeuristic = action.draftSource === "HEURISTIC";

  return (
    <div className="glass-card rounded-2xl overflow-hidden shadow-xl border-blue-500/10">
      <div className="p-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">Strategy #{action.id}</h3>
              <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400 border border-amber-500/20 text-xs font-bold uppercase tracking-wider">
                Needs Review
              </Badge>
              {isHeuristic ? (
                <Badge className="bg-purple-100 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300 border border-purple-500/30 text-xs font-semibold flex items-center gap-1">
                  <Cpu className="w-3 h-3" /> Heuristic Engine
                </Badge>
              ) : (
                <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300 border border-blue-500/30 text-xs font-semibold flex items-center gap-1">
                  <Bot className="w-3 h-3" /> Gemini 3.5 Flash
                </Badge>
              )}
            </div>
            <p className="text-xs font-mono text-slate-500 mt-1.5">
              Drafted: {new Date(action.createdAt).toLocaleString()}
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            {/* Tone Adjuster Pills */}
            <div className="flex items-center bg-slate-100 dark:bg-slate-800/80 p-1 rounded-xl border border-slate-200 dark:border-slate-700/60 text-xs">
              <Sliders className="w-3 h-3 text-slate-400 mx-1.5 hidden sm:block" />
              <button
                onClick={() => setToneSetting("gentle")}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all ${
                  toneSetting === "gentle"
                    ? "bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm"
                    : "text-slate-500 hover:text-slate-900 dark:hover:text-white"
                }`}
              >
                Gentle
              </button>
              <button
                onClick={() => setToneSetting("standard")}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all ${
                  toneSetting === "standard"
                    ? "bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm"
                    : "text-slate-500 hover:text-slate-900 dark:hover:text-white"
                }`}
              >
                Balanced
              </button>
              <button
                onClick={() => setToneSetting("urgent")}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all ${
                  toneSetting === "urgent"
                    ? "bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm"
                    : "text-slate-500 hover:text-slate-900 dark:hover:text-white"
                }`}
              >
                Urgent
              </button>
            </div>

            <Badge className="bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400 border border-emerald-500/20 text-xs font-semibold flex items-center gap-1.5 shadow-sm">
              <ShieldCheck className="w-3 h-3" /> Tone: Verified
            </Badge>
          </div>
        </div>

        {/* Split Interface - "The Code Editor" feel */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-0 border border-slate-200 dark:border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
          {/* Left: AI Draft (7 cols) */}
          <div className="lg:col-span-7 bg-white dark:bg-[#0a0a0a] p-5 relative">
            <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-purple-500 to-blue-500" />
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 mb-4">
              <Bot className="w-4 h-4 text-purple-500" /> {isHeuristic ? "Heuristic Template Draft" : "Gemini Generated Draft"}
            </h4>
            <div className="text-sm leading-relaxed text-slate-800 dark:text-slate-300 font-mono whitespace-pre-wrap pl-2 border-l-2 border-slate-100 dark:border-slate-800 min-h-[120px]">
              <TypewriterText text={action.aiDraftMessage} />
            </div>
          </div>

          {/* Right: Actions (5 cols) */}
          <div className="lg:col-span-5 bg-slate-50 dark:bg-slate-900/50 p-5 flex flex-col justify-between border-t lg:border-t-0 lg:border-l border-slate-200 dark:border-slate-700/60">
            <div className="space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2">
                <User className="w-4 h-4 text-blue-500" /> Human Decision
              </h4>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Review the draft. Approving will automatically generate a Razorpay Payment Link and dispatch the recovery communication.
              </p>
            </div>

            <div className="flex flex-col gap-3 mt-6">
              <Button
                onClick={onApprove}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold shadow-lg shadow-blue-600/20 py-6 text-base group"
              >
                <CheckCircle className="w-5 h-5 mr-2 group-hover:scale-110 transition-transform" /> Approve & Dispatch
              </Button>

              <AnimatePresence mode="wait">
                {!isRejecting ? (
                  <motion.div key="reject-btn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <Button
                      onClick={() => setIsRejecting(true)}
                      variant="outline"
                      className="w-full border-rose-200 text-rose-600 hover:bg-rose-50 dark:border-rose-900/50 dark:text-rose-400 dark:hover:bg-rose-950/30 font-semibold"
                    >
                      <XCircle className="w-4 h-4 mr-2" /> Reject Draft
                    </Button>
                  </motion.div>
                ) : (
                  <motion.div
                    key="reject-form"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    className="flex flex-col gap-2 overflow-hidden"
                  >
                    <Input
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="Why? (Improves AI model)"
                      className="bg-white dark:bg-slate-950 border-slate-300 dark:border-slate-700 text-sm focus-visible:ring-rose-500"
                      autoFocus
                    />
                    <div className="flex gap-2">
                      <Button onClick={handleReject} variant="destructive" className="flex-1 font-bold shadow-lg shadow-rose-600/20">
                        Confirm
                      </Button>
                      <Button
                        onClick={() => setIsRejecting(false)}
                        variant="outline"
                        className="flex-1 bg-white dark:bg-slate-900 border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300"
                      >
                        Cancel
                      </Button>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
