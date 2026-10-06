# Writing Assistant

Paste a text, pick a mode, get the result streamed in real time.

| Mode | What it does |
|---|---|
| Fix | Fixes spelling, grammar and punctuation |
| Summarize | Condenses the text to its key ideas |
| More formal | Rewrites in a formal, professional register |
| Shorter | Same message, fewer words |
| Change tone | Rewrites with a chosen tone (friendly, professional, persuasive, empathetic, confident) |

Covers three of the suggested features in one: summarizer, paraphraser and tone adjuster.

## Architecture

```
Browser ──POST /api/rewrite (JSON)──▶ Spring Boot ──POST /chat/completions (stream)──▶ Google Gemini (LLM)
   ▲                                      │
   └────── Server-Sent Events: token… done ◀┘
```

- **frontend/** — Next.js 15 (App Router, TypeScript) styled only with Bootstrap 5 (no custom CSS). Reads the SSE stream with `fetch` + `ReadableStream` (it is a POST, so `EventSource` doesn't apply). Modes and tones come from `GET /api/modes`, so the backend is the single source of truth.
- **backend/** — Spring Boot 3 (WebFlux, Java 21). Layers: `api` (controllers, DTOs, error handling) → `service` (prompt building, AI client) → `domain` (modes/tones) and `config`.

### Design choices

- **Streaming (bonus):** WebFlux returns a `Flux<ServerSentEvent>` that maps the provider's stream 1:1 to the browser. Events: `token`, `done`, `error`. Errors mid-stream are sent as an `error` event, since the HTTP status is already committed.
- **Presets (bonus):** each mode is an enum entry carrying its own instruction; adding a mode is one line in `WritingMode`.
- **Security:**
  - The API key lives only in an environment variable on the backend; the browser never sees it. Provider error details are logged, never returned.
  - Input validation (non-blank, max 5000 chars) on both client and server.
  - Prompt-injection mitigation: user text is wrapped in `<text>` tags and the system prompt declares it as data, not instructions.
  - Per-IP rate limit (20 req/min by default) on the AI endpoint to protect the API key's billing. It is in-memory, so it suits a single instance; use Redis or an API gateway if you scale out.
  - CORS restricted to the configured frontend origin.
- **Not included:** persistence of interactions (optional bonus), so there is no database.

## Getting a free API key (Google AI Studio)

The app uses Google Gemini, which has a free tier (no credit card needed).

1. Go to https://aistudio.google.com/apikey and sign in with a Google account.
2. Click **Create API key** and copy it.
3. Create your `.env` file from the template and paste the key:
   ```bash
   cp .env.example .env
   ```
   Then open `.env` and set:
   ```
   AI_API_KEY=your_key_here
   ```
   Leave the other variables as they are. Never commit `.env` (it is already in `.gitignore`).

The free tier has request limits per minute; if you hit them, the app shows a message asking you to wait a minute. If the default model is ever retired (error 404 in the backend logs), pick another one from https://ai.google.dev/gemini-api/docs/models and set `AI_MODEL` in `.env`.

The backend speaks the OpenAI-compatible chat completions protocol, so other providers also work by changing `AI_BASE_URL`, `AI_MODEL` and `AI_API_KEY`, e.g. OpenRouter (`https://openrouter.ai/api/v1`), Groq (`https://api.groq.com/openai/v1`) or a local Ollama (`http://host.docker.internal:11434/v1`).

## Environment variables

Copy `.env.example` to `.env`.

| Variable | Required | Default | Used by |
|---|---|---|---|
| `AI_API_KEY` | yes | — | backend |
| `AI_MODEL` | no | `gemini-3.1-flash-lite` | backend |
| `AI_BASE_URL` | no | `https://generativelanguage.googleapis.com/v1beta/openai` | backend |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000,http://127.0.0.1:3000` | backend (comma-separated) |
| `RATE_LIMIT_PER_MINUTE` | no | `20` | backend |
| `NEXT_PUBLIC_API_URL` | no | `http://localhost:8080` | frontend (build time) |

## Run with Docker (one command)

```bash
# 1. Create .env with your key (see "Getting a free API key" above)
# 2. Start everything:
docker compose up --build
```

Open http://localhost:3000. Both images use multi-stage builds and run as a non-root user.

## Run locally without Docker

Requirements: Java 21, Maven 3.9+, Node 20+.

```bash
# terminal 1
cd backend
export AI_API_KEY=your-key
mvn spring-boot:run

# terminal 2
cd frontend
npm install
npm run dev
```

## Deploy to the cloud

Any host that runs containers works (Render, Railway, Fly.io, Cloud Run, ECS…):

1. Deploy `backend/` as a container. Set `AI_API_KEY` as a **secret** and `CORS_ALLOWED_ORIGINS` to the frontend's public URL.
2. Deploy `frontend/` as a container, building with `--build-arg NEXT_PUBLIC_API_URL=https://<your-backend-url>` (the value is baked in at build time).
3. Serve both over HTTPS.
