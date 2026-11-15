# MES review fixes — 2026-10-24

User approved the four findings and requested implementation.

1. Transactional SELECT FOR UPDATE for stock execution, editing and deletion. Reject changing/deleting executed documents and writing executed status via CRUD.
2. Inject Spring ObjectMapper into production/work-order/inspection services. Separate record IDs from client keys; internal QC callbacks must preserve the parent snapshot.
3. Atomic timeout/failed-record recovery based on updated_at. Hold the record lock during business execution. Commit the response snapshot with business changes and record failure after rollback.
4. Isolated MySQL tests: stock concurrency, CRUD guards, lease recovery, rollback, real partial-report and QC replay. Prefer Testcontainers; use a disposable local schema when Docker is unavailable.
5. Compile, run regressions and review the final diff. Preserve API paths/payloads and database schema.
