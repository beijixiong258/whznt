import type { VersionRow } from './types'

export type MaterialKind = 'LIFE' | 'BUSINESS' | 'OBSERVATION' | 'STYLE'
export type ProjectStep = 'TRAITS' | 'STORY' | 'POSITION' | 'TOPICS' | 'SCRIPT'
export type JobState = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface PersonProject {
  id: number
  name: string
  summary: string
  updatedAt: string
}

export interface MaterialVersion {
  id: number
  filename: string
  createdAt: string
}

export interface MaterialPassage {
  id: number
  versionId: number
  material: string
  kind: MaterialKind
  location: string
  content: string
}

export interface MaterialRow {
  id: number
  name: string
  kind: MaterialKind
  currentVersionId: number
  versions: MaterialVersion[]
}

export interface MaterialText {
  version: MaterialVersion
  passages: MaterialPassage[]
}

export interface Artifact {
  id: number
  step: ProjectStep
  topic: string
  conversationId: number
  currentVersion: VersionRow
}

export interface Topic {
  index: number
  versionId: number
  title: string
}

export interface ProjectJob {
  id: number
  step: ProjectStep
  state: JobState
  error: string | null
  conversationId: number | null
  versionId: number | null
}

export interface ProjectDetail {
  project: PersonProject
  materials: MaterialRow[]
  artifacts: Artifact[]
  topics: Topic[]
  jobs: ProjectJob[]
}

export interface Sources {
  upstream: { step: ProjectStep; conversationId: number; versionId: number; title: string }[]
  passages: MaterialPassage[]
}

export const stepLabels: Record<ProjectStep, string> = {
  TRAITS: '提炼个人特质', STORY: '人物故事', POSITION: 'IP 定位', TOPICS: '短视频选题', SCRIPT: '口播稿',
}

export const materialLabels: Record<MaterialKind, string> = {
  LIFE: '人生访谈', BUSINESS: '商业访谈', OBSERVATION: '现场观察', STYLE: '风格样稿',
}

export function projectPath(id: number) {
  return '/api/projects/' + id
}

export function running(job: ProjectJob) {
  return job.state === 'QUEUED' || job.state === 'RUNNING'
}
