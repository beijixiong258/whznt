<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ChatIcon, Edit1Icon, TimeIcon } from 'tdesign-icons-vue-next'
import { Empty as TEmpty, List as TList, ListItem as TListItem } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody, loadConversations } from '../api'
import type { ConversationKind, ConversationRow } from '../types'

defineProps<{ opening: boolean }>()
const emit = defineEmits<{ open: [id: number, kind: ConversationKind]; renamed: [] }>()

const query = ref('')
const currentQuery = ref('')
const page = ref(1)
const items = ref<ConversationRow[]>([])
const hasMore = ref(false)
const pending = ref(false)
const renaming = ref<ConversationRow | null>(null)
const title = ref('')
const renameError = ref('')
const saving = ref(false)
const error = ref('')
const formatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

async function fetchPage(index: number) {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try {
    const result = await loadConversations(currentQuery.value, index)
    items.value = index === 1 ? result.items : [...items.value, ...result.items]
    page.value = index
    hasMore.value = result.hasMore
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    pending.value = false
  }
}

function search() {
  currentQuery.value = query.value.trim()
  void fetchPage(1)
}

function openRename(row: ConversationRow) {
  renaming.value = row
  title.value = row.title
  renameError.value = ''
}

function closeRename() {
  if (!saving.value) renaming.value = null
}

async function rename() {
  if (!renaming.value || saving.value) return
  if (!title.value.trim()) {
    renameError.value = '请输入标题。'
    return
  }
  saving.value = true
  renameError.value = ''
  try {
    await api<ConversationRow>('/api/conversations/' + renaming.value.id, jsonBody({ title: title.value }, 'PATCH'))
    renaming.value = null
    emit('renamed')
    await fetchPage(1)
  } catch (failure) {
    renameError.value = errorMessage(failure)
  } finally {
    saving.value = false
  }
}

onMounted(() => void fetchPage(1))
</script>

<template>
  <section class="history-pane">
    <div class="page-heading"><h1>历史</h1></div>
    <form class="history-search" @submit.prevent="search">
      <label class="sr-only" for="history-search">搜索会话标题</label>
      <t-input v-field-id="'history-search'" v-model="query" clearable placeholder="搜索会话标题"
        :maxlength="120" :disabled="pending" />
      <t-button type="submit" :loading="pending && page === 1">搜索</t-button>
    </form>
    <t-alert v-if="error" theme="error" :message="error" />
    <t-list v-if="items.length" class="history-list" split :aria-busy="opening || pending">
      <t-list-item v-for="row in items" :key="row.id">
        <div class="history-row">
          <span class="history-icon" aria-hidden="true"><Edit1Icon v-if="row.kind === 'WRITING'" /><ChatIcon v-else /></span>
          <t-button class="history-name" variant="text" theme="default" :disabled="opening"
            @click="emit('open', row.id, row.kind)">
            <strong>{{ row.title }}</strong>
            <span class="secondary-text">
              {{ row.kind === 'WRITING' ? '稿件' : '对话' }} · {{ formatter.format(new Date(row.updatedAt)) }}
            </span>
          </t-button>
          <t-button size="small" variant="text" theme="default" :disabled="opening" @click="openRename(row)">改名</t-button>
        </div>
      </t-list-item>
    </t-list>
    <t-empty v-else-if="!pending && !error" class="empty-history"
      :description="currentQuery ? '没有找到匹配的标题。' : '保存稿件或开始对话后，记录会显示在这里。'">
      <template #image><TimeIcon aria-hidden="true" /></template>
    </t-empty>
    <t-loading v-else-if="pending" text="正在读取历史" />
    <t-button v-if="hasMore" variant="outline" class="load-more" :loading="pending" @click="fetchPage(page + 1)">
      加载更多
    </t-button>

    <t-dialog :visible="renaming !== null" header="修改会话标题" :confirm-btn="{ content: '保存', loading: saving }"
      @cancel="closeRename" @close="closeRename" @confirm="rename">
      <label for="history-title">标题</label>
      <t-input v-field-id="'history-title'" v-model="title" class="dialog-input" :maxlength="120" @enter="rename" />
      <t-alert v-if="renameError" theme="error" :message="renameError" />
    </t-dialog>
  </section>
</template>
