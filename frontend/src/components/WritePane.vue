<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { api, ApiError, errorMessage, jsonBody, loadConversation, streamGeneration, StreamInterrupted } from '../api'
import type {
  ConversationDetail, DraftVersion, GenerationRequest, Manuscript, ModelStatus,
  RewriteAction, TopicIdea, WritingInput,
} from '../types'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{ initial: ConversationDetail | null; model: ModelStatus }>()
const emit = defineEmits<{ saved: []; dirty: [value: boolean]; busy: [value: boolean] }>()

const conversationId = ref(props.initial?.conversation.id ?? null)
const draft = ref(props.initial?.draft ?? null)
const version = ref<DraftVersion | null>(draft.value?.currentVersion ?? null)
const content = ref(version.value?.content ?? '')
const form = reactive<WritingInput>(version.value?.writingInput ? { ...version.value.writingInput } : {
  topic: '', angle: '', audience: '', tone: '', durationSeconds: 150, format: 'TALK', background: '',
})
const mode = ref<'edit' | 'preview'>('edit')
const ideas = ref<TopicIdea[]>([])
const operation = ref<'generate' | 'ideas' | 'save' | 'read' | 'rewrite'>()
const partial = ref('')
const error = ref('')
const editor = ref<HTMLTextAreaElement>()
const selection = ref({ start: 0, end: 0 })
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
const latest = computed(() => draft.value?.currentVersion.version.id ?? null)
const showingOld = computed(() => !!version.value && version.value.version.id !== latest.value)
const changed = computed(() => content.value !== (version.value?.content ?? ''))
const hasInput = computed(() => !draft.value && !!(form.topic.trim() || form.background.trim()))
const hasSelection = computed(() => !!content.value.slice(selection.value.start, selection.value.end).trim())
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
watch([changed, hasInput], ([edited, typed]) => emit('dirty', edited || typed))

function input(): WritingInput {
  return {
    ...form,
    topic: form.topic.trim(),
    durationSeconds: form.format === 'TALK' && typeof form.durationSeconds === 'number'
      ? form.durationSeconds : null,
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
  if (busy.value || !props.model.available || showingOld.value) return
  if (changed.value) {
    error.value = '先保存当前编辑，再生成新版本。'
    return
  }
  const requestInput = kind === 'WRITE' ? input() : undefined
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

async function findIdeas() {
  if (busy.value || !props.model.available) return
  operation.value = 'ideas'
  error.value = ''
  try {
    const result = await api<{ ideas: TopicIdea[] }>('/api/writing/ideas', jsonBody(input()))
    ideas.value = result.ideas
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

function chooseIdea(idea: TopicIdea) {
  if (busy.value || showingOld.value) return
  form.topic = idea.title
  form.angle = idea.angle
  if (!form.audience.trim()) form.audience = idea.audience
  void generate()
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
  if (busy.value || !props.model.available || !draft.value || showingOld.value || !hasSelection.value) return
  const { start, end } = selection.value
  rewrite.value = { action, start, end, document: content.value, tone: '', result: null, error: '' }
  if (action !== 'TONE') void requestRewrite()
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
      before: selected.document.slice(Math.max(0, selected.start - 1200), selected.start),
      after: selected.document.slice(selected.end, selected.end + 1200),
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

onBeforeUnmount(() => abort?.abort())
</script>

<template>
  <section class="write-pane">
    <div class="page-heading">
      <h1>写稿</h1>
      <p>输入一个主题，或让助手替你选题。也可以直接在稿件区动笔。</p>
    </div>
    <t-alert v-if="!model.available" theme="info" :message="model.message ?? '模型尚未就绪。'" />
    <t-alert v-if="error" theme="warning" :message="error" close @close="error = ''" />
    <div class="writing-grid">
      <div class="setup-column">
        <section class="work-card setup-card">
          <h2>稿件设置</h2>
          <div class="format-switch" role="group" aria-label="稿件文体">
            <button :class="{ selected: form.format === 'TALK' }" :aria-pressed="form.format === 'TALK'"
              :disabled="busy" @click="form.format = 'TALK'">短视频口播</button>
            <button :class="{ selected: form.format === 'ARTICLE' }" :aria-pressed="form.format === 'ARTICLE'"
              :disabled="busy" @click="form.format = 'ARTICLE'">图文稿</button>
          </div>
          <div class="field">
            <label for="topic">主题 <span>选填</span></label>
            <t-textarea id="topic" v-model="form.topic" placeholder="例如，第一次拍口播，怎么讲得自然一点？"
              :autosize="{ minRows: 3, maxRows: 5 }" :maxlength="300" :disabled="busy"
              @change="form.angle = ''" />
            <span v-if="form.angle" class="idea-angle">{{ form.angle }}</span>
          </div>
          <details class="writing-options">
            <summary>受众、语气和参考资料</summary>
            <div class="field">
              <label for="audience">目标受众</label>
              <t-input id="audience" v-model="form.audience" placeholder="例如，刚开始出镜的创作者"
                :maxlength="120" :disabled="busy" />
            </div>
            <div class="field">
              <label for="tone">语气</label>
              <t-input id="tone" v-model="form.tone" placeholder="例如，像跟朋友分享一个方法"
                :maxlength="120" :disabled="busy" />
            </div>
            <div v-if="form.format === 'TALK'" class="field">
              <label for="duration">口播时长（秒）</label>
              <t-input-number id="duration" v-model="form.durationSeconds" :min="15" :max="600" :step="15"
                :disabled="busy" />
            </div>
            <div class="field">
              <label for="background">参考资料</label>
              <t-textarea id="background" v-model="form.background" placeholder="粘贴希望用到的事实或文字素材"
                :maxlength="12000" :autosize="{ minRows: 3, maxRows: 6 }" :disabled="busy" />
            </div>
          </details>
          <div class="setup-actions">
            <t-button size="large" block :loading="operation === 'generate'"
              :disabled="busy || showingOld || !model.available" @click="generate()">
              {{ form.topic.trim() ? '按题生成' : '替我选题并写一篇' }}
            </t-button>
            <t-button size="large" block variant="outline" :loading="operation === 'ideas'"
              :disabled="busy || showingOld || !model.available" @click="findIdeas">先看三个方向</t-button>
          </div>
        </section>
        <section v-if="ideas.length" class="work-card ideas-card">
          <h2>选题方向</h2>
          <article v-for="idea in ideas" :key="idea.title" class="idea-row">
            <h3>{{ idea.title }}</h3>
            <p>{{ idea.angle }}</p>
            <p class="secondary-text">受众 · {{ idea.audience }}</p>
            <p class="secondary-text">收获 · {{ idea.takeaway }}</p>
            <t-button size="small" variant="text" :disabled="busy || showingOld" @click="chooseIdea(idea)">用这个选题写稿</t-button>
          </article>
        </section>
      </div>
      <section class="work-card editor-card" :aria-busy="busy">
        <div class="editor-heading">
          <div>
            <h2>稿件</h2>
            <span class="secondary-text">{{ showingOld ? '查看旧版' : changed ? '有未保存的修改' : draft ? '已保存' : '未保存' }}</span>
          </div>
          <t-select v-if="versionOptions.length" class="version-select" aria-label="查看稿件版本"
            :options="versionOptions" :model-value="version?.version.id" :disabled="busy" @change="selectVersion" />
        </div>
        <div class="editor-tabs" role="group" aria-label="稿件视图">
          <button :class="{ selected: mode === 'edit' }" :aria-pressed="mode === 'edit'"
            :disabled="operation === 'generate'" @click="mode = 'edit'">编辑</button>
          <button :class="{ selected: mode === 'preview' }" :aria-pressed="mode === 'preview'"
            @click="mode = 'preview'">预览</button>
        </div>
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
            <MarkdownView v-if="content.trim()" :content="content" />
            <p v-else class="secondary-text">稿件还没有内容。</p>
          </div>
        </template>
        <div v-if="mode === 'edit' && hasSelection && draft && !showingOld" class="rewrite-actions">
          <span>改写选中内容</span>
          <t-button v-for="(label, action) in rewriteLabels" :key="action" size="small" variant="outline"
            :disabled="busy || !model.available" @click="startRewrite(action)">{{ label }}</t-button>
        </div>
        <div class="editor-footer">
          <t-button v-if="operation === 'generate'" variant="outline" @click="abort?.abort()">停止</t-button>
          <template v-else>
            <t-button v-if="!showingOld" :loading="operation === 'save'"
              :disabled="busy || !changed || !content.trim()" @click="save">保存版本</t-button>
            <t-button v-if="!showingOld && version?.writingInput" variant="outline"
              :disabled="busy || !model.available" @click="generate('REGENERATE')">重新生成</t-button>
            <t-button variant="text" theme="default" :disabled="!content.trim() || busy" @click="copy">复制</t-button>
            <a v-if="exportHref" class="export-link" :href="exportHref" title="导出所选的已保存版本">导出 Markdown</a>
          </template>
        </div>
      </section>
    </div>
    <t-dialog :visible="rewrite !== null" :header="rewrite ? rewriteLabels[rewrite.action] + '选段' : '改写选段'"
      :confirm-btn="{ content: '应用到选区', disabled: rewrite?.result == null || busy }"
      cancel-btn="取消" @close="closeRewrite" @cancel="closeRewrite" @confirm="applyRewrite">
      <div v-if="rewrite" class="rewrite-dialog">
        <div v-if="rewrite.action === 'TONE' && rewrite.result === null" class="field">
          <label for="rewrite-tone">目标语气</label>
          <t-input id="rewrite-tone" v-model="rewrite.tone" :maxlength="120"
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
