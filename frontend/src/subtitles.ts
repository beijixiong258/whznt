export type SubtitleState = 'NONE' | 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED'

export interface DraftReference {
  draftId: number
  versionId: number
  title: string
}

export interface SubtitleSummary {
  id: number
  name: string
  mode: 'AUTO' | 'ALIGN' | 'IMPORT'
  state: SubtitleState
  exportState: SubtitleState
  updatedAt: string
}

export interface Cue {
  startMs: number
  endMs: number
  text: string
}

export interface SubtitleDetail {
  task: SubtitleSummary
  durationMs: number
  referenceTitle: string
  version: number
  cues: Cue[]
  error: string
  exportError: string
}

export const modeLabels = { AUTO: '按录音识别', ALIGN: '逐字稿对齐', IMPORT: '导入 SRT' }
export const stateLabels = { NONE: '待开始', QUEUED: '排队中', RUNNING: '处理中', READY: '可校对', FAILED: '未完成' }

export function subtitlePath(id: number) {
  return '/api/subtitles/' + id
}

export function pending(state: SubtitleState) {
  return state === 'QUEUED' || state === 'RUNNING'
}
