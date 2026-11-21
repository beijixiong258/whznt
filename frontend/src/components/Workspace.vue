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

type Pane = 'write' | 'chat' | 'history' | 'projects' | 'subtitles' | 'recharge'

const props = defineProps<{ account: Account }>()
const emit = defineEmits<{ logout: [] }>()
const pane = ref<Pane>('write')
const writing = ref<ConversationDetail | null>(null)
const chatting = ref<ConversationDetail | null>(null)
const writeKey = ref(0)
const initialVersion = ref<number>()
const projectId = ref<number | null>(null)
const projectsOpened = ref(false)
const subtitlesOpened = ref(false)
const rechargeOpened = ref(false)
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
const paneLabels: Record<Pane, string> = {
  write: '写稿', chat: '自由对话', history: '历史', projects: '人物项目', subtitles: '视频字幕', recharge: '充值申请',
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
    pane.value = kind
  }))
}

function openHistory(id: number, kind: 'WRITING' | 'CHAT', version?: number) {
  const target = kind === 'WRITING' ? 'write' : 'chat'
  whileIdle(() => confirmDiscard(target, () => void openConversation(id, version)))
}

function openProjects(id?: number) {
  if (id !== undefined) projectId.value = id
  projectsOpened.value = true
  pane.value = 'projects'
}

function openSubtitles(reference?: DraftReference) {
  if (reference) subtitleReference.value = reference
  subtitlesOpened.value = true
  pane.value = 'subtitles'
}

function openRecharge() {
  rechargeOpened.value = true
  pane.value = 'recharge'
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
      pane.value = 'write'
    } else {
      chatting.value = detail
      chatKey.value++
      dirty.chat = false
      pane.value = 'chat'
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
      <div class="brand-lockup"><img class="brand-mark" src="/brand.png" alt="" width="44" height="44">创作工作台</div>
      <nav class="rail-nav" aria-label="工作台">
        <button :class="{ active: pane === 'write' }" :aria-current="pane === 'write' ? 'page' : undefined"
          @click="pane = 'write'"><Edit1Icon /> 写稿</button>
        <button :class="{ active: pane === 'chat' }" :aria-current="pane === 'chat' ? 'page' : undefined"
          @click="pane = 'chat'"><ChatIcon /> 自由对话</button>
        <button :class="{ active: pane === 'projects' }" :aria-current="pane === 'projects' ? 'page' : undefined"
          @click="openProjects()"><UserIcon /> 人物项目</button>
        <button :class="{ active: pane === 'subtitles' }" :aria-current="pane === 'subtitles' ? 'page' : undefined"
          @click="openSubtitles()"><VideoIcon /> 视频字幕</button>
        <button :class="{ active: pane === 'history' }" :aria-current="pane === 'history' ? 'page' : undefined"
          @click="pane = 'history'"><TimeIcon /> 历史</button>
        <button :class="{ active: pane === 'recharge' }" :aria-current="pane === 'recharge' ? 'page' : undefined"
          @click="openRecharge"><WalletIcon /> 充值申请</button>
      </nav>
      <div class="rail-footer">
        <span class="account-label">{{ phoneLabel }}</span>
        <button class="logout-button" aria-label="退出登录" title="退出登录" @click="logout"><LogoutIcon /></button>
      </div>
    </aside>

    <main class="work-area">
      <header class="top-bar">
        <div class="breadcrumb">{{ paneLabels[pane] }}</div>
        <div class="top-actions">
          <span class="model-state" :class="{ ready: model.available }" :title="model.message ?? '可以使用 AI 创作'">
            <i aria-hidden="true"></i>{{ !modelLoaded ? '读取模型状态' : model.available ? '模型已配置' : '模型待配置' }}
          </span>
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
        <ProjectsPane v-if="projectsOpened" v-show="pane === 'projects'" :active="pane === 'projects'"
          :model="model" :requested-id="projectId" @open="(id, version) => openHistory(id, 'WRITING', version)" />
        <SubtitlePane v-if="subtitlesOpened" v-show="pane === 'subtitles'" :active="pane === 'subtitles'"
          :requested-reference="subtitleReference" />
        <RechargePane v-if="rechargeOpened" v-show="pane === 'recharge'" :active="pane === 'recharge'" />
      </div>
    </main>

    <t-dialog :visible="pendingAction !== null" header="离开当前内容？"
      body="当前还有未保存的内容。离开后会丢失这部分修改。" :confirm-btn="{ content: '仍然离开', theme: 'danger' }"
      cancel-btn="继续编辑" @close="pendingAction = null" @cancel="pendingAction = null" @confirm="discard" />
  </div>
</template>
