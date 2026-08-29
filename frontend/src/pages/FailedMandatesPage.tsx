import { useState, useEffect, Fragment } from "react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Search,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  Copy,
  Calendar,
  X,
  Download,
} from "lucide-react";
import {
  fetchPaymentEvents,
  exportRecoveryLedgerCsv,
  type PageResponse,
  type PaymentEventItem,
} from "../lib/api";
import { TransactionFlowDiagram } from "../components/TransactionFlowDiagram";
import { RazorpayMark } from "../components/RazorpayLogo";

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
  const [data, setData] = useState<PageResponse<PaymentEventItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState<"all" | "7d" | "30d">("all");
  const [isExporting, setIsExporting] = useState(false);

  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      await exportRecoveryLedgerCsv();
    } catch (e: any) {
      console.error("Failed to export recovery ledger CSV:", e);
    } finally {
      setIsExporting(false);
    }
  };

  const load = () => {
    setLoading(true);
    fetchPaymentEvents(page, 20)
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

  // Client-side filtering
  const rawItems: PaymentEventItem[] = data?.content || [];
  const filteredItems = rawItems.filter((item: PaymentEventItem) => {
    // Search query matching
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      const matchPaymentId = item.razorpayPaymentId?.toLowerCase().includes(q);
      const matchCategory = item.classificationCategory?.toLowerCase().includes(q);
      const matchSubId = item.razorpaySubscriptionId?.toLowerCase().includes(q);
      const matchReason = item.errorReason?.toLowerCase().includes(q);
      const matchStatus = item.classificationStatus?.toLowerCase().includes(q);
      if (!matchPaymentId && !matchCategory && !matchSubId && !matchReason && !matchStatus) {
        return false;
      }
    }

    // Date range matching
    if (dateRange !== "all" && item.createdAt) {
      const itemTime = new Date(item.createdAt).getTime();
      const now = Date.now();
      const days = dateRange === "7d" ? 7 : 30;
      const cutoff = now - days * 24 * 60 * 60 * 1000;
      if (itemTime < cutoff) {
        return false;
      }
    }

    return true;
  });

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load payment events: {error}</p>
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }} className="space-y-4">
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        {/* Header */}
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 flex flex-col md:flex-row justify-between md:items-center gap-4 bg-white/40 dark:bg-transparent">
          <div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">Failed Mandates Log</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Live feed of caught Razorpay webhook events. Click any row to inspect the lifecycle flow.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportCsv}
              disabled={isExporting}
              className="dark:border-slate-700 text-xs font-semibold gap-1.5 shadow-sm"
              title="Download recovery ledger CSV"
            >
              <Download className={`w-3.5 h-3.5 text-blue-500 ${isExporting ? "animate-bounce" : ""}`} />
              {isExporting ? "Exporting..." : "Export Ledger (.CSV)"}
            </Button>
            <Button variant="outline" size="sm" onClick={load} className="dark:border-slate-700 text-xs">
              <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
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
              placeholder="Search by Payment ID, Category, Sub ID..."
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

        <div className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="skeleton-shimmer h-16 w-full rounded-xl" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="py-20 flex flex-col items-center justify-center text-center space-y-4">
              <div className="w-16 h-16 rounded-full bg-slate-100 dark:bg-slate-800/50 flex items-center justify-center mb-2">
                <Search className="w-8 h-8 text-slate-400" />
              </div>
              <p className="font-bold text-lg text-slate-900 dark:text-white">
                {rawItems.length === 0 ? "No failed mandates found" : "No mandates match your filters"}
              </p>
              <p className="text-sm font-medium text-slate-500 dark:text-slate-400 max-w-sm">
                {rawItems.length === 0
                  ? "Payment failures intercepted from Razorpay webhooks will stream here live."
                  : "Try clearing your search query or selecting a broader date range."}
              </p>
              {(searchQuery || dateRange !== "all") && (
                <Button
                  onClick={() => {
                    setSearchQuery("");
                    setDateRange("all");
                  }}
                  variant="outline"
                  size="sm"
                  className="text-xs"
                >
                  Clear Filters
                </Button>
              )}
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
                    {filteredItems.map((item: PaymentEventItem, i: number) => {
                      const isExpanded = expandedId === item.id;
                      return (
                        <Fragment key={item.id}>
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
                              <td colSpan={6} className="p-4 bg-[#02042B]/90 border-b border-[#3395FF]/30 space-y-4">
                                <TransactionFlowDiagram
                                  failurePoint={item.classificationCategory}
                                  category={item.classificationCategory}
                                  failureReasonCode={item.failureReasonCode}
                                  autoRecoverable={item.autoRecoverable}
                                />

                                {/* Razorpay Link Card */}
                                <div className="mt-3 p-3 rounded-xl bg-[#0C2340]/90 border border-[#3395FF]/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-inner">
                                  <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-lg bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center p-1.5 shrink-0">
                                      <RazorpayMark className="w-4 h-4" />
                                    </div>
                                    <div>
                                      <div className="flex items-center gap-2">
                                        <span className="text-xs font-bold text-white tracking-wide">Razorpay Hosted Recovery Link</span>
                                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                                          Ready for Customer
                                        </span>
                                      </div>
                                      <span className="text-xs font-mono text-[#93c5fd]">
                                        https://rzp.io/simulated/pay_rec_{item.id}
                                      </span>
                                    </div>
                                  </div>

                                  <div className="flex items-center gap-2">
                                    <Button
                                      size="sm"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        navigator.clipboard.writeText(`https://rzp.io/simulated/pay_rec_${item.id}`);
                                      }}
                                      className="bg-[#02042B] hover:bg-[#3395FF]/20 border border-[#3395FF]/40 text-xs font-semibold text-white h-8"
                                    >
                                      <Copy className="w-3.5 h-3.5 mr-1 text-[#3395FF]" /> Copy Link
                                    </Button>
                                    <a
                                      href={`https://rzp.io/simulated/pay_rec_${item.id}`}
                                      target="_blank"
                                      rel="noreferrer"
                                      onClick={(e) => e.stopPropagation()}
                                      className="h-8 px-2.5 rounded-lg bg-[#3395FF] hover:bg-[#2582eb] text-white flex items-center gap-1 text-xs font-bold transition-colors shadow-md shadow-[#3395FF]/20"
                                    >
                                      <span>Test Checkout</span>
                                      <ExternalLink className="w-3 h-3" />
                                    </a>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>

              {/* Mobile Cards */}
              <div className="md:hidden p-4 space-y-3">
                {filteredItems.map((item: PaymentEventItem) => {
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
                  Page {page + 1} of {data?.totalPages || 1}
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
                    onClick={() => setPage(Math.min((data?.totalPages || 1) - 1, page + 1))}
                    disabled={page >= (data?.totalPages || 1) - 1}
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
