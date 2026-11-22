<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Card as TCard, MessagePlugin } from 'tdesign-vue-next'
import type { Upstream, UpstreamProbe } from '../admin'
import { api, errorMessage, jsonBody } from '../api'

const path = '/api/admin/upstream'
const saved = ref<Upstream>()
const form = reactive({ baseUrl: '', model: '', apiKey: '' })
const probe = ref<UpstreamProbe>()
const operation = ref<'load' | 'save' | 'test'>()
const error = ref('')
const reusesKey = computed(() => !!saved.value?.hasKey && form.baseUrl.trim().replace(/\/+$/, '') === saved.value.baseUrl)

function setForm(settings: Upstream) {
  saved.value = settings
  form.baseUrl = settings.baseUrl
  form.model = settings.model
  form.apiKey = ''
}

async function load() {
  operation.value = 'load'
  error.value = ''
  try {
    setForm(await api<Upstream>(path))
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

async function apply(action: 'save' | 'test') {
  if (operation.value) return
  if (!form.baseUrl.trim() || !form.model.trim()) {
    error.value = '请填写 API 地址和模型名称。'
    return
  }
  error.value = ''
  probe.value = undefined
  operation.value = action
  try {
    const input = { baseUrl: form.baseUrl.trim(), model: form.model.trim(), apiKey: form.apiKey.trim() }
    if (action === 'test') {
      probe.value = await api<UpstreamProbe>(path + '/test', jsonBody(input))
    } else {
      setForm(await api<Upstream>(path, jsonBody(input, 'PUT')))
      void MessagePlugin.success('已保存 AI 提供商配置')
    }
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    operation.value = undefined
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-panel" aria-label="AI 提供商配置">
    <t-card>
      <t-alert v-if="error" class="admin-feedback" theme="error" :message="error" />
      <t-loading v-if="operation === 'load'" text="正在读取配置" />
      <t-button v-else-if="!saved" variant="outline" @click="load">重新读取</t-button>
      <t-form v-else :data="form" label-align="top" @submit="apply('save')">
        <div class="admin-provider-fields">
          <t-form-item label="API 地址（OpenAI 兼容）" for="upstream-url" name="baseUrl" required-mark>
            <t-input v-field-id="'upstream-url'" v-model="form.baseUrl" :maxlength="512" :disabled="!!operation"
              placeholder="例如 https://api.deepseek.com/v1" @change="probe = undefined" />
          </t-form-item>
          <t-form-item label="模型名称" for="upstream-model" name="model" required-mark>
            <t-input v-field-id="'upstream-model'" v-model="form.model" :maxlength="128" :disabled="!!operation"
              placeholder="例如 deepseek-flash" @change="probe = undefined" />
          </t-form-item>
        </div>
        <t-form-item label="API 密钥" for="upstream-key" name="apiKey" :required-mark="!reusesKey">
          <t-input v-field-id="'upstream-key'" v-model="form.apiKey" type="password" autocomplete="new-password"
            :maxlength="1024" :disabled="!!operation" @change="probe = undefined"
            :placeholder="reusesKey ? '留空沿用当前密钥' : '输入提供商的 API 密钥'" />
        </t-form-item>
        <div class="admin-actions admin-config-actions">
          <t-button variant="outline" :loading="operation === 'test'" :disabled="!!operation"
            @click="apply('test')">测试连接和速度</t-button>
          <t-button type="submit" :loading="operation === 'save'" :disabled="!!operation">保存配置</t-button>
        </div>
      </t-form>
      <div v-if="probe" class="admin-probe" aria-live="polite">
        <t-alert :theme="probe.connected ? 'success' : 'warning'" :message="probe.message" />
        <dl class="admin-metrics">
          <div v-if="probe.firstReplyMs !== null">
            <dt>首字耗时</dt><dd>{{ probe.firstReplyMs }} 毫秒</dd>
          </div>
          <div>
            <dt>总耗时</dt><dd>{{ probe.totalMs }} 毫秒</dd>
          </div>
        </dl>
      </div>
    </t-card>
  </section>
</template>
