<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { Avatar as TAvatar, Card as TCard, Link as TLink, MessagePlugin, type TextareaValue } from 'tdesign-vue-next'
import { api, ApiError, errorMessage, jsonBody, loadConversation, streamGeneration, StreamInterrupted } from '../api'
import type { DraftReference } from '../subtitles'
import type {
  ConversationDetail, DraftVersion, GenerationRequest, Manuscript,
  RewriteAction, WritingInput,
} from '../types'
import { writingExpertFor, writingExpertPrompt, type WritingExpert } from '../writingExperts'
import BackButton from './BackButton.vue'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
  initial: ConversationDetail | null; initialVersion?: number; initialExpert?: WritingExpert
  inline?: boolean; showSubtitles?: boolean; backLabel?: string
}>()
const emit = defineEmits<{
  saved: [id: number]; dirty: [value: boolean]; busy: [value: boolean]
  experts: []
  project: [id: number]; back: []
  subtitle: [reference: DraftReference]
}>()
defineSlots<{ next?: (props: { disabled: boolean }) => unknown }>()

const projectId = props.initial?.conversation.projectId ?? null
const conversationId = ref(props.initial?.conversation.id ?? null)
const draft = ref(props.initial?.draft ?? null)
const version = ref<DraftVersion | null>(draft.value?.currentVersion ?? null)
const savedText = computed(() => documentText(version.value?.content ?? ''))
const content = ref(savedText.value)
const defaultInput: WritingInput = {
  topic: '', angle: '', audience: '', tone: '', durationSeconds: 150, format: 'TALK', background: '',
}
const maxTopicLength = 300
const maxBriefLength = 12000
const referenceInput = ref(version.value?.writingInput ?? defaultInput)
const brief = ref(briefFromInput(referenceInput.value))
const expert = props.initialExpert ?? writingExpertFor(referenceInput.value)
const revision = ref('')
const mode = ref<'edit' | 'preview'>('preview')
const operation = ref<'generate' | 'save' | 'read' | 'rewrite'>()
const partial = ref('')
const error = ref('')
const editingTitle = ref(false)
const title = ref('')
const titleError = ref('')
const draftHeading = ref<HTMLHeadingElement>()
const editor = ref<HTMLTextAreaElement>()
const preview = ref<HTMLDivElement>()
const versionFieldId = useId()
const toneFieldId = useId()
const titleFieldId = useId()
const revisionFieldId = useId()
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
const showEditor = computed(() => projectId !== null || !!draft.value || operation.value === 'generate')
const latest = computed(() => draft.value?.currentVersion.version.id ?? null)
const showingOld = computed(() => !!version.value && version.value.version.id !== latest.value)
const changed = computed(() => content.value !== savedText.value)
const hasUnsavedInput = computed(() => {
  if (projectId !== null) return false
  return !!revision.value.trim()
    || brief.value.trim() !== briefFromInput(draft.value?.currentVersion.writingInput ?? defaultInput)
})
const hasSelection = computed(() => !!content.value.slice(selection.value.start, selection.value.end).trim())
const selectionLength = computed(() => selection.value.end - selection.value.start)
const selectionTooLong = computed(() => selectionLength.value > maxRewriteSelection)
const versionOptions = computed(() => (draft.value?.versions ?? []).map((row, index, all) => ({
  value: row.id,
  label: '第 ' + (all.length - index) + ' 版',
})))
const showVersionSelect = computed(() => versionOptions.value.length > 1)
const exportHref = computed(() => {
  if (!draft.value || !version.value) return undefined
  return '/api/drafts/' + draft.value.id + '/versions/' + version.value.version.id + '/export'
})
const rewriteLabels: Record<RewriteAction, string> = { POLISH: '润色', SHORTEN: '缩写', TONE: '换语气' }

watch(busy, value => emit('busy', value))
watch([changed, hasUnsavedInput], ([edited, typed]) => emit('dirty', edited || typed))

function documentText(text: string) {
  return projectId === null ? text : text.replace(/【资料\d+】/g, '')
}

function goBack() {
  if (props.backLabel || props.inline) emit('back')
  else if (projectId === null) emit('experts')
  else emit('project', projectId)
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
    angle: writingExpertPrompt(expert),
    format: 'ARTICLE',
    durationSeconds: null,
  }
}

function showVersion(next: DraftVersion | null) {
  version.value = next
  content.value = savedText.value
  selection.value = { start: 0, end: 0 }
}

function setDraft(next: Manuscript | null) {
  draft.value = next
  showVersion(next?.currentVersion ?? null)
  if (next) conversationId.value = next.conversationId
}

async function generate(instruction?: string) {
  const message = instruction?.trim() || undefined
  const revising = message !== undefined
  if (projectId !== null || busy.value || showingOld.value || revising !== !!draft.value) return
  if (changed.value) {
    error.value = '先保存当前编辑，再生成新版本。'
    return
  }
  const kind = revising ? 'REGENERATE' : 'WRITE'
  const requestInput = revising ? undefined : writingInput()
  const signature = JSON.stringify({ kind, baseVersionId: latest.value, writingInput: requestInput, message })
  const request: GenerationRequest = retry?.signature === signature ? retry.request : {
    requestId: crypto.randomUUID(), kind, conversationId: conversationId.value,
    baseVersionId: latest.value, writingInput: requestInput, message,
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
    if (revising) {
      revision.value = ''
      void MessagePlugin.success('已改好并保存')
    }
    retry = undefined
    emit('saved', conversationId.value)
  } catch (failure) {
    error.value = controller.signal.aborted ? '已停止，原稿和输入已保留。' : errorMessage(failure)
    if (controller.signal.aborted || (failure instanceof ApiError && !(failure instanceof StreamInterrupted))) {
      retry = undefined
    }
    if (conversationId.value !== null) emit('saved', conversationId.value)
  } finally {
    partial.value = ''
    operation.value = undefined
    abort = undefined
  }
}

function handleRevisionKey(_value: TextareaValue, { e: event }: { e: KeyboardEvent }) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void generate(revision.value)
  }
}

function editDocument() {
  if (showingOld.value) showVersion(draft.value?.currentVersion ?? null)
  mode.value = 'edit'
  void nextTick(() => editor.value?.focus())
}

function cancelEdit() {
  showVersion(version.value)
  mode.value = 'preview'
  error.value = ''
}

async function save(text = content.value): Promise<boolean> {
  const id = conversationId.value
  if (id === null || busy.value || showingOld.value || !text.trim() || text === savedText.value) return false
  error.value = ''
  operation.value = 'save'
  try {
    const path = '/api/drafts?conversationId=' + id
    setDraft(await api<Manuscript>(path, jsonBody({ content: text, baseVersionId: latest.value })))
    retry = undefined
    mode.value = 'preview'
    emit('saved', id)
    void MessagePlugin.success(projectId === null ? '已保存新版本' : '修改已保存')
    return true
  } catch (failure) {
    error.value = errorMessage(failure)
    return false
  } finally {
    operation.value = undefined
  }
}

function editTitle() {
  if (!version.value || showingOld.value || busy.value) return
  title.value = version.value.version.title
  titleError.value = ''
  error.value = ''
  editingTitle.value = true
}

function closeTitle() {
  if (!busy.value) editingTitle.value = false
}

async function saveTitle() {
  if (busy.value) return
  const nextTitle = title.value.trim()
  if (!nextTitle) {
    titleError.value = '请输入稿件标题。'
    return
  }
  titleError.value = ''
  if (nextTitle === version.value?.version.title) {
    closeTitle()
    return
  }
  const heading = /^(?:[ \t]*\r?\n)*[ \t]{0,3}#{1,6}[ \t]+[^\r\n]*/
  const updated = heading.test(content.value)
    ? content.value.replace(heading, () => '# ' + nextTitle)
    : '# ' + nextTitle + '\n\n' + content.value
  if (await save(updated)) closeTitle()
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
    const text = preview.value?.innerText.trim() || content.value
    await navigator.clipboard.writeText(text)
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
  <section class="write-pane">
    <BackButton :label="backLabel ?? (projectId === null ? '返回专家库' : inline ? '返回分步创作' : '返回人物档案')"
      :disabled="busy" @back="goBack" />
    <div v-if="projectId === null" class="page-heading writer-heading">
      <t-avatar size="large" aria-hidden="true">
        <template #icon><component :is="expert.icon" /></template>
      </t-avatar>
      <div>
        <h1>{{ expert.name }}</h1>
        <p>{{ expert.description }}</p>
      </div>
    </div>
    <div v-else class="page-heading result-heading">
      <component :is="inline ? 'h2' : 'h1'">{{ initial?.conversation.title }}</component>
    </div>
    <div v-if="projectId === null && !draft" class="expert-panel">
      <t-card class="work-card" body-class-name="expert-composer">
        <label class="sr-only" for="writing-brief">写作要求或素材</label>
        <t-textarea v-field-id="'writing-brief'" v-model="brief" :placeholder="expert.example"
          :autosize="{ minRows: 4, maxRows: 9 }" :maxlength="maxBriefLength" :readonly="busy || showingOld" />
        <t-button class="writer-submit" :loading="operation === 'generate'" :disabled="busy || showingOld"
          @click="generate()">开始生成</t-button>
      </t-card>
    </div>
    <t-alert v-if="error && !editingTitle" theme="warning" :message="error" close @close="error = ''" />
    <div v-if="showEditor" class="writing-grid">
      <t-card class="work-card editor-card" body-class-name="editor-body" :aria-busy="busy">
        <div class="editor-heading">
          <div>
            <h2 ref="draftHeading">{{ projectId === null ? '稿件' : '成果正文' }}</h2>
            <span class="secondary-text">{{ showingOld ? '查看旧版' : changed ? '有未保存的修改' : draft ? '已保存' : '未保存' }}</span>
          </div>
          <label v-if="showVersionSelect" class="sr-only" :for="versionFieldId">查看稿件版本</label>
          <t-select v-if="showVersionSelect" v-field-id="versionFieldId" class="version-select"
            :options="versionOptions" :model-value="version?.version.id" :disabled="busy" @change="selectVersion" />
        </div>
        <div v-if="draft && version && operation !== 'generate'" class="result-actions">
          <template v-if="mode === 'edit'">
            <t-button :loading="operation === 'save'" :disabled="busy || !changed || !content.trim()" @click="save()">
              保存修改
            </t-button>
            <t-button variant="outline" theme="default" :disabled="busy" @click="cancelEdit">取消编辑</t-button>
          </template>
          <template v-else>
            <t-button :variant="projectId === null ? 'base' : 'outline'"
              :theme="projectId === null ? 'primary' : 'default'" :disabled="!content.trim() || busy" @click="copy">
              复制正文
            </t-button>
            <t-button variant="outline" theme="default" :disabled="busy" @click="editDocument">
              {{ showingOld ? '编辑最新版' : '编辑正文' }}
            </t-button>
            <t-button v-if="!showingOld" variant="text" theme="default" :disabled="busy" @click="editTitle">改标题</t-button>
            <t-button v-if="draft && version && (!inline || showSubtitles)" variant="outline" theme="default" :disabled="busy"
              @click="emit('subtitle', { draftId: draft.id, versionId: version.version.id, title: version.version.title })">
              给这版稿配字幕
            </t-button>
            <slot v-if="!showingOld" name="next" :disabled="busy || !content.trim()" />
            <t-link v-if="exportHref" :href="exportHref" theme="primary" title="下载所选版本的 Markdown 文件">
              下载稿件
            </t-link>
          </template>
        </div>
        <div v-if="operation === 'generate'" class="document-preview">
          <MarkdownView v-if="partial" :content="partial" />
          <t-loading v-else :text="draft ? '正在按要求改稿' : '正在起稿'" />
        </div>
        <template v-else>
          <textarea v-if="mode === 'edit'" ref="editor" v-model="content" class="document-editor"
            :aria-label="projectId === null ? '编辑稿件正文' : '编辑成果正文'" placeholder="在这里编辑正文。"
            maxlength="30000" :readonly="showingOld || busy" @select="readSelection" @input="readSelection"
            @keyup="readSelection" @mouseup="readSelection"></textarea>
          <div v-else ref="preview" class="document-preview">
            <MarkdownView v-if="content.trim()" :content="content" />
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
        <div v-if="operation === 'generate'" class="editor-footer">
          <t-button variant="outline" @click="abort?.abort()">停止</t-button>
        </div>
      </t-card>
    </div>
    <t-card v-if="projectId === null && draft && !showingOld && mode === 'preview'"
      class="work-card revision-panel" title="跟专家说怎么改" body-class-name="expert-composer">
      <label class="sr-only" :for="revisionFieldId">改稿要求</label>
      <t-textarea v-field-id="revisionFieldId" v-model="revision" :readonly="busy" :maxlength="maxBriefLength"
        :autosize="{ minRows: 2, maxRows: 6 }" placeholder="例如，多写点，或者把开头改得更口语一点。"
        @keydown="handleRevisionKey" />
      <t-button class="writer-submit" :loading="operation === 'generate'" :disabled="busy || !revision.trim()"
        @click="generate(revision)">发送</t-button>
    </t-card>
    <t-dialog :visible="editingTitle" header="修改稿件标题"
      :confirm-btn="{ content: '保存标题', loading: operation === 'save' }" cancel-btn="取消"
      :close-on-overlay-click="!busy" :close-on-esc-keydown="!busy"
      @close="closeTitle" @cancel="closeTitle" @confirm="saveTitle">
      <label class="sr-only" :for="titleFieldId">稿件标题</label>
      <t-input v-field-id="titleFieldId" v-model="title" :maxlength="120" :disabled="busy" @enter="saveTitle" />
      <t-alert v-if="titleError || error" theme="error" :message="titleError || error" />
    </t-dialog>
    <t-dialog :visible="rewrite !== null" :header="rewrite ? rewriteLabels[rewrite.action] + '选段' : '改写选段'"
      :confirm-btn="{ content: '应用到选区', disabled: rewrite?.result == null || busy }"
      cancel-btn="取消" @close="closeRewrite" @cancel="closeRewrite" @confirm="applyRewrite">
      <div v-if="rewrite" class="rewrite-dialog">
        <div v-if="rewrite.action === 'TONE' && rewrite.result === null" class="field">
          <label :for="toneFieldId">目标语气</label>
          <t-input v-field-id="toneFieldId" v-model="rewrite.tone" :maxlength="120"
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
