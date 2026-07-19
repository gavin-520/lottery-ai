import http, { type ApiResult } from './http'

export interface UserSummary {
  id: number
  username: string
  nickname: string
  role: string
  status: number
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  nickname?: string
  role: string
}

export async function listUsers(): Promise<UserSummary[]> {
  const res = await http.get<ApiResult<UserSummary[]>>('/api/v1/admin/users')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function createUser(data: CreateUserRequest): Promise<UserSummary> {
  const res = await http.post<ApiResult<UserSummary>>('/api/v1/admin/users', data)
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export interface ImportResult {
  jobId: number
  filename: string
  totalRows: number
  successRows: number
  failedRows: number
  status: string
}

export async function importCsv(file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post<ApiResult<ImportResult>>('/api/v1/admin/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
