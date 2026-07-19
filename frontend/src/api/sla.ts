import http, { type ApiResult } from './http'
import type { PlatformEvent } from './events'
import type { SyncLog } from './sync'

export interface SlaBreach {
  id: number
  metric: string
  thresholdValue: number
  actualValue: number
  severity: string
  region: string
  correlationId: string | null
  message: string
  createdAt: string
}

export interface TraceResult {
  correlationId: string
  events: PlatformEvent[]
  syncLogs: SyncLog[]
  slaLogs: SlaLog[]
  breaches: SlaBreach[]
}

export interface PageResult<T> {
  records: T[]
  total: number
}

export interface SlaSummary {
  totalCalls: number
  successCalls: number
  failedCalls: number
  successRate: number
  avgLatencyMs: number
  p95LatencyMs: number
  region: string
}

export interface SlaLog {
  id: number
  provider: string
  endpoint: string
  latencyMs: number
  httpStatus: number | null
  success: boolean
  errorType: string | null
  region: string
  createdAt: string
}

export async function getSlaSummary(hours = 24, region?: string): Promise<SlaSummary> {
  const res = await http.get<ApiResult<SlaSummary>>('/api/v1/admin/sla/summary', { params: { hours, region } })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function listSlaLogs(page = 1, size = 20, region?: string): Promise<PageResult<SlaLog>> {
  const res = await http.get<ApiResult<PageResult<SlaLog>>>('/api/v1/admin/sla/logs', { params: { page, size, region } })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function listSlaBreaches(page = 1, size = 20): Promise<PageResult<SlaBreach>> {
  const res = await http.get<ApiResult<PageResult<SlaBreach>>>('/api/v1/admin/sla/breaches', { params: { page, size } })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function traceCorrelation(correlationId: string): Promise<TraceResult> {
  const res = await http.get<ApiResult<TraceResult>>(`/api/v1/admin/trace/${correlationId}`)
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
