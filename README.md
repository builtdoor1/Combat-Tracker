# Combat Tracker

Proof that you're not cheating.

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11** that measures how you fight and turns it into a report you can show people. By *builtdoor*.

Got accused of reach, killaura, autoclicker or auto jump-reset? Hit record, play a few fights, and you get charts of your own timing and aim, plus a link you can paste in Discord.

It never touches your gameplay. It cannot help you aim, reach further, click faster or reset better. It only watches and writes down what already happened.

---

## Contents

- [What it measures](#what-it-measures) · [Install](#install) · [Using it](#using-it)
- [Recording a fight](#recording-a-fight) · [Reading your report](#reading-your-report) · [Sharing a fight](#sharing-a-fight)
- [What this can and can't prove](#what-this-can-and-cant-prove) · [Settings](#settings) · [Files](#files)
- [How it works under the hood](#how-it-works-under-the-hood) · [Building from source](#building-from-source) · [Credits](#credits)

---

## What it measures

Four accusations, four answers. In every case the thing that clears you is inconsistency: humans vary, cheats don't.

| Accusation | What the mod shows | What clears you |
|---|---|---|
| *"You're auto jump-resetting"* | Which tick you jumped on relative to the hit | A human lands across several ticks. A macro hits the same tick nearly every time. |
| *"You're using a triggerbot / autoclicker"* | The gap between each hit in a combo | Human clicking jitters. A bot's interval is machine-steady. |
| *"You have reach"* | How far the target was on every swing, hit or miss | Your hits sit at or under vanilla's 3.0 blocks. |
| *"You have aimbot"* | Where your crosshair sat on their hitbox | Human aim is scattered across the body. Aim assist clusters on centre. |

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3 or newer) for Minecraft 1.21.11.
2. Download these and drop them all into your `.minecraft/mods` folder:

   | File | Where | Needed? |
   |---|---|---|
   | `combat-tracker-<version>+1.21.11.jar` | [Latest release](../../releases/latest) | Yes |
   | Fabric API | [Modrinth](https://modrinth.com/mod/fabric-api) (`0.141.4+1.21.11`) | Yes |
   | Cloth Config | [Modrinth](https://modrinth.com/mod/cloth-config) | Yes, builds the settings screen |
   | Mod Menu | [Modrinth](https://modrinth.com/mod/modmenu) | Optional, adds the button that opens settings |

3. Launch Minecraft. You should see a small overlay in the top-left.

> Not sure where `.minecraft` is? On Windows press `Win+R`, type `%appdata%\.minecraft`, press Enter.

---

## Using it

Press **`J`** to show or hide the overlay. That's the only key bound by default. You can rebind it, and bind a recording key, under *Options → Controls*.

The overlay shows your running stats:

| Line | Meaning |
|---|---|
| `Jump: 12 perfect / 5 off` | Resets on the perfect tick vs off it |
| `Perfect 31.2%  Avg +0.3t SD 1.2` | How often you hit the perfect tick, your average offset, and how much it varies |
| `Last JR: Perfect!` | Your most recent attempt (or e.g. `Late 2t`) |
| `Combo: 3   Last 640ms` | Current combo length, and the gap since the last hit |
| `Variance 47ms` | How much your combo timing varies |

The Variance line changes colour. It goes **red when your clicking barely varies**, which is the bot-like pattern, and **white when it varies a lot**, which is what a human looks like. It stays grey until you've landed enough hits for the number to mean anything. White is good.

A red `● REC 1:24` line appears at the top while you're recording.

There's also a compact layout in the settings if the full overlay is too much.

---

## Recording a fight

1. Open **Mod Menu → Combat Tracker → Config → Recording tab**. (Or bind *Start/Stop Recording* under *Options → Controls*.)
2. Hit **Start Recording** and go fight.
3. Hit **Stop Recording** when you're done.

You get a chat message with a file path, and a **[Click to copy share link]**.

Two files land in `.minecraft/config/combat_tracker/recordings/`:

- **`session-<time>.html`** is the full report. Double-click it, it opens in your browser and needs no internet.
- **`session-<time>.json`** is the raw numbers, for anyone who wants to check them.

**Open recordings folder** in the settings takes you straight there.

---

## Reading your report

Four charts. On all of them you can **click a point to see its exact value**, scroll to zoom, drag to pan, and double-click to reset.

**Jump resets.** Every attempt plotted as **ticks** from the ideal reset tick, green for perfect and red for off. 0 means you jumped on the tick right after being hit, negative means early, positive means late.

Look at how much the dots move around. A human lands across several ticks. A macro parks on the same one almost every time, which shows up as a flat line.

**Combo timing.** The gap between consecutive hits in a combo. Same idea: spread is good. The jitter number in the cards above is that spread, measured. Low jitter looks automated.

**Reach.** How far the target was on each swing. The dashed line is vanilla's 3.0 block limit. Green dots (hits) should sit at or below it. Red dots (misses) can sit above it, which is normal: that's a swing thrown from too far away, which is why it missed.

**Aim placement.** Where your crosshair was on the target, with the hitbox drawn to scale. A messy cloud spread over the body is what a human tracking a moving target looks like. Aim assist produces a tight knot at the centre.

---

## Sharing a fight

Stopping a recording prints a click-to-copy link. **Copy share link** in the settings grabs the last one again.

Open it and you get the same charts, with your username and skin at the top, plus whoever you fought.

Nothing gets uploaded. The whole fight is compressed into the link itself, into the part after the `#`, which browsers never send to any server. There's no account, no server keeping your fights, and nothing that can go offline or leak later. The page just unpacks the link in your browser.

Links stay under 1800 characters so they fit in a Discord message. If a fight was long, the charts show an evenly spaced sample of it and the page says so. The headline numbers are always calculated from every event.

> A shared link names the people you fought. Fine for public PvP, but worth knowing before you post it.

---

## What this can and can't prove

**What it does well:** it shows the natural inconsistency of a real person playing, which is hard to fake convincingly and is usually what an admin wants to see.

**What it isn't:**

- **Not unforgeable.** Every report carries a SHA-256 hash and a signature, so casual editing of the file gets caught. But the signing key ships inside this open-source mod, so anyone who recompiles it can generate whatever numbers they like. Real proof would need a trusted server or a video, which no client-side mod can provide.
- **Reach and aim are measured on your computer.** Other players' positions are guessed between updates on your end, and the server saw them slightly differently depending on your ping. These figures won't exactly match a server anti-cheat's.
- **It can't prove a negative.** It shows what your fights looked like. Someone determined not to believe you still won't.

Treat a report as strong supporting evidence, not a verdict.

---

## Settings

**Mod Menu → Combat Tracker → Config.** Three tabs.

**General**, everything about the overlay:

| Setting | Default | What it does |
|---|---|---|
| Show HUD | On | Master switch (same as `J`) |
| Chat messages | On | One line per jump-reset attempt |
| Compact layout | Off | Shrinks the overlay to three lines |
| HUD scale | 1.00x | Size of the whole overlay (0.5 to 2.0) |
| Background opacity | 56% | Darkness of the box behind it (0 = none) |
| Theme | Yellow | Accent colour: Yellow / Aqua / Green / Pink / Orange / White |
| Move HUD… | | Drag the overlay anywhere on screen |

**Timing** has one setting: the **max tick gap** (default `10`), the largest gap between the hit and the jump that still counts as an attempt at a reset. Beyond it the two events are unrelated and nothing is recorded. It changes what gets counted, never how it's measured.

Everything else about detection is fixed and not adjustable. A wrong value would quietly corrupt the numbers this mod exists to defend, and a report only means something if every copy of the mod measured the same way.

**Recording** has Start/Stop Recording, Open recordings folder, Copy share link, and **Reset stats** (clears jump-reset and combo history, click twice to confirm).

---

## Files

```
.minecraft/config/combat_tracker/
├── config.json        # your settings
├── stats.json         # lifetime jump-reset history
├── combo_stats.json   # lifetime combo history
└── recordings/        # saved session reports (.html) and data (.json)
```

---

## How it works under the hood

*Implementation detail below. You can stop reading here if you just wanted to use the mod.*

<details>
<summary><b>Jump resets: detection, the 200ms cutoff, and why there's no ping compensation</b></summary>

- **Jump** comes from the real `jumpFromGround()` call via a mixin, not from upward velocity, so knockback (a crit while you stand still) can never be mistaken for a jump.
- **Hit** is detected when `hurtTime` goes 0 to positive while the invulnerability timer is active *and* horizontal knockback exceeds `0.065`, which filters out fall, fire and poison damage (none of which impart horizontal knockback).
- Timing is measured in **ticks**, following [sootysplash/jump-reset](https://github.com/sootysplash/jump-reset). Two tick stamps are compared, both read from `player.tickCount`: the tick you jumped and the tick you were hit. A reset is **perfect when the jump lands on the tick right after the hit**, so the signed offset is `jumpTick - (hurtTick + 1)`: 0 perfect, negative early, positive late.
- Pairs further apart than the **max tick gap** (default 10) are not an attempt at a reset and are not recorded. A jump older than 2.5s is likewise never considered against a hit.
- Milliseconds were the wrong unit. The knockback is applied on a tick and the jump impulse is applied on a tick, so a millisecond figure was a tick count in disguise, which is why the old values piled up on 50ms boundaries with nothing meaningful in between.
- **One deviation from sootysplash, on purpose:** it times off any `handleDamageEvent`, which includes fall, fire and poison damage. A hit here still has to carry horizontal knockback, because a jump after fall damage is not a jump reset and this report is meant to describe PvP.

**No ping compensation, deliberately.** Earlier versions wound the hit timestamp back by the estimated one-way latency. That's wrong: your jump is timed on your client's clock, so subtracting latency from only the hit mixes a server-frame time with a client-frame one and adds a flat bias of half your ping to every result. At 120ms that's +60ms of an 80ms success window, so genuine resets scored as TOO_LATE.

Both events are observed on your own client's tick clock and latency delays both equally, so it cancels on its own. Ping is still recorded on the report as context, averaged across the whole recording rather than sampled when you stop, and it adjusts nothing.

</details>

<details>
<summary><b>Reach: what's actually measured, and what isn't</b></summary>

Reach is the distance from your eye to the **nearest point** of the target's hitbox. That is verbatim what vanilla checks. `Player.isWithinEntityInteractionRange` is:

```java
box.distanceToSqr(getEyePosition()) < range * range
```

where `range` is the `entity_interaction_range` attribute, 3.0 by default. So it's also what server anti-cheats measure.

It is **not** the point where your aim ray crosses the hitbox. That's a different and always-larger number. Aim at someone's head from below and the ray enters the box further away than its nearest corner, which makes legitimate vanilla hits read above 3.0 blocks. [Wolren's ReachDisplay](https://github.com/Wolren/ReachDisplay) draws the same distinction and ships nearest-point as its default, with ray-crossing as an alternate display mode.

Measured against the tick position, not the interpolated render frame, for the same reason: vanilla validates on tick positions, and mixing an interpolated eye with a tick-position hitbox is worth up to a quarter of a block at sprint speed.

</details>

<details>
<summary><b>Swings, aim and target attribution</b></summary>

- Every left-click attack is caught at `Minecraft.startAttack()`, including the ones that hit nothing, which `MultiPlayerGameMode.attack` never sees. That's what makes measuring a whiff possible.
- Vanilla's own guards (`missTime`, `hitResult`, `isHandsBusy`) are re-checked there, so clicks vanilla itself discards don't enter the data as phantom swings.
- **Aim** is recorded two ways: the angle between your crosshair and the hitbox centre, and where on the hitbox the ray passed (used for the scatter plot).
- A whiff is attributed to the player closest to your crosshair within 6 blocks and 30°. Swinging at empty air with nobody nearby records nothing.

</details>

<details>
<summary><b>Combos</b></summary>

- A combo is a chain of 2 or more sprint hits on the same player, with no self-damage in between and no gap longer than ~0.7s.
- Only hits landed while sprinting count.
- The ~0.7s gap is roughly the sword's full attack-cooldown recharge (~0.625s) plus grace. If your sword is charged and you don't swing, the combo is over. It's a hardcoded constant so a stale config can't shift it.
- The mod reports the average interval and the jitter (standard deviation). Low jitter suggests a triggerbot, high jitter suggests a human.

**Intervals are timed from your mouse press, not from the tick the attack was processed on.** This matters more than it sounds. Attacks are handled inside the client tick loop, so timing them there quantises every interval to a 50ms boundary: you get 550, 600, 650 and nothing in between, and the natural variation between a person's clicks (the entire signal against an autoclicker) is rounded away before it can be measured.

Mouse buttons arrive through `MouseHandler.onButton`, which the GLFW callback hands to `Minecraft.execute`. That queue drains once per **frame**, so a timestamp taken there is accurate to a frame instead of a tick: roughly 5ms at 200fps versus 50ms. Presses are queued and paired first-in-first-out with attacks, matching vanilla's own `while (keyAttack.consumeClick())` loop, and only presses made with the mouse grabbed are recorded so clicks in a menu can't shift the pairing.

An autoclicker driving the real mouse is captured exactly like a human hand, which is the point: its steadiness becomes visible. Something injecting attacks inside the game leaves no click at all and falls back to tick timing.

</details>

<details>
<summary><b>Share links: encoding and format</b></summary>

The session is quantised to integers (reach to centiblocks, angles to centidegrees, offsets to milliblocks), timestamps delta-encoded, varint-packed with boolean flags folded into adjacent varints, deflated, and base64url'd into the URL fragment, which browsers never transmit.

The viewer ([`docs/index.html`](docs/index.html)) decodes it with the browser-native `DecompressionStream('deflate-raw')`. No JavaScript libraries are involved.

To fit Discord's 2000-character message limit, a binary search finds the largest number of plotted points that fits the budget. Summary statistics are always computed over every event and encoded exactly, so only graph resolution degrades, and the viewer states "showing N of M" when it does.

The payload starts with a version byte and that's a permanent contract: links already shared have to keep working, so the viewer decodes every past version. v1 links still render.

</details>

<details>
<summary><b>Integrity block</b></summary>

Each session embeds a SHA-256 hash and an HMAC-SHA256 signature over the exact canonical data, plus start and end timestamps. Editing the file breaks the hash, so casual tampering is detectable.

Verify independently by recomputing SHA-256 over the exact `canonicalData` string (UTF-8) in the `.json`.

As stated above: the signing key ships in the mod, so this is tamper-evidence, not unforgeable proof.

</details>

---

## Building from source

```bash
./gradlew build
```

Output: `build/libs/combat-tracker-<modversion>+<mcversion>.jar`. Requires **JDK 21**.

Everything downstream of a recorded session (statistics, charts, link encoding) is free of any Minecraft import, so the whole report pipeline can be exercised without launching the game:

```bash
./gradlew reportPreview
```

That renders synthetic sessions to `build/preview/` as HTML reports plus a link fragment, and prints how link length and plotted-point count behave as sessions grow.

---

## Credits

- Jump-reset detection is adapted from [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).
- Dropping ping compensation follows [sootysplash/jump-reset](https://github.com/sootysplash/jump-reset), which compares tick counts on the client clock and needs no latency correction.
- The reach metric follows [Wolren/ReachDisplay](https://github.com/Wolren/ReachDisplay).

## License

[MIT](LICENSE) © 2026 builtdoor
