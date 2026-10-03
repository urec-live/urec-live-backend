# Testing: Equipment Issue Reporting

Members can report a broken machine from the app, choosing **Not working** (won't move, weights don't engage) or **Damaged / hard to use** (torn padding, damaged seat). A description of at least 10 characters is required. Admins see every affected machine on the dashboard's **Equipment Issues** page and move each report through **New → Acknowledged → Repairing → Resolved**. The member who filed it follows that status in the app.

The feature spans three repos, each on the `equipment-issue-reports` branch. This guide covers all three. The app and admin repos each have a shorter `EQUIPMENT_ISSUES_TESTING.md` for their own part.

---

## 1. Automated tests

| Repo | Command | Tests | Needs |
|---|---|---|---|
| `urec-live-backend` | `mvn test -Dtest='*EquipmentIssue*'` | 41 | Java 21 + Maven. **No database or `.env`** — runs on in-memory H2 |
| `urec-live-admin` | `npx ng test --watch=false --browsers=ChromeHeadless` | 32 | Chrome |
| `urec-live-app` | `npm test` | 48 | Node 18+ |

All three should finish with zero failures.

### Backend (41)
- **`EquipmentIssueServiceTest` (6) and `AdminEquipmentIssueServiceTest` (7).** Mockito unit tests for the business rules:
  - descriptions are trimmed
  - a second open report on the same machine is rejected (409)
  - removed machines are rejected (404)
  - `resolvedAt` is set on resolve and cleared on reopen
  - setting a status that's already set does nothing
  - machines are grouped and ordered (Not working first, then newest)
  - "set all" applies to every open report
- **`EquipmentIssueApiIntegrationTest` (28).** Boots the whole app (real controllers, JWT filter, `@PreAuthorize`, validation, JPA queries) against H2, using real JWTs. It covers:
  - 401 without a token or with a bad one; 403 for members on admin endpoints
  - admins whose role is stored as `ADMIN` **or** `ROLE_ADMIN` are both let in
  - 9 kinds of invalid report → 400 (missing fields, unknown severity, blank, under 10 characters once trimmed, over 1000, malformed JSON)
  - unknown or removed machine → 404
  - My Reports only lists the caller's own reports, newest first
  - the machine-page summary never exposes reporter names or descriptions
  - the full status loop as the member sees it
  - resolved reports are hidden unless asked for
  - summary counts are correct
  - reports on removed machines drop out of the admin view

> A plain `mvn test` also runs the existing `UrecLiveBackendApplicationTests.contextLoads`. That test connects to the real database, so it fails unless the `.env` variables are loaded in your shell. This was already the case before this feature. To run the whole suite, load the variables first:
> ```bash
> set -a; source <(sed 's/\r$//' .env); set +a; mvn test
> ```

---

## 2. API smoke test (`scripts/smoke-test-equipment-issues.sh`)

This script runs 22 checks over real HTTP against a **running** backend: access control, reporting, duplicate and validation errors, the machine summary, admin status changes reaching the member, and a final resolve.

```bash
# Terminal 1: start the backend with your .env loaded
set -a; source <(sed 's/\r$//' .env); set +a
mvn spring-boot:run

# Terminal 2
MEMBER_USER=<member username> MEMBER_PASS='<password>' \
ADMIN_USER=<admin username>   ADMIN_PASS='<password>' \
./scripts/smoke-test-equipment-issues.sh
```

- Optional variables:
  - `API`: defaults to `http://localhost:8080/api`.
  - `EQUIPMENT_ID`: defaults to the first machine returned by `GET /machines`.
- If every check passes, the script exits 0 and prints `22 passed, 0 failed`.
- It's safe to re-run: the script resolves any report it left open on the same machine.
- **It writes real data**: one report, resolved at the end, plus activity-log entries. Run it against a dev database, not production. If your `.env` points at the database production uses, create a Neon branch for testing.
- You'll need a member account (register one in the app) and an admin account.

---

## 3. Manual end-to-end walkthrough

### Setup
1. **Backend.** Load `.env` and run `mvn spring-boot:run`, as in section 2. On first start, Hibernate creates the `equipment_issue_reports` table. This only adds a table.
2. **Admin dashboard** (`urec-live-admin`). Run `npx ng serve`, open http://localhost:4200, and log in as an admin.
3. **App** (`urec-live-app`). Run `npx expo start`.
   - On a phone with Expo Go on the same Wi-Fi, the app finds the backend on port 8080 by itself.
   - In a browser (`w`), it uses `http://localhost:8080`.
   - To point it somewhere else, set `EXPO_PUBLIC_BACKEND_ORIGIN`.
   - Log in as a member.

### Member: report a problem
| # | Do this | Expect |
|---|---|---|
| 1 | Equipment tab → open any machine | A **Report a problem** button under "Scan QR to Check In". No warning banner yet. |
| 2 | Tap **Report a problem** | A form with the machine's name, two choices (**Not working**, **Damaged / hard to use**) and a description box showing `0 / 1000` and "At least 10 characters". |
| 3 | Type `broken` without choosing a type | **Send report** stays disabled. |
| 4 | Choose **Not working**, then type `   short    ` | Still disabled, because the text is under 10 characters once trimmed. |
| 5 | Enter a real description and tap **Send report** | "Thanks, staff have been notified", with **View my reports** and **Done** buttons. |
| 6 | Tap **Done** | Back on the machine page, which now shows a red **Reported not working · Awaiting staff review** banner. |
| 7 | Report the same machine again | An inline error: "You already have an open report for this machine" with a **View my reports** link. |
| 8 | Profile → **My Equipment Reports** | The report appears with the status pill **Submitted** and the progress strip at step 1 (Submitted → Seen → Repairing → Fixed). |

### Other entry points
| # | Do this | Expect |
|---|---|---|
| 9 | Scan a machine's QR code | A **Report a Problem** button next to **Confirm Check-In**. It opens the same form without checking you in. |
| 10 | Check in to a machine, enter some reps/weight in the workout tracker, then tap **Report** in the tracker's header | The form opens for that machine and the tracker overlay disappears. Go back: the tracker returns with your reps and weight still there. |

### Admin: work the reports
| # | Do this | Expect |
|---|---|---|
| 11 | Look at the sidebar | **Equipment Issues** has a red badge with the number of reports awaiting review. It refreshes every 60s. |
| 12 | Open **Equipment Issues** | Four stat cards (Awaiting review, Acknowledged, Repair in progress, Machines affected) and one panel per machine. Not-working machines are listed first. Panels with new reports start open. Each report shows the reporter, the time, a severity chip and the full description. |
| 13 | Set a report to **Acknowledged** | A snackbar appears, the badge and stat cards update, and the change is still there after a page refresh. |
| 14 | In the member app, pull to refresh My Reports | The status now reads **Seen by staff**. The machine banner reads **Staff are aware**. |
| 15 | Use **Set all open reports to → Repairing** on a machine | Every open report on that machine changes. The member sees **Repair on the way**, and the banner reads **Repair in progress**. |
| 16 | Set the report to **Resolved** | After the next refresh, the machine drops off the list. Turn on **Show resolved** to see it, marked "All resolved". The member sees **Fixed** with a full progress strip, and the machine banner is gone. |
| 17 | Search by machine name or code, and filter by severity | The list narrows. With no matches it shows "No machines match your filters". |
| 18 | Stop the backend and change a status | A "Failed to update status" snackbar appears, and the toggle goes back to its previous value. |
| 19 | Activity page | **Issue Reported** and **Issue Updated** events, filterable by event type. The dashboard's recent activity shows them with a warning icon. |

### Access checks
- A member account can't use `/api/admin/equipment-issues/**` (403). The admin dashboard login already rejects non-admins.
- Every member endpoint requires a login. The machine-page summary (`GET /api/equipment-issues/equipment/{id}`) returns only counts and statuses, never who reported a problem or what they wrote.

---

## 4. Known issues that predate this feature
- **Admin role naming.** The admin dashboard's user form assigns the role `ADMIN`, but most admin controllers check `hasRole('ADMIN')`, which only matches `ROLE_ADMIN`. An admin promoted through the dashboard gets 403 on Equipment, Analytics and other pages. The new Equipment Issues endpoints accept both names, the same way `AdminUserController` does. If other pages fail for your test admin, this is why.
- **Admin-printed QR codes don't scan in the app.** The admin QR dialog encodes the bare equipment code, but the app's scanner expects a URL or JSON payload. Use QR codes generated by `urec-live-app/scripts/generate-qr.js` to test the scan entry point.
