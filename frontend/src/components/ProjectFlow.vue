<script setup lang="ts">
import { computed, ref } from 'vue'
import { MoreIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, Dropdown as TDropdown, MessagePlugin, type DropdownOption } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody } from '../api'
import { projectPath, running, stepLabels } from '../projects'
import type { Artifact, ProjectDetail, ProjectJob, ProjectStep, Topic } from '../projects'

const props = defineProps<{ detail: ProjectDetail }>()
const emit = defineEmits<{ changed: []; open: [conversationId: number, versionId?: number] }>()
const steps = ['TOPICS', 'POSITION', 'STORY', 'TRAITS'] as const
const stepDetails = {
  TOPICS: { action: '帮我想选题', description: '给出十个不同的短视频题目，挑一条就能写口播。' },
  POSITION: { action: '帮我想定位', description: '给出不同的账号方向，并推荐一个先试。' },
  STORY: { action: '帮我写故事', description: '从已有经历中找切入点，资料少时先给提纲。' },
  TRAITS: { action: '帮我找亮点', description: '找到擅长的事和可以讲的经历。' },
}
const jobLabels = { QUEUED: '排队中', RUNNING: '生成中', COMPLETED: '已完成', FAILED: '生成失败' }
const existing = computed(() => new Map(props.detail.artifacts.filter(row => row.step !== 'SCRIPT')
  .map(row => [row.step, row] as const)))
const scripts = computed(() => props.detail.artifacts.filter(row => row.step === 'SCRIPT'))
const importing = ref<ProjectStep | null>(null)
const importText = ref('')
type Generation = { step: ProjectStep; artifact?: Artifact; topic?: Topic; requestId: string }
const generating = ref<Generation | null>(null)
const instructions = ref('')
const duration = ref(150)
const busy = ref(false)
const dialogError = ref('')
const generationError = ref('')

function isGenerating(step: ProjectStep) {
  return props.detail.jobs.some(job => job.step === step && running(job))
}

function beginImport(step: ProjectStep) {
  importing.value = step
  importText.value = ''
  dialogError.value = ''
}

function beginGeneration(step: ProjectStep, artifact?: Artifact, topic?: Topic) {
  generating.value = { step, artifact, topic, requestId: crypto.randomUUID() }
  instructions.value = ''
  duration.value = 150
  dialogError.value = ''
}

function close() {
  if (busy.value) return
  importing.value = null
  generating.value = null
  dialogError.value = ''
}

async function saveImported() {
  if (importing.value === null || busy.value) return
  if (!importText.value.trim()) {
    dialogError.value = '请粘贴已有成果。'
    return
  }
  busy.value = true
  try {
    await api<Artifact>(projectPath(props.detail.project.id) + '/artifacts',
      jsonBody({ step: importing.value, content: importText.value }))
    importing.value = null
    emit('changed')
    void MessagePlugin.success('已录入成果')
  } catch (failure) {
    dialogError.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

async function queue(selected: Generation, customized = false) {
  if (busy.value || isGenerating(selected.step)) return
  busy.value = true
  dialogError.value = ''
  generationError.value = ''
  try {
    await api<ProjectJob>(projectPath(props.detail.project.id) + '/jobs', jsonBody({
      requestId: selected.requestId,
      step: selected.step,
      artifactId: selected.artifact?.id,
      baseVersionId: selected.artifact?.currentVersion.id,
      topicVersionId: selected.topic?.versionId,
      topicIndex: selected.topic?.index,
      durationSeconds: customized && selected.step === 'SCRIPT' ? duration.value : undefined,
      instructions: customized ? instructions.value : undefined,
    }))
    generating.value = null
    emit('changed')
    void MessagePlugin.success('已开始后台生成')
  } catch (failure) {
    if (customized) dialogError.value = errorMessage(failure)
    else generationError.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

function generate(step: ProjectStep, artifact?: Artifact, topic?: Topic) {
  void queue({ step, artifact, topic, requestId: crypto.randomUUID() })
}

function menuOptions(artifact?: Artifact): DropdownOption[] {
  return [
    { value: 'settings', content: '生成设置' },
    artifact ? { value: 'regenerate', content: '重做这一步' } : { value: 'import', content: '粘贴已有成果' },
  ]
}

function chooseAction(option: DropdownOption, step: ProjectStep) {
  const artifact = existing.value.get(step)
  if (option.value === 'regenerate') generate(step, artifact)
  else if (option.value === 'settings') beginGeneration(step, artifact)
  else if (option.value === 'import') beginImport(step)
}

function openArtifact(row: Artifact | undefined) {
  if (row) emit('open', row.conversationId)
}
</script>

<template>
  <section class="project-flow">
    <t-alert v-if="generationError" theme="error" :message="generationError" />
    <div class="steps-grid">
      <t-card v-for="step in steps" :key="step" :title="stepLabels[step]"
        :class="{ 'project-step-featured': step === 'TOPICS' && !existing.has(step) }">
        <template #actions>
          <t-tag v-if="isGenerating(step)" theme="primary" variant="light">生成中</t-tag>
          <t-tag v-else-if="existing.has(step)" theme="success" variant="light">已保存</t-tag>
        </template>
        <p class="step-description" :class="{ 'secondary-text': !existing.has(step) }">
          {{ existing.get(step)?.currentVersion.title ?? stepDetails[step].description }}
        </p>
        <div class="project-actions project-step-actions">
          <t-button v-if="existing.has(step)" variant="outline"
            @click="openArtifact(existing.get(step))">{{ step === 'TOPICS' ? '查看选题' : '打开成果' }}</t-button>
          <t-button v-else :variant="step === 'TOPICS' ? 'base' : 'outline'" :disabled="busy || isGenerating(step)"
            @click="generate(step)">{{ stepDetails[step].action }}</t-button>
          <t-dropdown :options="menuOptions(existing.get(step))" trigger="click" placement="bottom-right"
            :max-column-width="160" :disabled="busy || isGenerating(step)" @click="chooseAction($event, step)">
            <t-button variant="text" theme="default" shape="square" :aria-label="stepLabels[step] + '的更多操作'"
              :disabled="busy || isGenerating(step)">
              <template #icon><MoreIcon /></template>
            </t-button>
          </t-dropdown>
        </div>
      </t-card>
    </div>
    <t-card v-if="detail.topics.length || scripts.length" title="口播稿">
      <div v-if="detail.topics.length">
        <div v-for="topic in detail.topics" :key="topic.versionId + ':' + topic.index" class="project-row">
          <span>{{ topic.title }}</span>
          <div class="project-actions">
            <t-button size="small" variant="outline" :disabled="busy || isGenerating('SCRIPT')"
              @click="generate('SCRIPT', undefined, topic)">按题写口播</t-button>
            <t-button size="small" variant="text" theme="default" :disabled="busy || isGenerating('SCRIPT')"
              @click="beginGeneration('SCRIPT', undefined, topic)">生成设置</t-button>
          </div>
        </div>
      </div>
      <div v-if="scripts.length" class="scripts-list">
        <h3>已保存口播稿</h3>
        <div v-for="script in scripts" :key="script.id" class="project-row">
          <span>{{ script.topic }}</span>
          <div class="project-actions">
            <t-button size="small" variant="outline" @click="openArtifact(script)">打开稿件</t-button>
            <t-button size="small" variant="outline" :disabled="busy || isGenerating('SCRIPT')"
              @click="generate('SCRIPT', script)">重做这篇</t-button>
            <t-button size="small" variant="text" theme="default" :disabled="busy || isGenerating('SCRIPT')"
              @click="beginGeneration('SCRIPT', script)">生成设置</t-button>
          </div>
        </div>
      </div>
    </t-card>
    <t-card v-if="detail.jobs.length" title="生成记录">
      <div>
        <div v-for="job in detail.jobs" :key="job.id" class="project-row">
          <div>
            <b>{{ stepLabels[job.step] }}</b>
            <p v-if="job.error" class="secondary-text">{{ job.error }}</p>
          </div>
          <t-tag :theme="job.state === 'FAILED' ? 'danger' : running(job) ? 'primary' : 'success'"
            variant="light">{{ jobLabels[job.state] }}</t-tag>
          <t-button v-if="job.conversationId !== null" size="small" variant="text"
            @click="emit('open', job.conversationId, job.versionId ?? undefined)">查看结果</t-button>
        </div>
      </div>
    </t-card>

    <t-dialog :visible="importing !== null"
      :header="importing ? '录入已有' + stepLabels[importing] : '录入已有成果'"
      :confirm-btn="{ content: '保存', loading: busy }" @confirm="saveImported" @cancel="close" @close="close">
      <t-form :data="{ content: importText }" label-align="top">
        <t-form-item label="成果正文" name="content" for="artifact-content">
          <t-textarea v-field-id="'artifact-content'" v-model="importText" :maxlength="30000"
            :autosize="{ minRows: 6, maxRows: 12 }"
            :placeholder="importing === 'TOPICS' ? '每行一个选题标题，也可以用 Markdown 二级标题' : '粘贴现有正文'" />
        </t-form-item>
      </t-form>
      <t-alert v-if="dialogError" theme="error" :message="dialogError" />
    </t-dialog>
    <t-dialog :visible="generating !== null" :header="generating ? '生成' + stepLabels[generating.step] : '生成成果'"
      :confirm-btn="{ content: '开始生成', loading: busy }"
      @confirm="generating && queue(generating, true)" @cancel="close" @close="close">
      <p v-if="generating?.topic || generating?.artifact?.topic" class="selected-topic">
        {{ generating?.topic?.title ?? generating?.artifact?.topic }}
      </p>
      <t-form :data="{ instructions, duration }" label-align="top">
        <t-form-item v-if="generating?.step === 'SCRIPT'" label="口播时长（秒）" name="duration" for="script-duration">
          <t-input-number v-field-id="'script-duration'" v-model="duration" :min="15" :max="600" :step="15" />
        </t-form-item>
        <t-form-item label="补充要求" name="instructions" for="generation-instructions">
          <t-textarea v-field-id="'generation-instructions'" v-model="instructions" :maxlength="1500"
            :autosize="{ minRows: 3, maxRows: 7 }"
            placeholder="选填，例如突出一次实际转折" />
        </t-form-item>
      </t-form>
      <t-alert v-if="dialogError" theme="error" :message="dialogError" />
    </t-dialog>
  </section>
</template>
