INSERT OR IGNORE INTO users(id,email,name,role) VALUES
('owner-001','owner@nasr.media','NASR MEDIA Owner','owner'),
('editor-001','editor@nasr.media','Chief Editor','chief_editor');

INSERT OR IGNORE INTO sources(id,name,url,source_type,trust_score,language) VALUES
('src-001','NASR Internal Desk','https://nasr.media','internal',100,'fa'),
('src-002','Regional Wire Sample','https://example.com/feed','rss',70,'en');

INSERT OR IGNORE INTO articles(id,slug,status,language,title,summary,body,category,author_id,confidence_score,featured,published_at) VALUES
('art-001','regional-cooperation-talks','published','fa','گفت‌وگوهای تازه درباره همکاری‌های منطقه‌ای آغاز شد','نمایندگان چند کشور بر توسعه همکاری‌های اقتصادی و ثبات منطقه‌ای تأکید کردند.','این متن نمونه برای راه‌اندازی اولیه سامانه ثبت شده است.','افغانستان','editor-001',92,1,datetime('now')),
('art-002','food-security-conference','published','fa','نشست بین‌المللی با محوریت امنیت غذایی برگزار شد','کارشناسان درباره راهکارهای کاهش آسیب‌پذیری جوامع در برابر بحران‌های غذایی بحث کردند.','این متن نمونه برای راه‌اندازی اولیه سامانه ثبت شده است.','جهان','editor-001',88,0,datetime('now'));
