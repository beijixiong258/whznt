import type { ConversationDetail, ConversationKind, GenerationEvent, GenerationRequest, HistoryPage } from './types'

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

export class StreamInterrupted extends ApiError {
  constructor() {
    super(502, '连接已中断。可以从历史查看已保存结果。')
  }
}

interface CsrfToken {
  headerName: string
  token: string
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, init)
  if (response.status === 204 || response.status === 201) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export async function loadImage(path: string, signal?: AbortSignal): Promise<Blob> {
  const response = await request(path, { signal }, 'image/png, image/jpeg, application/json')
  return response.blob()
}

export function jsonBody(body: unknown, method = 'POST'): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

export function loadConversation(id: number, signal?: AbortSignal): Promise<ConversationDetail> {
  return api<ConversationDetail>('/api/conversations/' + id, { signal })
}

export function loadConversations(
  filters: { q?: string; assistant?: string; kind?: ConversationKind; since?: string; page?: number } = {},
  signal?: AbortSignal,
): Promise<HistoryPage> {
  const params = new URLSearchParams()
  for (const [name, value] of Object.entries(filters)) params.set(name, String(value))
  return api<HistoryPage>('/api/conversations?' + params, { signal })
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法完成当前操作，请稍后重试。'
}

async function request(path: string, init: RequestInit, accept = 'application/json'): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', accept)
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD'].includes(method)) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new ApiError(response.status, '无法获取会话，请刷新页面后再试。')
    const csrf = await response.json() as CsrfToken
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(path, { ...init, headers, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    if (response.status === 401 && !/^\/api\/(?:admin\/)?auth\//.test(path)) {
      window.dispatchEvent(new Event('whznt:session-expired'))
    }
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(response.status, payload?.message ?? '服务暂时不可用，请稍后重试。')
  }
  return response
}

export async function streamGeneration(
  input: GenerationRequest,
  handlers: { start: (event: GenerationEvent) => void; delta: (text: string) => void },
  signal: AbortSignal,
): Promise<GenerationEvent> {
  const response = await request('/api/writing/generate', { ...jsonBody(input), signal },
    'text/event-stream, application/json')
  const reader = response.body?.getReader()
  if (!reader) throw new ApiError(502, '无法读取生成结果。')

  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''
      for (const event of events) {
        const fields = event.split(/\r?\n/)
        const type = fields.find(line => line.startsWith('event:'))?.slice(6).trim()
        const data = fields.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
        if (!type || !data) continue

        const payload = JSON.parse(data) as GenerationEvent
        if (type === 'start') handlers.start(payload)
        if (type === 'delta' && payload.text) handlers.delta(payload.text)
        if (type === 'done') return payload
        if (type === 'error') throw new ApiError(502, payload.message ?? '生成没有完成，请重试。')
      }
      if (done) break
    }
    throw new StreamInterrupted()
  } catch (failure) {
    if (failure instanceof ApiError || signal.aborted) throw failure
    throw new StreamInterrupted()
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}
