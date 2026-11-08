# Live UREC website chatbot

`POST /api/chat` uses on-demand discovery and direct page fetching from
`https://urec.charlotte.edu`. No bundled corpus, ingestion command, vector database,
or database migration is required. Existing JWT authentication and message validation apply.

## Flow and boundaries

1. Resolve short follow-ups using the previous user topic. Simple hours/open-now
   questions fetch the official hours URL directly, bypassing search. Only the URL
   is a hint; operating hours, schedule dates and exceptions come from fetched content.
   Other questions search with Tavily using
   `include_domains: ["urec.charlotte.edu"]`, basic search, five results, and generated
   answers/raw content disabled. Read only structured `results[].url` metadata,
   then deduplicate and validate URLs. Search snippets never become answer evidence.
2. Supplement search results with known official URLs for hours, memberships, guest
   passes, classes, training, aquatics, policies and facilities. These are URL hints,
   not stored website content. Fetch at most three pages total.
3. Accept only HTTPS on the exact UREC host and standard port, with no userinfo or
   query URLs. Recheck every redirect. Respect robots.txt groups, Allow/Disallow
   precedence, wildcards and Crawl-delay. Robots failures fail closed (404 permits
   crawling); requests are spaced at least 500ms apart per backend instance.
4. Extract main HTML content with jsoup. Strip scripts/navigation and preserve
   headings, date labels and table rows. Select relevant windows for long pages.
5. Ask the Groq answer model for JSON containing `answerability`, `reply`, `missing`,
   and selected passage IDs tied to source IDs. The backend creates numbered passages
   from fetched content and validates that each selected passage exists and belongs
   to its cited source. This detects invented references; it does not prove that
   every generated claim follows from the evidence. The model does not copy quotes.
   The backend appends citations for validated evidence when the model omits them;
   empty evidence and invalid explicit citations still fail validation.

The model receives Charlotte's current date/time and instructions to check years,
seasons and holiday exceptions. Freshly fetched content may itself be outdated.
Personal workouts, live machine status, PDFs, images and JavaScript-only embedded
schedules are not supported. The chatbot never broadens sources beyond UREC.

## Cache, timeouts and failures

Successful extracted pages are cached in memory for five minutes (maximum 100 entries
per instance), keeping their original fetch timestamp. Expired entries are removed
before refresh and never returned on failure. Robots rules also expire after five
minutes. No conversation or completed-answer cache is retained.

The backend has a shared 65-second deadline: discovery gets at most 15 seconds;
page retrieval stops by 38 seconds, leaving the remainder for generation. HTTP
requests enforce body-delivery deadlines and size limits (2 MB HTML, 512 KB robots,
1 MB per provider response). Redirects are limited to three. The app keeps its 75-second timeout.

- Supported answer: `mode: generated`, `answerability: full` or `partial`.
- Insufficient evidence: `mode: no_match`, `answerability: none`; missing information is explicit.
- Retrieval outage: `mode: unavailable`, `answerability: unknown`.
- Generation/validation failure after successful fetching: `mode: excerpts`,
  `answerability: unknown`; clearly labeled live excerpts, not a generated answer.

Search failure can still yield an answer from fetched fallback URLs. It is recorded
as a failure in telemetry. A missing Tavily key still allows fetching known official URLs. A missing Groq key
returns labeled excerpts after retrieval. Neither masquerades as provider success.

The response retains `reply`, `sources` (`id`, `title`, `url`, `fetchedAt`), and `mode`,
and adds `answerability`. The app labels sources “Checked at” with date and time.

## Tavily search and Groq answers

Set `TAVILY_API_KEY` and `GROQ_API_KEY` in the backend process environment or hosting
secrets, then restart. Get a Tavily key at https://app.tavily.com. Spring Boot does
not load `.env` automatically. Never use Expo public variables for these keys.

For local zsh, from `backend/`, enter keys without saving them in shell history:

```sh
read -rs 'TAVILY_API_KEY?Tavily API key: '
export TAVILY_API_KEY
read -rs 'GROQ_API_KEY?Groq API key: '
export GROQ_API_KEY
mvn spring-boot:run
```

Use Java 21 or newer. Tavily receives a query capped at 400 characters; Groq receives
retrieved website passages and recent conversation messages.

Optional settings:
- `UREC_RAG_MODEL`: defaults to `openai/gpt-oss-120b`.
- `UREC_RAG_GROQ_URL`: defaults to `https://api.groq.com/openai/v1`.
- `UREC_RAG_TAVILY_URL`: full search endpoint, default `https://api.tavily.com/search`.

Keys are sent to their respective configured endpoints. Compound and the old
`UREC_RAG_SEARCH_MODEL` setting are no longer used.

References:
- https://docs.tavily.com/documentation/api-reference/endpoint/search
- https://console.groq.com/docs/api-reference

## Validation and rollout

```sh
mvn -Dtest=UrecRagTests,LiveHttpTests test
# Explicitly opt into public website, Tavily and Groq requests; may incur provider usage charges.
mvn -Dtest=UrecLiveSmokeTests -Durec.live-smoke=true test
```

Tests cover host/redirect restrictions, robots rules, extraction, cache timestamps
and expiry, changed website content, discovery metadata, partial/unsupported answers,
invalid citations and passage references, date/holiday prompt context, follow-ups, outages,
response-size/time bounds, and startup without a corpus. Isolated tests do not use
production database configuration. The live search test skips if either key is absent;
the direct open-now test needs only the Groq key.

Before rollout, run the live tests with the deployed account and verify actual
answers for holiday hours, membership eligibility/refunds, and a follow-up question.
Date handling is model-based; fixture tests verify date preservation and instructions,
not the reasoning quality of a live model. No deployment is performed by these tests.

Each completed request logs only duration, retrieval duration, page count, cache
hits, failure count, whether search executed, retrieval route, response mode and answerability.
Watch failure rate, latency and unknown/none outcomes during rollout. Keys, prompts,
conversation text and provider response bodies are not logged by the retrieval services.

## Troubleshooting

Sanitized warnings distinguish failure stages without logging keys, queries, raw
exception messages or provider response bodies:

```text
urec_failure stage=search provider=tavily code=PROVIDER_HTTP http_status=401
urec_failure stage=generation provider=groq code=MISSING_KEY http_status=0
urec_failure stage=validation provider=groq code=INVALID_EVIDENCE http_status=0
```

`MISSING_KEY` means the backend process lacks that credential. `PROVIDER_HTTP`
includes the status (401/403: check credentials/permissions; 429: throttling).
`TIMEOUT` and `NETWORK_ERROR` identify transport problems. `INVALID_RESPONSE`,
`EMPTY_RESPONSE` and `TRUNCATED_RESPONSE` identify unusable provider output.
`INVALID_EVIDENCE` identifies failed passage/citation checks. `REQUEST_FAILED`
covers other fetch/request failures. Successful empty searches are distinguished
from unavailable search. Tests also verify that diagnostics do not expose secrets.

Evidence-validation warnings additionally include a safe `reason` such as
`UNKNOWN_PASSAGE`, `PASSAGE_SOURCE_MISMATCH`, `MISSING_PASSAGES`,
`INVALID_SOURCE_ID` or `UNSUPPORTED_CITATION`. Missing inline citation formatting
alone no longer triggers a fallback.
Passages contain the actual backend-extracted source text, with overlap at chunk
boundaries. No generated quote string is required. Failed validation is displayed
as an unverified answer rather than a provider outage. For “open rn”, the prompt
asks for scheduled status at Charlotte's current time, distinguishing the UREC
Center from pools/other facilities and noting that live closures are unverified.
