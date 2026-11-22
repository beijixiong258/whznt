<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Card as TCard, MessagePlugin, Tabs as TTabs, TabPanel as TTabPanel } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody } from '../api'
import { projectPath, running } from '../projects'
import type { PersonProject, ProjectDetail } from '../projects'
import ProjectFlow from './ProjectFlow.vue'
import ProjectMaterials from './ProjectMaterials.vue'

const props = defineProps<{ active: boolean; requestedProject?: { id: number } }>()
const emit = defineEmits<{ open: [conversationId: number, versionId?: number] }>()
const projects = ref<PersonProject[]>([])
const detail = ref<ProjectDetail | null>(null)
const activeId = ref<number | null>(null)
const tab = ref<'flow' | 'materials'>('flow')
const loading = ref(false)
const error = ref('')
const editing = ref<'new' | 'edit' | null>(null)
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
    const initial = props.requestedProject?.id ?? activeId.value ?? projects.value[0]?.id
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
  if (!form.name.trim()) {
    formError.value = '请输入人物名称。'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const id = editing.value === 'new' ? null : activeId.value
    const result = await api<PersonProject>(
      id === null ? '/api/projects' : projectPath(id),
      jsonBody(form, id === null ? 'POST' : 'PATCH'),
    )
    projects.value = [result, ...projects.value.filter(project => project.id !== result.id)]
    editing.value = null
    await open(result.id)
    void MessagePlugin.success('已保存人物档案')
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
      <t-button @click="startProfile('new')">新建人物档案</t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <div v-if="options.length" class="project-toolbar">
      <t-select aria-label="切换人物项目" :model-value="activeId ?? undefined" :options="options"
        placeholder="选择人物项目" @change="open" />
      <t-button v-if="detail" theme="default" variant="outline" @click="startProfile('edit')">编辑人物档案</t-button>
    </div>
    <t-loading v-if="loading && !detail" text="正在读取人物项目" />
    <t-card v-else-if="!detail && !error">
      <p class="secondary-text">先建一份人物档案，填一点自己的经历。接下来可以写人物故事、想账号定位和视频选题。</p>
    </t-card>
    <div v-if="detail" class="project-detail">
      <h2>{{ detail.project.name }}</h2>
      <p v-if="detail.project.summary" class="project-summary">{{ detail.project.summary }}</p>
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
      :confirm-btn="{ content: '保存', loading: submitting }" @confirm="saveProfile"
      @cancel="closeProfile" @close="closeProfile">
      <t-form :data="form" label-align="top" @submit="saveProfile">
        <t-form-item label="人物名称" name="name" for="profile-name" required-mark>
          <t-input v-field-id="'profile-name'" v-model="form.name" :maxlength="120" placeholder="输入人物名称" />
        </t-form-item>
        <t-form-item label="人物简介" name="summary" for="profile-summary">
          <t-textarea v-field-id="'profile-summary'" v-model="form.summary" :maxlength="6000"
            :autosize="{ minRows: 3, maxRows: 8 }"
            placeholder="可填写本人身份、工作及希望创作的方向" />
        </t-form-item>
      </t-form>
      <t-alert v-if="formError" theme="error" :message="formError" />
    </t-dialog>
  </section>
</template>
