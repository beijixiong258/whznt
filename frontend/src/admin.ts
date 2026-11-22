import type { RechargeOrder } from './recharge'

export interface AdminPage<T> {
  items: T[]
  hasMore: boolean
}

export interface AdminUser {
  id: number
  phone: string
  enabled: boolean
  points: number
}

export interface PointCredit {
  id: number
  operatorId: number
  rechargeOrderId: number | null
  points: number
  balanceAfter: number
  note: string
  createdAt: string
}

export interface AdminOrder {
  ownerId: number
  phone: string
  order: RechargeOrder
}

export interface Upstream {
  baseUrl: string
  model: string
  hasKey: boolean
}

export interface UpstreamProbe {
  connected: boolean
  firstReplyMs: number | null
  totalMs: number
  message: string
}
