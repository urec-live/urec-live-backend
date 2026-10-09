# Testing: Equipment Issue Reporting and Out of Order Machines

Members can report a broken machine from the app, choosing **Not working** (won't move, weights don't engage) or **Damaged / hard to use** (torn padding, damaged seat). A description of at least 10 characters is required. Admins see every affected machine on the dashboard's **Equipment Issues** page and move each report through **New → Acknowledged → Repairing → Resolved**. The member who filed it follows that status in the app.

Admins can also mark a machine **Out of Order**, from the Equipment Issues page or the Equipment page.
- An out-of-order machine shows gray everywhere and can't be checked in to.
- Check-outs still work, so a workout that's already in progress can end.
- When the machine's last open report is resolved, it goes back to Available automatically.

On the dashboard:
- A new report shows up within about 10 seconds on any page, with no refresh: a toast, the sidebar badge and the Dashboard's Recent Activity.
- "Set all open reports to …" and resolving a report (one or all at once) ask "Are you sure?" first.
- The Dashboard's **Total Machines** card quietly notes how many machines are out of order.

Members can take back a report they filed by mistake, with **Reported by mistake? Withdraw** in My Reports, while it's still open.
- A withdrawn report is closed for good and no longer counts as open.
- Staff still see it, marked **Withdrawn by member**, but can't change it.
- The machine's status isn't touched. If staff took the machine out of order, they switch it back once they've checked it.
- Reports still can't be edited or deleted.

The feature spans three repos, each on the `equipment-issue-reports` branch. This guide covers all three. The app and admin repos each have a shorter `EQUIPMENT_ISSUES_TESTING.md` for their own part.

---

## 1. Automated tests

| Repo | Command | Tests | Needs |
|---|---|---|---|
| `urec-live-backend` | `mvn test -Dtest='*EquipmentIssue*,*OutOfOrder*,MachineStatusServiceTest'` | 78 | Java 21 + Maven. **No database or `.env`**: runs on in-memory H2 |
| `urec-live-admin` | `npx ng test --watch=false --browsers=ChromeHeadless` | 142 (the whole suite, help requests included) | Chrome |
| `urec-live-app` | `npm test` | 206 (the whole suite, help requests included; 78 are for equipment issues) | Node 18+ |

All three should finish with zero failures.

### Backend (78)
- **Unit tests** (Mockito, no Spring context): `EquipmentIssueServiceTest` (10), `AdminEquipmentIssueServiceTest` (17) and `MachineStatusServiceTest` (4). They cover:
  - descriptions are trimmed
  - a second open report on the same machine is rejected (409)
  - removed machines are rejected (404)
  - `resolvedAt` is set on resolve and cleared on reopen
  - machines are grouped and ordered (Not working first, then newest)
  - "set all" applies to every open report
  - resolving the last open report puts an out-of-order machine back in service, but not while another report is open
  - reopening a report doesn't take a machine out of service again
  - switching a machine back never turns In Use into Available
  - admin status changes are broadcast live and logged only when a machine goes out of or back into service
  - withdrawing: only the member who filed the report (404 for anyone else), only while it's open (409 once fixed or withdrawn), logged as `ISSUE_WITHDRAWN`; staff can't change a withdrawn report (409)
- **Integration tests** (the whole app on H2 with real JWTs, through the shared `ApiIntegrationTestSupport`):
  - **`EquipmentIssueApiIntegrationTest` (31):**
    - **withdrawing a report filed by mistake:**
      - The member who filed it can withdraw it while it's open.
      - It then shows as withdrawn in My Reports and among the admin's resolved reports, and stops counting as open in the machine summary, the admin list and the counts.
      - The member can report the machine again.
      - Other members and staff get 404; a second withdrawal, or withdrawing a fixed report, gets 409.
      - Staff can't reopen it (409).
      - DELETE, PUT and PATCH on a report still fail.
    - 401 without a token or with a bad one; 403 for members on admin endpoints
    - admins whose role is stored as `ADMIN` **or** `ROLE_ADMIN` are both let in
    - 9 kinds of invalid report → 400
    - unknown or removed machine → 404
    - My Reports only lists the caller's own reports, newest first
    - the machine-page summary never exposes reporter names or descriptions
    - the full status loop as the member sees it
    - resolved reports are hidden unless asked for
    - summary counts are correct
    - reports on removed machines drop out of the admin view
  - **`OutOfOrderIntegrationTest` (16):**
    - the admin switch works for both role names; members get 403
    - check-in to an out-of-order machine → 409 (by id, by code, and without a login)
    - check-out → 200, and the machine stays Out of Order
    - members can't set "out of order" in any letter case → 403
    - the Equipment page path
    - automatic return to service, both for a single report and for "set all"
    - an in-use machine is left alone
    - withdrawing the last report leaves an out-of-order machine out of service until staff switch it back
    - the machine status in the issues list and the out-of-order count in the summary
- **Mutation-checked:** these tests fail if you temporarily do any of the following:
  - remove the check-in guard or the automatic return
  - let anyone withdraw a report
  - let staff change a withdrawn report
  - let a fixed report be withdrawn

> A plain `mvn test` also runs the existing `UrecLiveBackendApplicationTests.contextLoads`. That test connects to the real database, so it fails unless the `.env` variables are loaded in your shell. This was already the case before this feature. To run the whole suite, load the variables first:
> ```bash
> set -a; source <(sed 's/\r$//' .env); set +a; mvn test
> ```

---

## 2. API smoke test (`scripts/smoke-test-equipment-issues.sh`)

This script runs 36 checks over real HTTP against a **running** backend:
- access control, reporting, and duplicate and validation errors
- the machine summary, and admin status changes reaching the member
- taking the machine out of order (check-in refused), and a resolve that puts it back in service
- a member withdrawing a second report filed by mistake: refused without a login, refused a second time, and staff can't reopen it

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
- If every check passes, the script exits 0 and prints `36 passed, 0 failed`.
- Other members' open reports on the same machine keep it out of order after the resolve. In that case the script says so and skips that one check.
- It's safe to re-run: the script resolves any report it left open on the same machine.
- **It never leaves a machine out of order:** an exit trap switches the machine back on, even if a check fails part-way.
- **It writes real data**: two reports, one resolved and one withdrawn by the end, plus activity-log entries. It also briefly takes the machine out of service. Run it against a dev database, not production. If your `.env` points at the database production uses, create a Neon branch for testing.
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
| 5 | Enter a real description and tap **Send report** | "Thanks, staff have been notified", with **View my reports** and **Done** buttons, and "Reported it by mistake? You can withdraw it there." |
| 6 | Tap **Done** | Back on the machine page, which now shows a red **Reported not working · Awaiting staff review** banner. |
| 7 | Report the same machine again | An inline error: "You already have an open report for this machine" with a **View my reports** link. |
| 8 | Profile → **My Equipment Reports** | The report appears with the status pill **Submitted** and the progress strip at step 1 (Submitted → Seen → Repairing → Fixed). Under it, a **Reported by mistake? Withdraw** link (steps 25 to 30 try it). |

### Other entry points
| # | Do this | Expect |
|---|---|---|
| 9 | Scan a machine's QR code | A **Report a Problem** button next to **Confirm Check-In**. It opens the same form without checking you in. |
| 10 | Check in to a machine, enter some reps/weight in the workout tracker, then tap **Report** in the tracker's header | The form opens for that machine and the tracker overlay disappears. Go back: the tracker returns with your reps and weight still there. |

### Admin: work the reports
| # | Do this | Expect |
|---|---|---|
| 11 | Stay on the **Dashboard** (the default page) while a member files a report | Within about 10 s, with no refresh: a toast "New equipment issue · Leg Press (LP01) · Not working" with **View**, the sidebar badge goes up, and Recent Activity shows "Issue Reported". Start with no reports awaiting review and the sidebar open: when the badge appears, the sidebar keeps its width and doesn't cover the page. It works on any admin page. There's no chime; that's kept for help requests. |
| 12 | Open **Equipment Issues** | Four stat cards (Awaiting review, Acknowledged, Repair in progress, Machines affected; "Machine affected" when it's 1) and one panel per machine. Not-working machines are listed first. Panels with new reports start open. Each report shows the reporter, the time, a severity chip and the full description. |
| 13 | Set a report to **Acknowledged** | No dialog. A snackbar appears, the badge and stat cards update, and the change is still there after a page refresh. |
| 14 | In the member app, pull to refresh My Reports | The status now reads **Seen by staff**. The machine banner reads **Staff are aware**. |
| 15 | Use **Set all open reports to → Repairing** on a machine | An "Are you sure?" dialog: "This sets the open report on Leg Press to Repairing." With several reports it reads "all N open reports". **Cancel** changes nothing and puts the toggle back. **Yes, set all to Repairing** changes every open report on that machine. The member sees **Repair on the way**, and the banner reads **Repair in progress**. |
| 16 | Search by machine name or code, and filter by severity | The list narrows. With no matches it shows "No machines match your filters". |
| 17 | Stop the backend and change a status | A "Failed to update status" snackbar appears, and the toggle goes back to its previous value. |

### Out of order
| # | Do this | Expect |
|---|---|---|
| 18 | On a machine's panel, turn on **Out of order** | A snackbar says "... marked out of order". The panel header shows an **Out of order** chip, and "Machines affected" shows "1 out of order". Within about 10 s, the small line under the Dashboard's **Total Machines** changes from a grey "0 out of order" to a darker "1 out of order". |
| 19 | In the app, look at the Equipment tab and the floor map (no refresh needed) | The machine turns gray with an **Out of order** badge, live. The floor map marker is gray and the legend lists Out of Order. The machine page shows "Staff have taken this machine out of service…" in place of **Scan QR to Check In**. |
| 20 | Try to check in: scan its QR code and tap **Confirm Check-In**, or use **Start** in a workout's machine list | The scan screen says "This machine is out of order…". In the workout list the machine is grayed out with no **Start** button. |
| 21 | Check in to a machine first, have the admin mark it out of order, then end the workout | The workout ends and saves normally, and the machine stays out of order. |
| 22 | Resolve the machine's last open report. First use **Set all open reports to → Resolved** and press **Cancel**. Then use the report's own **Resolved** toggle and confirm | Both ask "Are you sure?" first and end with "Leg Press goes back in service." "Set all" says "This resolves the open report on Leg Press. Members will see it as Fixed." (with several reports: "all N open reports", **Yes, resolve all**). **Cancel** changes nothing and puts the toggle back. The single toggle says "This resolves jdoe's report on Leg Press. They'll see it as Fixed." After **Yes, resolve**, the snackbar adds "back in service", the machine shows Available again in the admin and the app, and the member sees **Fixed**. |
| 23 | Equipment page → edit a machine with no reports → status **Out of Order** | Check-in is blocked in the app. Setting it back to **Available** restores it. |
| 24 | Activity page | **Out of Order** and **Back in Service** events, alongside **Issue Reported** and **Issue Updated**. The dashboard feed shows them with a build/warning icon. |

### Withdraw a report filed by mistake
| # | Do this | Expect |
|---|---|---|
| 25 | Admin: open **Equipment Issues** and turn **Auto-refresh** off. Member: file a report, then Profile → **My Equipment Reports** → **Reported by mistake? Withdraw** | The card asks "Withdraw this report?", says staff will see it was reported by mistake and that it can't be undone, and offers **Keep report** and **Withdraw report**. Nothing is sent yet. |
| 26 | Tap **Keep report** | The question closes and the report is unchanged. |
| 27 | Tap the link again, then **Withdraw report** | The pill turns gray **Withdrawn** and the progress strip is replaced by "You withdrew this report on …". The link is gone. The machine page's warning banner disappears (unless someone else has an open report). You can report the machine again. |
| 28 | Admin, without refreshing: change that report's status | "jdoe withdrew this report, so it can't be changed", and the list reloads without it. The badge and stat cards drop within about 10 s. |
| 29 | Admin: turn on **Show resolved** | The report is there with a gray **Withdrawn by member** chip in place of the status buttons, and "Withdrawn by jdoe … · reported by mistake". The Activity page (filter **Issue Withdrawn**) and the Dashboard feed (undo icon) show it. |
| 30 | Mark a machine out of order, then have its only reporter withdraw their report | The machine stays out of order. Switch it back yourself once it's checked. |

### Access checks
- A member account can't use `/api/admin/equipment-issues/**` (403). The admin dashboard login already rejects non-admins.
- The member check-in endpoints (`/api/machines/**`, no login required) refuse to set "Out of Order" (403) and refuse check-ins to out-of-order machines (409).
- Members can file, read and withdraw their own reports (`POST /api/equipment-issues/{id}/withdraw`). Withdrawing someone else's report returns 404, `DELETE`, `PUT` or `PATCH` on a report fails, and the admin status endpoints return 403.
- Every member issue endpoint requires a login. The machine-page summary (`GET /api/equipment-issues/equipment/{id}`) returns only counts and statuses, never who reported a problem or what they wrote.

---

## 4. Known issues that predate this feature
- **Admin role naming.** The admin dashboard's user form assigns the role `ADMIN`, but most admin controllers check `hasRole('ADMIN')`, which only matches `ROLE_ADMIN`. An admin promoted through the dashboard gets 403 on Equipment, Analytics and other pages, including the Equipment page's new "Out of Order" option. The Equipment Issues endpoints, including its out-of-order switch, accept both names.
- **Admin-printed QR codes don't scan in the app.** The admin QR dialog encodes the bare equipment code, but the app's scanner expects a URL or JSON payload. Use QR codes generated by `urec-live-app/scripts/generate-qr.js` to test the scan entry point.
- **The admin Live Monitor page likely crashes on load.** Its production bundle references Node's `global` (via `sockjs-client`), which browsers don't define. This is probably why it's commented out of the sidebar. Its new Out of Order styling is in place but can't be checked until that's fixed. A one-line fix is to define `window.global = window` before the app loads.
