<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Card as TCard, RadioButton as TRadioButton, RadioGroup as TRadioGroup, Upload as TUpload } from 'tdesign-vue-next'
import type { UploadFile } from 'tdesign-vue-next'
import { api, errorMessage } from '../api'
import { modeLabels, pending, stateLabels, subtitlePath } from '../subtitles'
import type { DraftReference, SubtitleDetail, SubtitleSummary } from '../subtitles'
import SubtitleEditor from './SubtitleEditor.vue'

const props = defineProps<{ active: boolean; requestedReference?: DraftReference }>()
const all = ref<SubtitleSummary[]>([])
const current = ref<SubtitleDetail | null>(null)
const loading = ref(false)
const busy = ref(false)
const reading = ref(false)
const error = ref('')
const videos = ref<UploadFile[]>([])
const files = ref<UploadFile[]>([])
const script = ref('')
const reference = ref<DraftReference>()
const mode = ref<SubtitleSummary['mode']>('AUTO')
const editorState = ref({ dirty: false, busy: false })
const pendingTaskChange = ref<(() => void) | null>(null)
const modeOptions = Object.entries(modeLabels)
const pollNeeded = computed(() => all.value.some(task => pending(task.state) || pending(task.exportState)))

function apply(detail: SubtitleDetail) {
  if (detail.task.id !== current.value?.task.id) editorState.value = { dirty: false, busy: false }
  current.value = detail
  const list = all.value.filter(task => task.id !== detail.task.id)
  all.value = [detail.task, ...list].sort((a, b) => b.id - a.id)
}

function begin() {
  current.value = null
  editorState.value = { dirty: false, busy: false }
  mode.value = 'AUTO'
  reference.value = undefined
  videos.value = []
  files.value = []
  script.value = ''
  error.value = ''
}

function changeTask(action: () => void) {
  if (editorState.value.busy || busy.value || reading.value) {
    error.value = '请等待当前操作完成。'
  } else if (editorState.value.dirty) {
    pendingTaskChange.value = action
  } else {
    action()
  }
}

function discardEdits() {
  const action = pendingTaskChange.value
  pendingTaskChange.value = null
  action?.()
}

watch(() => props.requestedReference, value => {
  if (!value) return
  changeTask(() => {
    begin()
    mode.value = 'ALIGN'
    reference.value = value
  })
}, { immediate: true })

async function refresh() {
  const selected = current.value
  all.value = await api<SubtitleSummary[]>('/api/subtitles')
  if (selected && current.value?.task.id === selected.task.id && !busy.value && !reading.value) {
    const summary = all.value.find(task => task.id === selected.task.id)
    if (summary && (summary.state !== selected.task.state || summary.exportState !== selected.task.exportState)) {
      const detail = await api<SubtitleDetail>(subtitlePath(selected.task.id))
      if (current.value?.task.id === selected.task.id && !busy.value && !reading.value) apply(detail)
    }
  }
}

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    await refresh()
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}

async function open(task: SubtitleSummary) {
  if (busy.value || reading.value) return
  reading.value = true
  error.value = ''
  try {
    apply(await api<SubtitleDetail>(subtitlePath(task.id)))
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    reading.value = false
  }
}

async function create() {
  if (busy.value) return
  const video = videos.value[0]?.raw
  const srt = files.value[0]?.raw
  if (!video || (mode.value === 'IMPORT' && !srt)
    || (mode.value === 'ALIGN' && !reference.value && !script.value.trim())) {
    error.value = !video ? '请选择 MP4 视频。' : mode.value === 'IMPORT' ? '请选择 SRT 文件。' : '请粘贴逐字稿。'
    return
  }
  const body = new FormData()
  body.set('video', video)
  if (mode.value === 'IMPORT' && srt) body.set('srt', srt)
  if (mode.value === 'ALIGN') {
    const selected = reference.value
    if (selected) {
      body.set('draftId', String(selected.draftId))
      body.set('draftVersionId', String(selected.versionId))
    } else {
      body.set('script', script.value)
    }
  }
  busy.value = true
  error.value = ''
  try {
    apply(await api<SubtitleDetail>('/api/subtitles', { method: 'POST', body }))
    videos.value = []
    files.value = []
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

async function retry() {
  if (!current.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    apply(await api<SubtitleDetail>(subtitlePath(current.value.task.id) + '/retry', { method: 'POST' }))
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

let poll = 0
onMounted(() => {
  void load()
  poll = window.setInterval(() => {
    if (props.active && pollNeeded.value && !busy.value && !reading.value) void load()
  }, 2000)
})
watch(() => props.active, value => { if (value) void load() })
onBeforeUnmount(() => window.clearInterval(poll))
</script>

<template>
  <section class="utility-pane">
    <div class="page-heading project-heading">
      <h1>视频字幕</h1>
      <t-button v-if="current" variant="outline" :disabled="busy || reading || editorState.busy"
        @click="changeTask(begin)">新建字幕任务</t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <div class="utility-grid">
      <t-card title="字幕任务" :bordered="true">
        <t-loading v-if="loading && !all.length" text="读取任务" />
        <p v-else-if="!all.length" class="secondary-text">上传第一个视频后，任务会出现在这里。</p>
        <div v-for="task in all" :key="task.id" class="utility-row">
          <div>
            <strong>{{ task.name }}</strong>
            <p class="secondary-text">{{ modeLabels[task.mode] }}</p>
          </div>
          <t-tag variant="light" :theme="task.state === 'FAILED' ? 'danger' : task.state === 'READY' ? 'success' : 'primary'">
            {{ pending(task.exportState) ? '导出中' : stateLabels[task.state] }}
          </t-tag>
          <t-button variant="text" size="small" :disabled="reading || busy || editorState.busy || current?.task.id === task.id"
            :aria-label="'打开字幕任务 ' + task.name" @click="changeTask(() => void open(task))">打开</t-button>
        </div>
      </t-card>

      <t-card v-if="!current" title="新建字幕任务" :bordered="true">
        <t-form class="utility-form" :data="{ mode, script }" label-align="top" @submit="create">
          <t-form-item label="口播视频" required-mark>
            <t-upload v-model="videos" :auto-upload="false" :max="1" accept=".mp4"
              :disabled="busy" tips="H.264 编码的 MP4，不超过 512 MB" />
          </t-form-item>
          <t-form-item label="字幕来源">
            <t-radio-group v-model="mode" :disabled="busy" variant="default-filled" class="utility-choices">
              <t-radio-button v-for="[value, label] in modeOptions" :key="value" :value="value">{{ label }}</t-radio-button>
            </t-radio-group>
          </t-form-item>
          <t-form-item v-if="mode === 'ALIGN'" label="用于对齐的逐字稿" for="subtitle-script">
            <div v-if="reference" class="utility-inline">
              <span>{{ reference.title }}</span>
              <t-button variant="text" size="small" :disabled="busy" @click="reference = undefined">改为粘贴</t-button>
            </div>
            <t-textarea v-else v-field-id="'subtitle-script'" v-model="script" :maxlength="30000"
              :autosize="{ minRows: 4, maxRows: 10 }" :disabled="busy" placeholder="粘贴录制时参考的逐字稿" />
          </t-form-item>
          <t-form-item v-else-if="mode === 'IMPORT'" label="字幕文件" required-mark>
            <t-upload v-model="files" :auto-upload="false" :max="1" accept=".srt"
              :disabled="busy" tips="不超过 1 MB 的 SRT" />
          </t-form-item>
          <t-button type="submit" :loading="busy">{{ mode === 'IMPORT' ? '导入并校对' : '开始生成字幕' }}</t-button>
        </t-form>
      </t-card>

      <SubtitleEditor v-else-if="current.task.state === 'READY'" :key="current.task.id" :detail="current"
        @changed="apply" @edit-state="editorState = $event" />

      <t-card v-else :title="current.task.name" :bordered="true">
        <t-alert v-if="current.task.state === 'FAILED'" theme="error" :message="current.error" />
        <p v-else class="secondary-text">{{ stateLabels[current.task.state] }}，可以返回工作台继续写稿。</p>
        <t-button v-if="current.task.state === 'FAILED'" class="task-action" :loading="busy" @click="retry">重新识别</t-button>
      </t-card>
    </div>
    <t-dialog :visible="pendingTaskChange !== null" header="放弃当前字幕改动？"
      body="时间轴还有未保存的改动。继续打开其他任务将丢弃这些改动。"
      :confirm-btn="{ content: '放弃改动', theme: 'danger' }" cancel-btn="继续校对"
      @confirm="discardEdits" @cancel="pendingTaskChange = null" @close="pendingTaskChange = null" />
  </section>
</template>
