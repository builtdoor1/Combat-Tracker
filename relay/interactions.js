/**
 * Discord slash-command handling for `/search`.
 *
 * <p>A webhook is one-way: it can post into a channel and nothing more. Answering a
 * command needs a Discord *application*, which posts interactions to an endpoint and
 * requires every one of them to be signature-verified. That is what this file does,
 * and why `/search` needed more than an extra branch in the relay.</p>
 *
 * <p>Verification is not optional politeness. The endpoint is a public URL, so
 * without it anyone could POST a forged interaction and read back the flag history
 * of any player. Discord signs each request with Ed25519 over the timestamp and the
 * raw body; the signature is checked against the application's public key before the
 * body is looked at at all.</p>
 */

/** Interaction types and response types, from Discord's API. */
const PING = 1;
const APPLICATION_COMMAND = 2;
const PONG = 1;
const CHANNEL_MESSAGE = 4;

/**
 * Verifies Discord's signature over `timestamp + rawBody`.
 *
 * <p>The raw text is required, not a re-serialised object: JSON.stringify would
 * reorder or reformat and the signature would never match.</p>
 */
export async function verifySignature(rawBody, signature, timestamp, publicKeyHex) {
  if (!signature || !timestamp || !publicKeyHex) {
    console.error('missing signature, timestamp or public key');
    return false;
  }

  // Trimmed and shape-checked before use. A key pasted with a trailing newline, or
  // the Application ID pasted in place of the Public Key, both fail verification in
  // exactly the same silent way as a genuinely bad signature — and Discord reports
  // only "could not be verified", which is no help at all. Checking the shape turns
  // those into a log line that names the mistake. Lengths and character classes are
  // safe to log; the key itself is not, and is never printed.
  const keyHex = String(publicKeyHex).trim();
  const sigHex = String(signature).trim();

  if (!/^[0-9a-fA-F]+$/.test(keyHex)) {
    console.error('DISCORD_PUBLIC_KEY is not hex (length ' + keyHex.length
        + ') — check you copied the Public Key from General Information, not the Application ID or client secret');
    return false;
  }
  if (keyHex.length !== 64) {
    console.error('DISCORD_PUBLIC_KEY should be 64 hex characters, got ' + keyHex.length
        + ' — an Ed25519 public key is 32 bytes');
    return false;
  }
  if (!/^[0-9a-fA-F]+$/.test(sigHex) || sigHex.length !== 128) {
    console.error('signature header malformed: length ' + sigHex.length);
    return false;
  }

  const data = new TextEncoder().encode(timestamp + rawBody);
  const sig = hexToBytes(sigHex);
  const raw = hexToBytes(keyHex);

  // Cloudflare originally exposed Ed25519 under the name NODE-ED25519 and later
  // added the standard 'Ed25519'. Which one a given runtime accepts has changed, so
  // both are tried rather than pinning to whichever happens to work today.
  for (const alg of [{ name: 'Ed25519' }, { name: 'NODE-ED25519', namedCurve: 'NODE-ED25519' }]) {
    try {
      const key = await crypto.subtle.importKey('raw', raw, alg, false, ['verify']);
      const ok = await crypto.subtle.verify(alg.name, key, sig, data);
      if (!ok) {
        console.error('signature did not verify against the configured public key ('
            + alg.name + ') — the key is well-formed, so it is most likely from a different application');
      }
      return ok;
    } catch (e) {
      // Unsupported algorithm name: try the other. Anything else is a genuine
      // verification failure and falls through to false below.
      if (!String(e).includes('Unrecognized') && !String(e).includes('not supported')
          && !String(e).includes('NotSupportedError')) {
        console.error('signature verify threw', String(e));
        return false;
      }
    }
  }
  console.error('no usable Ed25519 implementation in this runtime');
  return false;
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return out;
}

/**
 * Handles a verified interaction.
 *
 * @param lookup async (query) => rows, supplied by the caller so this file stays
 *               free of storage concerns
 */
export async function handleInteraction(body, lookup) {
  if (body.type === PING) {
    return { type: PONG };
  }
  if (body.type !== APPLICATION_COMMAND || !body.data) {
    return message('Unsupported interaction.');
  }
  if (body.data.name !== 'search') {
    return message(`Unknown command \`${String(body.data.name).slice(0, 32)}\`.`);
  }

  const opt = (body.data.options || []).find(o => o.name === 'player');
  const query = opt && typeof opt.value === 'string' ? opt.value.trim() : '';
  if (!query) {
    return message('Give a name or UUID: `/search player:<ign or uuid>`');
  }

  const found = await lookup(query);
  if (!found || found.rows.length === 0) {
    return message(`No flags recorded for \`${clean(query)}\`.\n`
        + '_Nothing recorded is not the same as nothing happened — only players running '
        + 'the mod report at all._');
  }
  return { type: CHANNEL_MESSAGE, data: { embeds: [summaryEmbed(query, found)], allowed_mentions: { parse: [] } } };
}

function summaryEmbed(query, found) {
  const { rows, totals, firstSeen, lastSeen, servers, opponents, versions } = found;

  const lines = [];
  if (totals.hotbar) lines.push(`**Hotbar switched** × ${totals.hotbar}`);
  if (totals.use) lines.push(`**Item used** × ${totals.use}`);
  if (totals.attack) lines.push(`**Attacked** × ${totals.attack}`);
  if (totals.keybind) lines.push(`**Keybind pressed by code** × ${totals.keybind}`);

  const fields = [
    { name: 'Alerts', value: String(rows.length), inline: true },
    { name: 'First seen', value: `<t:${Math.floor(firstSeen / 1000)}:R>`, inline: true },
    { name: 'Last seen', value: `<t:${Math.floor(lastSeen / 1000)}:R>`, inline: true },
    { name: 'What tripped', value: lines.join('\n') || '_none recorded_', inline: false },
  ];
  if (servers.length) {
    fields.push({ name: 'Servers', value: servers.slice(0, 5).map(s => `\`${clean(s)}\``).join('\n'), inline: true });
  }
  if (versions && versions.length) {
    // Worth showing: the checks are not identical across versions — hotbar
    // attribution does not exist before 1.21.5 — so the same flag count means
    // different things depending on where it came from.
    fields.push({ name: 'Minecraft', value: versions.slice(0, 5).map(s => `\`${clean(s)}\``).join('
'), inline: true });
  }
  if (opponents.length) {
    fields.push({ name: 'Fought', value: opponents.slice(0, 5).map(s => `\`${clean(s)}\``).join('\n'), inline: true });
  }

  const name = rows[0].player || query;
  return {
    title: `Flag history — ${clean(name)}`,
    color: 0xFFB454,
    thumbnail: { url: 'https://mc-heads.net/avatar/' + urlSafe(rows[0].uuid || name) + '/128' },
    fields,
    footer: {
      text: 'Unattributed input is not by itself proof of cheating; controller and '
          + 'accessibility mods trip the keybind check legitimately.',
    },
  };
}

function message(content) {
  return { type: CHANNEL_MESSAGE, data: { content, allowed_mentions: { parse: [] } } };
}

/** Values here came from players. Strip what would break out of a code span. */
function clean(s) {
  return String(s).replace(/[`\r\n]/g, '').slice(0, 80) || 'unknown';
}

function urlSafe(s) {
  const out = String(s).replace(/[^A-Za-z0-9_-]/g, '');
  return out || 'steve';
}
