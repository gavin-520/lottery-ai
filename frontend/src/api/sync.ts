import http, { type ApiResult } from './http'

export interface SyncLog {
  id: number
  source: string
  status: string
  fetchedCount: number
  newCount: number
  message: string
  region: string
  correlationId: string
  errorType: string | null
  httpStatus: number | null
  startedAt: string
  finishedAt: string | null
  parentLogId: number | null
}

export interface RegionStats {
  region: string
  totalCalls: number
  successRate: number
  p95LatencyMs: number
  failedSyncs: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface SyncStatus {
  lastSyncId: number | null
  source: string
  status: string
  fetchedCount: number
  newCount: number
  message: string
  startedAt: string | null
  finishedAt: string | null
  region: string | null
  correlationId: string | null
  errorType: string | null
  httpStatus: number | null
  schedulerEnabled: boolean
  cron: string
}

export async function fetchSyncStatus(): Promise<SyncStatus> {
  const res = await http.get<ApiResult<SyncStatus>>('/api/v1/sync/status')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function listSyncLogs(page = 1, size = 20, status?: string): Promise<PageResult<SyncLog>> {
  const res = await http.get<ApiResult<PageResult<SyncLog>>>('/api/v1/admin/sync/logs', {
    params: { page, size, status }
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchRegionStats(hours = 24): Promise<RegionStats[]> {
  const res = await http.get<ApiResult<RegionStats[]>>('/api/v1/admin/sync/regions', { params: { hours } })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function triggerSync(): Promise<SyncStatus> {
  const res = await http.post<ApiResult<SyncStatus>>('/api/v1/admin/sync/trigger')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function retrySync(): Promise<SyncStatus> {
  const res = await http.post<ApiResult<SyncStatus>>('/api/v1/admin/sync/retry')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function retrySyncByLogId(logId: number): Promise<SyncStatus> {
  const res = await http.post<ApiResult<SyncStatus>>(`/api/v1/admin/sync/retry/${logId}`)
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export function subscribeEvents(token: string, onEvent: (name: string, data: unknown) => void): EventSource {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const url = `${base}/api/v1/events/stream?token=${encodeURIComponent(token)}`
  const es = new EventSource(url)
  es.addEventListener('sync', (e) => {
    try {
      onEvent('sync', JSON.parse(e.data))
    } catch {
      onEvent('sync', e.data)
    }
  })
  es.addEventListener('sync-failed', (e) => {
    try {
      onEvent('sync-failed', JSON.parse(e.data))
    } catch {
      onEvent('sync-failed', e.data)
    }
  })
  es.addEventListener('sla-breach', (e) => {
    try {
      onEvent('sla-breach', JSON.parse(e.data))
    } catch {
      onEvent('sla-breach', e.data)
    }
  })
  es.addEventListener('connected', (e) => {
    onEvent('connected', e.data)
  })
  return es
}
