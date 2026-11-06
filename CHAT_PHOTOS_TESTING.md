# Testing: chat photos (backend)

Branch `chat-photos` (same name in the app and admin repos) starts from `main`, which already has the GIF, sticker, reaction and report work (`chat-gifs-reactions`).

It adds photo sharing to community chat and DMs:
- **Upload** with `POST /api/messaging/media` (multipart `file`): a JPEG of at most 5 MB. The server decodes it and re-encodes it with ImageIO. That drops every metadata block (EXIF, GPS location, camera details, embedded thumbnails) and caps the long edge at 2048 px. The EXIF orientation is applied to the pixels first, so sideways phone shots stay upright. The response is `{mediaId, width, height}`.
- **Send** an `IMAGE` message over the existing STOMP path: `{clientId, kind: "IMAGE", content: "<optional caption>", media: {mediaId}}`. Branch 1's checks still apply (500-character cap, DM recipient rules, error queue). The photo must be the sender's own and not already on a message. The `clientId` lookup now runs **before** those checks, so a retried send finds its saved message instead of failing with "already sent". Claiming the photo and saving the message happen in one transaction.
- **Viewing:** photos stay private. Every response that carries a photo (history, the live broadcast, the admin queue) gets a fresh signed URL that expires after 2 hours (`MEDIA_URL_TTL_MINUTES`). Deleted and staff-removed photos are blanked for members like other kinds. Staff still see them in Chat Reports, along with the caption.
- **Storage:** `MEDIA_STORAGE=s3` keeps photos in a private Cloudflare R2 bucket and hands out presigned URLs. `local` (the default) keeps them in `~/.urec-live/chat-media` and serves them from `GET /api/messaging/media/{id}/raw?exp=…&sig=…` (an HMAC-signed, expiring URL). No sign-in is needed, because image loaders don't send the sign-in header.
- **Inbox:** "Photo" as the preview.
- **Abuse cap:** a member can have at most 20 photos uploaded but not sent in an hour (`MEDIA_MAX_PENDING_UPLOADS`); the next upload gets 429.

## Setup

1. **Local mode (nothing to set up).** Start the backend as usual. The log says `[Media] Chat photos are stored on this machine in C:\Users\<you>\.urec-live\chat-media (media.storage=local)`. Photo URLs are relative (`/api/…`): the app resolves them against its backend address, and the admin queue makes them absolute.
2. **Cloudflare R2** (for production, or to try the real thing):
   1. Cloudflare dashboard → **R2 Object Storage** → **Create bucket**, e.g. `urec-live-chat`. Leave public access off: no r2.dev URL and no custom domain. Members only ever get presigned URLs.
   2. **R2 → Manage API tokens → Create Account API token** (or User API token). Permission **Object Read & Write**, scoped to that bucket. Copy the **Access Key ID** and **Secret Access Key**; Cloudflare shows the secret only once. The same page shows the S3 endpoint, `https://<account id>.r2.cloudflarestorage.com`.
   3. Add to your local `.env` (load it before `mvn spring-boot:run`, as `TEAM_WORKFLOW.md` describes):
      ```
      MEDIA_STORAGE=s3
      MEDIA_S3_ENDPOINT=https://<account id>.r2.cloudflarestorage.com
      MEDIA_S3_BUCKET=urec-live-chat
      MEDIA_S3_ACCESS_KEY=<access key id>
      MEDIA_S3_SECRET_KEY=<secret access key>
      ```
      With `MEDIA_STORAGE=s3` and any of the four missing, the backend refuses to start and names the missing ones.
   4. Restart. The log says `[Media] Chat photos are stored in bucket urec-live-chat at https://….r2.cloudflarestorage.com`.
   5. On a deployed backend, set the same five variables in its environment. Use `local` in production only if `MEDIA_LOCAL_DIR` is on a disk that survives redeploys; most container hosts wipe it on every deploy.
   - No CORS rule is needed: the app and the admin page load photos as images, not with `fetch`.
3. **Database.** On first start, `ddl-auto=update` creates `chat_media` and adds `media_upload_id` to `community_messages` and `direct_messages`. Both changes only add, so a backend built from `main` without this branch keeps working on the same database. If such an older build reads a photo message, it shows the caption as plain text, or an empty bubble if there's no caption.
4. Accounts: an admin account (ALICE below) and a member account (BOB) on your development database.

## Automated tests

From a shell that never sourced `.env` (see `CLAUDE.md`'s Testing section):

```bash
mvn test -Dtest='ChatPhotosApiIntegrationTest,ChatImageProcessorTest,ChatMediaServiceTest,ChatPhotoSendTest,MediaStorageTest'
```

Expected: `Tests run: 57, Failures: 0, Errors: 0`. The full `mvn test` runs 381 with no failures or errors; 3 are skipped (`UrecLiveSmokeTests`, which run only with `-Durec.live-smoke=true`).

| Test | What it covers |
|---|---|
| `ChatPhotosApiIntegrationTest` (11) | **Upload:** stored without EXIF or the GPS block, and turned upright (orientation 6, so 320x240 is stored as 240x320). Refused with nothing stored: PNG 415, over 5 MB 413, corrupt 400, empty 400, no file part 400, signed out 401. **Send:** an IMAGE message with a trimmed caption, `mediaId`, size and a signed `/raw` URL. That URL serves the stored bytes with no sign-in (`image/jpeg`, `private` caching, `nosniff`). The expiring URL itself isn't stored. **Signature:** a changed signature, a later expiry, another id, missing parameters or a past expiry are each a 403. **Rules:** the caption is optional but capped at 500, and a refused send leaves the photo free. Someone else's photo, an unknown id, no id or no media block: `INVALID_MEDIA`. Sending a photo twice: `MEDIA_IN_USE`. A retry with the same `clientId` re-broadcasts the saved message with no error. **DMs:** both people's queues get a URL and nothing goes to the community topic. The partner's history has a working URL and an outsider's conversation doesn't have the message. The inbox says "Photo". **History** carries a fresh working URL. **Delete:** blanked live and in history. The admin queue still has the photo with an absolute URL that works, plus the caption and "deleted by sender". **Staff removal** blanks it live |
| `ChatImageProcessorTest` (19) | EXIF and GPS gone, still a JPEG of the same size. Long edge capped at 2048, both wide and tall (the subsampled decode plus exact scaling). Smaller photos keep their size. Grayscale accepted. Orientation 6 (big-endian), 8 (little-endian) and 3 put a marked corner where it belongs, and all 8 orientations map each pixel as the EXIF spec says. The orientation tag is only trusted when well formed (out of range, truncated, bad byte order → upright). Non-JPEGs and files that only start like one are rejected. A header claiming 20000x20000 is rejected before decoding |
| `ChatMediaServiceTest` (9) | A good upload is re-encoded, stored under `chat-media/<uuid>.jpg` and recorded with its size. Every rejection (missing, empty, PNG, 5 MB + 1 byte, corrupt) stores nothing; exactly 5 MB is fine. The pending-upload cap gives 429. A storage failure gives 503 and saves no record. Only the uploader's unsent photo can be attached; a claim is the conditional update. URLs use the configured lifetime, and one photo that fails to sign doesn't sink the page. Relative URLs become absolute only inside an HTTP request |
| `ChatPhotoSendTest` (8) | With mocks, one step at a time. The claim and the save run inside the same transaction, in that order. A lost claim saves nothing. A retry racing its own first copy for the photo sends that copy's message. A retry of a saved send is found before the photo is checked. A bad caption fails before the photo is touched. History makes all photo URLs in one call and none for hidden photos; one that couldn't be signed shows as unavailable. Deleting broadcasts no URL. The inbox label is "Photo" |
| `MediaStorageTest` (10) | **Local:** files stay inside the folder, the atomic write leaves no temp files, keys can't escape (`../`). URLs are signed and expire (valid through the expiry second, then not), and are tied to the id, the expiry and the server's secret. **R2:** presigned for path-style `endpoint/bucket/key` with `X-Amz-Expires`, region `auto` and a signature, never the secret (no network). Uploads set the content type and caching; closing closes both clients. **Choosing:** `local` with no bucket settings; `s3` names each missing variable; `s3` with all four builds without connecting; an unknown type stops startup |

## API checks (backend on localhost:8080)

```bash
API=http://localhost:8080/api
login() { curl -s "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
ALICE=$(login '<admin username>' '<password>')
BOB=$(login '<member username>' '<password>')
```

1. **Upload strips location data.** Take a photo on a phone with location tags on and copy it to the PC as `gps.jpg`. Check that it really has GPS data: `grep -ac GPS gps.jpg` prints 1 or more.
   ```bash
   curl -s -F "file=@gps.jpg;type=image/jpeg" "$API/messaging/media" -H "Authorization: Bearer $ALICE"
   ```
   The reply is `{"mediaId":…,"width":…,"height":…}`, with the long edge at most 2048. In local mode the newest file in `~/.urec-live/chat-media/chat-media/` is the stored copy: `grep -ac GPS <file>` and `grep -ac Exif <file>` both print 0. With R2, download it from the bucket in the Cloudflare dashboard and check the same way.
2. **Rejections.** A PNG gives 415. `head -c 6000000 /dev/urandom > big.jpg` and uploading that gives 413. Without the `Authorization` header it's 401.
3. **Send it from the app** (see the app guide), then look at history:
   ```bash
   curl -s "$API/messaging/community/history?limit=1" -H "Authorization: Bearer $BOB"
   ```
   `"kind":"IMAGE"`, the caption in `content`, and `media` with `url`, `width`, `height` and `mediaId`.
   - Local mode: the URL is `/api/messaging/media/<id>/raw?exp=…&sig=…`. Open `http://localhost:8080<url>` in a browser and the photo shows. Change one character of `sig` and it's a 403.
   - R2: the URL is `https://<account>.r2.cloudflarestorage.com/urec-live-chat/chat-media/<uuid>.jpg?X-Amz-…`, and it opens in a browser. The same URL without its query string is refused, because the bucket is private. After 2 hours the signed URL stops working; fetch history again for a fresh one.
4. **Reuse is refused.** Uploading once and sending the same `mediaId` twice from the app isn't possible by hand. The integration test covers `MEDIA_IN_USE` and the retry case.
5. **Moderation.** As BOB, report the photo message: `POST "$API/messaging/community/messages/<id>/report"` with `{"reason":"INAPPROPRIATE"}`. Then `GET "$API/admin/chat-reports" -H "Authorization: Bearer $ALICE"` shows `"kind":"IMAGE"`, the caption, and an absolute `media.url` (`http://localhost:8080/api/…` in local mode) that opens. Delete the message as ALICE: history shows `"deleted":true` and `"media":null`, while the admin queue still has the photo.

## Known limits (follow-ups)

- Photos uploaded but never sent stay in storage. The hourly cap limits how many pile up; a cleanup job could remove ones older than a day.
- Deleted and removed photos are kept for moderation (decided for v1). A URL already handed out keeps working until it expires (2 hours by default), because R2 can't revoke a presigned URL early.
- One rendition per photo (up to 2048 px); bubbles download it and show it scaled down. A smaller thumbnail could be added if data use matters.
- Only JPEG uploads are accepted. The app always converts to JPEG, so this only affects other clients.
