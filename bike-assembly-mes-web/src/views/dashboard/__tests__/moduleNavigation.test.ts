import { describe, expect, it } from 'vitest'
import { findModulePath } from '../moduleNavigation'

describe('dashboard business navigation', () => {
  const unrelatedRoutes = [
    { path: '/equipment/repair-order', meta: { title: '维修工单', perms: 'admin:repair:view' } },
    { path: '/report/work-order', meta: { title: '生产工单报表', perms: 'admin:report:view' } },
    { path: '/report/andon', meta: { title: '安灯告警报表', perms: 'admin:report:view' } }
  ]
  it('does not offer production actions for users with only repair/report menus', () => {
    expect(findModulePath(unrelatedRoutes, '工单')).toBeUndefined()
    expect(findModulePath(unrelatedRoutes, '安灯')).toBeUndefined()
    expect(findModulePath(unrelatedRoutes, '工序')).toBeUndefined()
  })
  it('uses the exact permitted production route even when other titles match', () => {
    expect(findModulePath([...unrelatedRoutes, { path: '/production/work-order', meta: { perms: 'admin:work-order:view' } }], '工单')).toBe('/production/work-order')
  })
})
