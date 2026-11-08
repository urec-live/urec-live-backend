# Testing: chat GIFs, stickers, reactions and moderation (backend)

Branch `chat-gifs-reactions` (same name in the app and admin repos) adds:
- **GIFs and stickers** in community chat and DMs, searched through KLIPY. The backend holds the API key and proxies the search (`GET /api/media/{gifs|stickers}/{trending|search}`). A sent GIF must be an https URL on a KLIPY host, so members can't post other image links as "GIFs".
- **Emoji reactions**: 👍 ❤️ 😂 🔥 💪 😮, one per member per message, updated live.
- **Delete your own message.** Members see "This message was deleted"; the row is kept for moderation.
- **Report someone else's message**, plus the admin **Chat Reports** queue (dismiss, or remove as "Removed by staff").
- Fixes to existing chat:
  - Community history returned the **oldest** 50 messages, so new ones never loaded once there were more than 50. It now returns the newest page, and older pages load with `?before=<id>`. DM history pages the same way.
  - The server checks sends: at most 500 characters, a recipient who exists, and not yourself. Rejections go back to the sender on `/user/queue/errors`, where they used to be dropped silently.
  - Each send carries a `clientId`, so the app can show it as pending and retry without saving it twice.

The app and admin guides (`urec-live-app/CHAT_MEDIA_TESTING.md`, `urec-live-admin/CHAT_MEDIA_TESTING.md`) cover their screens; this one has the setup and the API checks.

## Setup

1. **KLIPY key** (only needed for GIFs and stickers; everything else works without it):
   - Sign up at <https://klipy.com/developers> and create an API key. Test keys are rate-limited (about 100 requests a minute); request production access from the same panel before launch.
   - Add it to `urec-live-backend/.env`: `KLIPY_API_KEY=<your key>`. TERMINAL 1 in `commands.txt` (and `runall.sh`) exports it with the rest.
   - For Railway, add `KLIPY_API_KEY` to the backend service's variables.
2. **Database:** on first start, `ddl-auto=update` adds `kind`, `media_*`, `client_id`, `deleted_at` and `removed_by_staff` to `community_messages` and `direct_messages`, and creates `chat_message_reactions` and `chat_message_reports`.
   - It also adds unique constraints `uk_cm_sender_client` and `uk_dm_sender_client` on `(sender_username, client_id)`, and a `version` column (default 0) to `chat_message_reports`. Rows without a `client_id` (all older messages) never clash.
   - If a database already ran an earlier build of this branch, it keeps the old plain indexes `idx_cm_sender_client` / `idx_dm_sender_client`. They're harmless and can be dropped. Hibernate skips a unique constraint it can't create without telling you, so check `\d community_messages` and `\d direct_messages` in psql after the first start. If a constraint is missing, delete the duplicate test rows and restart.
   - Every change only adds something, and each new column has a default, so a `main` backend on the same database keeps working.
   - Still, starting this branch against the shared Neon database changes its schema for everyone. Use a local Postgres if you'd rather not.
3. Two app accounts. `test-accounts.txt` creates `urecadmin` (admin) and `urecuser`; the API checks below call them ALICE and BOB.

## Automated tests

From a shell that never sourced `.env` (see the note in `CLAUDE.md`'s Testing section):

```bash
mvn test -Dtest='ChatMessagingApiIntegrationTest,KlipyClientTest,ChatEntitiesTest,ChatMessageServiceTest,AdminChatReportServiceTest'
```

Expected: `Tests run: 69, Failures: 0, Errors: 0`. The full `mvn test` runs 294; only `UrecLiveBackendApplicationTests.contextLoads` fails without the real database variables, and it fails the same way on `main`.

| Test | What it covers |
|---|---|
| `ChatMessagingApiIntegrationTest` (40) | **Sending:** text is trimmed, saved and broadcast with its `clientId`; old `{content}`-only payloads still work. Rejections go to the sender's error queue with the code (`TOO_LONG` at 501 characters, `EMPTY`, `INVALID_CLIENT_ID`) and nothing is saved; exactly 500 is fine. Frames whose JSON doesn't fit the request (`"kind":"BOGUS"`, a non-numeric width, not JSON, an empty body) go through the real inbound channel and still reach the error queue as `INVALID_MESSAGE`, with the `clientId` when the JSON has one. The database rejects a second row with the same sender and `clientId` (in both tables) but allows any number without one. GIFs and stickers are saved with media and no text. Rejected media: non-https, other hosts, `klipy.com.evil.io` lookalikes, `javascript:`, URLs over 1000 characters, a bad preview URL, missing media. Bad sizes are dropped instead. Retrying with the same `clientId` saves once (the same id from someone else is a new message). Sends without a signed-in user are dropped. DMs go to both people's queues and nowhere else; sending to yourself or to an unknown or blank recipient is rejected. One send runs through the real STOMP inbound channel. **History:** the newest page oldest first; `before` paging; the >50 regression; the page size is clamped; DM history holds only that conversation; inbox labels for a GIF and a deleted message. **Reactions:** set, switch and remove (one per member), re-reacting with the same emoji, history includes reactions, only the six emoji (a bare ❤ counts as ❤️), DM outsiders get 404, unknown channels and messages 404, sign-in required. **Delete:** sender only (403 otherwise; 404 for DM outsiders), a placeholder with no content, media or reactions, the row kept, deleting twice harmless, reacting to or reporting a deleted message is a 409. **Reports:** created once (409 twice), not your own (400), reason required and valid, note at most 300 characters, a blank note dropped; DM outsiders 404. **Admin:** members 403, signed out 401, `ROLE_ADMIN` accepted; grouped by message with the original content after a delete; summary counts; dismiss (and a second dismiss is a 409, with the report visible under `includeResolved`); remove broadcasts "removed by staff", actions the reports, and blocks reactions; a never-reported message is a 404; a stale copy of a report can't overwrite another admin's decision (`@Version`). **Rows from a newer deploy:** an unknown `kind` reads as TEXT in history; an unknown reason, status or channel doesn't fail the report queue or the summary (a report on an unknown channel is left out). **GIF proxy:** 503 without a key, 404 for an unknown category, 401 signed out |
| `KlipyClientTest` (14) | Against a local stand-in for KLIPY: ads and unusable items dropped; md beats hd and webp beats gif; preview from sm; sizes; the next page from `has_next`; the request carries the key, `rating=pg-13`, `per_page=24` and a hashed `customer_id`, never the username; long queries cut and blank ones turn into trending; trending cached for 5 minutes (per page and category), search never; 429 → 429, other failures → 502; nothing is called without a key; the https KLIPY host allowlist; the share trigger is posted in the background |
| `ChatEntitiesTest` (8) | Emoji allowlist; reports resolve once; enum converters (old rows read as TEXT, and names they don't know read like null instead of throwing); hidden messages keep their place but lose content, media and reactions |
| `ChatMessageServiceTest` (5) | The races, one step at a time with mocked repositories. A community or DM retry whose lookup missed and whose insert hit the unique constraint re-broadcasts the saved copy and doesn't count the KLIPY share twice. Other insert failures still fail. A double tap's rejected reaction insert keeps one reaction, and two quick taps on different emoji end on the later one |
| `AdminChatReportServiceTest` (2) | If another admin resolved the reports first (optimistic lock), remove and dismiss answer 409. Remove then leaves the message alone and broadcasts nothing |

## API checks (backend running on localhost:8080)

```bash
API=http://localhost:8080/api
login() { curl -s "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
ALICE=$(login urecadmin '<password>')
BOB=$(login urecuser '<password>')
```

1. **History pages from the newest.** `curl -s "$API/messaging/community/history?limit=3" -H "Authorization: Bearer $ALICE"` returns your 3 newest messages, oldest first. Then pass the first id as `&before=<id>` to get the 3 before those.
2. **GIF search.** `curl -s "$API/media/gifs/search?q=deadlift" -H "Authorization: Bearer $ALICE"` lists items with `url`, `previewUrl`, `width`, `height` and `nextPage`. Without a key it's a 503.
   - Check the backend log after the first search. If it says `[KLIPY] media host … isn't in klipy.media-hosts`, KLIPY serves files from another domain: add it with `KLIPY_MEDIA_HOSTS=klipy.com,<that domain>`. Otherwise sends of those GIFs are rejected.
3. **Reactions.** Pick a message id from step 1:
   ```bash
   curl -s -X PUT "$API/messaging/community/messages/<id>/reaction" -H "Authorization: Bearer $BOB" \
     -H 'Content-Type: application/json' -d '{"emoji":"🔥"}'
   ```
   The response's `reactions` is `{"🔥":["urecuser"]}`. Send `{"emoji":"💪"}`: it switches. `DELETE` on the same URL removes it. `{"emoji":"🍕"}` is a 400.
   Then, with no reaction set, fire two at once (a double tap). Both return 200 (before the fix, the one that lost the race could be a 500), and the message ends up with one reaction from urecuser:
   ```bash
   for e in 👍 😂; do curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$API/messaging/community/messages/<id>/reaction" \
     -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' -d "{\"emoji\":\"$e\"}" & done; wait
   ```
4. **Report, then moderate.** As BOB, `POST "$API/messaging/community/messages/<urecadmin's id>/report"` with `{"reason":"SPAM"}` returns 201; doing it again returns 409. As ALICE, `GET "$API/admin/chat-reports"` lists it, and `PUT "$API/admin/chat-reports/community/<id>/remove"` hides it. History then shows `"removedByStaff":true` with empty content.
   Two admins acting at the same moment (both read the reports before either saves) now get a 409 for the second instead of two 200s. That window is too narrow to hit reliably by hand. `AdminChatReportServiceTest` and the stale-copy integration test cover it. Removing after a dismissal that has already finished is still allowed and returns 200.
5. **Delete your own.** `DELETE "$API/messaging/community/messages/<your id>"` returns the placeholder (`"deleted":true`). Someone else's message is a 403.

Sends go over STOMP, so check them from the app (see the app guide). The integration test covers the rejection codes, unreadable frames (`INVALID_MESSAGE`) and the `clientId` constraint.

## Things to confirm with a real KLIPY key

KLIPY's docs site didn't render for me while building this, so these come from third-party integrations and are worth a look the first time you search:
- The response shape `{result, data: {data: [...], has_next}}`, with renditions under `file.{hd,md,sm,xs}.{gif,webp}`. If results come back empty, the backend log shows `result=false` or the HTTP code.
- `rating=pg-13` is the content filter parameter.
- The share trigger path (`POST /{key}/{gifs|stickers}/share/{slug}`). It's best effort: failures are only logged at debug level.
- Attribution: the app's picker shows "Powered by KLIPY".
