# Testing: "Call staff" help requests

Members who aren't sure how to use a machine can press **Call staff**, much like a cabin-crew call button.
- The request starts as **Request received**. Staff answer from the admin dashboard with **On the way** or **Too busy**, and close it with **Done helping**.
- The member follows each step in the app. They can close the request themselves with **I received help**, behind an "Are you sure?" prompt, or **Cancel** it.
- A request nobody updates for 30 minutes expires.
- While they wait, the member gets "watch how it's done" demos for the exercise. For now these are **placeholder media**, a short sample video and a "Coming soon" GIF, played in the app's **demo player** screen.

The feature spans three repos, each on the `staff-help-requests` branch. This guide covers all three. The app and admin repos each have a shorter `HELP_REQUESTS_TESTING.md` for their own part.

---

## 1. Automated tests

| Repo | Command | Tests | Needs |
|---|---|---|---|
| `urec-live-backend` | `mvn test -Dtest='HelpRequest*,HelpDemoLinksTest,AdminHelpRequestServiceTest'` | 124 | Java 21 + Maven. **No database or `.env`**: runs on in-memory H2 |
| `urec-live-admin` | `npx ng test --watch=false --browsers=ChromeHeadless` | 69 | Chrome |
| `urec-live-app` | `npm test` | 128 | Node 18+ |

All three should finish with zero failures. In Git Bash, put Maven on the `PATH` first if it isn't already: `export PATH="$PATH:/c/tools/apache-maven-3.9.9/bin"`.

### Backend (124)
- **Unit tests** (no Spring context, apart from one lightweight settings check):
  - **`HelpRequestTest` (19), the entity rules:**
    - a new request starts as Request received; only the first three statuses count as open
    - staff can only respond with On the way or Too busy
    - the first response time is kept when staff respond again; responding with the current status changes nothing
    - a closed request can't be responded to or closed again
    - closing records who closed it and when
  - **`HelpDemoLinksTest` (27), the demo media:**
    - the exercise the member is doing wins; otherwise up to 3 of the machine's exercises, alphabetically; otherwise one demo named after the machine (without its number)
    - every demo plays the placeholder video
    - exercises with no GIF, a blank one or the seeded dead `via.placeholder.com` images get the placeholder GIF
    - a real GIF set by an admin wins over the placeholder and isn't flagged as one
    - blank settings switch the placeholders off, and settings are trimmed
    - the Spring wiring: the built-in placeholders when nothing is set, and `app.help-requests.placeholder-*-url` swaps in other media
  - **`HelpRequestServiceTest` (20), the member side:**
    - calling staff by machine id or QR code
    - a second open request → 409; an unknown or removed machine → 404; no machine at all → 400
    - the exercise is matched on the machine first, and the machine code the workout tracker sends as an "exercise" is ignored
    - the response carries the placeholder demo media
    - another member's request is reported as missing (404)
    - Received help and Cancel; closing a closed request → 409; a staff update at the same moment → 409
  - **`AdminHelpRequestServiceTest` (25), the staff side:**
    - On the way records the staff member and is logged; Too busy is logged differently; repeating the current status is a no-op
    - switching from Too busy to On the way keeps the first response time
    - other statuses and a missing status → 400; a closed request → 409; an unknown one → 404; a member closing it at the same moment → 409
    - Done helping works from any open status
    - the history limit is clamped
    - expiry closes every idle request and logs each one
- **Integration tests** (`HelpRequestApiIntegrationTest`, 33): the whole app on H2 with real JWTs, through `HelpRequestApiTestSupport`.
  - Every endpoint needs a login (401). Members get 403 on the staff endpoints. Staff whose role is stored as `ADMIN` **or** `ROLE_ADMIN` can both work the queue.
  - Members can't see or close someone else's request (404).
  - Calling staff returns Request received with the placeholder demo media.
  - Seeded machines get the placeholder GIF instead of their dead images. An admin-set GIF wins. A machine with no exercises gets one demo named after the machine.
  - 6 kinds of invalid request → 400; an unknown or removed machine → 404.
  - One open request per member (409), but another member can still call staff to the same machine.
  - `GET /me/active` is 204 until the member calls staff.
  - The full flows: Done helping, Received help and Cancel. A closed request can't be reopened or closed again (409).
  - Staff can only set On the way or Too busy, the status body is required, and unknown requests are 404.
  - The queue is oldest first; the history is newest first.
  - Idle requests expire, after which the member can call staff again. A staff response keeps a request from expiring.
- **Mutation-checked:** temporarily breaking the placeholder logic makes these tests fail. The breaks tried were: flagging a real GIF as a placeholder, ignoring a real GIF, ignoring the video setting, dropping the placeholder flag, and not treating a blank setting as off.

> A plain `mvn test` also runs the existing `UrecLiveBackendApplicationTests.contextLoads`. That test connects to the real database, so it fails unless the `.env` variables are loaded in your shell. This was already the case before this feature. To run the whole suite, load the variables first, as TERMINAL 1 in `commands.txt` does, then run `mvn test`.

---

## 2. API smoke test (`scripts/smoke-test-help-requests.sh`)

This script runs 33 checks over real HTTP against a **running** backend:
- access control
- calling staff, and the demo links in the response
- **the placeholder video and GIF actually load** from their hosts, so this part needs internet access
- the one-open-request rule
- staff Too busy → On the way, with the member seeing each step but never the staff member's username
- Done helping, Received help and Cancel
- 409s on closed requests, and the Recently closed list

```bash
# Terminal 1: start the backend with your .env loaded (TERMINAL 1 in commands.txt)

# Terminal 2, from urec-live-backend (passwords are in test-accounts.txt)
MEMBER_USER=urecuser MEMBER_PASS='<password>' \
ADMIN_USER=urecadmin ADMIN_PASS='<password>' \
./scripts/smoke-test-help-requests.sh
```

- Optional variables:
  - `API`: defaults to `http://localhost:8080/api`.
  - `EQUIPMENT_ID`: defaults to the first machine returned by `GET /machines`.
- If every check passes, the script exits 0 and prints `33 passed, 0 failed`.
- It's safe to re-run. It cancels a request an earlier run left open, and an exit trap cancels anything still open if a check fails part-way.
- **It writes real data**: three closed help requests plus activity-log entries. Run it against a dev database, not production. If your `.env` points at the database production uses, create a Neon branch for testing.

---

## 3. Manual end-to-end walkthrough

### Setup
1. **Backend.** Run TERMINAL 1 from `commands.txt`. On first start, Hibernate creates the `help_requests` table. This only adds a table.
2. **Admin dashboard** (`urec-live-admin`). Run TERMINAL 2 (`npx ng serve`), open http://localhost:4200 and log in as `urecadmin`.
3. **App** (`urec-live-app`). Run TERMINAL 3 and log in as `urecuser`.
   - **No dev-build rebuild is needed.** The demo player only uses `expo-image` and `expo-web-browser`, which are already in the build, so `npx expo start --dev-client --android` is enough.

### Member: call staff
| # | Do this | Expect |
|---|---|---|
| 1 | Equipment tab → open any machine | A **Call staff** card near the top of the machine page. |
| 2 | Tap **Call staff** | The help screen: "Help is on its way", a stepper on **Request received**, and the status card. Under "While you wait, watch how it's done" there's a how-to video and a GIF demo link for each exercise, then "These are placeholder clips for now. Real demos are coming soon." |
| 3 | Go back | A banner on every screen reads "Help: Request received · *machine*  View ›". The machine page's card shows the request's status instead of the button. |
| 4 | Open a different machine | Its card points to the request that's open at the first machine. A member can only have one open request at a time. |

### Member: the demo player (placeholder media)
| # | Do this | Expect |
|---|---|---|
| 5 | On the help screen, tap "*exercise*: how-to video" | The **demo player** opens on its **Video** tab: a black panel with a play button. A yellow note reads "Placeholder video: the real how-to demo for *exercise* is coming soon." If you're checked in to a machine, the workout tracker hides here. The help banner stays at the top, so status changes still show. |
| 6 | Tap the play button | A 5-second sample clip (a flower) plays in the in-app browser's video player. Close it to come back to the player. |
| 7 | Tap the **GIF** tab | A spinner, then an animated "Coming soon" GIF plays in the screen itself. The note now reads "Placeholder GIF: …". |
| 8 | Tap **Pause**, then **Play** | The GIF freezes, then carries on. |
| 9 | Go back and tap "*exercise*: GIF demo" | The player opens straight on the GIF tab. |
| 10 | Switch off the emulator's network and open the GIF tab | "Couldn't load the GIF. Check your connection." with **Try again**. Restore the network and tap it: the GIF loads. |
| 11 | Optional: the web build (press `w` in Metro) | The video plays inline in the page with the browser's own controls. There's no Pause button for the GIF, because browsers can't pause GIFs. |

### Admin: the queue
| # | Do this | Expect |
|---|---|---|
| 12 | Have the member call staff while the dashboard is open on any page | Within about 5 seconds, all of these appear: a toast "New help request · *machine* (*code*)" with **View**; a two-tone chime; "(1)" at the start of the tab title; and an amber badge on **Help Requests** in the sidebar. Browsers block sound until you've clicked or pressed a key on the page once since it loaded. |
| 13 | Open **Help Requests** | Stat cards for New, On the way and Too busy. Each open request has a card showing the machine, its code, a status chip, the member, the exercise, the floor, and "Waiting N min" (updated every 15 s). Each card has **On the way**, **Too busy** and **Done helping** buttons. |
| 14 | Click **On the way** | A "Marked On the way" snackbar, and the card reads "urecadmin is on the way". The badge and the tab-title count go down. Within about 5 seconds the app shows **On the way**, the phone vibrates, and the banner turns green. |
| 15 | Click **Too busy** | The app shows **Too busy** ("Staff are busy right now…"). The badge counts the request again, because it still needs someone. |
| 16 | Switch **Sound** off, have the member call staff again, then switch it back on | With sound off, no chime plays, and the setting survives a reload. Switching it on plays the chime once. |

### Closing a request
| # | Do this | Expect |
|---|---|---|
| 17 | In the app, tap **I received help**, then **Not yet** | Nothing changes. |
| 18 | Tap **I received help** → **Yes, I got help** | "Glad you got the help you needed!" Within about 5 seconds the card leaves the admin queue. Under **Recently closed** it's listed as "Member got help". |
| 19 | Call staff again; in the admin click **Done helping** | A "Marked done helping" snackbar. The app shows "Staff marked this as done. Glad we could help!", and the banner says so until the member dismisses it. Recently closed shows "Done helping" with the time to the first response. |
| 20 | Call staff again and tap **Cancel request** → **Cancel request** | "You cancelled this request." Recently closed shows "Cancelled by member". |
| 21 | Call staff, cancel in the app, then click **On the way** in the admin before the page refreshes | A "This request was already closed or changed" snackbar, and the queue refreshes without the card. |
| 22 | Check in to a machine by scanning its QR code, then tap **Call staff** in the workout tracker's header | The help screen opens for that machine. The tracker hides on the help and player screens and comes back with your sets intact. |
| 23 | Call staff, then close the app completely and reopen it | The open request is still shown in the banner and on the help screen. |
| 24 | Leave a request untouched for 30 minutes | It expires. The app says "This request expired after 30 minutes without an update…", and Recently closed shows "Expired". |
| 25 | Activity page | **Help Requested**, **Help Response** and **Help Closed** events, each of which can be used as a filter. |

### Access checks
- Every member endpoint (`/api/help-requests/**`) needs a login. The staff endpoints (`/api/admin/help-requests/**`) need `ADMIN` or `ROLE_ADMIN`, and members get 403.
- Members can only see and close their own requests. Anyone else's request answers 404, so ids reveal nothing.
- The member's view never includes the staff member's username.

---

## 4. Swapping in real demo media

The placeholder URLs are settings, so changing them needs no code change:

| Setting | Environment variable | Default |
|---|---|---|
| `app.help-requests.placeholder-video-url` | `APP_HELPREQUESTS_PLACEHOLDERVIDEOURL` | `https://mdn.github.io/shared-assets/videos/flower.mp4` (CC0, from MDN) |
| `app.help-requests.placeholder-gif-url` | `APP_HELPREQUESTS_PLACEHOLDERGIFURL` | `https://media.giphy.com/media/KzeZ3OXHoSDVZH9cmy/giphy.gif` |

- A blank value switches that placeholder off, so the app leaves out that link.
- A GIF URL set on an exercise in the admin's **Exercises** page already takes priority over the placeholder GIF.
- Per-exercise videos would need a `videoUrl` on `Exercise`, which isn't built yet.
- Both defaults are hosted by third parties. The smoke test checks that they still load.

---

## 5. Known gaps
- **Polling, not WebSocket.** The app checks an open request every 5 seconds, and the dashboard checks the queue every 5 seconds. The dashboard can't use the WebSocket because `sockjs-client` crashes in the browser (`global` is undefined). Status changes therefore take up to about 5 seconds to show.
- **Dashboard home feed icons.** The Dashboard page's recent-activity feed shows help events with its generic "logout" icon. The `equipment-issue-reports` branch rewrites that icon logic, so a help icon will be added when the two branches are merged.
- **No inline video on Android yet.** The app has no native video module, and adding one (`expo-video`) needs a dev-build rebuild. Until then, the video plays in the in-app browser's player. The video component is isolated (`components/DemoVideo.tsx`), so switching to `expo-video` later only touches that file.
