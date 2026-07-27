PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS users(
 id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,name TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN('owner','admin','chief_editor','editor','fact_checker','translator','reporter','viewer')),
 active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL DEFAULT(datetime('now'))
);
CREATE TABLE IF NOT EXISTS sources(
 id TEXT PRIMARY KEY,name TEXT NOT NULL,url TEXT,source_type TEXT NOT NULL,
 trust_score INTEGER NOT NULL DEFAULT 50,language TEXT,active INTEGER NOT NULL DEFAULT 1,
 created_at TEXT NOT NULL DEFAULT(datetime('now'))
);
CREATE TABLE IF NOT EXISTS articles(
 id TEXT PRIMARY KEY,slug TEXT UNIQUE NOT NULL,status TEXT NOT NULL DEFAULT 'draft',
 language TEXT NOT NULL DEFAULT 'fa',title TEXT NOT NULL,summary TEXT,body TEXT,category TEXT,
 author_id TEXT,confidence_score INTEGER NOT NULL DEFAULT 0,featured INTEGER NOT NULL DEFAULT 0,
 published_at TEXT,created_at TEXT NOT NULL DEFAULT(datetime('now')),updated_at TEXT NOT NULL DEFAULT(datetime('now')),
 FOREIGN KEY(author_id)REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS article_sources(
 id TEXT PRIMARY KEY,article_id TEXT NOT NULL,source_id TEXT,source_url TEXT,claim TEXT,
 verification_status TEXT NOT NULL DEFAULT 'pending',created_at TEXT NOT NULL DEFAULT(datetime('now')),
 FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE CASCADE,
 FOREIGN KEY(source_id)REFERENCES sources(id)
);
CREATE TABLE IF NOT EXISTS pipeline_runs(
 id TEXT PRIMARY KEY,article_id TEXT NOT NULL,stage INTEGER NOT NULL,agent_key TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'queued',input_json TEXT,output_json TEXT,confidence_score INTEGER,
 started_at TEXT,completed_at TEXT,created_at TEXT NOT NULL DEFAULT(datetime('now')),
 FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS translations(
 id TEXT PRIMARY KEY,article_id TEXT NOT NULL,language TEXT NOT NULL,title TEXT,summary TEXT,body TEXT,
 status TEXT NOT NULL DEFAULT 'draft',created_at TEXT NOT NULL DEFAULT(datetime('now')),updated_at TEXT NOT NULL DEFAULT(datetime('now')),
 UNIQUE(article_id,language),FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS approvals(
 id TEXT PRIMARY KEY,article_id TEXT NOT NULL,reviewer_id TEXT,decision TEXT NOT NULL CHECK(decision IN('approved','rejected','changes_requested')),
 note TEXT,created_at TEXT NOT NULL DEFAULT(datetime('now')),
 FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE CASCADE,FOREIGN KEY(reviewer_id)REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS revisions(
 id TEXT PRIMARY KEY,article_id TEXT NOT NULL,editor_id TEXT,snapshot_json TEXT NOT NULL,reason TEXT,
 created_at TEXT NOT NULL DEFAULT(datetime('now')),
 FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE CASCADE,FOREIGN KEY(editor_id)REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS media_assets(
 id TEXT PRIMARY KEY,article_id TEXT,file_key TEXT NOT NULL,mime_type TEXT,caption TEXT,alt_text TEXT,
 created_at TEXT NOT NULL DEFAULT(datetime('now')),FOREIGN KEY(article_id)REFERENCES articles(id)ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS audit_logs(
 id TEXT PRIMARY KEY,user_id TEXT,action TEXT NOT NULL,entity_type TEXT,entity_id TEXT,details_json TEXT,
 created_at TEXT NOT NULL DEFAULT(datetime('now')),FOREIGN KEY(user_id)REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_articles_status ON articles(status);
CREATE INDEX IF NOT EXISTS idx_articles_language ON articles(language);
CREATE INDEX IF NOT EXISTS idx_articles_published ON articles(published_at);
CREATE INDEX IF NOT EXISTS idx_pipeline_article ON pipeline_runs(article_id,stage);
CREATE INDEX IF NOT EXISTS idx_sources_active ON sources(active,trust_score);
