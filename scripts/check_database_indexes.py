"""Run the production 4→5 migration SQL against SQLite without extra dependencies."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/cinetrack/data/local/AppDatabase.kt").read_text()
migration = source.split("object : Migration(4, 5) {", 1)[1].split("fun create(context:", 1)[0]
statements = re.findall(r'database\.execSQL\("([^"\n]+)"\)', migration)
assert len(statements) == 3, "Expected the three production index statements"
assert all(sql.startswith("CREATE INDEX IF NOT EXISTS ") for sql in statements)

# Version 4 column/PK definitions. Includes the existing episode-history index.
db = sqlite3.connect(":memory:")
db.executescript('''
CREATE TABLE watch_history (
 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mediaType TEXT NOT NULL,
 mediaId INTEGER NOT NULL, episodeId INTEGER, season INTEGER, episodeNumber INTEGER,
 episodeTitle TEXT, watchedAt TEXT NOT NULL);
CREATE INDEX index_watch_history_mediaType_mediaId_season_episodeNumber
 ON watch_history(mediaType, mediaId, season, episodeNumber);
CREATE TABLE playback (
 mediaType TEXT NOT NULL, mediaId INTEGER NOT NULL, episodeId INTEGER NOT NULL,
 progress REAL NOT NULL, positionSeconds INTEGER NOT NULL, durationSeconds INTEGER NOT NULL,
 updatedAt TEXT NOT NULL, season INTEGER, episodeNumber INTEGER, episodeTitle TEXT,
 PRIMARY KEY(mediaType, mediaId, episodeId));
CREATE TABLE user_media_state (
 mediaType TEXT NOT NULL, mediaId INTEGER NOT NULL, status TEXT NOT NULL,
 watched INTEGER NOT NULL, simklId INTEGER, updatedAt INTEGER NOT NULL, dirty INTEGER NOT NULL,
 PRIMARY KEY(mediaType, mediaId));
''')
for i in range(1, 2001):
    stamp = f"2026-08-{(i % 28) + 1:02d}T{(i % 24):02d}:00:00Z"
    kind = "TV" if i % 2 else "MOVIE"
    db.execute("INSERT INTO watch_history VALUES (?,?,?,?,?,?,?,?)", (i, kind, i, i if kind == "TV" else None, 1 if kind == "TV" else None, i if kind == "TV" else None, "Episodio è" if kind == "TV" else None, stamp))
    db.execute("INSERT INTO playback VALUES (?,?,?,?,?,?,?,?,?,?)", (kind, i, i if kind == "TV" else 0, .25, 600, 2400, stamp, 1 if kind == "TV" else None, i if kind == "TV" else None, None))
    db.execute("INSERT INTO user_media_state VALUES (?,?,?,?,?,?,?)", (kind, i, ("NONE", "WATCHING", "DROPPED", "PLAN_TO_WATCH", "COMPLETED")[i % 5], int(i % 5 == 4), i * 100, i * 1000, i % 2))

queries = {
    "history": "SELECT * FROM watch_history ORDER BY watchedAt DESC",
    "playback": "SELECT * FROM playback ORDER BY updatedAt DESC",
    "active_tv": "SELECT * FROM user_media_state WHERE mediaType = 'TV' AND status NOT IN ('NONE', 'DROPPED')",
}
snapshots = {table: db.execute(f"SELECT * FROM {table} ORDER BY rowid").fetchall() for table in ("watch_history", "playback", "user_media_state")}
tables_before = db.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
results_before = {key: sorted(db.execute(sql).fetchall(), key=repr) for key, sql in queries.items()}
for key in ("history", "playback"):
    assert "USE TEMP B-TREE" in str(db.execute("EXPLAIN QUERY PLAN " + queries[key]).fetchall())

with db:
    for sql in statements:
        db.execute(sql)
# IF NOT EXISTS must preserve both data and index definitions on a second call.
with db:
    for sql in statements:
        db.execute(sql)

assert tables_before == db.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for table, before in snapshots.items():
    assert before == db.execute(f"SELECT * FROM {table} ORDER BY rowid").fetchall(), table
for key, sql in queries.items():
    assert results_before[key] == sorted(db.execute(sql).fetchall(), key=repr), key
for index, columns in {
    "index_watch_history_watchedAt": ["watchedAt"],
    "index_playback_updatedAt": ["updatedAt"],
    "index_user_media_state_mediaType_status": ["mediaType", "status"],
    "index_watch_history_mediaType_mediaId_season_episodeNumber": ["mediaType", "mediaId", "season", "episodeNumber"],
}.items():
    assert [row[2] for row in db.execute(f"PRAGMA index_info('{index}')")] == columns, index
for key, index in (("history", "index_watch_history_watchedAt"), ("playback", "index_playback_updatedAt")):
    plan = str(db.execute("EXPLAIN QUERY PLAN " + queries[key]).fetchall())
    assert index in plan and "USE TEMP B-TREE" not in plan, plan
    timestamps = [row[-1] if key == "history" else row[6] for row in db.execute(queries[key])]
    assert timestamps == sorted(timestamps, reverse=True)
    print(f"{key}: {plan}")
assert "SELECT * FROM media WHERE title LIKE '%' || :query || '%' ORDER BY score DESC LIMIT 60" in source
print("PASS: 6,000 rows preserved, table schemas unchanged, four indexes intact, ordered-query sorts removed, local search unchanged.")
