import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
} from "lucide-react";
import { fetchAuditLogs } from "../lib/api";

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

export function AuditLogPage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  const load = () => {
    setLoading(true);
    fetchAuditLogs(page, 15)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [page, refreshTrigger]);

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

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load audit trail: {error}</p>
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-transparent flex flex-col sm:flex-row justify-between sm:items-center gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">Immutable Audit Log</h2>
              <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                <ShieldCheck className="w-3 h-3" /> SHA-256 Hash Chained
              </Badge>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Cryptographically secure, tamper-evident timeline of all state transitions and AI actions.
            </p>
          </div>
        </div>

        <div className="p-6">
          {loading ? (
            <div className="space-y-4">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="skeleton-shimmer h-20 w-full rounded-xl" />
              ))}
            </div>
          ) : !data || data.content.length === 0 ? (
            <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
              <List className="w-10 h-10 text-slate-400" />
              <p className="font-bold text-lg text-slate-900 dark:text-white">No audit logs found</p>
            </div>
          ) : (
            <ScrollArea className="h-[600px] pr-4">
              <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-4 timeline-line ml-2">
                {data.content.map((log: any) => {
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
                  onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                  disabled={page >= data.totalPages - 1}
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
