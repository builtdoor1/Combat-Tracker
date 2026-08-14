# Combat Tracker

Shows what your fights actually looked like.

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1 through 26.2** that measures how you fight and turns it into a report you can show people. By *builtdoor*.

Got accused of reach, killaura, autoclicker or auto jump-reset? Hit record, play a few fights, and you get charts of your own timing and aim, plus a link you can paste in Discord.

It never touches your gameplay. It cannot help you aim, reach further, click faster or reset better. It only watches and writes down what already happened.

> **This mod reports to its author.** When an action happens that no keyboard, mouse or server input asked for, it sends your Minecraft name and UUID, the server address, the Minecraft version, the UTC time, and how many checks tripped. Nothing else. There is no setting to turn it off — the only person who would is the one it would be reporting on. If you would rather not be reported on, don't install it.

---

## Contents

- [What it measures](#what-it-measures) · [Supported versions](#supported-versions) · [Install](#install) · [Using it](#using-it)
- [Recording a fight](#recording-a-fight) · [Reading your report](#reading-your-report) · [Sharing a fight](#sharing-a-fight)
- [What this can and can't show](#what-this-can-and-cant-show) · [Settings](#settings) · [Files](#files)
- [How it works under the hood](#how-it-works-under-the-hood) · [Building from source](#building-from-source) · [Credits](#credits)

---

## What it measures

Four accusations, four answers. In every case the tell is inconsistency: humans vary, cheats don't. The mod shows you the shape — it doesn't settle the argument, but it gives someone willing to look something concrete to look at.

| Accusation | What the mod shows | What a human looks like |
|---|---|---|
| *"You're auto jump-resetting"* | The millisecond gap between getting hit and jumping | Human timing varies by tens of milliseconds. A macro repeats nearly the same number. |
| *"You're using a triggerbot / autoclicker"* | The gap between each hit in a combo | Human clicking jitters. A bot's interval is machine-steady. |
| *"You have reach"* | How far the target was on every swing, hit or miss | Hits sit at or under vanilla's 3.0 blocks, and the misses above it are swings thrown from too far — which is what a person does. |
| *"You have aimbot"* | Where your crosshair sat on their hitbox | Human aim is scattered across the body. Aim assist clusters on centre. |

---

## Supported versions

One download per Minecraft version. The file is named for the version it is for, so
match the number to your game — `combat-tracker-1.21.11.jar` is the 1.21.11 build.

| Minecraft | File | Java | Everything works? |
|---|---|---|---|
| 26.2 | `combat-tracker-26.2.jar` | **25** | Yes |
| 1.21.11 | `combat-tracker-1.21.11.jar` | 21 | Yes |
| 1.21.10 | `combat-tracker-1.21.10.jar` | 21 | Yes |
| 1.21.9 | `combat-tracker-1.21.9.jar` | 21 | Yes |
| 1.21.8 | `combat-tracker-1.21.8.jar` | 21 | Yes |
| 1.21.4 | `combat-tracker-1.21.4.jar` | 21 | All but hotbar detection |
| 1.21.1 | `combat-tracker-1.21.1.jar` | 21 | All but hotbar detection |

Each build declares the one version it is for, so the loader will refuse the wrong
file rather than start and break in a confusing way. **26.2 needs Java 25**, because
Minecraft itself does.

**Why hotbar detection is missing on 1.21.1 and 1.21.4.** From 1.21.5 Minecraft
changes your selected slot through a method the mod can watch, which is how it tells
an ordinary switch from a scripted one. Before that the slot is just a number the
game writes directly, with nothing to watch. All that would be left is noticing the
number changed, which cannot tell your scroll wheel from a macro, so it is switched
off rather than left to report everything. Nothing else differs, and none of what the
mod *measures* — jump resets, combo timing, reach, aim — is affected.

Versions not listed are not supported. Minecraft changed how input and rendering work
several times across this range, and each build is compiled against the version it
names.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3 or newer) for your Minecraft version.
2. Download these and drop them all into your `.minecraft/mods` folder:

   | File | Where | Needed? |
   |---|---|---|
   | `combat-tracker-<your minecraft version>.jar` | [Latest release](../../releases/latest) | Yes |
   | Fabric API | [Modrinth](https://modrinth.com/mod/fabric-api) | Yes, matching your version |
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
| `Jump: 12 hit / 5 miss` | Jump resets you landed vs missed |
| `Rate 70.6%  Avg 44 SD 21` | Success rate, average timing, and SD (how much your timing varies) |
| `Last JR: HIT +38ms` | Your most recent attempt |
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

**Jump resets.** Every attempt, green for hits and red for misses, plotted as milliseconds from the hit. Look at how much the dots move around. Human timing drifts by tens of milliseconds and never repeats exactly. A macro produces a nearly flat line.

**Combo timing.** The gap between consecutive hits in a combo. Same idea: spread is good. The jitter number in the cards above is that spread, measured. Low jitter looks automated.

**Reach.** How far the target was on each swing. The dashed line is vanilla's 3.0 block limit. Green dots (hits) should sit at or below it. Red dots (misses) can sit above it, which is normal: that's a swing thrown from too far away, which is why it missed.

**Aim placement.** Where your crosshair was on the target, with the hitbox drawn to scale. A messy cloud spread over the body is what a human tracking a moving target looks like. Aim assist produces a tight knot at the centre.

---

## Sharing a fight

Stopping a recording prints a click-to-copy link. **Copy share link** in the settings grabs the last one again.

Open it and you get the same charts, with your username and skin at the top, plus whoever you fought.

The fight data is never uploaded. It's compressed into the part of the link after the `#`, which browsers never send to any server. There's no account and no server keeping your fights — the page just unpacks the link in your browser. One exception worth naming: the page fetches player heads from `mc-heads.net`, so that service sees the names on the report.

Links stay under 1800 characters so they fit in a Discord message. If a fight was long, the charts show an evenly spaced sample of it and the page says so. The mod computes the headline numbers from every event, not from the sampled points — but the viewer prints whatever numbers the link carries and never re-derives them from the plotted points. On a link you didn't make yourself, check that the cards and the graph agree.

> A shared link names the people you fought. Fine for public PvP, but worth knowing before you post it. The reverse is also true: the name and skin at the top of a link are just text in the payload, so anyone can produce a link that appears to be someone else's.

---

## What this can and can't show

**What it does well:** it makes the texture of your play visible — scattered aim versus a knot on centre, timing that wanders versus timing that doesn't. That's what an admin is actually trying to see, and it reads off a chart in ten seconds where it takes twenty minutes of clips.

**What it isn't:**

- **The share link proves nothing.** It carries no signature at all. It's deflate + base64url of the struct documented below, and the viewer's only check is that the version byte is in range. Anyone can write one from scratch in about a hundred lines of Python, in any name they like, and the page will fetch that player's real skin to go with it. No mod, no recompile.
- **The saved files are a checksum, not a seal.** The `.json` and `.html` carry a SHA-256 and an HMAC-SHA256 over the exact recorded data, and `linkifyReports` now recomputes both before it will rebuild a link from a report. That catches a corrupted file and it catches someone editing the numbers in a text editor. It does not stop anyone who edits the numbers *and* recomputes the two values, because the HMAC key is a string literal in this repo. There is nothing secret here.
- **Reach and aim are measured on your computer.** Other players' positions are guessed between updates on your end, and the server saw them slightly differently depending on your ping. These figures won't exactly match a server anti-cheat's.
- **It can't prove a negative.** It shows what your fights looked like. Someone determined not to believe you still won't.

Real proof would need a trusted server or a video, which no client-side mod can provide. A report is a record of what a fight looked like. To someone who already has reason to take you seriously it's worth a lot, because it shows a shape that's tedious to fake and instant to read. To someone who doesn't, it's worth nothing, and you should expect them to say so.

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

**Timing** has one setting: the success window (default `0` to `80 ms`), which decides what counts as a successful reset. It changes the scoring only, never the measurement.

Everything else about detection is fixed and not adjustable. A wrong value would quietly corrupt the numbers, and two reports are only comparable if both were measured the same way.

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
- A hit opens a short tick window (6 ticks grounded, 10 airborne, longer in the air because you can't jump until you land). The next jump inside it is scored with `System.nanoTime()` and classed **HIT**, **MISS too late**, or **MISS too early**.
- **Attempts further than 200ms from the hit are discarded**, not scored. A jump a fifth of a second either side of taking damage is just a jump that happened nearby, and counting it drags the statistics around and flattens the chart's axis.

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

To fit Discord's 2000-character message limit, a binary search finds the largest number of plotted points that fits the budget. Summary statistics are computed over every event before any downsampling and encoded exactly, so only graph resolution degrades, and the viewer states "showing N of M" when it does.

Note that this is a property of the encoder, not a checkable property of a link. The viewer renders the summary fields as given and never re-derives them from the plotted points, so on a forged link the cards and the graph need not agree.

The payload starts with a version byte and that's a permanent contract: links already shared have to keep working, so the viewer decodes every past version. v1 links still render.

</details>

<details>
<summary><b>Checksums, and what they aren't</b></summary>

Each saved session embeds a SHA-256 hash and an HMAC-SHA256 over the exact canonical data. Both catch a corrupted or truncated file, and both catch a file someone edited and left alone afterwards. Neither survives contact with someone who edits the data *and* recomputes the two values: the hash sits next to the data it covers, and the HMAC key is the string literal in [`IntegrityUtil.java`](src/client/java/combat_tracker/record/IntegrityUtil.java). There is nothing secret here.

Recompute SHA-256 over the exact `canonicalData` string (UTF-8) to confirm a `.json` arrived intact. That is the entire scope of what it tells you — it says nothing about where the numbers came from.

`linkifyReports` recomputes both before it will rebuild a share link, and writes a link only for a report that still matches its own hashes. For a long time it didn't, which made it the easiest way to launder edited numbers: change the figures in the canonical block with a text editor, run the tool, and out came a clean-looking link while the untouched hash a few lines above still described the data the report used to hold.

Anything it cannot check is refused rather than downgraded — a missing integrity block, an unreadable one, or two canonical blocks where there should be one. A link carries no verdict, so a link written for a report that failed to verify would be indistinguishable from any other once it left the machine that made it.

**Share links carry neither value.** `SharePayload.pack()` writes a version byte and the data, and that's all; the viewer checks the version byte is in range and renders whatever it finds.

</details>

---

## Building from source

```bash
./gradlew build
```

That builds for the version named in `gradle.properties`. To build for a different
one, pass its properties — and, for the versions that need different code, its source
variant:

```bash
./gradlew build -PsourceVariant=1.21.8 -Pminecraft_version=1.21.8 -Pfabric_api_version=0.136.1+1.21.8 -Pcloth_config_version=19.0.147 -Pmodmenu_version=15.0.2
```

Most of the source is shared. Where a version genuinely needs different code — a
different mixin target, not a different constant — the file is overridden under
`versions/<version>/java`, and `versions/<version>/exclude.txt` can drop one
outright. Variants exist for 1.21.1, 1.21.4, 1.21.8 and 26.2; 1.21.9 through 1.21.11
build from the shared source with no variant.

Two checks worth running:

```bash
./gradlew provenanceSelfTest
```

Drives the detection logic headlessly — software switches reported, legitimate input
ignored, alert scheduling — in seconds, without launching the game.

```bash
./gradlew runClient -PmixinDebug
```

Launches the game and writes every patched class to `run/.mixin.out`, so you can
confirm which hooks actually attached rather than inferring it from the absence of a
crash. Useful after porting to a new version.

Reporting is off unless an endpoint is baked in, so a build from a fresh clone sends
nothing. See `combatTrackerAlertEndpoint` in `build.gradle`.

## Credits

- Jump-reset detection is adapted from [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).
- Dropping ping compensation follows [sootysplash/jump-reset](https://github.com/sootysplash/jump-reset), which compares tick counts on the client clock and needs no latency correction.
- The reach metric follows [Wolren/ReachDisplay](https://github.com/Wolren/ReachDisplay).

## License

[MIT](LICENSE) © 2026 builtdoor
