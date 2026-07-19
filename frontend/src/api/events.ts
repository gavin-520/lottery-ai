import http, { type ApiResult } from './http'

export interface PlatformEvent {
  id: number
  eventType: string
  topic: string
  payload: string
  schemaVersion: string
  region: string
  correlationId: string
  published: boolean
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export async function listEvents(
  page = 1,
  size = 20,
  eventType?: string,
  region?: string,
  correlationId?: string
): Promise<PageResult<PlatformEvent>> {
  const res = await http.get<ApiResult<PageResult<PlatformEvent>>>('/api/v1/admin/events', {
    params: { page, size, eventType, region, correlationId }
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
