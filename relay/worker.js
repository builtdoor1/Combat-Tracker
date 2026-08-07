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
export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response('', { status: 405 });
    }
    if (!env.DISCORD_WEBHOOK) {
      // Misconfigured deploy. Say so in the log, not to the caller.
      console.error('DISCORD_WEBHOOK secret is not set');
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
    if (typeof body.content !== 'string' || body.content.length === 0 || body.content.length > 2000) {
      return new Response('', { status: 400 });
    }

    // Rebuilt here rather than forwarded as-is. The caller supplies text and nothing
    // else, so a modified client cannot strip allowed_mentions to make your channel
    // ping, or smuggle through fields like tts or embeds.
    const res = await fetch(env.DISCORD_WEBHOOK, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        content: body.content,
        allowed_mentions: { parse: [] },
      }),
    });

    if (!res.ok) {
      console.error('discord returned', res.status);
      return new Response('', { status: 502 });
    }
    return new Response('', { status: 204 });
  },
};
