/**
 * Keeps the relay under Discord's ceiling without losing anything.
 *
 * <p>Discord accepts about 30 messages a minute per webhook. The mod sends one
 * message per flagging player every 15 seconds, so eight players tripping checks at
 * once already exceeds it, and fifty overshoots by nearly seven times. Past the
 * ceiling Discord answers 429, the relay turned that into a 502, and the mod
 * swallowed it — so the failure mode was silence, which reads exactly like nobody
 * cheating.</p>
 *
 * <p>The rule, per minute:</p>
 *
 * <ol>
 *   <li>The first {@code BUDGET} alerts go straight through, unchanged — full embed,
 *       player head, opponent, no delay. This is the ordinary case and it behaves
 *       exactly as it did before.</li>
 *   <li>Anything beyond that is held rather than dropped.</li>
 *   <li>When the minute closes, everything held is sent as a single summary naming
 *       who flagged and how often.</li>
 * </ol>
 *
 * <p>That caps outgoing traffic at {@code BUDGET + 1} messages a minute however many
 * players are reporting — ten or ten thousand — while the only thing lost is
 * immediacy for the overflow, not the overflow itself.</p>
 *
 * <p>A single instance handles everything, reached by a fixed name. That is
 * deliberate: the budget only means something if one place counts all of it, and a
 * per-player or per-region instance would each get their own budget and collectively
 * blow straight through the limit this exists to respect.</p>
 */

/** Messages sent individually per minute. Under Discord's ~30 with room to spare. */
const BUDGET = 20;

/** How long a window lasts. Matches the limit Discord actually enforces. */
const WINDOW_MS = 60_000;

/** Named in the summary before it gives up and counts the rest. */
const MAX_NAMED = 15;

/** Flags older than this are dropped. Long enough to be useful, bounded so the
 *  table cannot grow without limit on the free plan's row budget. */
const RETAIN_MS = 90 * 24 * 60 * 60 * 1000;

export class AlertAggregator {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sql = state.storage.sql;
    // Player name is stored alongside the UUID rather than looked up: names change,
    // and the name a flag was recorded under is what someone will search for.
    this.sql.exec(`CREATE TABLE IF NOT EXISTS flags(
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      ts INTEGER NOT NULL,
      uuid TEXT NOT NULL,
      player TEXT NOT NULL,
      player_lc TEXT NOT NULL,
      server TEXT,
      opponent TEXT,
      hotbar INTEGER NOT NULL DEFAULT 0,
      \`use\` INTEGER NOT NULL DEFAULT 0,
      attack INTEGER NOT NULL DEFAULT 0,
      keybind INTEGER NOT NULL DEFAULT 0,
      mc TEXT
    )`);
    // Added after the table already existed in production, so CREATE TABLE IF NOT
    // EXISTS would not have introduced it. ALTER is the only way in, and it throws
    // rather than no-ops when the column is already there, so the throw is the
    // check — there is no ADD COLUMN IF NOT EXISTS in SQLite.
    try {
      this.sql.exec('ALTER TABLE flags ADD COLUMN mc TEXT');
    } catch (e) {
      // Already present. Nothing to do and nothing worth logging.
    }
    this.sql.exec('CREATE INDEX IF NOT EXISTS flags_uuid ON flags(uuid)');
    this.sql.exec('CREATE INDEX IF NOT EXISTS flags_name ON flags(player_lc)');
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === '/search') {
      return json(this.search(url.searchParams.get('q') || ''));
    }

    const { payload, player, uuid, counts, meta } = await request.json();
    this.record(meta, player, uuid, counts);

    const now = Date.now();
    let windowStart = (await this.state.storage.get('windowStart')) || 0;
    let spent = (await this.state.storage.get('spent')) || 0;

    // A window that has aged out is simply a new one. Held alerts from it were
    // already flushed by the alarm; if the alarm somehow did not run, the held list
    // is still here and gets folded into the next summary rather than discarded.
    if (now - windowStart >= WINDOW_MS) {
      windowStart = now;
      spent = 0;
      await this.state.storage.put('windowStart', windowStart);
    }

    if (spent < BUDGET) {
      await this.state.storage.put('spent', spent + 1);
      // Sent from here rather than handed back to the caller, so the budget and the
      // send cannot disagree if the caller goes away mid-request.
      const ok = await postToDiscord(this.env, payload);
      return json({ sent: true, ok });
    }

    // Over budget: hold it. Only the parts the summary needs are kept — retaining
    // whole embeds would grow storage without making the summary any better.
    const held = (await this.state.storage.get('held')) || [];
    held.push({ player, counts });
    await this.state.storage.put('held', held);

    // One alarm per window, set when the first alert overflows.
    if ((await this.state.storage.getAlarm()) === null) {
      await this.state.storage.setAlarm(windowStart + WINDOW_MS);
    }
    return json({ sent: false, held: held.length });
  }

  /**
   * Files one alert so `/search` can find it later.
   *
   * <p>Recorded whether or not the alert is sent individually. A flag that arrived
   * during a flood and only appeared in a summary is still a flag against that
   * player, and losing it from the history would make a busy period look quiet in
   * hindsight — exactly backwards.</p>
   */
  record(meta, fallbackPlayer, fallbackUuid, fallbackCounts) {
    const m = meta || {};
    const c = fallbackCounts || {};
    const player = String(m.player || fallbackPlayer || 'unknown').slice(0, 80);
    const uuid = String(m.uuid || fallbackUuid || '').slice(0, 64);
    try {
      this.sql.exec(
          'INSERT INTO flags (ts, uuid, player, player_lc, server, opponent, hotbar, `use`, attack, keybind, mc)'
          + ' VALUES (?,?,?,?,?,?,?,?,?,?,?)',
          Date.now(), uuid, player, player.toLowerCase(),
          String(m.server || '').slice(0, 120), String(m.opponent || '').slice(0, 80),
          num(m.hotbar ?? c.hotbar), num(m.use ?? c.use),
          num(m.attack ?? c.attack), num(m.keybind ?? c.keybind),
          String(m.mc || '').slice(0, 24));
      // Pruned opportunistically rather than on a timer: it is one cheap delete on
      // an indexed column and saves owning another alarm.
      this.sql.exec('DELETE FROM flags WHERE ts < ?', Date.now() - RETAIN_MS);
    } catch (e) {
      // History is a convenience. Never let it cost an alert.
      console.error('record failed', String(e));
    }
  }

  /** Everything recorded for one player, by name or UUID. */
  search(query) {
    const q = String(query || '').trim();
    if (!q) {
      return { rows: [] };
    }
    let rows = [];
    try {
      rows = this.sql.exec(
          'SELECT * FROM flags WHERE uuid = ? OR player_lc = ? ORDER BY ts DESC LIMIT 500',
          q, q.toLowerCase()).toArray();
    } catch (e) {
      console.error('search failed', String(e));
      return { rows: [] };
    }
    if (rows.length === 0) {
      return { rows: [] };
    }
    const totals = { hotbar: 0, use: 0, attack: 0, keybind: 0 };
    const servers = new Set(); const opponents = new Set(); const versions = new Set();
    let firstSeen = Infinity; let lastSeen = 0;
    for (const r of rows) {
      totals.hotbar += r.hotbar; totals.use += r.use;
      totals.attack += r.attack; totals.keybind += r.keybind;
      if (r.server) servers.add(r.server);
      if (r.opponent) opponents.add(r.opponent);
      if (r.mc) versions.add(r.mc);
      firstSeen = Math.min(firstSeen, r.ts);
      lastSeen = Math.max(lastSeen, r.ts);
    }
    return { rows, totals, firstSeen, lastSeen, servers: [...servers],
      opponents: [...opponents], versions: [...versions] };
  }

  /** Window closed: send one summary for everything that overflowed. */
  async alarm() {
    const held = (await this.state.storage.get('held')) || [];
    await this.state.storage.delete('held');
    await this.state.storage.put('spent', 0);
    await this.state.storage.put('windowStart', Date.now());
    if (held.length === 0) {
      return;
    }
    // Logged so a summary that fails to send is visible in `wrangler tail` rather
    // than being the one message nobody notices is missing.
    const ok = await postToDiscord(this.env, summaryPayload(held));
    console.log('summary flushed', held.length, 'held alerts, delivered:', ok);
  }
}

/** Rolls the held alerts into one message. */
function summaryPayload(held) {
  const byPlayer = new Map();
  for (const h of held) {
    const key = h.player || 'unknown';
    const acc = byPlayer.get(key) || { hotbar: 0, use: 0, attack: 0, keybind: 0 };
    for (const k of Object.keys(acc)) acc[k] += (h.counts && h.counts[k]) || 0;
    byPlayer.set(key, acc);
  }

  const rows = [...byPlayer.entries()]
      // Busiest first, because a summary that truncates should keep what matters.
      .sort((a, b) => total(b[1]) - total(a[1]));

  const lines = rows.slice(0, MAX_NAMED).map(([name, c]) => {
    const parts = [];
    if (c.hotbar) parts.push(`${c.hotbar} hotbar`);
    if (c.use) parts.push(`${c.use} use`);
    if (c.attack) parts.push(`${c.attack} attack`);
    if (c.keybind) parts.push(`${c.keybind} keybind`);
    return `\`${name}\` — ${parts.join(', ') || 'flagged'}`;
  });
  if (rows.length > MAX_NAMED) {
    lines.push(`_…and ${rows.length - MAX_NAMED} more players_`);
  }

  return {
    embeds: [{
      title: `${rows.length} more player${rows.length === 1 ? '' : 's'} flagged this minute`,
      color: 0xFFB454,
      description: lines.join('\n').slice(0, 4000),
      footer: { text: 'Batched because per-alert messages would exceed Discord\'s rate limit.' },
    }],
    allowed_mentions: { parse: [] },
  };
}

function num(v) {
  const n = Number(v);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

function total(c) {
  return c.hotbar + c.use + c.attack + c.keybind;
}

/** Shared sender. Returns whether Discord accepted it; never throws. */
async function postToDiscord(env, body) {
  const target = (env.DISCORD_WEBHOOK || '').trim();
  if (!target.startsWith('https://')) {
    console.error('DISCORD_WEBHOOK unset or not https');
    return false;
  }
  try {
    const res = await fetch(target, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      console.error('discord returned', res.status, (await res.text()).slice(0, 300));
    }
    return res.ok;
  } catch (e) {
    console.error('discord send threw', String(e));
    return false;
  }
}

function json(obj) {
  return new Response(JSON.stringify(obj), { headers: { 'content-type': 'application/json' } });
}
