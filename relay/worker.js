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
/** Only the head-render service may supply images, and only over https. */
function imageOk(url) {
  return typeof url === 'string'
      && url.length < 300
      && /^https:\/\/mc-heads\.net\//.test(url);
}

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response('', { status: 405 });
    }
    // Validate the secret itself, not just its presence. `wrangler secret put`
    // prompts with hidden input, and a paste that does not register stores an empty
    // string — the secret then exists, `wrangler secret list` shows it, and the only
    // symptom is an opaque "TypeError: Invalid URL" from fetch further down. Checking
    // here turns that into a log line that names the problem.
    const target = (env.DISCORD_WEBHOOK || '').trim();
    if (!target) {
      console.error('DISCORD_WEBHOOK is unset or empty — re-run: wrangler secret put DISCORD_WEBHOOK');
      return new Response('', { status: 500 });
    }
    if (!target.startsWith('https://')) {
      console.error('DISCORD_WEBHOOK is not an https URL (length ' + target.length + ')');
      return new Response('', { status: 500 });
    }

    // Cap the body before parsing it. Without this a single large POST costs you
    // CPU time on every abusive request.
    const raw = await request.text();
    if (raw.length > 4000) {
      return new Response('', { status: 413 });
    }

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return new Response('', { status: 400 });
    }

    // Accept either a plain message or a single embed, and rebuild the request from
    // the parts we recognise. Forwarding the caller's JSON as-is would let anyone
    // holding this address strip allowed_mentions, or smuggle through tts, or fan
    // out ten embeds per request. Rebuilding means the shape is ours.
    const out = { allowed_mentions: { parse: [] } };

    if (typeof body.content === 'string' && body.content.length > 0) {
      if (body.content.length > 2000) return new Response('', { status: 400 });
      out.content = body.content;
    }

    if (Array.isArray(body.embeds)) {
      if (body.embeds.length > 1) return new Response('', { status: 400 });
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
      return new Response('', { status: 400 });
    }

    const res = await fetch(target, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(out),
    });

    if (!res.ok) {
      console.error('discord returned', res.status);
      return new Response('', { status: 502 });
    }
    return new Response('', { status: 204 });
  },
};
