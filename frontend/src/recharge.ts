export type RechargeState = 'PENDING' | 'CREDITED' | 'REJECTED'

export interface RechargeOrder {
  id: number
  number: string
  amount: number
  note: string
  submittedAt: string
  state: RechargeState
  creditedPoints: number | null
  reviewNote: string
}

export type RechargeRecord = RechargeOrder & { phone?: string }

export const rechargeLabels = { PENDING: '待处理', CREDITED: '已入账', REJECTED: '已拒绝' } as const
export const rechargeThemes = { PENDING: 'warning', CREDITED: 'success', REJECTED: 'danger' } as const
export const currency = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' })
export const dates = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
