# Book Illustration Studio

Turns a book's text into style-matched character portraits and chapter illustrations, via a 5-step Gemini-powered pipeline (style → characters → portraits → chapters → illustrations).

## Prerequisites

- Java 21+ (Spring Boot 4.1.0)
- Docker + Docker Compose (Postgres 17)
- Node.js 18+ (npm 9+) (React 19 / Vite 8 frontend)
- A Gemini API key ([ai.google.dev](https://ai.google.dev)) — free tier covers text generation; image generation (Nano Banana 2) currently requires billing, which we couldn't enable — see `DECISIONS.md`. The app runs fine without it: text steps call the real API, image steps use bundled mock images.

## Architecture overview

- **Backend:** Spring Boot, 3-layer structure (`controller` / `service` / `repository` / `entity`), Postgres via Spring Data JPA.
- **Pipeline:** each of the 5 steps follows a claim → 202 Accepted → async execute → finalize pattern — a conditional `UPDATE` claims the step (avoiding a held DB connection during the 10-30s+ Gemini call), the actual work runs in a background thread, and a second `UPDATE` finalizes success or failure. Retries and stuck-step recovery use the same claim-style conditional updates.
- **Gemini integration:** text generation (style, characters, chapters) calls the real Gemini Interactions API, including structured JSON output for the list-returning steps. Image generation (portraits, illustrations) is mocked with bundled sample images — real Nano Banana 2 calls are blocked by a billing/card issue outside our control (see `DECISIONS.md` for the full story).
- **Frontend:** React 19, Vite, React Router DOM v7, and Vanilla CSS with custom design tokens (`tokens.css`). User session state is managed via React Context (`AuthContext`) with synchronous `localStorage` hydration. Mid-step pipeline progress is driven by a custom `usePolling` hook that polls project state while a step is `RUNNING` and stops automatically on terminal states (`SUCCESS` / `FAIL`).


## Environment variables

Copy `.env.example` to `.env` and fill in:

| Variable | Description |
| --- | --- |
| `APP_GEMINI_API_KEY` | Your Gemini API key |
| `APP_GEMINI_TEXT_MODEL` | Gemini text generation model (default: `gemini-3.1-flash-lite`) |
| `APP_GEMINI_IMAGE_MODEL` | Gemini image generation model (default: `gemini-3.1-flash-image`) |
| `SERVER_PORT` | Backend Spring Boot server port (default: `8080`) |
| `FRONTEND_PORT` | Frontend dev server port (default: `5173`) |
| `STEP_TIMEOUT_SECONDS` | How long a step can sit `RUNNING` before it's considered stuck and eligible for recovery |
| `MAX_RETRY_COUNT` | Max retry attempts per step before the retry endpoint stops accepting retries |
| `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`, `DB_PORT` | Postgres database connection details |
| `FILE_STORAGE_ROOT` | Local storage root for book text files and generated assets |
| `VITE_API_BASE_URL` | Frontend API base URL (default: `http://localhost:8080/api/v1`) |
| `VITE_PROXY_TARGET` | Frontend Vite dev server proxy target (default: `http://localhost:${SERVER_PORT}`) |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins for asset server (default: `http://localhost:5173`) |

## Running the application

```bash
./start.sh
```

Brings up Postgres (via `docker-compose.yml`), starts the Spring Boot backend service, and launches the React/Vite frontend dev server.

### Default Ports & API Contract

- **Frontend Application**: [http://localhost:5173](http://localhost:5173) (default port: `5173`, configured via `FRONTEND_PORT`)
- **Backend Service**: [http://localhost:8080](http://localhost:8080) (default port: `8080`, configured via `SERVER_PORT`)
- **Swagger UI / API Contract**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## Running tests

```bash
./test.sh
```

Runs both the backend test suite (JUnit — claim/finalize state machine, retry logic, `GeminiRestClient` error paths) and the frontend test suite (Vitest + React Testing Library — auth flow, project list, creation tabs, and stepper polling).

See `TESTING.md` for what's covered, what's deliberately not, and the actual test run output.

## Other docs

- `DECISIONS.md` — key engineering trade-offs and why we made them.
- `TESTING.md` — what's tested, what isn't, and why.
