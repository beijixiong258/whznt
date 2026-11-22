<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RefreshIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, MessagePlugin, RadioButton as TRadioButton, RadioGroup as TRadioGroup } from 'tdesign-vue-next'
import type { AdminOrder, AdminPage } from '../admin'
import { api, errorMessage, jsonBody } from '../api'
import { currency, rechargeLabels, type RechargeOrder, type RechargeRecord, type RechargeState } from '../recharge'
import AdminRechargeQr from './AdminRechargeQr.vue'
import RechargeRecordsTable from './RechargeRecordsTable.vue'

const path = '/api/admin/recharges'
const filters = [
  { value: 'PENDING', label: rechargeLabels.PENDING },
  { value: 'CREDITED', label: rechargeLabels.CREDITED },
  { value: 'REJECTED', label: rechargeLabels.REJECTED },
  { value: 'all', label: '全部记录' },
] as const
const filter = ref<RechargeState | 'all'>('PENDING')
const page = ref(1)
const hasMore = ref(false)
const rows = ref<RechargeRecord[]>([])
const loading = ref(false)
const error = ref('')
const selected = ref<RechargeRecord>()
const action = ref<'credit' | 'reject'>()
const form = reactive({ points: 0, note: '' })
const submitting = ref(false)
const reviewError = ref('')
let loadSequence = 0

async function load(next = page.value) {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(next) })
    if (filter.value !== 'all') params.set('state', filter.value)
    const result = await api<AdminPage<AdminOrder>>(path + '?' + params)
    if (sequence === loadSequence) {
      rows.value = result.items.map(({ phone, order }) => ({ ...order, phone }))
      hasMore.value = result.hasMore
      page.value = next
    }
  } catch (failure) {
    if (sequence === loadSequence) error.value = errorMessage(failure)
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function changeFilter(value: unknown) {
  const next = filters.find(option => option.value === value)
  if (!next) return
  filter.value = next.value
  rows.value = []
  page.value = 1
  hasMore.value = false
  void load(1)
}

function open(mode: 'credit' | 'reject', entry: RechargeRecord) {
  selected.value = entry
  action.value = mode
  form.points = 0
  form.note = ''
  reviewError.value = ''
}

function close() {
  if (!submitting.value) action.value = undefined
}

async function review() {
  const entry = selected.value
  const mode = action.value
  if (!entry || !mode || submitting.value) return
  const note = form.note.trim()
  if (mode === 'credit' && (!Number.isInteger(form.points) || form.points <= 0)) {
    reviewError.value = '请输入要入账的积分，须为正整数。'
    return
  }
  if (mode === 'reject' && !note) {
    reviewError.value = '请填写拒绝原因。'
    return
  }
  submitting.value = true
  reviewError.value = ''
  try {
    await api<RechargeOrder>(path + '/' + entry.id + '/' + mode,
      jsonBody(mode === 'credit' ? { points: form.points, note } : { note }))
    action.value = undefined
    void MessagePlugin.success(mode === 'credit' ? '积分已入账' : '已拒绝充值申请')
    await load(Math.max(1, rows.value.length === 1 && !hasMore.value ? page.value - 1 : page.value))
  } catch (failure) {
    reviewError.value = errorMessage(failure)
  } finally {
    submitting.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-panel" aria-label="充值管理">
    <t-card>
      <div class="admin-toolbar">
        <t-radio-group aria-label="充值状态" :value="filter" variant="default-filled" @change="changeFilter">
          <t-radio-button v-for="option in filters" :key="option.value" :value="option.value">{{ option.label }}</t-radio-button>
        </t-radio-group>
        <div class="admin-actions">
          <AdminRechargeQr />
          <t-button theme="default" variant="outline" :disabled="loading" @click="load()">
            <template #icon><RefreshIcon /></template>
            刷新
          </t-button>
        </div>
      </div>
      <t-alert v-if="error" class="admin-feedback" theme="error" :message="error" />
      <RechargeRecordsTable :rows="rows" :loading="loading" admin empty="没有符合条件的充值记录">
        <template #actions="{ row }">
          <template v-if="row.state === 'PENDING'">
            <t-button theme="danger" :disabled="submitting" @click="open('reject', row)">拒绝</t-button>
            <t-button theme="success" :disabled="submitting" @click="open('credit', row)">入账</t-button>
          </template>
        </template>
      </RechargeRecordsTable>
      <div v-if="page > 1 || hasMore" class="admin-actions admin-pages">
        <t-button size="small" variant="outline" :disabled="loading || page === 1" @click="load(page - 1)">上一页</t-button>
        <span>第 {{ page }} 页</span>
        <t-button size="small" variant="outline" :disabled="loading || !hasMore" @click="load(page + 1)">下一页</t-button>
      </div>
    </t-card>
    <t-dialog :visible="!!action" :header="action === 'credit' ? '手动入账' : '拒绝充值申请'"
      :confirm-btn="{ content: action === 'credit' ? '确认入账' : '确认拒绝', theme: action === 'credit' ? 'success' : 'danger', loading: submitting }"
      @confirm="review" @cancel="close" @close="close">
      <t-form :data="form" label-align="top" @submit="review">
        <p v-if="selected" class="admin-user-label">{{ selected.phone }} · 申请 {{ currency.format(selected.amount) }}</p>
        <t-form-item v-if="action === 'credit'" label="到账积分" for="recharge-credit-points" required-mark>
          <t-input-number v-field-id="'recharge-credit-points'" v-model="form.points" :min="1" :max="2147483647"
            :decimal-places="0" :disabled="submitting" />
        </t-form-item>
        <t-form-item :label="action === 'credit' ? '给用户的说明（选填）' : '拒绝原因'" for="recharge-review-note"
          :required-mark="action === 'reject'">
          <t-textarea v-field-id="'recharge-review-note'" v-model="form.note" :maxlength="200" :disabled="submitting" />
        </t-form-item>
      </t-form>
      <t-alert v-if="reviewError" theme="error" :message="reviewError" />
    </t-dialog>
  </section>
</template>
