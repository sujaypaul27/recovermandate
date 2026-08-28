import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RefreshCw, ChevronLeft, ChevronRight, Search, ChevronDown, ChevronUp } from "lucide-react";
import { fetchPaymentEvents } from "../lib/api";
import { TransactionFlowDiagram } from "../components/TransactionFlowDiagram";

function getCategoryClass(cat: string | null | undefined) {
  if (!cat) return "";
  const lower = cat.toLowerCase();
  if (lower.includes("insufficient")) return "category-insufficient_funds";
  if (lower.includes("technical")) return "category-technical_decline";
  if (lower.includes("expired")) return "category-expired_mandate";
  return "category-unknown";
}

function getCategoryLabel(cat: string | null | undefined) {
  if (!cat) return "PENDING";
  return cat.replace(/_/g, " ").replace(/\b\w/g, (l) => l.toUpperCase());
}

export function FailedMandatesPage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    fetchPaymentEvents(page, 10)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [page, refreshTrigger]);

  const toggleExpand = (id: number) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load payment events: {error}</p>
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center bg-white/40 dark:bg-transparent">
          <div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">Failed Mandates Log</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Live feed of caught Razorpay webhook events. Click any row to inspect the lifecycle flow.
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={load} className="hidden sm:flex dark:border-slate-700">
            <RefreshCw className="w-4 h-4 mr-2" /> Refresh
          </Button>
        </div>

        <div className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="skeleton-shimmer h-16 w-full rounded-xl" />
              ))}
            </div>
          ) : !data || data.content.length === 0 ? (
            <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
              <div className="w-16 h-16 rounded-full bg-slate-100 dark:bg-slate-800/50 flex items-center justify-center mb-2">
                <Search className="w-8 h-8 text-slate-400" />
              </div>
              <p className="font-bold text-lg text-slate-900 dark:text-white">No failed mandates found</p>
              <p className="text-sm font-medium text-slate-500 dark:text-slate-400 max-w-sm">
                Payment failures intercepted from Razorpay webhooks will stream here live.
              </p>
            </div>
          ) : (
            <>
              {/* Desktop Table */}
              <div className="hidden md:block overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="border-slate-200 dark:border-slate-800 hover:bg-transparent">
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Payment ID</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Amount</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Category</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Recoverable</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold text-right">Status</TableHead>
                      <TableHead className="w-10"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.content.map((item: any, i: number) => {
                      const isExpanded = expandedId === item.id;
                      return (
                        <div key={item.id} className="contents">
                          <motion.tr
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.03 }}
                            onClick={() => toggleExpand(item.id)}
                            className={`border-slate-200 dark:border-slate-800/50 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-all duration-200 group cursor-pointer ${
                              isExpanded ? "bg-slate-50/80 dark:bg-slate-800/60" : ""
                            }`}
                          >
                            <TableCell className="font-mono text-sm text-slate-700 dark:text-slate-300 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                              {item.razorpayPaymentId}
                            </TableCell>
                            <TableCell className="font-bold text-slate-900 dark:text-white">
                              ₹{(item.amount / 100).toFixed(2)}
                            </TableCell>
                            <TableCell>
                              <Badge variant="outline" className={`text-xs font-semibold border ${getCategoryClass(item.classificationCategory)}`}>
                                {getCategoryLabel(item.classificationCategory)}
                              </Badge>
                            </TableCell>
                            <TableCell>
                              {item.autoRecoverable ? (
                                <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-100 dark:bg-emerald-500/10 px-2 py-1 rounded-md">
                                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                                  Yes
                                </span>
                              ) : (
                                <span className="text-xs font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded-md">
                                  No
                                </span>
                              )}
                            </TableCell>
                            <TableCell className="text-right">
                              <span className="text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider bg-slate-100 dark:bg-slate-800/60 px-2 py-1 rounded border border-slate-200 dark:border-slate-700">
                                {item.classificationStatus || "UNCLASSIFIED"}
                              </span>
                            </TableCell>
                            <TableCell className="text-slate-400">
                              {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                            </TableCell>
                          </motion.tr>

                          {/* Expanded Flow Diagram Row */}
                          {isExpanded && (
                            <tr>
                              <td colSpan={6} className="p-4 bg-slate-950/40 border-b border-slate-800">
                                <TransactionFlowDiagram
                                  failurePoint={item.classificationCategory}
                                  category={item.classificationCategory}
                                  failureReasonCode={item.failureReasonCode}
                                  autoRecoverable={item.autoRecoverable}
                                />
                              </td>
                            </tr>
                          )}
                        </div>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>

              {/* Mobile Cards */}
              <div className="md:hidden p-4 space-y-3">
                {data.content.map((item: any) => {
                  const isExpanded = expandedId === item.id;
                  return (
                    <div
                      key={item.id}
                      className="p-4 rounded-xl bg-white dark:bg-slate-800/30 border border-slate-200 dark:border-slate-700/50 shadow-sm space-y-3"
                    >
                      <div className="flex justify-between items-center" onClick={() => toggleExpand(item.id)}>
                        <span className="font-mono text-xs text-blue-600 dark:text-blue-400 font-medium">{item.razorpayPaymentId}</span>
                        <span className="font-bold text-slate-900 dark:text-white text-lg">₹{(item.amount / 100).toFixed(2)}</span>
                      </div>
                      <div className="flex justify-between items-center" onClick={() => toggleExpand(item.id)}>
                        <Badge variant="outline" className={`text-[10px] font-bold border ${getCategoryClass(item.classificationCategory)}`}>
                          {getCategoryLabel(item.classificationCategory)}
                        </Badge>
                        {item.autoRecoverable ? (
                          <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /> Recoverable
                          </span>
                        ) : (
                          <span className="text-xs text-slate-500 font-medium">Terminal</span>
                        )}
                      </div>
                      {isExpanded && (
                        <div className="pt-2">
                          <TransactionFlowDiagram
                            failurePoint={item.classificationCategory}
                            category={item.classificationCategory}
                            failureReasonCode={item.failureReasonCode}
                            autoRecoverable={item.autoRecoverable}
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Pagination */}
              <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-transparent flex items-center justify-between">
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
            </>
          )}
        </div>
      </div>
    </motion.div>
  );
}
