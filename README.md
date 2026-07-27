# NASR MEDIA

Global multilingual digital newsroom with human editorial control.

## Current system

- Responsive public news website
- Editorial command center
- Draft creation and article editing
- Human approval before publication
- Article revisions and audit-ready workflow
- Cloudflare Worker REST API
- Cloudflare D1 database schema and seed data
- Seven publishing languages: Dari, Pashto, Arabic, English, Turkish, French and Urdu
- Eight-stage intelligent newsroom pipeline

## Eight agents

1. Rasad — source intake
2. Palayesh — deduplication and cleaning
3. Mizan — verification and confidence scoring
4. Nabz — summarization and entity extraction
5. Basir — context and analysis
6. Dabir — headline, lead and newsroom editing
7. Lesan — seven-language translation
8. Nashr — final risk review and transfer to a human editor

The final agent never publishes automatically. A human editor must approve publication.

## Frontend

```bash
npm install
npm run dev
npm run build
```

Set the API URL by copying `.env.example` to `.env` and replacing the Worker address.

## Backend

```bash
cd worker
npm install
npx wrangler d1 create nasr-media-db
```

Place the returned D1 database ID in `worker/wrangler.toml`, then run:

```bash
npm run db:migrate
npm run deploy
```

For real AI processing, store the API key as a Worker secret rather than committing it:

```bash
npx wrangler secret put OPENAI_API_KEY
npx wrangler secret put OPENAI_MODEL
```

Without a model key, the pipeline remains in simulation mode while the editorial, database and approval workflow still functions.

## API routes

- `GET /api/health`
- `GET /api/agents`
- `GET /api/articles`
- `POST /api/articles`
- `GET /api/articles/:id`
- `PATCH /api/articles/:id`
- `POST /api/articles/:id/process`
- `GET /api/pipeline?articleId=:id`
- `POST /api/approvals`

## Security rule

Never commit API keys, tokens or Cloudflare credentials to this repository.

Copyright © NASR MEDIA
