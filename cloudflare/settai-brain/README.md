# Set-Tai Cloud brain (v10.28)

Public health: https://settai-brain.settai-companion.workers.dev/health
Android gateway: `POST https://settai-brain.settai-companion.workers.dev/v1/chat`.

**Model is configured in ONE place:** `MODEL` in `worker.js`. Health reports it.
Currently `gemini-3.5-flash-lite`; minimal thinking; no temperature/top_p/top_k.
The Google key is a Cloudflare `GEMINI_API_KEY` secret, never source or Android config.

## Deploy / maintain

Use the connected Cloudflare MCP API with the account ID in wrangler.jsonc, or
an authorised Wrangler login (`npx wrangler deploy`). Keep the existing secret
binding; rotate with `npx wrangler secret put GEMINI_API_KEY` using private input.
Do not put a key in a command argument, repo file, chat or log.
Initial MCP upload used multipart metadata with KV + Durable Object + secret_text.
Its temporary private payload was shredded after upload. No APP_TOKEN was added:
the install UUID is anonymous and this is not authentication.

`npm ci && npm test` runs mocked-Gemini tests in Cloudflare's Workers runtime.
No live tests belong in CI. `GET /health` never calls Google.

## Contract / privacy / limits

POST JSON `{userId, characterId, system, bodySense, message, history?}`;
history entries `{fromUser: boolean, text: string}`. Success `{text, leftToday}`.
Missing IDs/messages: 400; streamed bodies >8192 bytes: 413; over quota: 429
`{error:"daily_limit",leftToday:0}`. Gemini failures: safe 502, no raw upstream
body, key, URL, or request data in errors or console logs. Usual replies are about 30-40 words and are capped at 200 Unicode codepoints. If the user asks to explain more fully, the cap is 560 (about 90 words). v10.39.5: `task: "show"` (the app's "Describe a show") gets a story-lines guard instead of the character voice, 600 output tokens and a 1400-codepoint cap. **Needs a redeploy** to take effect; until then the app still works with the 200-character cap (shorter shows).

40 **attempts** per anonymous install ID per UTC day, including background AI.
Failures count conservatively. SQLite-backed `Quota` Durable Objects reserve
atomically (KV alone is eventually consistent and cannot enforce a hard cap).
One object per SHA-256 user hash stores only the current date/count. KV
`settai-brain-quota` mirrors `hash:yyyy-mm-dd => count`, expires after 48 hours.
KV failures/rate limits fail closed. There are no stored chats, prompts, Gmail,
Android IDs, phone identifiers or raw UUIDs. Logging has invocation logs disabled;
no application logging is emitted. Metrics/traces are sampled without body logging.

Cloudflare's free platform quotas still apply. Anonymous IDs can be reset/replaced;
this is a fair-use cap, not anti-abuse authentication or a global Google budget.
Local jokes/dance/phone commands bypass this endpoint. Android AI defaults to Set-Tai Cloud; Give Brain can still be turned off. Own-key and on-device engines remain optional.

## Deployment check

Health returned HTTP 200 with the requested model. The single live Gemini chat
returned gateway 502 / upstream 400. A non-generative model metadata GET returned
200 for this exact model; no fallback to another model was made. Android handles
unavailable upstream service with scripted local lines. Further upstream diagnosis
is needed before claiming live Gemini replies work.
