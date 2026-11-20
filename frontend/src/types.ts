export type ConversationKind = 'WRITING' | 'CHAT'
export type WritingFormat = 'TALK' | 'ARTICLE'
export type RewriteAction = 'POLISH' | 'SHORTEN' | 'TONE'

export interface Account {
  id: number
  phone: string
}

export interface ModelStatus {
  available: boolean
  message: string | null
}

export interface WritingInput {
  topic: string
  angle: string
  audience: string
  tone: string
  durationSeconds: number | null
  format: WritingFormat
  background: string
}

export interface TopicIdea {
  title: string
  angle: string
  audience: string
  takeaway: string
}

export interface ConversationRow {
  id: number
  kind: ConversationKind
  title: string
  updatedAt: string
}

export interface VersionRow {
  id: number
  title: string
  source: 'GENERATED' | 'EDITED'
  createdAt: string
  baseVersionId: number | null
}

export interface DraftVersion {
  version: VersionRow
  content: string
  writingInput: WritingInput | null
  model: string | null
}

export interface Manuscript {
  id: number
  conversationId: number
  currentVersion: DraftVersion
  versions: VersionRow[]
}

export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface ConversationDetail {
  conversation: ConversationRow
  messages: ChatMessage[]
  draft: Manuscript | null
}

export interface HistoryPage {
  items: ConversationRow[]
  hasMore: boolean
}

export interface GenerationRequest {
  requestId: string
  kind: 'WRITE' | 'REGENERATE' | 'CHAT'
  conversationId?: number | null
  baseVersionId?: number | null
  writingInput?: WritingInput
  message?: string
}

export interface GenerationEvent {
  conversationId: number | null
  versionId: number | null
  text: string | null
  message: string | null
}
