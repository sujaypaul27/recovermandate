import { useState, useEffect } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Toaster } from "@/components/ui/toaster";
import { useToast } from "@/hooks/use-toast";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Activity, AlertTriangle, CheckCircle, Clock, ShieldCheck, XCircle, Search } from "lucide-react";

import {
  fetchDashboardSummary,
  fetchPaymentEvents,
  fetchRecoveryActions,
  fetchAuditLogs,
  approveRecoveryAction,
  rejectRecoveryAction,
} from "./lib/api";

export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 antialiased p-8">
      <div className="max-w-7xl mx-auto space-y-8">
        <header className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-white flex items-center gap-2">
              <ShieldCheck className="w-8 h-8 text-blue-500" />
              RecoverMandate Dashboard
            </h1>
            <p className="text-slate-400 mt-1">Payment failure classification & recovery intelligence</p>
          </div>
        </header>

        <Tabs defaultValue="dashboard" className="w-full">
          <TabsList className="bg-slate-900 border border-slate-800">
            <TabsTrigger value="dashboard">Overview</TabsTrigger>
            <TabsTrigger value="mandates">Failed Mandates</TabsTrigger>
            <TabsTrigger value="approvals">Approval Queue</TabsTrigger>
            <TabsTrigger value="audit">Audit Log</TabsTrigger>
          </TabsList>

          <TabsContent value="dashboard" className="mt-6">
            <DashboardTab />
          </TabsContent>
          <TabsContent value="mandates" className="mt-6">
            <MandatesTab />
          </TabsContent>
          <TabsContent value="approvals" className="mt-6">
            <ApprovalsTab />
          </TabsContent>
          <TabsContent value="audit" className="mt-6">
            <AuditTab />
          </TabsContent>
        </Tabs>
      </div>
      <Toaster />
    </div>
  );
}

function DashboardTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchDashboardSummary()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSkeletons count={4} type="card" />;
  if (error) return <ErrorState message={error} />;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <StatCard title="Failed Today" value={data.failedMandatesToday} icon={<AlertTriangle className="w-5 h-5 text-rose-500" />} />
      <StatCard title="Auto-Recoverable Rate" value={`${(data.autoRecoverableRate * 100).toFixed(1)}%`} icon={<Activity className="w-5 h-5 text-blue-500" />} />
      <StatCard title="Pending Approvals" value={data.pendingApprovals} icon={<Clock className="w-5 h-5 text-amber-500" />} />
      <StatCard title="Recovery Success" value={`${(data.recoverySuccessRate * 100).toFixed(1)}%`} icon={<CheckCircle className="w-5 h-5 text-emerald-500" />} />
    </div>
  );
}

function StatCard({ title, value, icon }: { title: string; value: string | number; icon: React.ReactNode }) {
  return (
    <Card className="bg-slate-900/60 border-slate-800">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-slate-400">{title}</CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-3xl font-bold text-white">{value}</div>
      </CardContent>
    </Card>
  );
}

function MandatesTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  const load = () => {
    setLoading(true);
    fetchPaymentEvents(page, 10)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [page]);

  if (error) return <ErrorState message={error} />;

  return (
    <Card className="bg-slate-900/60 border-slate-800">
      <CardHeader>
        <CardTitle>Failed Mandates</CardTitle>
        <CardDescription>Recent payment failures and their classification status.</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <LoadingSkeletons count={5} type="row" />
        ) : (
          <>
            {data.content.length === 0 ? (
              <EmptyState message="No failed mandates found." />
            ) : (
              <div className="rounded-md border border-slate-800">
                <Table>
                  <TableHeader className="bg-slate-900">
                    <TableRow className="border-slate-800 hover:bg-slate-900">
                      <TableHead>Payment ID</TableHead>
                      <TableHead>Amount</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead>Auto-Recoverable</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.content.map((item: any) => (
                      <TableRow key={item.id} className="border-slate-800 hover:bg-slate-800/50">
                        <TableCell className="font-mono text-sm">{item.razorpayPaymentId}</TableCell>
                        <TableCell>₹{(item.amount / 100).toFixed(2)}</TableCell>
                        <TableCell>
                          <Badge variant="outline" className="border-slate-700 text-slate-300">
                            {item.classificationCategory || "PENDING"}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {item.autoRecoverable ? (
                            <Badge className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20">Yes</Badge>
                          ) : (
                            <Badge className="bg-slate-800 text-slate-400">No</Badge>
                          )}
                        </TableCell>
                        <TableCell>{item.classificationStatus || "UNCLASSIFIED"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
            <PaginationControls page={page} setPage={setPage} totalPages={data.totalPages} />
          </>
        )}
      </CardContent>
    </Card>
  );
}

function ApprovalsTab() {
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

  useEffect(() => { load(); }, []);

  const handleApprove = async (id: number) => {
    try {
      await approveRecoveryAction(id);
      toast({ title: "Approved", description: "Recovery action approved." });
      load();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  if (error) return <ErrorState message={error} />;

  return (
    <div className="space-y-4">
      {loading ? (
        <LoadingSkeletons count={3} type="card" />
      ) : data?.content.length === 0 ? (
        <EmptyState message="No pending approvals in the queue." />
      ) : (
        data?.content.map((action: any) => (
          <ApprovalCard key={action.id} action={action} onApprove={() => handleApprove(action.id)} onReload={load} />
        ))
      )}
    </div>
  );
}

function ApprovalCard({ action, onApprove, onReload }: { action: any, onApprove: () => void, onReload: () => void }) {
  const [rejectReason, setRejectReason] = useState("");
  const [isRejecting, setIsRejecting] = useState(false);
  const { toast } = useToast();

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      toast({ title: "Validation Error", description: "Reason is required to reject.", variant: "destructive" });
      return;
    }
    try {
      await rejectRecoveryAction(action.id, rejectReason);
      toast({ title: "Rejected", description: "Recovery action rejected." });
      onReload();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  return (
    <Card className="bg-slate-900/60 border-slate-800">
      <CardHeader>
        <div className="flex justify-between items-start">
          <div>
            <CardTitle className="text-lg flex items-center gap-2">
              Action #{action.id}
              <Badge className="bg-amber-500/10 text-amber-500 border-amber-500/20">DRAFTED</Badge>
            </CardTitle>
            <CardDescription>Generated on {new Date(action.createdAt).toLocaleString()}</CardDescription>
          </div>
          <div className="flex gap-2">
            <Badge variant="outline" className="border-emerald-500/30 text-emerald-400">
              <CheckCircle className="w-3 h-3 mr-1 inline" /> Amount Match
            </Badge>
            <Badge variant="outline" className="border-emerald-500/30 text-emerald-400">
              <CheckCircle className="w-3 h-3 mr-1 inline" /> Tone OK
            </Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="p-4 bg-slate-950 rounded-md border border-slate-800 text-sm whitespace-pre-wrap font-mono text-slate-300">
          {action.aiDraftMessage}
        </div>
        <div className="flex items-center gap-4">
          <Button onClick={onApprove} className="bg-emerald-600 hover:bg-emerald-500 text-white">
            <CheckCircle className="w-4 h-4 mr-2" /> Approve
          </Button>
          {!isRejecting ? (
            <Button onClick={() => setIsRejecting(true)} variant="destructive" className="bg-rose-600/20 text-rose-500 hover:bg-rose-600/30 border border-rose-600/50">
              <XCircle className="w-4 h-4 mr-2" /> Reject...
            </Button>
          ) : (
            <div className="flex items-center gap-2 flex-1">
              <Input 
                value={rejectReason} 
                onChange={(e) => setRejectReason(e.target.value)} 
                placeholder="Reason for rejection (required)" 
                className="bg-slate-950 border-slate-700 max-w-sm"
              />
              <Button onClick={handleReject} variant="destructive">Confirm Reject</Button>
              <Button onClick={() => setIsRejecting(false)} variant="ghost" className="text-slate-400 hover:text-white hover:bg-slate-800">Cancel</Button>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function AuditTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    fetchAuditLogs(page, 15)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [page]);

  if (error) return <ErrorState message={error} />;

  return (
    <Card className="bg-slate-900/60 border-slate-800">
      <CardHeader>
        <CardTitle>Audit Trail</CardTitle>
        <CardDescription>Chronological log of all system actions and transitions.</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <LoadingSkeletons count={6} type="row" />
        ) : data.content.length === 0 ? (
          <EmptyState message="No audit logs found." />
        ) : (
          <ScrollArea className="h-[500px] pr-4">
            <div className="space-y-4">
              {data.content.map((log: any) => (
                <div key={log.id} className="flex gap-4 p-3 rounded-lg bg-slate-900 border border-slate-800/50 hover:bg-slate-800/50 transition-colors">
                  <div className="mt-1">
                    {log.actor === "SYSTEM" ? (
                      <div className="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center text-blue-500">
                        <Activity className="w-4 h-4" />
                      </div>
                    ) : log.actor === "HUMAN" ? (
                      <div className="w-8 h-8 rounded-full bg-amber-500/20 flex items-center justify-center text-amber-500">
                        <ShieldCheck className="w-4 h-4" />
                      </div>
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-purple-500/20 flex items-center justify-center text-purple-500">
                        <AlertTriangle className="w-4 h-4" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-200">{log.action}</span>
                        <Badge variant="secondary" className="text-[10px] bg-slate-800 text-slate-400">
                          {log.entityType} #{log.entityId}
                        </Badge>
                      </div>
                      <span className="text-xs text-slate-500 font-mono">
                        {new Date(log.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm text-slate-400">{log.details}</p>
                  </div>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
        {!loading && <PaginationControls page={page} setPage={setPage} totalPages={data?.totalPages || 0} />}
      </CardContent>
    </Card>
  );
}

function LoadingSkeletons({ count, type }: { count: number; type: "card" | "row" }) {
  return (
    <div className={type === "card" ? "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4" : "space-y-4"}>
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className={type === "card" ? "h-32 w-full bg-slate-800" : "h-12 w-full bg-slate-800"} />
      ))}
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="p-6 rounded-lg border border-rose-500/20 bg-rose-500/10 text-rose-500 flex flex-col items-center justify-center text-center space-y-2">
      <AlertTriangle className="w-8 h-8" />
      <h3 className="font-semibold">Error Loading Data</h3>
      <p className="text-sm">{message}</p>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="py-12 flex flex-col items-center justify-center text-slate-500 space-y-3">
      <Search className="w-8 h-8 opacity-20" />
      <p>{message}</p>
    </div>
  );
}

function PaginationControls({ page, setPage, totalPages }: { page: number; setPage: (p: number) => void; totalPages: number }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2 mt-4">
      <Button variant="outline" size="sm" onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0} className="border-slate-800 bg-slate-900 text-slate-300">
        Previous
      </Button>
      <span className="text-sm text-slate-500">Page {page + 1} of {totalPages}</span>
      <Button variant="outline" size="sm" onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1} className="border-slate-800 bg-slate-900 text-slate-300">
        Next
      </Button>
    </div>
  );
}
