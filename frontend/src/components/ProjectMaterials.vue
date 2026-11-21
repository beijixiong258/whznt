<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Card as TCard, Link as TLink, MessagePlugin, Tabs as TTabs, TabPanel as TTabPanel, Upload as TUpload } from 'tdesign-vue-next'
import type { UploadFile } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody } from '../api'
import { materialLabels, projectPath } from '../projects'
import type { MaterialKind, MaterialPassage, MaterialRow, MaterialText } from '../projects'

const props = defineProps<{ projectId: number; materials: MaterialRow[] }>()
const emit = defineEmits<{ changed: [] }>()
const editing = ref(false)
const mode = ref<'text' | 'file'>('text')
const updating = ref<MaterialRow | null>(null)
const viewing = ref<MaterialRow | null>(null)
const original = ref<MaterialText | null>(null)
const reading = ref(false)
const fetching = ref(false)
const saving = ref(false)
const query = ref('')
const hits = ref<MaterialPassage[] | null>(null)
const files = ref<UploadFile[]>([])
const focused = ref<MaterialPassage | null>(null)
const error = ref('')
const editError = ref('')
const originalError = ref('')
const form = reactive({ name: '', kind: 'LIFE' as MaterialKind, text: '' })
const kinds = Object.entries(materialLabels).map(([value, label]) => ({ value, label }))
const versionOptions = computed(() => (viewing.value?.versions ?? []).map((row, index, all) => ({
  value: row.id, label: '第 ' + (all.length - index) + ' 版 · ' + row.filename,
})))
const originalHref = computed(() => viewing.value && original.value
  ? materialPath(viewing.value.id, original.value.version.id) + '/download' : undefined)

function materialPath(material: number, version: number) {
  return projectPath(props.projectId) + '/materials/' + material + '/versions/' + version
}

function add(row: MaterialRow | null = null) {
  updating.value = row
  mode.value = 'text'
  form.name = row?.name ?? ''
  form.kind = row?.kind ?? 'LIFE'
  form.text = ''
  files.value = []
  editError.value = ''
  editing.value = true
}

function dismissEdit() {
  if (!saving.value) editing.value = false
}

async function save() {
  if (saving.value || !editing.value) return
  if (!form.name.trim()) {
    editError.value = '请输入资料名称。'
    return
  }
  const data = {
    materialId: updating.value?.id, baseVersionId: updating.value?.currentVersionId,
    name: form.name, kind: form.kind,
  }
  const file = files.value[0]?.raw
  if ((mode.value === 'file' && !file) || (mode.value === 'text' && !form.text.trim())) {
    editError.value = mode.value === 'file' ? '请选择要上传的文件。' : '请粘贴资料原文。'
    return
  }
  saving.value = true
  editError.value = ''
  try {
    const path = projectPath(props.projectId) + '/materials/' + mode.value
    if (mode.value === 'file' && file) {
      const body = new FormData()
      for (const [key, value] of Object.entries(data)) {
        if (value !== undefined) body.set(key, String(value))
      }
      body.set('file', file, file.name)
      await api<MaterialRow>(path, { method: 'POST', body })
    } else {
      await api<MaterialRow>(path, jsonBody({ ...data, text: form.text }))
    }
    editing.value = false
    hits.value = null
    emit('changed')
    void MessagePlugin.success('已保存资料版本')
  } catch (failure) {
    editError.value = errorMessage(failure)
  } finally {
    saving.value = false
  }
}

function open(row: MaterialRow) {
  viewing.value = row
  original.value = null
  void readVersion(row.currentVersionId)
}

async function readVersion(id: unknown) {
  const row = viewing.value
  if (!row || typeof id !== 'number') return
  reading.value = true
  originalError.value = ''
  try {
    original.value = await api<MaterialText>(materialPath(row.id, id))
  } catch (failure) {
    originalError.value = errorMessage(failure)
  } finally {
    reading.value = false
  }
}

async function search() {
  if (fetching.value || !query.value.trim()) return
  fetching.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ q: query.value.trim() })
    hits.value = await api<MaterialPassage[]>(projectPath(props.projectId) + '/materials/search?' + params)
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    fetching.value = false
  }
}
</script>

<template>
  <section class="project-materials">
    <div class="project-toolbar">
      <form class="project-search" @submit.prevent="search">
        <t-input v-model="query" clearable placeholder="搜索当前资料" aria-label="搜索当前资料" :maxlength="120" />
        <t-button type="submit" variant="outline" :loading="fetching" :disabled="!query.trim()">搜索</t-button>
      </form>
      <t-button @click="add()">添加资料</t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <t-card v-if="hits !== null" title="搜索结果" :bordered="true">
      <template #actions><t-button size="small" variant="text" @click="hits = null">关闭</t-button></template>
      <p v-if="!hits.length" class="secondary-text">没有找到相关片段。</p>
      <div v-for="hit in hits" :key="hit.id" class="project-row">
        <span>{{ hit.material }} · {{ hit.location }}</span>
        <t-button size="small" variant="text" @click="focused = hit">查看片段</t-button>
      </div>
    </t-card>
    <t-card :bordered="true">
      <p v-if="!materials.length" class="secondary-text">可以粘贴原文，也可以导入 DOCX、TXT、MD 或文字版 PDF。</p>
      <div v-for="row in materials" :key="row.id" class="project-row">
        <div>
          <b>{{ row.name }}</b>
          <p class="secondary-text">{{ materialLabels[row.kind] }} · {{ row.versions.length }} 个版本</p>
        </div>
        <div class="project-actions">
          <t-button size="small" variant="outline" @click="open(row)">查看原文</t-button>
          <t-button size="small" variant="outline" @click="add(row)">新增版本</t-button>
        </div>
      </div>
    </t-card>
    <t-dialog :visible="editing" :header="updating ? '新增资料版本' : '添加资料'"
      :confirm-btn="{ content: '保存资料', loading: saving }" @confirm="save" @cancel="dismissEdit" @close="dismissEdit">
      <t-form :data="form" label-align="top">
        <t-form-item label="资料名称" name="name" for="material-name">
          <t-input v-field-id="'material-name'" v-model="form.name" :maxlength="120" :disabled="!!updating || saving" />
        </t-form-item>
        <t-form-item label="资料类型" name="kind" for="material-kind">
          <t-select v-field-id="'material-kind'" v-model="form.kind" :options="kinds" :disabled="!!updating || saving" />
        </t-form-item>
      </t-form>
      <t-tabs v-model="mode">
        <t-tab-panel value="text" label="粘贴文字">
          <t-textarea v-model="form.text" aria-label="资料原文" placeholder="粘贴访谈、观察或风格样稿"
            :maxlength="200000" :autosize="{ minRows: 5, maxRows: 9 }" />
        </t-tab-panel>
        <t-tab-panel value="file" label="导入文件">
          <t-upload v-model="files" :auto-upload="false" :max="1" :show-upload-progress="false"
            accept=".docx,.txt,.md,.pdf" tips="DOCX、TXT、MD 或文字版 PDF，单份不超过 10 MB" />
        </t-tab-panel>
      </t-tabs>
      <t-alert v-if="editError" theme="error" :message="editError" />
    </t-dialog>
    <t-dialog :visible="viewing !== null" :header="viewing?.name ?? '资料原文'" :footer="false"
      width="min(680px, 92vw)" @close="viewing = null">
      <div class="project-toolbar">
        <t-select :model-value="original?.version.id" :options="versionOptions" aria-label="资料版本"
          :disabled="reading" @change="readVersion" />
        <t-link v-if="originalHref" :href="originalHref" theme="primary">下载原件</t-link>
      </div>
      <t-alert v-if="originalError" theme="error" :message="originalError" />
      <t-loading v-else-if="reading" text="正在读取原文" />
      <div v-else-if="original" class="project-scroll">
        <article v-for="part in original.passages" :key="part.id" class="source-text">
          <h3>{{ part.location }}</h3><p>{{ part.content }}</p>
        </article>
      </div>
    </t-dialog>
    <t-dialog :visible="focused !== null" :header="focused?.material ?? '原文片段'" :footer="false"
      width="min(620px, 92vw)" @close="focused = null">
      <div v-if="focused" class="source-text">
        <h3>{{ focused.location }} · {{ materialLabels[focused.kind] }}</h3>
        <p>{{ focused.content }}</p>
      </div>
    </t-dialog>
  </section>
</template>
