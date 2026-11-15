# Production operations screen implementation plan

User approved direct implementation on 2026-10-24 after reviewing the reference projects and proposed production, quality, equipment and Andon features. Execute in the existing project so the running application receives the change. Preserve unrelated working-tree changes.

## Contract and scope

- Keep GET /admin/dashboard/overview compatible. Add authenticated GET /admin/dashboard/screen, query days (1–30, default 7) and optional positive workUnitId. Return the existing Result envelope with a typed DashboardScreenVO.
- Return summary, daily trend, process load, up to 12 priority work orders, up to 8 open alerts, top 5 defect categories, equipment totals and permitted production-unit filters. Use actual database aggregates; fill missing dates with zeros only.
- Calendar boundaries use Asia/Shanghai; actual events exclude future data, while the plan includes the current calendar day. Trend is planned-due vs actually completed work orders, in orders. First-pass quality counts confirmed inspection_round=1 inspection records, not product quantity. Equipment availability is enabled/normal equipment, not OEE or measured utilization. Work-order progress counts completed/skipped non-cancelled process records.
- ALL scope can see all records; every other scope fails closed to the current user's assigned work orders and associated production/quality records. Independent alerts are visible to their reporter/handler. Shared equipment is restricted to devices used on the user's assigned orders. Unit filters include active descendants.
- No schema changes. Use Spring-managed Controller → Service → Mapper/XML and DTO/VO types.
- Replace only the existing dashboard. User-confirmed original blue-white theme: --mes-bg-page/#f4f7fb, --mes-bg-card/#fff, --mes-primary/#2563eb, existing rounded cards and sans-serif typography, tabular numbers and a selectable assembly-stage diagram. Keep business navigation, login and permissions.
- Add 7/14/30-day and work-unit filters, refresh/pause, 30-second refresh, fullscreen, selectable process details, work-order details, permitted business links. Preserve last successful data on errors and label it stale; loading, empty and zero-denominator states are explicit.

## Task 1 — database-backed screen API

- [x] Create DashboardScreenQueryDTO, DashboardScreenVO, DashboardScreenController, DashboardScreenService, DashboardScreenServiceImpl, DashboardScreenMapper and XML.
- [x] Validate query parameters; use bound SQL parameters and shared scope fragments. Compute rates in service with null for no samples. Limit detail rows in SQL.
- [x] Add transaction-rolled-back integration fixtures checking descendant filters, time boundaries, no multi-stage production double counting, initial vs repeated inspection, SELF isolation and missing context. Add HTTP validation and unauthenticated checks.

## Task 2 — interactive production screen

- [x] Define matching TypeScript response types and API call in src/api/dashboard.ts.
- [x] Add reusable ECharts component with resize/disposal and reduced motion; add a data composable for refresh lifecycle and race cancellation.
- [x] Replace dashboard/index.vue and create focused screen components/styles. Add scoped layout treatment on /dashboard only.
- [x] Exercise filters, manual/automatic refresh, pause, fullscreen, keyboard focus, process selection, work-order dialog and navigation.

## Task 3 — verification and live delivery

- [x] Run focused backend tests, Maven package and frontend typecheck/build. Fix substantive failures.
- [x] Restart only the local backend process created for this session; retain MySQL/Redis and RabbitMQ-disabled development settings.
- [x] Validate the live API using the documented development account without printing tokens. Inspect real data, empty filters, invalid inputs and unauthorized requests.
- [x] Capture and inspect desktop/mobile browser screenshots and exercise interactions; check console errors and horizontal overflow.
- [x] Run independent code review using the repository's requested Superpowers review workflow, resolve findings, document verified scope and remaining limitations.

## References

Business reference: https://github.com/metaxk-company/free-mes . Visual/interaction reference: https://github.com/wenxingjun/threejs-factory-demo . Implementation is original; no third-party project code/assets are copied.

## Final evidence

Backend: 9 focused tests passed (7 MySQL, 2 HTTP binding). Frontend: 6 tests passed; typecheck and production build passed. Live Edge validation passed all 14 recorded API/UI scenarios, including process pagination and direct-route refresh, with no runtime errors or mobile overflow. Independent review findings were fixed and re-reviewed; final additions also passed review. See docs/dashboard-screen.md and the production-screen verification.json artifact.


## User visual correction — 2026-10-24

The user explicitly requested the original blue-white technology style. The dashboard now reuses the existing application tokens, navigation/header, typography, white cards, light blue process selection, blue chart series and white dialog/tooltips. No business logic or API changes. Typecheck/production build passed, and 15 live browser checks including palette assertions, full-screen and mobile layout passed. Evidence is in production-screen-blue-white/verification.json under this session's visualization directory.
