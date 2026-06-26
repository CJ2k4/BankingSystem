import { useQuery } from '@tanstack/react-query'
import { fetchPing } from './lib/api'

function StatusBadge({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-sm font-medium ${
        ok ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
      }`}
    >
      <span className={`h-2 w-2 rounded-full ${ok ? 'bg-green-500' : 'bg-red-500'}`} />
      {label}
    </span>
  )
}

function App() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ping'],
    queryFn: fetchPing,
    refetchInterval: 10_000,
  })

  const backendUp = !isLoading && !isError && data?.status === 'UP'

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex items-center justify-center p-6">
      <div className="w-full max-w-xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-3xl font-bold tracking-tight">🏦 Banking System</h1>
        <p className="mt-2 text-slate-500">
          Phase 0 scaffold — frontend and backend are wired together.
        </p>

        <div className="mt-6 rounded-xl bg-slate-50 p-4">
          <div className="flex items-center justify-between">
            <span className="font-medium">Backend</span>
            {isLoading ? (
              <StatusBadge ok={false} label="Checking…" />
            ) : (
              <StatusBadge ok={backendUp} label={backendUp ? 'UP' : 'DOWN'} />
            )}
          </div>
          {data && (
            <dl className="mt-4 grid grid-cols-2 gap-2 text-sm text-slate-600">
              <dt className="text-slate-400">Service</dt>
              <dd>{data.service}</dd>
              <dt className="text-slate-400">Last checked</dt>
              <dd>{new Date(data.timestamp).toLocaleTimeString()}</dd>
            </dl>
          )}
          {isError && (
            <p className="mt-3 text-sm text-red-600">
              Could not reach the API. Is the backend running on{' '}
              {import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}?
            </p>
          )}
        </div>

        <p className="mt-6 text-xs text-slate-400">
          Next: Phase 1 — authentication &amp; user accounts.
        </p>
      </div>
    </div>
  )
}

export default App
