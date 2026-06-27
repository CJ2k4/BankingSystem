import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import AppLayout from '../components/AppLayout'
import { useAuth } from '../context/AuthContext'
import { listAllUsers, setKyc, type KycStatus } from '../lib/admin'

const KYC_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  VERIFIED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
}

export default function AdminPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const usersQ = useQuery({ queryKey: ['admin-users'], queryFn: listAllUsers, enabled: user?.role === 'ADMIN' })

  const kycM = useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: KycStatus }) => setKyc(userId, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  if (user?.role !== 'ADMIN') {
    return (
      <AppLayout>
        <p className="text-slate-500">Admins only.</p>
      </AppLayout>
    )
  }

  const users = usersQ.data ?? []

  return (
    <AppLayout>
      <h1 className="text-2xl font-bold tracking-tight">Admin · KYC approvals</h1>
      <p className="mt-1 text-sm text-slate-500">Verify or reject customer identity checks.</p>

      <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-6">
        {usersQ.isLoading && <p className="text-slate-500">Loading…</p>}
        {!usersQ.isLoading && users.length === 0 && (
          <p className="text-slate-500">No customers yet.</p>
        )}
        {users.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-400">
                <th className="pb-2">Customer</th>
                <th className="pb-2">Email</th>
                <th className="pb-2">KYC</th>
                <th className="pb-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const name = [u.firstName, u.lastName].filter(Boolean).join(' ') || '—'
                const pending = kycM.isPending && kycM.variables?.userId === u.userId
                return (
                  <tr key={u.userId} className="border-t border-slate-100">
                    <td className="py-2">{name}</td>
                    <td className="py-2 text-slate-500">{u.email}</td>
                    <td className="py-2">
                      <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${KYC_STYLES[u.kycStatus]}`}>
                        {u.kycStatus}
                      </span>
                    </td>
                    <td className="py-2 text-right">
                      <span className="inline-flex gap-2">
                        <button
                          onClick={() => kycM.mutate({ userId: u.userId, status: 'VERIFIED' })}
                          disabled={pending || u.kycStatus === 'VERIFIED'}
                          className="rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-40"
                        >
                          Verify
                        </button>
                        <button
                          onClick={() => kycM.mutate({ userId: u.userId, status: 'REJECTED' })}
                          disabled={pending || u.kycStatus === 'REJECTED'}
                          className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50 disabled:opacity-40"
                        >
                          Reject
                        </button>
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        {kycM.isError && <p className="mt-3 text-sm text-red-600">Could not update KYC.</p>}
      </section>
    </AppLayout>
  )
}
