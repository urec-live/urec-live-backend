# Testing: Equipment Issues and Out-of-Order Machines

*Audited against the checked-out repositories on October 23, 2026.*

## Current integration status

The Spring Boot backend implements and tests equipment-issue reporting and out-of-order rules. The checked-out mobile app and admin dashboard do **not** contain equipment-issue screens, services, routes, or tests. Do not use the older three-client walkthrough as current acceptance evidence.

Backend behavior implemented in source:

- Members can create, list, and withdraw their own reports through `/api/equipment-issues/*`.
- Admins can group/filter reports, update one or all open statuses, and put equipment out of order through `/api/admin/equipment-issues/*`.
- Out-of-order machines reject check-in but allow an existing workout to check out.
- Resolving the last open report returns an out-of-order machine to Available; withdrawing a report does not.
- Admin status changes publish machine updates and write activity-log events.

Client work is still required before this is an end-to-end member/admin feature.

## Automated backend tests

Use JDK 21.

```bash
mvn test -Dtest='*EquipmentIssue*,*OutOfOrder*,MachineStatusServiceTest'
```

These tests use the H2 `test` profile through `ApiIntegrationTestSupport` and cover validation, ownership, authorization, grouping, lifecycle transitions, withdrawal, out-of-order check-in rules, broadcasts, and activity logging.

A plain `mvn test` also includes `UrecLiveBackendApplicationTests.contextLoads`, which does not select the test profile and therefore needs the normal database/JWT environment. That baseline issue is separate from the focused suite.

## API smoke test

`scripts/smoke-test-equipment-issues.sh` exercises the real HTTP API against a running backend.

```bash
# Terminal 1: load a development environment, then start the backend
mvn spring-boot:run

# Terminal 2
MEMBER_USER=<member username> MEMBER_PASS='<password>' \
ADMIN_USER=<admin username> ADMIN_PASS='<password>' \
./scripts/smoke-test-equipment-issues.sh
```

Optional variables:

- `API` defaults to `http://localhost:8080/api`.
- `EQUIPMENT_ID` defaults to the first machine returned by `GET /machines`.

The smoke script writes reports and activity-log rows and temporarily changes equipment state. Run it only against a development database. Its cleanup trap attempts to return the machine to service, but confirm final state after any interrupted run.

## Manual backend contract checks

Until client UI is implemented, validate with an API client:

1. Authenticate a member and an admin.
2. Member: create a valid report and confirm a duplicate open report returns 409.
3. Member: confirm `GET /api/equipment-issues/me` shows only that member's reports.
4. Admin: confirm the report appears under `GET /api/admin/equipment-issues` and summary counts change.
5. Admin: put the machine out of order.
6. Confirm check-in returns 409, while checkout can complete without changing the out-of-order status.
7. Admin: advance the report to resolved and verify the final open-report rule returns the machine to service.
8. Create another report, withdraw it as its owner, and verify another member gets 404 and admin status changes get 409.

## Required client follow-up

- Mobile: report form, machine warning, “My reports,” withdrawal, and API service.
- Admin: equipment-issue route, queue/status UI, summary/badge/alerts, and out-of-order controls.
- Shared: decide the QR payload contract. The admin currently prints a bare equipment code, while `app/utils/qr.ts` accepts only the documented URL or JSON payload.
- Add client tests and repeat the cross-client live-status walkthrough only after those screens exist.

## Validation boundary

Passing backend tests proves the backend contract covered by those tests. It does not prove mobile/admin integration, QR compatibility, hosted WebSockets, production database safety, or a deployed release.
