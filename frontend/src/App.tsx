import { useState } from "react"
import { motion } from "framer-motion"
import { 
  Server, 
  Layers, 
  Database, 
  Cpu, 
  ShieldCheck, 
  Webhook, 
  FileCheck2, 
  Terminal, 
  Activity,
  ArrowUpRight,
  RefreshCw,
  Sparkles
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

const PACKAGES = [
  { name: "config", desc: "CORS, App Security & Bean Setup", icon: Server },
  { name: "controller", desc: "REST Endpoints & Routing", icon: Layers },
  { name: "service", desc: "Business Logic & Recovery Orchestration", icon: Cpu },
  { name: "repository", desc: "Spring Data JPA Repositories", icon: Database },
  { name: "entity", desc: "Domain & PostgreSQL Schema Entities", icon: Layers },
  { name: "dto", desc: "Data Transfer Objects & Validation", icon: FileCheck2 },
  { name: "webhook", desc: "Event Callbacks & Signature Verification", icon: Webhook },
  { name: "ai", desc: "AI Recovery Prompts & Intelligence", icon: Sparkles },
  { name: "audit", desc: "Immutable Logs & Action Auditing", icon: ShieldCheck },
]

export default function App() {
  const [healthStatus, setHealthStatus] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const checkBackendHealth = async () => {
    setIsLoading(true)
    try {
      const response = await fetch("http://localhost:8080/api/health")
      if (!response.ok) throw new Error("Backend returned status " + response.status)
      const data = await response.json()
      setHealthStatus(`Connected: ${data.service} (${data.status})`)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Connection failed"
      setHealthStatus(`Backend offline: ${message}`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 antialiased selection:bg-blue-600 selection:text-white">
      {/* Header / Navbar */}
      <header className="border-b border-slate-800/80 bg-slate-900/60 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Activity className="w-5 h-5 text-white" />
            </div>
            <span className="font-bold text-lg tracking-tight bg-gradient-to-r from-blue-400 to-indigo-300 bg-clip-text text-transparent">
              recovermandate
            </span>
            <Badge variant="outline" className="ml-2 border-slate-700 text-slate-400 text-xs">
              v0.0.1-SNAPSHOT
            </Badge>
          </div>
          <div className="flex items-center gap-3">
            <Badge variant="secondary" className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              Spring Boot 3.3.3 • Java 17
            </Badge>
            <Badge variant="secondary" className="bg-blue-500/10 text-blue-400 border border-blue-500/20">
              React + Vite + Tailwind + shadcn
            </Badge>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-12 space-y-12">
        {/* Hero Section */}
        <motion.div 
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="text-center space-y-4 max-w-3xl mx-auto"
        >
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-900 border border-slate-800 text-slate-300 text-xs font-medium">
            <Sparkles className="w-3.5 h-3.5 text-blue-400" />
            Full-Stack Skeleton Initialized & Ready
          </div>
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-white">
            Mandate Recovery & Intelligence Platform
          </h1>
          <p className="text-slate-400 text-base md:text-lg leading-relaxed">
            Scaffolded with Spring Boot 3.x, Java 17, JPA, PostgreSQL, Spring Validation, Lombok,
            and an animated React frontend styled with Tailwind CSS & shadcn/ui.
          </p>

          <div className="pt-2 flex items-center justify-center gap-4">
            <Button 
              onClick={checkBackendHealth} 
              disabled={isLoading}
              className="bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-600/25"
            >
              {isLoading ? (
                <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Terminal className="w-4 h-4 mr-2" />
              )}
              Test Backend Health (/api/health)
            </Button>
          </div>

          {healthStatus && (
            <motion.div 
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="mt-4 p-3 rounded-lg bg-slate-900/90 border border-slate-800 text-xs font-mono text-slate-300 inline-block"
            >
              {healthStatus}
            </motion.div>
          )}
        </motion.div>

        {/* Backend Packages Grid */}
        <section className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-white tracking-tight">Backend Architecture Packages</h2>
              <p className="text-slate-400 text-sm">Package skeletons organized under <code>com.recovermandate</code></p>
            </div>
            <Badge variant="outline" className="border-slate-800 text-slate-400 font-mono text-xs">
              9 Packages Configured
            </Badge>
          </div>

          <motion.div 
            initial="hidden"
            animate="visible"
            variants={{
              hidden: { opacity: 0 },
              visible: {
                opacity: 1,
                transition: { staggerChildren: 0.05 }
              }
            }}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {PACKAGES.map((pkg) => {
              const Icon = pkg.icon
              return (
                <motion.div
                  key={pkg.name}
                  variants={{
                    hidden: { opacity: 0, y: 10 },
                    visible: { opacity: 1, y: 0 }
                  }}
                  whileHover={{ scale: 1.02 }}
                  transition={{ duration: 0.2 }}
                >
                  <Card className="bg-slate-900/60 border-slate-800 hover:border-slate-700 transition-colors">
                    <CardHeader className="pb-2">
                      <div className="flex items-center justify-between">
                        <div className="p-2 rounded-lg bg-slate-800/80 border border-slate-700/50 text-blue-400">
                          <Icon className="w-5 h-5" />
                        </div>
                        <Badge variant="secondary" className="bg-slate-800 text-slate-300 font-mono text-xs">
                          .{pkg.name}
                        </Badge>
                      </div>
                      <CardTitle className="text-base font-semibold text-slate-100 mt-2">
                        {pkg.name}
                      </CardTitle>
                      <CardDescription className="text-slate-400 text-xs">
                        {pkg.desc}
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="pt-2 text-xs text-slate-500 font-mono flex items-center justify-between">
                      <span>com.recovermandate.{pkg.name}</span>
                      <ArrowUpRight className="w-3.5 h-3.5 text-slate-600" />
                    </CardContent>
                  </Card>
                </motion.div>
              )
            })}
          </motion.div>
        </section>

        {/* Tech Stack Summary Footer */}
        <section className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="space-y-1">
            <h3 className="text-sm font-semibold text-slate-200">Tech Stack & Starters Configured</h3>
            <p className="text-xs text-slate-400">
              Spring Boot Web, Data JPA, PostgreSQL Driver, Spring Validation, Lombok, React 19, Vite, Tailwind CSS, Framer Motion, shadcn/ui.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="border-slate-700 text-slate-300 text-xs">
              Ready for Development
            </Badge>
          </div>
        </section>
      </main>
    </div>
  )
}
