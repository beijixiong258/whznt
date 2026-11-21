<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { ChatIcon, Edit1Icon, LogoutIcon, TimeIcon, UserIcon, VideoIcon, WalletIcon } from 'tdesign-icons-vue-next'
import { api, errorMessage, loadConversation } from '../api'
import type { DraftReference } from '../subtitles'
import type { Account, ConversationDetail, ModelStatus } from '../types'
import ChatPane from './ChatPane.vue'
import HistoryPane from './HistoryPane.vue'
import WritePane from './WritePane.vue'

const ProjectsPane = defineAsyncComponent(() => import('./ProjectsPane.vue'))
const SubtitlePane = defineAsyncComponent(() => import('./SubtitlePane.vue'))
const RechargePane = defineAsyncComponent(() => import('./RechargePane.vue'))

const navigation = [
  { id: 'write', label: '写稿', icon: Edit1Icon },
  { id: 'chat', label: '自由对话', icon: ChatIcon },
  { id: 'projects', label: '人物项目', icon: UserIcon },
  { id: 'subtitles', label: '视频字幕', icon: VideoIcon },
  { id: 'history', label: '历史', icon: TimeIcon },
  { id: 'recharge', label: '充值申请', icon: WalletIcon },
] as const
type Pane = typeof navigation[number]['id']

const props = defineProps<{ account: Account }>()
const emit = defineEmits<{ logout: [] }>()
const pane = ref<Pane>('write')
const opened = reactive(new Set<Pane>())
const writing = ref<ConversationDetail | null>(null)
const chatting = ref<ConversationDetail | null>(null)
const writeKey = ref(0)
const initialVersion = ref<number>()
const projectId = ref<number | null>(null)
const subtitleReference = ref<DraftReference>()
const chatKey = ref(0)
const historyKey = ref(0)
const dirty = reactive({ write: false, chat: false })
const busy = reactive({ write: false, chat: false })
const model = ref<ModelStatus>({ available: false, message: '正在读取模型配置。' })
const modelLoaded = ref(false)
const alert = ref('')
const opening = ref(false)
const pendingAction = ref<(() => void) | null>(null)

const phoneLabel = computed(() => props.account.phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2'))
const anyBusy = computed(() => busy.write || busy.chat)
const paneLabel = computed(() => navigation.find(item => item.id === pane.value)?.label)

function openPane(next: Pane) {
  opened.add(next)
  pane.value = next
}

async function loadModel() {
  try {
    model.value = await api<ModelStatus>('/api/writing/model-status')
  } catch (failure) {
    model.value = { available: false, message: errorMessage(failure) }
  } finally {
    modelLoaded.value = true
  }
}

function whileIdle(action: () => void) {
  if (anyBusy.value || opening.value) {
    alert.value = '请先等待当前操作完成。'
    return
  }
  alert.value = ''
  action()
}

function confirmDiscard(mode: 'write' | 'chat' | 'both', action: () => void) {
  const hasChanges = mode === 'both' ? dirty.write || dirty.chat : dirty[mode]
  if (hasChanges) {
    pendingAction.value = action
  } else {
    action()
  }
}

function discard() {
  const action = pendingAction.value
  pendingAction.value = null
  action?.()
}

function startNew(kind: 'write' | 'chat') {
  whileIdle(() => confirmDiscard(kind, () => {
    if (kind === 'write') {
      writing.value = null
      initialVersion.value = undefined
      writeKey.value++
    } else {
      chatting.value = null
      chatKey.value++
    }
    dirty[kind] = false
    openPane(kind)
  }))
}

function openHistory(id: number, kind: 'WRITING' | 'CHAT', version?: number) {
  const target = kind === 'WRITING' ? 'write' : 'chat'
  whileIdle(() => confirmDiscard(target, () => void openConversation(id, version)))
}

function openProjects(id: number) {
  projectId.value = id
  openPane('projects')
}

function openSubtitles(reference: DraftReference) {
  subtitleReference.value = reference
  openPane('subtitles')
}

async function openConversation(id: number, version?: number) {
  opening.value = true
  try {
    const detail = await loadConversation(id)
    if (detail.conversation.kind === 'WRITING') {
      writing.value = detail
      initialVersion.value = version
      writeKey.value++
      dirty.write = false
      openPane('write')
    } else {
      chatting.value = detail
      chatKey.value++
      dirty.chat = false
      openPane('chat')
    }
  } catch (failure) {
    alert.value = errorMessage(failure)
  } finally {
    opening.value = false
  }
}

function logout() {
  whileIdle(() => confirmDiscard('both', () => {
    void api<void>('/api/auth/logout', { method: 'POST' })
      .then(() => emit('logout'))
      .catch(failure => { alert.value = errorMessage(failure) })
  }))
}

onMounted(() => void loadModel())
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="brand-lockup"><img class="brand-mark" src="/brand.png" alt="" width="44" height="44">网红内容生成平台</div>
      <nav class="rail-nav" aria-label="工作台">
        <t-button v-for="item in navigation" :key="item.id" size="large"
          :variant="pane === item.id ? 'base' : 'text'" :theme="pane === item.id ? 'primary' : 'default'"
          :aria-current="pane === item.id ? 'page' : undefined" @click="openPane(item.id)">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </t-button>
      </nav>
      <div class="rail-footer">
        <span class="account-label">{{ phoneLabel }}</span>
        <t-button variant="text" theme="default" shape="square" aria-label="退出登录" title="退出登录" @click="logout">
          <template #icon><LogoutIcon /></template>
        </t-button>
      </div>
    </aside>

    <main class="work-area">
      <header class="top-bar">
        <div class="breadcrumb">{{ paneLabel }}</div>
        <div class="top-actions">
          <t-tag variant="light" :theme="!modelLoaded ? 'default' : model.available ? 'success' : 'warning'"
            :title="model.message ?? '可以使用 AI 创作'">
            {{ !modelLoaded ? '读取模型状态' : model.available ? '模型已配置' : '模型待配置' }}
          </t-tag>
          <t-button v-if="pane === 'write' || pane === 'chat'" size="small" variant="outline" theme="default"
            @click="startNew(pane === 'write' ? 'write' : 'chat')">
            {{ pane === 'write' ? '新写稿' : '新对话' }}
          </t-button>
        </div>
      </header>

      <div class="page-content">
        <t-alert v-if="alert" class="page-alert" theme="warning" :message="alert" close
          @close="alert = ''" />
        <WritePane v-show="pane === 'write'" :key="writeKey" :initial="writing" :initial-version="initialVersion" :model="model"
          @saved="historyKey++" @dirty="dirty.write = $event" @busy="busy.write = $event"
          @project="openProjects" @upstream="(id, version) => openHistory(id, 'WRITING', version)" @subtitle="openSubtitles" />
        <ChatPane v-show="pane === 'chat'" :key="chatKey" :initial="chatting" :model="model"
          @saved="historyKey++" @dirty="dirty.chat = $event" @busy="busy.chat = $event" />
        <HistoryPane v-if="pane === 'history'" :key="historyKey" :opening="opening" @open="openHistory" />
        <ProjectsPane v-if="opened.has('projects')" v-show="pane === 'projects'" :active="pane === 'projects'"
          :model="model" :requested-id="projectId" @open="(id, version) => openHistory(id, 'WRITING', version)" />
        <SubtitlePane v-if="opened.has('subtitles')" v-show="pane === 'subtitles'" :active="pane === 'subtitles'"
          :requested-reference="subtitleReference" />
        <RechargePane v-if="opened.has('recharge')" v-show="pane === 'recharge'" :active="pane === 'recharge'" />
      </div>
    </main>

    <t-dialog :visible="pendingAction !== null" header="离开当前内容？"
      body="当前还有未保存的内容。离开后会丢失这部分修改。" :confirm-btn="{ content: '仍然离开', theme: 'danger' }"
      cancel-btn="继续编辑" @close="pendingAction = null" @cancel="pendingAction = null" @confirm="discard" />
  </div>
</template>
