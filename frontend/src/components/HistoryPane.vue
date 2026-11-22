<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { AddIcon, ChatIcon, Edit1Icon, SearchIcon, TimeIcon } from 'tdesign-icons-vue-next'
import { Empty as TEmpty } from 'tdesign-vue-next'
import { errorMessage, loadConversations } from '../api'
import type { ConversationAction, ConversationKind, ConversationRow, HistoryItem } from '../types'
import { writingExperts } from '../writingExperts'
import BackButton from './BackButton.vue'
import ConversationActions from './ConversationActions.vue'

const props = defineProps<{ opening: boolean; revision: number; kind: ConversationKind }>()
const emit = defineEmits<{
  open: [id: number, kind: ConversationKind]; action: [action: ConversationAction, row: ConversationRow]
  back: []; create: []
}>()

const drafts = computed(() => props.kind === 'WRITING')
const heading = computed(() => drafts.value ? '我的稿件' : '历史对话')
const searchLabel = computed(() => drafts.value ? '搜索稿件标题' : '搜索对话标题')
const searchFieldId = useId()
const assistants = ['', ...writingExperts.map(expert => expert.name), '人物故事与定位', '手动写稿']
  .map(value => ({ value, label: value || '全部助手' }))
const times = [
  { value: 0, label: '全部时间' },
  { value: 1, label: '今天' },
  { value: 7, label: '最近 7 天' },
  { value: 30, label: '最近 30 天' },
]
const formatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
const relative = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' })
const query = ref('')
const currentQuery = ref('')
const assistant = ref('')
const days = ref(0)
const page = ref(1)
const items = ref<HistoryItem[]>([])
const hasMore = ref(false)
const pending = ref(false)
const error = ref('')
let activeRequest: AbortController | undefined

function since(days: number) {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() - days + 1)
  return date.toISOString()
}

function timeLabel(updatedAt: string) {
  const elapsed = Math.max(0, Date.now() - Date.parse(updatedAt))
  if (elapsed < 60_000) return '刚刚'
  if (elapsed < 3_600_000) return relative.format(-Math.floor(elapsed / 60_000), 'minute')
  if (elapsed < 86_400_000) return relative.format(-Math.floor(elapsed / 3_600_000), 'hour')
  return formatter.format(new Date(updatedAt))
}

function previewText(preview: string) {
  const body = drafts.value ? preview.replace(/^[ \t]{0,3}#{1,6}[ \t]+[^\r\n]*/, '') : preview
  return body.replace(/^[ \t]{0,3}#{1,6}[ \t]+/gm, '').trim()
}

async function fetchPage(index: number) {
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  pending.value = true
  error.value = ''
  try {
    const filters = { kind: props.kind, q: currentQuery.value, assistant: assistant.value, page: index }
    const result = await loadConversations(days.value ? { ...filters, since: since(days.value) } : filters, request.signal)
    if (request.signal.aborted) return
    items.value = index === 1 ? result.items : [...items.value, ...result.items]
    page.value = index
    hasMore.value = result.hasMore
  } catch (failure) {
    if (!request.signal.aborted) error.value = errorMessage(failure)
  } finally {
    if (activeRequest === request) {
      pending.value = false
      activeRequest = undefined
    }
  }
}

function search() {
  currentQuery.value = query.value.trim()
  items.value = []
  hasMore.value = false
  void fetchPage(1)
}

function clearQuery() {
  query.value = ''
  search()
}

watch(() => props.revision, () => void fetchPage(1))
onMounted(() => void fetchPage(1))
onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <section class="history-pane" :aria-label="heading">
    <BackButton :label="drafts ? '返回专家库' : '返回对话'" @back="emit('back')" />
    <header class="history-heading">
      <h1>{{ heading }}</h1>
      <t-button variant="outline" @click="emit('create')">
        <template #icon><AddIcon /></template>
        {{ drafts ? '选专家写稿' : '新对话' }}
      </t-button>
    </header>
    <div class="history-filters" :class="{ 'history-filters-with-assistant': drafts }" role="search">
      <label class="sr-only" :for="searchFieldId">{{ searchLabel }}</label>
      <t-input v-field-id="searchFieldId" v-model="query" clearable :placeholder="searchLabel"
        :maxlength="120" @enter="search" @clear="clearQuery">
        <template #prefix-icon><SearchIcon /></template>
      </t-input>
      <t-select v-if="drafts" v-model="assistant" :options="assistants" aria-label="筛选稿件助手" @change="search" />
      <t-select v-model="days" :options="times" aria-label="筛选时间" @change="search" />
    </div>

    <t-alert v-if="error" theme="error" :message="error" />
    <ul v-if="items.length" class="history-list" :aria-busy="opening || pending">
      <li v-for="row in items" :key="row.id" class="history-card">
        <button type="button" class="history-card-open" :disabled="opening" @click="emit('open', row.id, row.kind)">
          <span class="history-icon" aria-hidden="true"><Edit1Icon v-if="row.kind === 'WRITING'" /><ChatIcon v-else /></span>
          <span class="history-summary">
            <strong>{{ row.title }}</strong>
            <span class="history-meta">
              {{ row.assistant }} · {{ row.entryCount }} {{ row.kind === 'CHAT' ? '条消息' : '个版本' }}
            </span>
            <span v-if="row.preview" class="history-preview">{{ previewText(row.preview) }}</span>
          </span>
        </button>
        <time :datetime="row.updatedAt" :title="formatter.format(new Date(row.updatedAt))">{{ timeLabel(row.updatedAt) }}</time>
        <ConversationActions :title="row.title" :disabled="opening" :draft="drafts" @action="emit('action', $event, row)" />
      </li>
    </ul>
    <t-loading v-else-if="pending" :text="drafts ? '正在读取稿件' : '正在读取历史对话'" />
    <t-empty v-else-if="!error" class="empty-history"
      :title="currentQuery || assistant || days
        ? (drafts ? '未找到相关稿件' : '未找到相关对话')
        : (drafts ? '还没有稿件' : '还没有对话记录')">
      <template #image><TimeIcon aria-hidden="true" /></template>
      <template v-if="!currentQuery && !assistant && !days" #action>
        <t-button variant="outline" @click="emit('create')">{{ drafts ? '选专家写稿' : '开始对话' }}</t-button>
      </template>
    </t-empty>
    <t-button v-if="hasMore" variant="outline" class="load-more" :loading="pending" @click="fetchPage(page + 1)">
      加载更多
    </t-button>
  </section>
</template>
