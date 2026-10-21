# Testing: faster, fuller machine endpoints

Branch `minor-fixes-and-speed-ups`, here and in `urec-live-app`. The app side (saved data, live updates on every screen, empty sessions) is described in `urec-live-app/MINOR_FIXES_TESTING.md`. Here there are two changes, and no schema change.

**1. A fixed number of queries per machine list, instead of one per machine.**
- Each machine's exercises are a lazy collection, and every machine response reads them (for the primary exercise name).
- So `GET /api/machines` and `GET /api/machines/floor-plans` ran 1 + N queries, each a round trip to Neon: 97 queries for 96 machines, about 1.7 s each.
- `spring.jpa.properties.hibernate.default_batch_fetch_size=100` in `application.properties` loads them for up to 100 machines in one query.
- It applies to every lazy collection, so the admin equipment endpoints benefit too.

**2. Every machine carries its exercises.**
- `MachineDTO` adds `exerciseNames` and `muscleGroups`: sorted, with blanks left out, and empty lists rather than null.
- They're on every machine the API returns:
  - `GET /api/machines`, `/{id}`, `/code/{code}` and `/exercise/{name}`
  - the floor plans
  - status updates
  - the `/topic/machines` broadcast, including staff changes from main's out-of-order switch and the admin Equipment page (`MachineStatusService` builds the same `MachineDTO`)
- The app's Equipment tab used to send one `GET /api/machines/{id}/exercises` per machine (95) to show them. Now it sends one request.
- The fields are only added, so clients that don't know them (the admin dashboard, older app builds) ignore them.

## 1. Automated tests

```bash
mvn test
```

You should see `Tests run: 213, Failures: 0`, including 9 from this branch. The branch now includes main as of PR #16 (equipment issue reports). As before, `UrecLiveBackendApplicationTests.contextLoads` connects to the database in `application.properties`; everything else runs on in-memory H2. Run it in a terminal where `.env` isn't loaded: TERMINAL 1's `SPRING_DATASOURCE_URL` overrides the tests' H2 database, and the integration tests then fail to start.

| File | What it covers |
|---|---|
| `controller/MachineApiIntegrationTest` (6) | The list, by id, by code, by exercise and the floor plans include both fields. A machine listed under one of its other exercises keeps that exercise as `exercise`, and lists all of them in `exerciseNames`. The status update's response and its `/topic/machines` broadcast (captured on the broker channel) carry them, and so does the broadcast when an admin takes a machine out of service (main's switch, which goes through `MachineStatusService`). `GET /api/machines` and `/floor-plans` run fewer than 10 SQL statements with the ~96 seeded machines (counted with Hibernate statistics, switched on inside the test) |
| `dto/MachineDTOTest` (3) | Sorted and distinct; blank and missing values left out; empty lists, never null |

**Mutation check.** Turning batching off makes the query test fail with `GET /api/machines ran 97 queries for 96 machines`:

```bash
mvn test -Dtest=MachineApiIntegrationTest -Dspring.jpa.properties.hibernate.default_batch_fetch_size=1
```

## 2. Measured

Local backend with Neon behind it, median of 3 runs, replaying the app's requests 5 at a time like Android:

| | main | this branch |
|---|---|---|
| `GET /api/machines` (95 machines) | 2.73 s | 0.08 s |
| `GET /api/machines/floor-plans` | about 2.7 s | about 0.1 s |
| The current app's Equipment tab (97 requests) | 6.95 s | 2.01 s |
| The current app's machine picker | 2.81 s | 0.16 s |

With the app branch too, the Equipment tab is down to 2 requests and 0.07 s.

## 3. Manual checks

Switch to the branch and start the backend as usual (TERMINAL 1 in `commands.txt`):

```bash
cd /c/Users/abelv/portfolio/getmefit/urec-live-backend && git switch minor-fixes-and-speed-ups
```

1. Each machine has the new fields. This prints the first machine:

   ```bash
   curl -s http://127.0.0.1:8080/api/machines | python -c "import json,sys; print(json.dumps(json.load(sys.stdin)[0], indent=1))"
   ```

   It should include `"exerciseNames"` and `"muscleGroups"`.
2. The list is fast. This should print about 0.1 s (main takes 2–3 s):

   ```bash
   curl -s -o /dev/null -w "%{time_total}s\n" http://127.0.0.1:8080/api/machines
   ```

3. The query count. Start the backend with `export SPRING_JPA_SHOW_SQL=true` before `mvn spring-boot:run`, and call `/api/machines` once. The log shows 2 selects: the machines, then the exercises for all of them. On main it's about 96.
4. Check in to a machine from the app. The admin dashboard's **Live Monitor** still updates (it ignores the new fields), and the app's lists change without a refresh.
5. On the admin dashboard's **Equipment** page, set a machine to **Out of Order**. The app's Equipment tab shows it as Out of order within a second or two, with its muscle groups still on the card. Set it back to **Available** afterwards. Use an admin account whose role is `ROLE_ADMIN`: the Equipment page returns 403 for one promoted through the dashboard (role `ADMIN`; see "Admin role naming" in `EQUIPMENT_ISSUES_TESTING.md`). The **Out of order** switch under **Equipment Issues** works for both, once the machine has a report.
