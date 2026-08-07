/**
 * Combat Tracker alert relay.
 *
 * The mod posts here; this forwards to Discord. The point is that the real webhook
 * URL lives in this worker's secrets, server-side, and never ships inside the jar.
 * What ships is this worker's address, which is not a credential: someone who
 * extracts it can send junk, which you can rate-limit or redeploy away from, but
 * they cannot read your webhook, post directly to your channel, or delete it.
 *
 * Deploy:
 *   npx wrangler secret put DISCORD_WEBHOOK   (paste the Discord webhook URL)
 *   npx wrangler deploy
 *
 * Then set the worker's https URL as combatTrackerAlertEndpoint in
 * ~/.gradle/gradle.properties and rebuild the mod.
 */
// The Durable Object class has to be exported from the entrypoint Cloudflare loads.
export { AlertAggregator } from './aggregator.js';

/**
 * Pulls the player and per-check counts out of the embed the mod sent, for the
 * overflow summary. Both ends of this are ours, but a summary is not worth a
 * failed request, so every step degrades instead of throwing: no author name means
 * "unknown", unparseable counts mean the player is still named with no breakdown.
 */
function describe(embed) {
  const player = embed && embed.author && typeof embed.author.name === 'string'
      ? embed.author.name.slice(0, 80) : 'unknown';
  const counts = { hotbar: 0, use: 0, attack: 0, keybind: 0 };
  const field = embed && Array.isArray(embed.fields)
      ? embed.fields.find(f => f.name === 'What tripped') : null;
  if (field && typeof field.value === 'string') {
    const map = {
      'Hotbar switched': 'hotbar', 'Item used': 'use',
      'Attacked': 'attack', 'Keybind pressed by code': 'keybind',
    };
    // Located by plain string search rather than a regex built from the label.
    // Building one meant escaping the asterisks through two layers of quoting, and
    // getting that wrong produced `**` at the start of a pattern — an invalid
    // quantifier that threw on every single request.
    for (const [label, key] of Object.entries(map)) {
      const marker = '**' + label + '**';
      const at = field.value.indexOf(marker);
      if (at < 0) continue;
      const digits = field.value.slice(at + marker.length, at + marker.length + 12).match(/\d+/);
      if (digits) counts[key] = parseInt(digits[0], 10) || 0;
    }
  }
  return { player, counts };
}

/** Only the head-render service may supply images, and only over https. */
function imageOk(url) {
  return typeof url === 'string'
      && url.length < 300
      && /^https:\/\/mc-heads\.net\//.test(url);
}

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response(null, { status: 405 });
    }

    // Refuse a flood before doing any work for it. Keyed on the caller's address so
    // one abusive source cannot spend anyone else's allowance — there is no account
    // or token here to key on instead, since the mod authenticates as nobody.
    //
    // Both ceilings sit well above what the mod can generate: it batches to one
    // message every 15 seconds, so a genuine client uses a third of the burst
    // allowance and under half the sustained one, and a household with two players
    // still clears both. What this stops is a script that found this address inside
    // the jar and would otherwise bury real detections under junk.
    //
    // Worth being honest about the strength of it. Cloudflare documents this binding
    // as "not an accurate accounting system", and measured here it refused roughly a
    // third of an 80-request flood rather than everything past the limit. It is a
    // filter that raises the cost of spamming, not a gate. Strict enforcement would
    // need a Durable Object keeping the count itself.
    const who = request.headers.get('cf-connecting-ip') || 'unknown';
    for (const limiter of [env.BURST, env.SUSTAINED]) {
      // Guarded rather than assumed: a local `wrangler dev` without the bindings
      // should still run, and a missing limiter must not throw the request away.
      if (!limiter) continue;
      const { success } = await limiter.limit({ key: who });
      if (!success) {
        return new Response(null, { status: 429 });
      }
    }
    // Validate the secret itself, not just its presence. `wrangler secret put`
    // prompts with hidden input, and a paste that does not register stores an empty
    // string — the secret then exists, `wrangler secret list` shows it, and the only
    // symptom is an opaque "TypeError: Invalid URL" from fetch further down. Checking
    // here turns that into a log line that names the problem.
    const target = (env.DISCORD_WEBHOOK || '').trim();
    if (!target) {
      console.error('DISCORD_WEBHOOK is unset or empty — re-run: wrangler secret put DISCORD_WEBHOOK');
      return new Response(null, { status: 500 });
    }
    if (!target.startsWith('https://')) {
      console.error('DISCORD_WEBHOOK is not an https URL (length ' + target.length + ')');
      return new Response(null, { status: 500 });
    }

    // Cap the body before parsing it. Without this a single large POST costs you
    // CPU time on every abusive request.
    const raw = await request.text();
    if (raw.length > 4000) {
      return new Response(null, { status: 413 });
    }

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return new Response(null, { status: 400 });
    }

    // Accept either a plain message or a single embed, and rebuild the request from
    // the parts we recognise. Forwarding the caller's JSON as-is would let anyone
    // holding this address strip allowed_mentions, or smuggle through tts, or fan
    // out ten embeds per request. Rebuilding means the shape is ours.
    const out = { allowed_mentions: { parse: [] } };

    if (typeof body.content === 'string' && body.content.length > 0) {
      if (body.content.length > 2000) return new Response(null, { status: 400 });
      out.content = body.content;
    }

    if (Array.isArray(body.embeds)) {
      if (body.embeds.length > 1) return new Response(null, { status: 400 });
      const e = body.embeds[0];
      if (e && typeof e === 'object') {
        const embed = {};
        if (typeof e.title === 'string') embed.title = e.title.slice(0, 256);
        if (typeof e.description === 'string') embed.description = e.description.slice(0, 4096);
        if (Number.isInteger(e.color)) embed.color = e.color;
        if (typeof e.timestamp === 'string') embed.timestamp = e.timestamp.slice(0, 64);
        // Images are restricted to the head-render service. Otherwise the address
        // becomes a way to post any picture on the internet into the channel.
        if (e.thumbnail && imageOk(e.thumbnail.url)) embed.thumbnail = { url: e.thumbnail.url };
        if (e.author && typeof e.author.name === 'string') {
          embed.author = { name: e.author.name.slice(0, 256) };
          if (imageOk(e.author.icon_url)) embed.author.icon_url = e.author.icon_url;
        }
        if (Array.isArray(e.fields)) {
          embed.fields = e.fields
              .filter(f => f && typeof f.name === 'string' && typeof f.value === 'string')
              .slice(0, 25)
              .map(f => ({
                name: f.name.slice(0, 256),
                value: f.value.slice(0, 1024),
                inline: f.inline === true,
              }));
        }
        out.embeds = [embed];
      }
    }

    if (!out.content && !out.embeds) {
      return new Response(null, { status: 400 });
    }

    // Handed to the aggregator rather than sent from here. It holds the per-minute
    // budget, and the budget only means anything if one place counts all of it.
    // Falling back to a direct send when the binding is missing keeps a local
    // `wrangler dev` working and means a misconfigured deploy degrades to the old
    // behaviour rather than dropping alerts entirely.
    if (!env.AGGREGATOR) {
      const direct = await fetch(target, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(out),
      });
      return new Response(null, { status: direct.ok ? 204 : 502 });
    }

    const { player, counts } = describe(out.embeds && out.embeds[0]);
    const id = env.AGGREGATOR.idFromName('global');
    const stub = env.AGGREGATOR.get(id);
    const r = await stub.fetch('https://aggregator/alert', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ payload: out, player, counts }),
    });
    if (!r.ok) {
      console.error('aggregator returned', r.status);
      return new Response(null, { status: 502 });
    }
    // A held alert is not a failure: it arrives in the summary at the end of the
    // window. Only a send that was attempted and refused by Discord is worth
    // reporting back as one.
    const verdict = await r.json();
    return new Response(null, { status: verdict.sent && !verdict.ok ? 502 : 204 });
  },
};
