/**
 * Registers the `/search` slash command with Discord. Run once, and again only if
 * the command's shape changes.
 *
 * Usage:
 *   node register-command.js <APPLICATION_ID>
 *
 * The bot token is read from the DISCORD_BOT_TOKEN environment variable rather than
 * an argument, because arguments end up in shell history and in the process list
 * where other users on the machine can read them.
 *
 *   Windows (PowerShell):  $env:DISCORD_BOT_TOKEN="..."; node register-command.js 123
 *   macOS / Linux:         DISCORD_BOT_TOKEN="..." node register-command.js 123
 *
 * Global commands can take up to an hour to appear the first time. To test
 * immediately, pass a guild id as the second argument — guild commands register
 * instantly.
 *
 *   node register-command.js <APPLICATION_ID> <GUILD_ID>
 */
const APP_ID = process.argv[2];
const GUILD_ID = process.argv[3];
const TOKEN = process.env.DISCORD_BOT_TOKEN;

if (!APP_ID || !TOKEN) {
  console.error('Usage: DISCORD_BOT_TOKEN=... node register-command.js <APPLICATION_ID> [GUILD_ID]');
  process.exit(1);
}

const command = {
  name: 'search',
  description: 'Show every flag recorded for a player',
  options: [{
    type: 3,               // STRING
    name: 'player',
    description: 'Minecraft name or UUID',
    required: true,
  }],
};

const url = GUILD_ID
    ? `https://discord.com/api/v10/applications/${APP_ID}/guilds/${GUILD_ID}/commands`
    : `https://discord.com/api/v10/applications/${APP_ID}/commands`;

fetch(url, {
  method: 'POST',
  headers: { 'content-type': 'application/json', authorization: `Bot ${TOKEN}` },
  body: JSON.stringify(command),
}).then(async (res) => {
  const text = await res.text();
  if (res.ok) {
    console.log(`registered /search ${GUILD_ID ? 'in guild ' + GUILD_ID + ' (available now)' : 'globally (may take up to an hour)'}`);
  } else {
    // Printed in full: Discord names the offending field, and guessing from a bare
    // status code is how a five-second fix becomes an afternoon.
    console.error('failed:', res.status, text);
    process.exit(1);
  }
}).catch((e) => {
  console.error('request failed:', String(e));
  process.exit(1);
});
