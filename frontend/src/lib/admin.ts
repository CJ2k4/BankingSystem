import { api, type Profile } from './api'

export type KycStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'

export async function listAllUsers(): Promise<Profile[]> {
  const { data } = await api.get<Profile[]>('/api/v1/admin/users')
  return data
}

export async function setKyc(userId: string, status: KycStatus): Promise<Profile> {
  const { data } = await api.post<Profile>(`/api/v1/admin/users/${userId}/kyc`, { status })
  return data
}
