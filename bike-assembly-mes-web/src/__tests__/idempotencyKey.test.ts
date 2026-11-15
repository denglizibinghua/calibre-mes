/**
 * 幂等键管理工具最小测试：覆盖生成、复用和清除。
 * 使用 vitest + jsdom 环境（sessionStorage 可用）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { idempotencyKey } from '@/utils/idempotencyKey'

describe('idempotencyKey', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  describe('getOrCreate', () => {
    it('第一次调用生成新 Key', () => {
      const key = idempotencyKey.getOrCreate('start-123')
      expect(key).toBeTruthy()
      expect(typeof key).toBe('string')
      expect(key.length).toBeGreaterThan(20)
    })

    it('相同 actionKey 在有效期内返回相同 Key（复用）', () => {
      const key1 = idempotencyKey.getOrCreate('report-456')
      const key2 = idempotencyKey.getOrCreate('report-456')
      expect(key1).toBe(key2)
    })

    it('不同 actionKey 生成不同 Key', () => {
      const key1 = idempotencyKey.getOrCreate('start-1')
      const key2 = idempotencyKey.getOrCreate('start-2')
      expect(key1).not.toBe(key2)
    })

    it('不同业务记录不复用同一个 Key', () => {
      const keyA = idempotencyKey.getOrCreate('rework-start-100')
      const keyB = idempotencyKey.getOrCreate('rework-start-200')
      expect(keyA).not.toBe(keyB)
    })
  })

  describe('getExisting', () => {
    it('未创建时返回 null', () => {
      expect(idempotencyKey.getExisting('nonexistent')).toBeNull()
    })

    it('已创建时返回已有 Key', () => {
      const key = idempotencyKey.getOrCreate('confirm-789')
      const existing = idempotencyKey.getExisting('confirm-789')
      expect(existing).toBe(key)
    })

    it('getExisting 不会创建新 Key', () => {
      const existing = idempotencyKey.getExisting('never-created')
      expect(existing).toBeNull()
      // 确认没有副产物
      expect(idempotencyKey.getExisting('never-created')).toBeNull()
    })
  })

  describe('clear', () => {
    it('清除后 getExisting 返回 null', () => {
      idempotencyKey.getOrCreate('report-999')
      idempotencyKey.clear('report-999')
      expect(idempotencyKey.getExisting('report-999')).toBeNull()
    })

    it('清除后 getOrCreate 生成新 Key', () => {
      const key1 = idempotencyKey.getOrCreate('report-888')
      idempotencyKey.clear('report-888')
      const key2 = idempotencyKey.getOrCreate('report-888')
      expect(key1).not.toBe(key2)
    })

    it('清除不存在的 key 不报错', () => {
      expect(() => idempotencyKey.clear('ghost')).not.toThrow()
    })
  })

  describe('完整生命周期', () => {
    it('生成 → 复用 → 清除 → 重新生成', () => {
      const actionKey = 'rework-complete-42'

      // 1. 生成
      const key1 = idempotencyKey.getOrCreate(actionKey)
      expect(key1).toBeTruthy()

      // 2. 复用（网络超时重试）
      const key2 = idempotencyKey.getExisting(actionKey)
      expect(key2).toBe(key1)

      // 3. 成功清除
      idempotencyKey.clear(actionKey)
      expect(idempotencyKey.getExisting(actionKey)).toBeNull()

      // 4. 重新生成（下次操作）
      const key3 = idempotencyKey.getOrCreate(actionKey)
      expect(key3).not.toBe(key1)
    })
  })
})
