import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Server,
  User,
  Bot,
  Activity,
  List,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  Hash,
  Fingerprint,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  Calendar,
  Search,
  X,
  Radio,
  Play,
  Eye,
  Copy,
  Check,
  Inbox,
  ShieldAlert,
} from "lucide-react";
import {
  fetchAuditLogs,
  verifyAuditChain,
  fetchWebhookDlq,
  replayWebhookDlq,
  type AuditChainVerification,
  type PageResponse,
  type AuditLogItem,
  type WebhookDlqItem,
} from "../lib/api";

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

export function AuditLogPage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [activeTab, setActiveTab] = useState<"trail" | "dlq">("trail");

  // Audit Logs State
  const [data, setData] = useState<PageResponse<AuditLogItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState<"all" | "7d" | "30d">("all");

  const [chainStatus, setChainStatus] = useState<AuditChainVerification | null>(null);
  const [verifyingChain, setVerifyingChain] = useState(false);

  // Webhook DLQ State
  const [dlqItems, setDlqItems] = useState<WebhookDlqItem[]>([]);
  const [dlqLoading, setDlqLoading] = useState(false);
  const [replayingId, setReplayingId] = useState<number | null>(null);
  const [selectedDlqPayload, setSelectedDlqPayload] = useState<WebhookDlqItem | null>(null);
  const [copied, setCopied] = useState(false);
  const [replaySuccessMsg, setReplaySuccessMsg] = useState<string | null>(null);

  const checkChain = async () => {
    setVerifyingChain(true);
    try {
      const res = await verifyAuditChain();
      setChainStatus(res);
    } catch (e: any) {
      setChainStatus({
        valid: false,
        chainLength: 0,
        brokenAtId: null,
        message: e.message || "Failed to verify cryptographic chain",
      });
    } finally {
      setVerifyingChain(false);
    }
  };

  const loadAuditLogs = () => {
    setLoading(true);
    fetchAuditLogs(page, 30)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  const loadDlq = () => {
    setDlqLoading(true);
    fetchWebhookDlq()
      .then(setDlqItems)
      .catch((e) => setError(e.message))
      .finally(() => setDlqLoading(false));
  };

  useEffect(() => {
    loadAuditLogs();
  }, [page, refreshTrigger]);

  useEffect(() => {
    checkChain();
  }, [refreshTrigger]);

  useEffect(() => {
    if (activeTab === "dlq") {
      loadDlq();
    }
  }, [activeTab, refreshTrigger]);

  const handleReplay = async (id: number) => {
    setReplayingId(id);
    setReplaySuccessMsg(null);
    try {
      const res = await replayWebhookDlq(id);
      setReplaySuccessMsg(`Webhook #${id} replayed successfully into pipeline! Produced Event #${res.eventId}`);
      loadDlq();
      loadAuditLogs();
    } catch (e: any) {
      alert(`Replay failed: ${e.message}`);
    } finally {
      setReplayingId(null);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getActorConfig = (actor: string) => {
    switch (actor) {
      case "SYSTEM":
        return {
          icon: <Server className="w-4 h-4" />,
          bg: "bg-blue-100 dark:bg-blue-500/15",
          text: "text-blue-700 dark:text-blue-400",
          label: "System",
        };
      case "HUMAN":
        return {
          icon: <User className="w-4 h-4" />,
          bg: "bg-amber-100 dark:bg-amber-500/15",
          text: "text-amber-700 dark:text-amber-400",
          label: "Human",
        };
      case "AI":
        return {
          icon: <Bot className="w-4 h-4" />,
          bg: "bg-purple-100 dark:bg-purple-500/15",
          text: "text-purple-700 dark:text-purple-400",
          label: "AI Model",
        };
      default:
        return {
          icon: <Activity className="w-4 h-4" />,
          bg: "bg-slate-100 dark:bg-slate-500/15",
          text: "text-slate-700 dark:text-slate-400",
          label: actor,
        };
    }
  };

  const rawItems: AuditLogItem[] = data?.content || [];
  const filteredItems = rawItems.filter((log: AuditLogItem) => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      const matchAction = log.action?.toLowerCase().includes(q);
      const matchEntity = log.entityType?.toLowerCase().includes(q);
      const matchActor = log.actor?.toLowerCase().includes(q);
      const matchDetails = log.details?.toLowerCase().includes(q);
      const matchTraceId = log.traceId?.toLowerCase().includes(q);
      const matchEntityId = String(log.entityId || "").includes(q);
      if (!matchAction && !matchEntity && !matchActor && !matchDetails && !matchTraceId && !matchEntityId) {
        return false;
      }
    }

    if (dateRange !== "all" && log.timestamp) {
      const logTime = new Date(log.timestamp).getTime();
      const now = Date.now();
      const days = dateRange === "7d" ? 7 : 30;
      const cutoff = now - days * 24 * 60 * 60 * 1000;
      if (logTime < cutoff) {
        return false;
      }
    }

    return true;
  });

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load audit trail: {error}</p>
        <Button onClick={loadAuditLogs} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }} className="space-y-4">
      {/* Sub-Tab Switcher */}
      <div className="flex items-center gap-2 p-1.5 glass-card rounded-2xl w-fit">
        <button
          onClick={() => setActiveTab("trail")}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            activeTab === "trail"
              ? "bg-blue-600 text-white shadow-md shadow-blue-500/20"
              : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
          }`}
        >
          <ShieldCheck className="w-4 h-4" /> Cryptographic Audit Trail
        </button>
        <button
          onClick={() => setActiveTab("dlq")}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            activeTab === "dlq"
              ? "bg-rose-600 text-white shadow-md shadow-rose-500/20"
              : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
          }`}
        >
          <Radio className="w-4 h-4" /> Webhook DLQ & Forensic Replay
          {dlqItems.filter((i) => i.status === "REJECTED").length > 0 && (
            <span className="px-1.5 py-0.5 rounded-full bg-rose-500/20 text-rose-300 text-[10px] font-mono font-bold">
              {dlqItems.filter((i) => i.status === "REJECTED").length}
            </span>
          )}
        </button>
      </div>

      {activeTab === "trail" ? (
        <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
          <div className="p-6 border-b border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-transparent flex flex-col sm:flex-row justify-between sm:items-center gap-4">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-lg font-bold text-slate-900 dark:text-white">Immutable Audit Log</h2>
                <Badge className="bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3" /> SHA-256 Hash Chained
                </Badge>
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                Cryptographically secure, tamper-evident timeline of all state transitions and AI actions.
              </p>
            </div>

            {/* Cryptographic Verification Badge & Action */}
            <div className="flex items-center gap-2.5 flex-wrap">
              {chainStatus && (
                chainStatus.valid ? (
                  <div
                    title={chainStatus.message}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 text-xs font-bold shadow-sm"
                  >
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                    <span>✅ Chain Verified ({chainStatus.chainLength} entries)</span>
                  </div>
                ) : (
                  <div
                    title={chainStatus.message}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-rose-500/10 border border-rose-500/30 text-rose-700 dark:text-rose-400 text-xs font-bold shadow-sm"
                  >
                    <AlertTriangle className="w-3.5 h-3.5 text-rose-500" />
                    <span>⚠️ Chain Broken at ID #{chainStatus.brokenAtId}</span>
                  </div>
                )
              )}

              <Button
                onClick={checkChain}
                disabled={verifyingChain}
                variant="outline"
                size="sm"
                className="h-8 text-xs font-semibold gap-1.5 bg-white/80 dark:bg-slate-800/80 hover:bg-slate-100 dark:hover:bg-slate-700 border-slate-300 dark:border-slate-700"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${verifyingChain ? "animate-spin text-blue-500" : ""}`} />
                {verifyingChain ? "Verifying..." : "Verify Chain"}
              </Button>
            </div>
          </div>

          {/* Filter Toolbar */}
          <div className="p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/40 flex flex-col sm:flex-row items-center justify-between gap-3">
            {/* Search Box */}
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <Input
                type="text"
                placeholder="Search by Action, Entity, Actor, Trace..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 pr-8 h-9 text-xs bg-white dark:bg-slate-800/80 border-slate-300 dark:border-slate-700"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {/* Date Range Selector */}
            <div className="flex items-center gap-1.5 w-full sm:w-auto justify-end">
              <span className="text-xs font-semibold text-slate-400 mr-1 flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5" /> Period:
              </span>
              {(["all", "7d", "30d"] as const).map((r) => (
                <button
                  key={r}
                  onClick={() => setDateRange(r)}
                  className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                    dateRange === r
                      ? "bg-blue-600 text-white shadow-sm"
                      : "bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700"
                  }`}
                >
                  {r === "all" ? "All Time" : r === "7d" ? "Last 7 Days" : "Last 30 Days"}
                </button>
              ))}

              {(searchQuery || dateRange !== "all") && (
                <Badge variant="secondary" className="ml-1 text-[10px] font-mono">
                  {filteredItems.length} of {rawItems.length}
                </Badge>
              )}
            </div>
          </div>

          <div className="p-6">
            {loading ? (
              <div className="space-y-4">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="skeleton-shimmer h-20 w-full rounded-xl" />
                ))}
              </div>
            ) : filteredItems.length === 0 ? (
              <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
                <List className="w-10 h-10 text-slate-400" />
                <p className="font-bold text-lg text-slate-900 dark:text-white">
                  {rawItems.length === 0 ? "No audit logs found" : "No logs match your filters"}
                </p>
                {(searchQuery || dateRange !== "all") && (
                  <Button
                    onClick={() => {
                      setSearchQuery("");
                      setDateRange("all");
                    }}
                    variant="outline"
                    size="sm"
                    className="text-xs mt-2"
                  >
                    Clear Filters
                  </Button>
                )}
              </div>
            ) : (
              <ScrollArea className="h-[600px] pr-4">
                <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-4 timeline-line ml-2">
                  {filteredItems.map((log: AuditLogItem) => {
                    const actorConfig = getActorConfig(log.actor);
                    return (
                      <motion.div key={log.id} variants={fadeUp} className="flex gap-5 relative group">
                        <div className="relative z-10 flex-shrink-0 mt-1">
                          <div
                            className={`w-10 h-10 rounded-full shadow-sm border border-white dark:border-slate-800 ${actorConfig.bg} flex items-center justify-center ${actorConfig.text} group-hover:scale-110 transition-transform duration-300`}
                          >
                            {actorConfig.icon}
                          </div>
                        </div>

                        <div className="flex-1 min-w-0 bg-white/80 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-700/50 p-4 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="font-bold text-sm text-slate-900 dark:text-white">{log.action}</span>
                              <Badge
                                variant="secondary"
                                className="text-[10px] bg-slate-100 text-slate-600 dark:bg-slate-900 dark:text-slate-400 border-slate-300 dark:border-slate-700/50 font-mono font-bold tracking-wider uppercase"
                              >
                                {log.entityType} #{log.entityId}
                              </Badge>
                              <Badge className={`text-[10px] ${actorConfig.bg} ${actorConfig.text} border-transparent font-bold uppercase tracking-wider`}>
                                {actorConfig.label}
                              </Badge>
                              {log.traceId && (
                                <Badge className="bg-slate-100 dark:bg-slate-800 text-slate-500 text-[10px] font-mono border-slate-300 dark:border-slate-700">
                                  <Fingerprint className="w-2.5 h-2.5 mr-1 text-slate-400" />
                                  {String(log.traceId).substring(0, 8)}...
                                </Badge>
                              )}
                            </div>
                            <span className="text-[11px] font-bold text-slate-400 dark:text-slate-500 font-mono tabular-nums shrink-0">
                              {new Date(log.timestamp).toLocaleString()}
                            </span>
                          </div>

                          <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed font-medium mb-2">
                            {log.details}
                          </p>

                          {/* Hash Checksum Footer */}
                          {log.checksum && (
                            <div className="pt-2 border-t border-slate-100 dark:border-slate-700/40 flex items-center justify-between text-[10px] font-mono text-slate-400">
                              <span className="flex items-center gap-1 truncate">
                                <Hash className="w-3 h-3 text-slate-500" /> Checksum: {log.checksum.substring(0, 16)}...{log.checksum.substring(log.checksum.length - 8)}
                              </span>
                              {log.aiModelUsed && (
                                <span className="text-purple-400 font-sans font-semibold">
                                  {log.aiModelUsed}
                                </span>
                              )}
                            </div>
                          )}
                        </div>
                      </motion.div>
                    );
                  })}
                </motion.div>
              </ScrollArea>
            )}

            {!loading && data && (
              <div className="mt-6 flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Page {page + 1} of {data.totalPages || 1}
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.min((data.totalPages || 1) - 1, page + 1))}
                    disabled={page >= (data.totalPages || 1) - 1}
                    className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* Webhook DLQ Inspector */
        <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
          <div className="p-6 border-b border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-transparent flex flex-col sm:flex-row justify-between sm:items-center gap-4">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-lg font-bold text-slate-900 dark:text-white">Webhook Dead-Letter Queue (DLQ)</h2>
                <Badge className="bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                  <ShieldAlert className="w-3 h-3" /> Forensic Quarantine & Replay
                </Badge>
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                Forensic capture of rejected, unparseable, or signature-failed webhooks. Inspect raw payloads and replay events.
              </p>
            </div>

            <Button
              onClick={loadDlq}
              disabled={dlqLoading}
              variant="outline"
              size="sm"
              className="h-8 text-xs font-semibold gap-1.5 bg-white/80 dark:bg-slate-800/80 hover:bg-slate-100 dark:hover:bg-slate-700 border-slate-300 dark:border-slate-700 self-start sm:self-auto"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${dlqLoading ? "animate-spin text-rose-500" : ""}`} />
              {dlqLoading ? "Refreshing..." : "Refresh DLQ"}
            </Button>
          </div>

          {replaySuccessMsg && (
            <div className="m-6 p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 text-xs font-bold flex items-center justify-between">
              <span className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                {replaySuccessMsg}
              </span>
              <button onClick={() => setReplaySuccessMsg(null)} className="text-slate-400 hover:text-slate-600">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          <div className="p-6">
            {dlqLoading ? (
              <div className="space-y-4">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="skeleton-shimmer h-16 w-full rounded-xl" />
                ))}
              </div>
            ) : dlqItems.length === 0 ? (
              <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
                <Inbox className="w-10 h-10 text-slate-400" />
                <p className="font-bold text-lg text-slate-900 dark:text-white">DLQ Queue is Clean</p>
                <p className="text-xs text-slate-500 max-w-md">
                  No rejected, unparseable, or signature-failed webhooks have been intercepted. All incoming webhooks have passed HMAC signature validation.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-400 uppercase tracking-wider font-semibold">
                      <th className="pb-3 px-3">DLQ ID</th>
                      <th className="pb-3 px-3">Received Time</th>
                      <th className="pb-3 px-3">Rejection Cause</th>
                      <th className="pb-3 px-3">Status</th>
                      <th className="pb-3 px-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60">
                    {dlqItems.map((item) => (
                      <tr key={item.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                        <td className="py-3.5 px-3 font-mono font-bold text-slate-900 dark:text-white">
                          #{item.id}
                        </td>
                        <td className="py-3.5 px-3 font-mono text-slate-500">
                          {new Date(item.createdAt).toLocaleString()}
                        </td>
                        <td className="py-3.5 px-3 text-rose-600 dark:text-rose-400 font-medium max-w-xs truncate" title={item.errorMessage}>
                          {item.errorMessage || "Unknown error"}
                        </td>
                        <td className="py-3.5 px-3">
                          {item.status === "REPLAYED" ? (
                            <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-bold uppercase">
                              Replayed
                            </Badge>
                          ) : (
                            <Badge className="bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20 text-[10px] font-bold uppercase">
                              Quarantined
                            </Badge>
                          )}
                        </td>
                        <td className="py-3.5 px-3 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Button
                              onClick={() => setSelectedDlqPayload(item)}
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs font-semibold gap-1 px-2.5 bg-white/80 dark:bg-slate-800"
                            >
                              <Eye className="w-3 h-3 text-blue-500" /> Inspect
                            </Button>
                            <Button
                              onClick={() => handleReplay(item.id)}
                              disabled={replayingId === item.id || item.status === "REPLAYED"}
                              variant="outline"
                              size="sm"
                              className={`h-7 text-xs font-semibold gap-1 px-2.5 ${
                                item.status === "REPLAYED"
                                  ? "opacity-50 cursor-not-allowed"
                                  : "text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-950/30 border-emerald-500/30"
                              }`}
                            >
                              <Play className={`w-3 h-3 ${replayingId === item.id ? "animate-spin" : ""}`} />
                              {replayingId === item.id ? "Replaying..." : item.status === "REPLAYED" ? "Replayed" : "Replay"}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Payload Inspection Modal */}
      {selectedDlqPayload && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in">
          <div className="glass-card rounded-2xl w-full max-w-2xl overflow-hidden shadow-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-rose-500" />
                <h3 className="font-bold text-slate-900 dark:text-white text-base">
                  DLQ Payload Inspector #{selectedDlqPayload.id}
                </h3>
              </div>
              <button
                onClick={() => setSelectedDlqPayload(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-slate-500">
                <span>Received Headers</span>
              </div>
              <pre className="p-3 rounded-lg bg-slate-950 text-slate-300 font-mono text-[11px] overflow-x-auto border border-slate-800">
                {selectedDlqPayload.headers || "No custom headers recorded"}
              </pre>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-slate-500">
                <span>Raw Webhook JSON Body</span>
                <button
                  onClick={() => copyToClipboard(selectedDlqPayload.payload)}
                  className="flex items-center gap-1 text-blue-500 hover:text-blue-600 text-xs font-bold"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? "Copied" : "Copy JSON"}
                </button>
              </div>
              <ScrollArea className="h-64 rounded-lg bg-slate-950 p-3 border border-slate-800">
                <pre className="text-emerald-400 font-mono text-[11px] whitespace-pre-wrap">
                  {(() => {
                    try {
                      return JSON.stringify(JSON.parse(selectedDlqPayload.payload), null, 2);
                    } catch {
                      return selectedDlqPayload.payload;
                    }
                  })()}
                </pre>
              </ScrollArea>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                onClick={() => setSelectedDlqPayload(null)}
                variant="outline"
                size="sm"
                className="text-xs"
              >
                Close
              </Button>
              <Button
                onClick={() => {
                  handleReplay(selectedDlqPayload.id);
                  setSelectedDlqPayload(null);
                }}
                disabled={selectedDlqPayload.status === "REPLAYED"}
                size="sm"
                className="bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold gap-1.5"
              >
                <Play className="w-3.5 h-3.5" /> Replay Webhook Now
              </Button>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  );
}
