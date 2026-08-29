import { Component, ErrorInfo, ReactNode } from "react";
import { AlertTriangle, RefreshCw, Home } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorInfo: null };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("[Uncaught UI Exception]", error, errorInfo);
    this.setState({ errorInfo });
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen w-full flex items-center justify-center p-6 bg-slate-950 text-white antialiased font-sans">
          <div className="max-w-lg w-full p-8 rounded-2xl bg-slate-900 border border-rose-500/30 shadow-2xl space-y-6 text-center">
            <div className="w-16 h-16 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-500 flex items-center justify-center mx-auto shadow-lg shadow-rose-500/10">
              <AlertTriangle className="w-8 h-8" />
            </div>

            <div className="space-y-2">
              <h2 className="text-xl font-bold text-white">Application Interruption</h2>
              <p className="text-sm text-slate-400">
                An unexpected interface error occurred. The recovery engine and backend services remain healthy.
              </p>
            </div>

            {this.state.error && (
              <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-left font-mono text-xs text-rose-400 overflow-x-auto max-h-32">
                {this.state.error.message || String(this.state.error)}
              </div>
            )}

            <div className="flex items-center justify-center gap-3 pt-2">
              <Button
                onClick={this.handleReload}
                className="bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs gap-2"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Reload Application
              </Button>
              <Button
                onClick={this.handleReset}
                variant="outline"
                className="border-slate-700 hover:bg-slate-800 text-slate-300 text-xs gap-2"
              >
                <Home className="w-3.5 h-3.5" /> Recover View
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
