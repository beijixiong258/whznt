<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { ArrowLeftIcon } from 'tdesign-icons-vue-next'
import {
  Avatar as TAvatar, Card as TCard, Link as TLink, MessagePlugin,
  RadioButton as TRadioButton, RadioGroup as TRadioGroup,
} from 'tdesign-vue-next'
import { api, ApiError, errorMessage, jsonBody, loadConversation, streamGeneration, StreamInterrupted } from '../api'
import type {
  ConversationDetail, DraftVersion, GenerationRequest, Manuscript,
  RewriteAction, WritingInput,
} from '../types'
import { writingExpertFor, writingExpertPrompt, writingExperts, type WritingExpert } from '../writingExperts'
import MarkdownView from './MarkdownView.vue'
import ProjectSources from './ProjectSources.vue'

const props = defineProps<{
  initial: ConversationDetail | null; initialVersion?: number; initialExpert?: WritingExpert
}>()
const emit = defineEmits<{
  saved: []; dirty: [value: boolean]; busy: [value: boolean]
  experts: []
  project: [id: number]; upstream: [id: number, version: number]
  subtitle: [reference: { draftId: number; versionId: number; title: string }]
}>()

const projectId = props.initial?.conversation.projectId ?? null
const sources = ref<InstanceType<typeof ProjectSources>>()
const citationIds = ref<number[]>([])
const conversationId = ref(props.initial?.conversation.id ?? null)
const draft = ref(props.initial?.draft ?? null)
const version = ref<DraftVersion | null>(draft.value?.currentVersion ?? null)
const content = ref(version.value?.content ?? '')
const defaultInput: WritingInput = {
  topic: '', angle: '', audience: '', tone: '', durationSeconds: 150, format: 'TALK', background: '',
}
const maxTopicLength = 300
const maxBriefLength = 12000
const referenceInput = ref(version.value?.writingInput ?? defaultInput)
const brief = ref(briefFromInput(referenceInput.value))
const expert = shallowRef(props.initialExpert ?? writingExpertFor(referenceInput.value))
const expertOptions = writingExperts.map(option => ({ value: option.name, label: option.name }))
const manualEditor = ref(false)
const mode = ref<'edit' | 'preview'>('edit')
const operation = ref<'generate' | 'save' | 'read' | 'rewrite'>()
const partial = ref('')
const error = ref('')
const draftHeading = ref<HTMLHeadingElement>()
const editor = ref<HTMLTextAreaElement>()
const selection = ref({ start: 0, end: 0 })
const maxRewriteSelection = 6000
const maxRewriteContext = 1500
const rewrite = ref<{
  action: RewriteAction
  start: number
  end: number
  document: string
  tone: string
  result: string | null
  error: string
} | null>(null)
let abort: AbortController | undefined
let retry: { signature: string; request: GenerationRequest } | undefined

const busy = computed(() => operation.value !== undefined)
const showEditor = computed(() => projectId !== null || !!draft.value || manualEditor.value || operation.value === 'generate')
const latest = computed(() => draft.value?.currentVersion.version.id ?? null)
const showingOld = computed(() => !!version.value && version.value.version.id !== latest.value)
const changed = computed(() => content.value !== (version.value?.content ?? ''))
const hasUnsavedInput = computed(() => {
  if (projectId !== null) return false
  return brief.value.trim() !== briefFromInput(draft.value?.currentVersion.writingInput ?? defaultInput)
})
const hasSelection = computed(() => !!content.value.slice(selection.value.start, selection.value.end).trim())
const selectionLength = computed(() => selection.value.end - selection.value.start)
const selectionTooLong = computed(() => selectionLength.value > maxRewriteSelection)
const versionOptions = computed(() => (draft.value?.versions ?? []).map((row, index, all) => ({
  value: row.id,
  label: '第 ' + (all.length - index) + ' 版 · ' + (row.source === 'GENERATED' ? '生成' : '手动保存'),
})))
const exportHref = computed(() => {
  if (!draft.value || !version.value) return undefined
  return '/api/drafts/' + draft.value.id + '/versions/' + version.value.version.id + '/export'
})
const rewriteLabels: Record<RewriteAction, string> = { POLISH: '润色', SHORTEN: '缩写', TONE: '换语气' }

watch(busy, value => emit('busy', value))
watch([changed, hasUnsavedInput], ([edited, typed]) => emit('dirty', edited || typed))

function selectExpert(value: unknown) {
  const option = writingExperts.find(candidate => candidate.name === value)
  if (option) expert.value = option
}

function briefFromInput(input: WritingInput) {
  // A long brief is saved in full as background, including its opening topic.
  const topic = input.topic ?? ''
  const background = input.background ?? ''
  if (background.startsWith(topic)) return background
  return [topic, background].filter(Boolean).join('\n\n')
}

function setWritingInput(input: WritingInput) {
  referenceInput.value = input
  brief.value = briefFromInput(input)
}

function writingInput(): WritingInput {
  const text = brief.value.trim()
  const source = referenceInput.value
  const longBrief = text.length > maxTopicLength
  const params = text === briefFromInput(source) ? source : {
    ...defaultInput,
    topic: longBrief ? contextSlice(text, 0, maxTopicLength).split('\n', 1)[0].trimEnd() : text,
    background: longBrief ? text : '',
  }
  return {
    ...params,
    // The existing writing API includes the angle in the instructions sent to the model.
    angle: writingExpertPrompt(expert.value),
    format: 'ARTICLE',
    durationSeconds: null,
  }
}

function showVersion(next: DraftVersion | null) {
  version.value = next
  content.value = next?.content ?? ''
  selection.value = { start: 0, end: 0 }
}

function setDraft(next: Manuscript | null) {
  draft.value = next
  showVersion(next?.currentVersion ?? null)
  if (next) conversationId.value = next.conversationId
}

async function generate(kind: 'WRITE' | 'REGENERATE' = 'WRITE') {
  if (projectId !== null || busy.value || showingOld.value) return
  if (changed.value) {
    error.value = '先保存当前编辑，再生成新版本。'
    return
  }
  const requestInput = kind === 'WRITE' ? writingInput() : undefined
  const signature = JSON.stringify({ kind, baseVersionId: latest.value, writingInput: requestInput })
  const request: GenerationRequest = retry?.signature === signature ? retry.request : {
    requestId: crypto.randomUUID(), kind, conversationId: conversationId.value,
    baseVersionId: latest.value, writingInput: requestInput,
  }
  retry = { signature, request }
  operation.value = 'generate'
  partial.value = ''
  error.value = ''
  mode.value = 'preview'
  void nextTick(() => draftHeading.value?.scrollIntoView({ block: 'nearest' }))
  const controller = new AbortController()
  abort = controller
  try {
    const result = await streamGeneration(request, {
      start: event => { conversationId.value = event.conversationId },
      delta: text => { partial.value += text },
    }, controller.signal)
    if (result.conversationId === null) throw new StreamInterrupted()
    conversationId.value = result.conversationId
    const detail = await loadConversation(result.conversationId)
    setDraft(detail.draft)
    if (draft.value && result.versionId !== null && result.versionId !== latest.value) {
      const saved = await api<DraftVersion>('/api/drafts/' + draft.value.id + '/versions/' + result.versionId)
      showVersion(saved)
    }
    if (requestInput && version.value?.writingInput) setWritingInput(version.value.writingInput)
    retry = undefined
    emit('saved')
  } catch (failure) {
    error.value = controller.signal.aborted ? '已停止生成。原稿和输入已保留。' : errorMessage(failure)
    if (controller.signal.aborted || (failure instanceof ApiError && !(failure instanceof StreamInterrupted))) {
      retry = undefined
    }
    if (conversationId.value !== null) emit('saved')
    mode.value = 'edit'
  } finally {
    partial.value = ''
    operation.value = undefined
    abort = undefined
  }
}

function openEditor() {
  manualEditor.value = true
  mode.value = 'edit'
  void nextTick(() => editor.value?.focus())
}

async function save() {
  if (busy.value || showingOld.value || !content.value.trim() || !changed.value) return
  error.value = ''
  operation.value = 'save'
  try {
    const path = conversationId.value === null ? '/api/drafts' : '/api/drafts?conversationId=' + conversationId.value
    setDraft(await api<Manuscript>(path, jsonBody({ content: content.value, baseVersionId: latest.value })))
    retry = undefined
    emit('saved')
    void MessagePlugin.success('已保存新版本')
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

async function selectVersion(value: unknown) {
  const id = Number(value)
  if (!draft.value || !Number.isSafeInteger(id) || busy.value || version.value?.version.id === id) return
  if (changed.value) {
    error.value = '先保存当前编辑，再切换版本。'
    return
  }
  operation.value = 'read'
  error.value = ''
  try {
    const next = id === latest.value ? draft.value.currentVersion
      : await api<DraftVersion>('/api/drafts/' + draft.value.id + '/versions/' + id)
    showVersion(next)
    mode.value = 'preview'
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

function readSelection() {
  if (!editor.value) return
  selection.value = { start: editor.value.selectionStart, end: editor.value.selectionEnd }
}

function startRewrite(action: RewriteAction) {
  if (busy.value || !draft.value || showingOld.value
    || !hasSelection.value || selectionTooLong.value) return
  const { start, end } = selection.value
  rewrite.value = { action, start, end, document: content.value, tone: '', result: null, error: '' }
  if (action !== 'TONE') void requestRewrite()
}

function contextSlice(text: string, start: number, end: number) {
  // A UTF-16 limit can cut through a supplementary character at either edge.
  return text.slice(start, end).replace(/^[\uDC00-\uDFFF]|[\uD800-\uDBFF]$/g, '')
}

async function requestRewrite() {
  const selected = rewrite.value
  if (!selected || !draft.value || busy.value) return
  if (selected.action === 'TONE' && !selected.tone.trim()) {
    selected.error = '请输入想要的语气。'
    return
  }
  selected.error = ''
  operation.value = 'rewrite'
  try {
    const response = await api<{ content: string }>('/api/writing/rewrite', jsonBody({
      draftId: draft.value.id,
      baseVersionId: latest.value,
      action: selected.action,
      selection: selected.document.slice(selected.start, selected.end),
      before: contextSlice(selected.document, Math.max(0, selected.start - maxRewriteContext), selected.start),
      after: contextSlice(selected.document, selected.end, selected.end + maxRewriteContext),
      tone: selected.tone,
    }))
    selected.result = response.content
  } catch (failure) {
    selected.error = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

function applyRewrite() {
  const selected = rewrite.value
  if (!selected || selected.result === null || busy.value) return
  if (content.value !== selected.document) {
    selected.error = '原文已经变化，请重新选择要改写的内容。'
    return
  }
  const next = selected.document.slice(0, selected.start) + selected.result + selected.document.slice(selected.end)
  if (next.length > 30000) {
    selected.error = '替换后超过稿件的 30000 字上限，请缩短选区后再试。'
    return
  }
  content.value = next
  const end = selected.start + selected.result.length
  rewrite.value = null
  void nextTick(() => {
    editor.value?.focus()
    editor.value?.setSelectionRange(selected.start, end)
    readSelection()
  })
}

function closeRewrite() {
  if (!busy.value) rewrite.value = null
}

async function copy() {
  try {
    await navigator.clipboard.writeText(content.value)
    void MessagePlugin.success('已复制稿件')
  } catch {
    error.value = '无法访问剪贴板，可以选择文字后手动复制。'
  }
}

onMounted(() => {
  if (props.initialVersion !== undefined) {
    mode.value = 'preview'
    void selectVersion(props.initialVersion)
  }
})
onBeforeUnmount(() => abort?.abort())
</script>

<template>
  <section class="write-pane" :class="{ 'expert-writing': projectId === null }">
    <template v-if="projectId === null">
      <t-button class="writer-back" variant="text" theme="default" size="small" @click="emit('experts')">
        <template #icon><ArrowLeftIcon /></template>
        专家库
      </t-button>
      <div class="page-heading writer-heading">
        <t-avatar size="large" aria-hidden="true">
          <template #icon><component :is="expert.icon" /></template>
        </t-avatar>
        <div>
          <h1>{{ expert.name }}</h1>
          <p>{{ expert.description }}</p>
        </div>
      </div>
    </template>
    <div v-else class="page-heading">
      <h1>{{ initial?.conversation.title }}</h1>
      <t-button variant="text" size="small" @click="emit('project', projectId)">返回人物项目</t-button>
    </div>
    <div v-if="projectId === null" class="expert-panel">
      <t-card class="work-card" body-class-name="expert-composer">
        <label class="sr-only" for="writing-brief">写作要求或素材</label>
        <t-textarea v-field-id="'writing-brief'" v-model="brief" :placeholder="expert.example"
          :autosize="{ minRows: 4, maxRows: 9 }" :maxlength="maxBriefLength" :readonly="busy || showingOld" />
        <div class="expert-actions">
          <div class="expert-select">
            <label class="sr-only" for="writer-expert">切换专家</label>
            <t-select v-field-id="'writer-expert'" :options="expertOptions" :value="expert.name"
              :disabled="busy || showingOld" @change="selectExpert" />
          </div>
          <div class="writer-submit">
            <t-button v-if="!showEditor" variant="text" theme="default" @click="openEditor">自己写</t-button>
            <t-button :loading="operation === 'generate'" :disabled="busy || showingOld"
              @click="generate()">开始生成</t-button>
          </div>
        </div>
      </t-card>
    </div>
    <t-alert v-if="error" theme="warning" :message="error" close @close="error = ''" />
    <div v-if="showEditor" class="writing-grid" :class="{ 'project-writing': projectId !== null }">
      <t-card class="work-card editor-card" body-class-name="editor-body" :aria-busy="busy">
        <div class="editor-heading">
          <div>
            <h2 ref="draftHeading">稿件</h2>
            <span class="secondary-text">{{ showingOld ? '查看旧版' : changed ? '有未保存的修改' : draft ? '已保存' : '未保存' }}</span>
          </div>
          <label v-if="versionOptions.length" class="sr-only" for="draft-version">查看稿件版本</label>
          <t-select v-if="versionOptions.length" v-field-id="'draft-version'" class="version-select"
            :options="versionOptions" :model-value="version?.version.id" :disabled="busy" @change="selectVersion" />
        </div>
        <t-radio-group v-model="mode" variant="outline" class="editor-tabs" aria-label="稿件视图">
          <t-radio-button value="edit" :disabled="operation === 'generate'">编辑</t-radio-button>
          <t-radio-button value="preview">预览</t-radio-button>
        </t-radio-group>
        <div v-if="operation === 'generate'" class="document-preview">
          <MarkdownView v-if="partial" :content="partial" />
          <t-loading v-else text="正在起稿" />
        </div>
        <template v-else>
          <textarea v-if="mode === 'edit'" ref="editor" v-model="content" class="document-editor"
            aria-label="稿件 Markdown 正文" placeholder="在这里写下正文。用 # 加空格写标题。"
            maxlength="30000" :readonly="showingOld || busy" @select="readSelection" @input="readSelection"
            @keyup="readSelection" @mouseup="readSelection"></textarea>
          <div v-else class="document-preview">
            <MarkdownView v-if="content.trim()" :content="content" :citation-ids="citationIds"
              @citation="sources?.openCitation($event)" />
            <p v-else class="secondary-text">稿件还没有内容。</p>
          </div>
        </template>
        <div v-if="mode === 'edit' && hasSelection && draft && !showingOld" class="rewrite-actions">
          <span aria-live="polite">
            {{ selectionTooLong ? `已选 ${selectionLength} 个字符，单次最多改写 ${maxRewriteSelection} 个。` : '改写选中内容' }}
          </span>
          <t-button v-for="(label, action) in rewriteLabels" :key="action" size="small" variant="outline"
            :disabled="busy || selectionTooLong" @click="startRewrite(action)">{{ label }}</t-button>
        </div>
        <div class="editor-footer">
          <t-button v-if="operation === 'generate'" variant="outline" @click="abort?.abort()">停止</t-button>
          <template v-else>
            <t-button v-if="!showingOld" :loading="operation === 'save'"
              :disabled="busy || !changed || !content.trim()" @click="save">保存版本</t-button>
            <t-button v-if="projectId === null && !showingOld && version?.writingInput" variant="outline"
              title="按该版保存的要求重新生成"
              :disabled="busy" @click="generate('REGENERATE')">重新生成</t-button>
            <t-button variant="text" theme="default" :disabled="!content.trim() || busy" @click="copy">复制</t-button>
            <t-link v-if="exportHref" :href="exportHref" theme="primary" title="导出所选的已保存版本">
              导出 Markdown
            </t-link>
            <t-button v-if="draft && version" variant="outline" size="small" :disabled="busy"
              @click="emit('subtitle', { draftId: draft.id, versionId: version.version.id, title: version.version.title })">
              给这版稿配字幕
            </t-button>
          </template>
        </div>
      </t-card>
      <ProjectSources v-if="projectId !== null && version" ref="sources" :project-id="projectId"
        :version-id="version.version.id" @loaded="citationIds = $event"
        @open="(id, number) => emit('upstream', id, number)" />
    </div>
    <t-dialog :visible="rewrite !== null" :header="rewrite ? rewriteLabels[rewrite.action] + '选段' : '改写选段'"
      :confirm-btn="{ content: '应用到选区', disabled: rewrite?.result == null || busy }"
      cancel-btn="取消" @close="closeRewrite" @cancel="closeRewrite" @confirm="applyRewrite">
      <div v-if="rewrite" class="rewrite-dialog">
        <div v-if="rewrite.action === 'TONE' && rewrite.result === null" class="field">
          <label for="rewrite-tone">目标语气</label>
          <t-input v-field-id="'rewrite-tone'" v-model="rewrite.tone" :maxlength="120"
            placeholder="例如，更直接、更口语" :disabled="busy" @enter="requestRewrite" />
        </div>
        <div class="rewrite-text">
          <strong>原文</strong><p>{{ rewrite.document.slice(rewrite.start, rewrite.end) }}</p>
        </div>
        <t-loading v-if="operation === 'rewrite'" text="正在改写" />
        <div v-if="rewrite.result !== null" class="rewrite-text result">
          <strong>改写结果</strong><p>{{ rewrite.result }}</p>
        </div>
        <t-alert v-if="rewrite.error" theme="error" :message="rewrite.error" />
        <t-button v-if="rewrite.result === null && !busy" variant="outline" @click="requestRewrite">生成预览</t-button>
      </div>
    </t-dialog>
  </section>
</template>
