<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Link as TLink, MessagePlugin, Table as TTable } from 'tdesign-vue-next'
import type { AdminOrder, AdminPage } from '../admin'
import { api, errorMessage, jsonBody } from '../api'
import { currency, dates, rechargeLabels, rechargeThemes, type RechargeOrder, type RechargeState } from '../recharge'

const path = '/api/admin/recharges'
const columns = [
  { colKey: 'user', title: '用户', width: 140 },
  { colKey: 'details', title: '申请', minWidth: 260 },
  { colKey: 'status', title: '状态', minWidth: 134 },
  { colKey: 'actions', title: '操作', width: 220 },
]
const filters = [
  { value: 'PENDING', label: '待处理' },
  { value: 'CREDITED', label: '已入账' },
  { value: 'REJECTED', label: '未通过' },
  { value: 'all', label: '全部记录' },
] as const
const filter = ref<RechargeState | 'all'>('PENDING')
const page = ref(1)
const hasMore = ref(false)
const rows = ref<(AdminOrder & { id: number })[]>([])
const loading = ref(false)
const error = ref('')
const selected = ref<AdminOrder>()
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
      rows.value = result.items.map(entry => ({ ...entry, id: entry.order.id }))
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

function open(mode: 'credit' | 'reject', entry: AdminOrder) {
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
    reviewError.value = '请填写给用户的说明。'
    return
  }
  submitting.value = true
  reviewError.value = ''
  try {
    await api<RechargeOrder>(path + '/' + entry.order.id + '/' + mode,
      jsonBody(mode === 'credit' ? { points: form.points, note } : { note }))
    action.value = undefined
    void MessagePlugin.success(mode === 'credit' ? '积分已入账' : '已标记未通过')
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
  <section class="admin-panel" aria-label="充值记录">
    <div class="admin-toolbar">
      <t-select class="admin-filter" aria-label="充值状态" :value="filter" :options="[...filters]" @change="changeFilter" />
      <t-button theme="default" variant="outline" :disabled="loading" @click="load()">刷新</t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <div class="admin-table">
      <t-table :data="rows" :columns="columns" :loading="loading" row-key="id" empty="没有符合条件的充值记录">
        <template #user="{ row }">{{ row.phone }}</template>
        <template #details="{ row }">
          <strong>{{ currency.format(row.order.amount) }}</strong>
          <p class="secondary-text">{{ dates.format(new Date(row.order.submittedAt)) }}</p>
          <p class="secondary-text order-number">{{ row.order.number }}</p>
          <p v-if="row.order.note" class="secondary-text">{{ row.order.note }}</p>
        </template>
        <template #status="{ row }">
          <t-tag :theme="rechargeThemes[row.order.state as RechargeState]" variant="light">
            {{ rechargeLabels[row.order.state as RechargeState] }}
          </t-tag>
          <p v-if="row.order.creditedPoints !== null" class="secondary-text">+{{ row.order.creditedPoints }} 积分</p>
          <p v-if="row.order.reviewNote" class="secondary-text">{{ row.order.reviewNote }}</p>
        </template>
        <template #actions="{ row }">
          <div class="admin-actions">
            <t-link :href="path + '/' + row.order.id + '/receipt'" target="_blank" theme="primary">看截图</t-link>
            <template v-if="row.order.state === 'PENDING'">
              <t-button size="small" variant="text" @click="open('credit', row)">入账</t-button>
              <t-button size="small" variant="text" theme="danger" @click="open('reject', row)">未通过</t-button>
            </template>
          </div>
        </template>
      </t-table>
    </div>
    <div v-if="page > 1 || hasMore" class="admin-actions admin-pages">
      <t-button size="small" variant="outline" :disabled="loading || page === 1" @click="load(page - 1)">上一页</t-button>
      <span>第 {{ page }} 页</span>
      <t-button size="small" variant="outline" :disabled="loading || !hasMore" @click="load(page + 1)">下一页</t-button>
    </div>
    <t-dialog :visible="!!action" :header="action === 'credit' ? '手动入账' : '标记未通过'"
      :confirm-btn="{ content: action === 'credit' ? '确认入账' : '确认', loading: submitting }"
      @confirm="review" @cancel="close" @close="close">
      <t-form :data="form" label-align="top" @submit="review">
        <p v-if="selected" class="admin-user-label">{{ selected.phone }} · 申请 {{ currency.format(selected.order.amount) }}</p>
        <t-form-item v-if="action === 'credit'" label="到账积分" for="recharge-credit-points" required-mark>
          <t-input-number v-field-id="'recharge-credit-points'" v-model="form.points" :min="1" :max="2147483647"
            :decimal-places="0" :disabled="submitting" />
        </t-form-item>
        <t-form-item :label="action === 'credit' ? '给用户的说明（选填）' : '未通过原因'" for="recharge-review-note"
          :required-mark="action === 'reject'">
          <t-textarea v-field-id="'recharge-review-note'" v-model="form.note" :maxlength="200" :disabled="submitting" />
        </t-form-item>
      </t-form>
      <t-alert v-if="reviewError" theme="error" :message="reviewError" />
    </t-dialog>
  </section>
</template>
