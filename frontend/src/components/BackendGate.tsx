import { useState, useEffect, useCallback, useRef, type ReactNode } from "react";
import { VITE_API_URL } from "../lib/api";

interface BackendGateProps {
  children: ReactNode;
}

export function BackendGate({ children }: BackendGateProps) {
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const isMounted = useRef(true);

  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
    };
  }, []);

  const checkHealth = useCallback(async () => {
    const startTime = Date.now();
    const timeout = 60000;
    const interval = 5000;

    while (Date.now() - startTime < timeout) {
      if (!isMounted.current) return;
      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 4500);
        const url = `${VITE_API_URL.replace(/\/+$/, "")}/health`;
        const res = await fetch(url, { signal: controller.signal });
        clearTimeout(timer);
        if (res.ok) {
          const data = await res.json().catch(() => null);
          if (data && data.status === "UP") {
            if (isMounted.current) {
              setStatus("ready");
            }
            return;
          }
        }
      } catch {
      }
      if (Date.now() - startTime + interval >= timeout) {
        break;
      }
      await new Promise((resolve) => setTimeout(resolve, interval));
    }
    if (isMounted.current) {
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  if (status === "ready") {
    return <>{children}</>;
  }

  if (status === "error") {
    return (
      <div className="min-h-screen bg-[#060b18] text-white flex items-center justify-center p-6">
        <div className="max-w-md w-full glass-card rounded-2xl p-8 border border-rose-500/30 text-center space-y-4 shadow-2xl flex flex-col items-center">
          <div className="w-12 h-12 rounded-full bg-rose-500/20 text-rose-400 flex items-center justify-center text-xl font-bold">
            !
          </div>
          <h2 className="text-xl font-bold">Unable to Reach Server</h2>
          <p className="text-xs text-slate-400">
            The server could not be reached within 60 seconds. The backend may still be spinning up.
          </p>
          <button
            onClick={() => {
              setStatus("loading");
              checkHealth();
            }}
            className="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm transition-colors shadow-lg shadow-blue-600/30 cursor-pointer"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#060b18] text-white flex items-center justify-center p-6">
      <div className="max-w-md w-full glass-card rounded-2xl p-8 border border-blue-500/20 text-center space-y-4 shadow-2xl flex flex-col items-center">
        <div className="w-12 h-12 rounded-full border-4 border-blue-500/20 border-t-blue-500 animate-spin" />
        <h2 className="text-xl font-bold">Starting RecoverMandate</h2>
        <p className="text-sm text-slate-400">
          Waking up the server, this can take up to 60 seconds on the free plan...
        </p>
      </div>
    </div>
  );
}
