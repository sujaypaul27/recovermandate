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
} from "lucide-react";
import {
  fetchAuditLogs,
  verifyAuditChain,
  type AuditChainVerification,
  type PageResponse,
  type AuditLogItem,
} from "../lib/api";

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

export function AuditLogPage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [data, setData] = useState<PageResponse<AuditLogItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState<"all" | "7d" | "30d">("all");

  const [chainStatus, setChainStatus] = useState<AuditChainVerification | null>(null);
  const [verifyingChain, setVerifyingChain] = useState(false);

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

  const load = () => {
    setLoading(true);
    fetchAuditLogs(page, 30)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [page, refreshTrigger]);

  useEffect(() => {
    checkChain();
  }, [refreshTrigger]);

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
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }} className="space-y-4">
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
    </motion.div>
  );
}
