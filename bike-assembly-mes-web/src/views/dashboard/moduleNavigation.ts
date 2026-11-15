export type DashboardModule = '工单' | '工序' | '安灯'
const modulePermissions: Record<DashboardModule, string> = {
  工单: 'admin:work-order:view',
  工序: 'admin:work-process:view',
  安灯: 'admin:andon:view'
}

/** Menu titles are not identifiers: repair orders and reports can share their words. */
export function findModulePath(routes: ReadonlyArray<{ path: string; meta: Record<string, unknown> }>, module: DashboardModule) {
  return routes.find(route => route.meta.perms === modulePermissions[module])?.path
}
