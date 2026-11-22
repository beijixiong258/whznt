<script setup lang="ts">
import { computed } from 'vue'
import { Link as TLink, Table as TTable } from 'tdesign-vue-next'
import { currency, dates, rechargeLabels, rechargeThemes, type RechargeRecord, type RechargeState } from '../recharge'

const props = defineProps<{
  rows: RechargeRecord[]
  admin?: boolean
  loading: boolean
  empty: string
  maxHeight?: number | string
}>()
defineSlots<{ actions?: (props: { row: RechargeRecord }) => void }>()

const columns = computed(() => [
  ...(props.admin ? [{ colKey: 'phone', title: '用户手机号', width: 140 }] : []),
  { colKey: 'details', title: '充值申请', width: 304 },
  { colKey: 'status', title: '处理状态', width: 120 },
  { colKey: 'messages', title: '留言' },
  { colKey: 'actions', title: '操作', width: props.admin ? 260 : 132, align: 'right' as const },
])
const receiptPath = computed(() => props.admin ? '/api/admin/recharges/' : '/api/recharge/orders/')
</script>

<template>
  <div :class="['recharge-record-table', { 'recharge-record-table-admin': admin }]">
    <t-table :data="rows" :columns="columns" :loading="loading" :max-height="maxHeight" row-key="id" hover :empty="empty">
      <template #phone="{ row }"><strong class="tabular-number">{{ row.phone }}</strong></template>
      <template #details="{ row }">
        <strong class="tabular-number">{{ currency.format(row.amount) }}</strong>
        <p class="secondary-text"><time :datetime="row.submittedAt">{{ dates.format(new Date(row.submittedAt)) }}</time></p>
        <p class="secondary-text order-number">{{ row.number }}</p>
      </template>
      <template #status="{ row }">
        <t-tag :theme="rechargeThemes[row.state as RechargeState]" variant="light">
          {{ rechargeLabels[row.state as RechargeState] }}
        </t-tag>
        <p v-if="row.creditedPoints !== null" class="secondary-text tabular-number">
          +{{ row.creditedPoints.toLocaleString('zh-CN') }} 积分
        </p>
      </template>
      <template #messages="{ row }">
        <dl v-if="row.note || row.reviewNote" class="recharge-record-messages">
          <div v-if="row.note">
            <dt>用户备注</dt>
            <dd>{{ row.note }}</dd>
          </div>
          <div v-if="row.reviewNote">
            <dt>{{ row.state === 'REJECTED' ? '拒绝原因' : '管理员回复' }}</dt>
            <dd>{{ row.reviewNote }}</dd>
          </div>
        </dl>
        <span v-else class="secondary-text">—</span>
      </template>
      <template #actions="{ row }">
        <div class="recharge-record-actions">
          <t-link :href="receiptPath + row.id + '/receipt'" target="_blank" rel="noopener" theme="primary">查看付款截图</t-link>
          <slot name="actions" :row="row as RechargeRecord" />
        </div>
      </template>
    </t-table>
  </div>
</template>
