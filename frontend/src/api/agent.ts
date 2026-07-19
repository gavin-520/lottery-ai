import http, { type ApiResult } from './http'

export interface AgentAnalysis {
  summary: string
  insights: string[]
  recommendations: string[]
  agentName: string
}

export interface WorkflowStep {
  agent: string
  role: string
  output: string
}

export interface AgentWorkflow {
  finalReport: string
  steps: WorkflowStep[]
  workflowName: string
}

export async function analyze(question?: string): Promise<AgentAnalysis> {
  const res = await http.post<ApiResult<AgentAnalysis>>('/api/v1/agent/analyze', null, {
    params: question ? { question } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function runWorkflow(question?: string): Promise<AgentWorkflow> {
  const res = await http.post<ApiResult<AgentWorkflow>>('/api/v1/agent/workflow', null, {
    params: question ? { question } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
