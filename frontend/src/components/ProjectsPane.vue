<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { AddIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, MessagePlugin, Tabs as TTabs, TabPanel as TTabPanel } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody } from '../api'
import { projectPath, running } from '../projects'
import type { PersonProject, ProjectDetail } from '../projects'
import ProjectFlow from './ProjectFlow.vue'
import ProjectMaterials from './ProjectMaterials.vue'

const props = defineProps<{ active: boolean; requestedProject?: { id: number } }>()
const emit = defineEmits<{
  open: [conversationId: number, versionId?: number]; deleted: [projectId: number]; historyChanged: []
}>()
const projects = ref<PersonProject[]>([])
const detail = ref<ProjectDetail | null>(null)
const activeId = ref<number | null>(null)
const tab = ref<'flow' | 'materials'>('flow')
const loading = ref(false)
const error = ref('')
const editing = ref<'new' | 'edit' | null>(null)
const deleting = ref<PersonProject | null>(null)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ name: '', summary: '' })
const options = computed(() => projects.value.map(row => ({ label: row.name, value: row.id })))
const hasJobs = computed(() => detail.value?.jobs.some(running) ?? false)
let polling: ReturnType<typeof setInterval> | undefined
let refreshRequest: AbortController | undefined

async function loadProfiles() {
  try {
    projects.value = await api<PersonProject[]>('/api/projects')
    const requested = props.requestedProject?.id ?? activeId.value
    const initial = projects.value.find(project => project.id === requested)?.id ?? projects.value[0]?.id
    if (initial) await open(initial)
  } catch (failure) {
    error.value = errorMessage(failure)
  }
}

async function open(id: unknown) {
  if (typeof id !== 'number' || !Number.isSafeInteger(id)) return
  if (activeId.value !== id) detail.value = null
  activeId.value = id
  error.value = ''
  await refresh()
}

async function refresh(quiet = false) {
  const id = activeId.value
  if (id === null || (quiet && refreshRequest)) return
  refreshRequest?.abort()
  const request = new AbortController()
  refreshRequest = request
  if (!quiet) loading.value = true
  try {
    const result = await api<ProjectDetail>(projectPath(id), { signal: request.signal })
    if (refreshRequest === request) {
      const previous = detail.value?.artifacts ?? []
      if (result.artifacts.length !== previous.length
        || result.artifacts.some((artifact, index) => artifact.currentVersion.id !== previous[index]?.currentVersion.id)) {
        emit('historyChanged')
      }
      detail.value = result
      error.value = ''
    }
  } catch (failure) {
    if (!quiet && refreshRequest === request) error.value = errorMessage(failure)
  } finally {
    if (refreshRequest === request) {
      refreshRequest = undefined
      loading.value = false
    }
  }
}

function startProfile(mode: 'new' | 'edit') {
  form.name = mode === 'edit' ? (detail.value?.project.name ?? '') : ''
  form.summary = mode === 'edit' ? (detail.value?.project.summary ?? '') : ''
  formError.value = ''
  editing.value = mode
}

function closeProfile() {
  if (!submitting.value) editing.value = null
}

async function saveProfile() {
  if (submitting.value || editing.value === null) return
  const summary = form.summary.trim()
  const summaryStart = summary.split(/[。！？；\r\n]/, 1)[0].trim()
  const name = editing.value === 'edit' ? form.name.trim() : [...summaryStart].slice(0, 20).join('') || '新人物档案'
  if (!name) {
    formError.value = '请输入人物名称。'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const id = editing.value === 'new' ? null : activeId.value
    const result = await api<PersonProject>(
      id === null ? '/api/projects' : projectPath(id),
      jsonBody({ name, summary }, id === null ? 'POST' : 'PATCH'),
    )
    projects.value = [result, ...projects.value.filter(project => project.id !== result.id)]
    editing.value = null
    if (id === null) tab.value = 'flow'
    await open(result.id)
    void MessagePlugin.success('已保存人物档案')
  } catch (failure) {
    formError.value = errorMessage(failure)
  } finally {
    submitting.value = false
  }
}

function confirmDelete() {
  if (!detail.value) return
  formError.value = ''
  deleting.value = detail.value.project
}

function closeDelete() {
  if (!submitting.value) deleting.value = null
}

async function deleteProfile() {
  const project = deleting.value
  if (!project || submitting.value) return
  submitting.value = true
  formError.value = ''
  try {
    await api<void>(projectPath(project.id), { method: 'DELETE' })
    refreshRequest?.abort()
    refreshRequest = undefined
    loading.value = false
    projects.value = projects.value.filter(row => row.id !== project.id)
    detail.value = null
    activeId.value = null
    deleting.value = null
    tab.value = 'flow'
    emit('deleted', project.id)
    const next = projects.value[0]
    if (next) void open(next.id)
    void MessagePlugin.success('已删除人物档案')
  } catch (failure) {
    formError.value = errorMessage(failure)
  } finally {
    submitting.value = false
  }
}

watch([() => props.active, () => props.requestedProject], ([active, request], [, previousRequest]) => {
  if (!active) return
  if (request && request !== previousRequest) void open(request.id)
  else if (activeId.value !== null) void refresh()
})
watch([() => props.active, hasJobs], ([active, jobs]) => {
  clearInterval(polling)
  if (active && jobs) polling = setInterval(() => void refresh(true), 1300)
})
onMounted(() => void loadProfiles())
onBeforeUnmount(() => {
  clearInterval(polling)
  refreshRequest?.abort()
  refreshRequest = undefined
})
</script>

<template>
  <section class="projects-pane">
    <div class="page-heading project-heading">
      <h1>人物故事与定位</h1>
      <t-button @click="startProfile('new')">
        <template #icon><AddIcon /></template>
        新建人物档案
      </t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <t-card v-if="options.length" class="project-profile-card" size="small">
      <div class="project-profile-controls">
        <t-select aria-label="切换人物档案" :model-value="activeId ?? undefined" :options="options"
          placeholder="选择人物档案" @change="open" />
        <t-button v-if="detail" theme="default" variant="outline" @click="startProfile('edit')">编辑档案</t-button>
        <t-button v-if="detail" theme="danger" variant="text" @click="confirmDelete">删除档案</t-button>
      </div>
      <p v-if="detail?.project.summary" class="project-summary">{{ detail.project.summary }}</p>
    </t-card>
    <t-loading v-if="loading && !detail" text="正在读取人物项目" />
    <t-card v-else-if="!detail && !error">
      <p class="secondary-text">新建档案后，可以先请 AI 想选题，再挑一条写成口播稿。</p>
    </t-card>
    <div v-if="detail" class="project-detail">
      <t-tabs v-model="tab">
        <t-tab-panel value="flow" label="分步创作">
          <ProjectFlow :key="detail.project.id" :detail="detail"
            @changed="refresh()" @open="(id, version) => emit('open', id, version)" />
        </t-tab-panel>
        <t-tab-panel value="materials" label="资料与版本">
          <ProjectMaterials :key="detail.project.id" :project-id="detail.project.id"
            :materials="detail.materials" @changed="refresh()" />
        </t-tab-panel>
      </t-tabs>
    </div>
    <t-dialog :visible="editing !== null" :header="editing === 'edit' ? '编辑人物档案' : '新建人物档案'"
      :confirm-btn="{ content: editing === 'new' ? '新建档案' : '保存', loading: submitting }" @confirm="saveProfile"
      @cancel="closeProfile" @close="closeProfile">
      <t-form :data="form" label-align="top" @submit="saveProfile">
        <t-form-item v-if="editing === 'edit'" label="档案名称" name="name" for="profile-name" required-mark>
          <t-input v-field-id="'profile-name'" v-model="form.name" :maxlength="120" placeholder="输入人物名称" />
        </t-form-item>
        <t-form-item label="说说你在做什么，或者想拍什么（选填）" name="summary" for="profile-summary">
          <t-textarea v-field-id="'profile-summary'" v-model="form.summary" :maxlength="6000"
            :autosize="{ minRows: 3, maxRows: 8 }"
            placeholder="例如，我开了一家社区面包店，想拍视频讲讲怎么选面包。" />
        </t-form-item>
      </t-form>
      <t-alert v-if="formError" theme="error" :message="formError" />
    </t-dialog>
    <t-dialog :visible="deleting !== null" header="删除人物档案？"
      :confirm-btn="{ content: '删除档案', theme: 'danger', loading: submitting }"
      @confirm="deleteProfile" @cancel="closeDelete" @close="closeDelete">
      <p v-if="deleting" class="project-delete-note">「{{ deleting.name }}」的资料和稿件会从工作台移除，排队的生成任务会取消。</p>
      <t-alert v-if="formError" theme="error" :message="formError" />
    </t-dialog>
  </section>
</template>
