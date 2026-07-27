# Combat Tracker

**Proof that you're not cheating.**

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11** that quietly measures how you fight and turns it into a report you can show people. By *builtdoor*.

Got accused of reach, killaura, autoclicker or auto jump-reset? Hit record, play a few fights, and Combat Tracker gives you charts of your own timing and aim — plus a link you can paste in Discord.

**It never touches your gameplay.** It cannot help you aim, reach further, click faster or reset better. It only watches what already happens and writes it down. That's the whole point: something that changed how you play would be worthless as evidence that you don't.

---

## Contents

- [What it measures](#what-it-measures) · [Install](#install) · [Using it](#using-it)
- [Recording a fight](#recording-a-fight) · [Reading your report](#reading-your-report) · [Sharing a fight](#sharing-a-fight)
- [What this can and can't prove](#what-this-can-and-cant-prove) · [Settings](#settings) · [Files](#files)
- [How it works under the hood](#how-it-works-under-the-hood) · [Building from source](#building-from-source) · [Credits](#credits)

---

## What it measures

Four accusations, four answers. The short version of all four: **humans are inconsistent, and cheats are not.**

| Accusation | What the mod shows | What clears you |
|---|---|---|
| *"You're auto jump-resetting"* | The exact millisecond gap between getting hit and jumping, every single time | A human's timing **wanders** by tens of milliseconds. A macro repeats nearly the same number. |
| *"You're using a triggerbot / autoclicker"* | The gap between each hit in a combo | Human clicking **jitters**. A bot's interval is machine-steady. |
| *"You have reach"* | How far away the target was on every swing, hit or miss | Your hits sit **at or under vanilla's 3.0 blocks**. |
| *"You have aimbot"* | Where your crosshair actually sat on their hitbox | Human aim is **scattered** across the body. Aim assist clusters on dead centre. |

In every case the **messiness of your data is the evidence**. A perfect-looking graph is the suspicious one.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3 or newer) for Minecraft 1.21.11.
2. Download these and drop them all into your `.minecraft/mods` folder:

   | File | Where | Needed? |
   |---|---|---|
   | `combat-tracker-<version>+1.21.11.jar` | [Latest release](../../releases/latest) | Yes |
   | Fabric API | [Modrinth](https://modrinth.com/mod/fabric-api) (`0.141.4+1.21.11`) | Yes |
   | Cloth Config | [Modrinth](https://modrinth.com/mod/cloth-config) | Yes — builds the settings screen |
   | Mod Menu | [Modrinth](https://modrinth.com/mod/modmenu) | Optional — adds the button that opens settings |

3. Launch Minecraft. You should see a small overlay in the top-left.

> **Not sure where `.minecraft` is?** Windows: press `Win+R`, type `%appdata%\.minecraft`, press Enter.

---

## Using it

**Press `J`** to show or hide the overlay. That's the only key bound by default; you can rebind it, and bind a recording key, under *Options → Controls*.

The overlay shows your running stats:

| Line | Meaning |
|---|---|
| `Jump: 12 hit / 5 miss` | Jump resets you landed vs missed |
| `Rate 70.6%  Avg 44 SD 21` | Success rate, average timing, and **SD** — how much your timing varies |
| `Last JR: HIT +38ms` | Your most recent attempt |
| `Combo: 3   Last 640ms` | Current combo length, and the gap since the last hit |
| `Variance 47ms` | How much your combo timing varies |

**The Variance line changes colour, and that's the one to watch.** It goes **red when your clicking barely varies** — the bot-like pattern — and **white when it varies a lot**, which is what a human looks like. It stays grey until you've landed enough hits for the number to mean anything. White is good.

A red `● REC 1:24` line appears at the top while you're recording.

There's also a **compact layout** in the settings if the full overlay is too much.

---

## Recording a fight

1. Open the settings screen: **Mod Menu → Combat Tracker → Config → Recording tab**. (Or bind *Start/Stop Recording* under *Options → Controls* and press that.)
2. Hit **Start Recording** and go fight.
3. Hit **Stop Recording** when you're done.

You get a chat message with a file path, and a **[Click to copy share link]** you can paste anywhere.

Two files land in `.minecraft/config/combat_tracker/recordings/`:

- **`session-<time>.html`** — the full report. Double-click it; it opens in your browser and needs no internet.
- **`session-<time>.json`** — the raw numbers, for anyone who wants to check them.

**Open recordings folder** in the settings takes you straight there.

---

## Reading your report

Five charts. On all of them you can **scroll to zoom, drag to pan, and double-click to reset**, and hovering any dot shows its exact value.

### 1. Successful jump resets

Every reset you landed, and nothing else. **This is the chart that clears you.**

Look at how much the dots **bounce around** the dashed mean line. A human's timing drifts by tens of milliseconds — you're never hitting the same millisecond twice. A macro produces a nearly flat line.

The axis is deliberately zoomed to just your successful timings, so a few milliseconds of scatter is clearly visible instead of being squashed flat.

### 2. Every attempt

The same thing including misses, so nobody can say you hid the bad ones. Green landed, red missed.

### 3. Combo timing

The gap between consecutive hits in a combo. Same principle: **spread is good.** A triggerbot swings on a machine-steady interval; your gaps should wobble.

The **jitter** number under the heading is that wobble, measured. Low jitter looks automated.

### 4. Reach

How far the target was on each swing. The dashed line is vanilla's **3.0 block** limit.

- **Green dots** are hits. These should sit **at or below the line.**
- **Red dots** are misses, and they can sit *above* the line — that's just a swing thrown from too far away, which is exactly why it missed. Red above the line is normal and not incriminating.

### 5. Aim placement

Where your crosshair was on the target, with their hitbox drawn to scale (a player is 0.6 blocks wide and 1.8 tall, and the box keeps those real proportions).

**You want a messy cloud spread over the body.** That's a human tracking a moving target. Aim assist produces a tight knot right at the centre.

---

## Sharing a fight

Stopping a recording prints a **click-to-copy link**. There's also **Copy share link** in the settings to grab the last one again.

Open it and you'll see the same charts, with your username and skin at the top, plus whoever you fought.

**Nothing gets uploaded.** The entire fight is compressed into the link itself — into the part after the `#`, which browsers never send to any server. There's no account, no server keeping your fights, and nothing that can go offline or leak later. The page just unpacks the link in your browser.

Links stay under 1800 characters so they fit in a Discord message. If a fight was long, the *charts* show an evenly spaced sample of it and the page tells you so — the headline numbers are always calculated from every single event, never estimated.

> A shared link **names the people you fought**. Fine for public PvP, but worth knowing before you post it.

---

## What this can and can't prove

Being straight with you, because a tool like this is worthless if it oversells itself.

**What it does well:** it shows the natural inconsistency of a real human playing. That's genuinely hard to fake convincingly, and it's usually what a reasonable admin wants to see.

**What it isn't:**

- **It's not unforgeable.** Every report carries a SHA-256 hash and a signature, so *casual* editing of the file gets caught. But the signing key ships inside this open-source mod — anyone who recompiles it can generate whatever numbers they like. Real proof would need a trusted server or a video, which no client-side mod can provide.
- **Reach and aim are measured on your computer.** Other players' positions are guessed between updates on your end, and the server saw them somewhere slightly different depending on your ping. So these figures won't exactly match a server anti-cheat's.
- **It can't prove a negative.** It shows what your fights looked like. Someone determined not to believe you still won't.

Treat a report as **strong supporting evidence**, not a verdict.

---

## Settings

**Mod Menu → Combat Tracker → Config.** Three tabs.

**General** — everything about the overlay:

| Setting | Default | What it does |
|---|---|---|
| Show HUD | On | Master switch (same as `J`) |
| Chat messages | On | One line per jump-reset attempt |
| Compact layout | Off | Shrinks the overlay to three lines |
| HUD scale | 1.00x | Size of the whole overlay (0.5–2.0) |
| Background opacity | 56% | Darkness of the box behind it (0 = none) |
| Theme | Yellow | Accent colour: Yellow / Aqua / Green / Pink / Orange / White |
| Move HUD… | — | Drag the overlay anywhere on screen |

**Timing** — one setting: the **success window** (default `0`–`80 ms`), which decides what counts as a *successful* reset. It changes the scoring only, never the measurement.

Everything else about detection is **deliberately fixed and not adjustable.** A wrong value would quietly corrupt the very numbers this mod exists to defend, and a report only means something if every copy of the mod measured the same way.

**Recording** — Start/Stop Recording, Open recordings folder, Copy share link, and **Reset stats** (clears jump-reset *and* combo history; click twice to confirm).

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

*Everything below is implementation detail — you can stop reading here if you just wanted to use the mod.*

<details>
<summary><b>Jump resets — detection and why there's no ping compensation</b></summary>

- **Jump** comes from the real `jumpFromGround()` call via a mixin, not from upward velocity — so knockback (a crit while you stand still) can never be mistaken for a jump.
- **Hit** is detected when `hurtTime` goes 0→positive while the invulnerability timer is active **and** horizontal knockback exceeds `0.065`, which filters out fall, fire and poison damage (none of which impart horizontal knockback).
- A hit opens a short tick window (6 ticks grounded, 10 airborne — longer in the air because you can't jump until you land). The next jump inside it is scored with `System.nanoTime()`, and the signed delta is classed **HIT**, **MISS – too late**, or **MISS – too early**.

**No ping compensation, deliberately.** Earlier versions wound the hit timestamp back by the estimated one-way latency. That's wrong: your jump is timed on your client's clock, so subtracting latency from only the hit mixes a server-frame time with a client-frame one and adds a flat bias of **half your ping** to every result. At 120ms that's +60ms of an 80ms success window — genuine resets scored as TOO_LATE, which is exactly how it failed.

Both events are observed on your own client's tick clock and latency delays both equally, so it cancels on its own. Your ping is still recorded on the report, purely as context; it adjusts nothing.

</details>

<details>
<summary><b>Reach — what's actually measured, and what isn't</b></summary>

Reach is the distance from your eye to the **nearest point** of the target's hitbox. That is verbatim what vanilla checks — `Player.isWithinEntityInteractionRange` is:

```java
box.distanceToSqr(getEyePosition()) < range * range
```

…where `range` is the `entity_interaction_range` attribute, **3.0** by default. So it's also what server anti-cheats measure.

It is **not** the point where your aim ray *crosses* the hitbox. That's a different and always-larger number — aim at someone's head from below and the ray enters the box further away than its nearest corner — which makes legitimate vanilla hits read above 3.0 blocks. [Wolren's ReachDisplay](https://github.com/Wolren/ReachDisplay) draws the same distinction and ships nearest-point as its default, with ray-crossing as an alternate display mode.

Measured against the **tick** position, not the interpolated render frame, for the same reason: vanilla validates on tick positions, and mixing an interpolated eye with a tick-position hitbox is worth up to a quarter of a block at sprint speed.

</details>

<details>
<summary><b>Swings, aim and target attribution</b></summary>

- Every left-click attack is caught at `Minecraft.startAttack()` — **including the ones that hit nothing**, which `MultiPlayerGameMode.attack` never sees. That's what makes measuring a whiff possible at all.
- Vanilla's own guards (`missTime`, `hitResult`, `isHandsBusy`) are re-checked there, so clicks vanilla itself discards don't enter the data as phantom swings.
- **Aim** is recorded two ways: the angle between your crosshair and the hitbox centre, and where on the hitbox the ray passed (used for the scatter plot).
- A whiff is attributed to the player closest to your crosshair within **6 blocks and 30°**. Swinging at empty air with nobody nearby records nothing.

</details>

<details>
<summary><b>Combos</b></summary>

- A **combo** is a chain of **≥2 sprint hits** on the *same* player, with no self-damage in between and no gap longer than ~0.7s.
- Only hits landed while **sprinting** count (a true sprint-reset combo).
- The ~0.7s gap is roughly the sword's full attack-cooldown recharge (~0.625s) plus grace: if your sword is charged and you don't swing, the combo is over. It's a hardcoded constant so a stale config can't shift it.
- The mod reports the **average interval** and the **jitter** (standard deviation). Low jitter ≈ triggerbot; high jitter ≈ human.

</details>

<details>
<summary><b>Share links — encoding and format</b></summary>

The session is quantised to integers (reach → centiblocks, angles → centidegrees, offsets → milliblocks), timestamps delta-encoded, varint-packed with boolean flags folded into adjacent varints, deflated, and base64url'd into the URL **fragment** — which browsers never transmit.

The viewer ([`docs/index.html`](docs/index.html)) decodes it with the browser-native `DecompressionStream('deflate-raw')`; no JavaScript libraries are involved.

To fit Discord's 2000-character message limit, a binary search finds the largest number of plotted points that fits the budget. **Summary statistics are always computed over every event and encoded exactly** — only graph resolution degrades, and the viewer states "showing N of M" when it does.

The payload starts with a **version byte** and that's a permanent contract: links already shared have to keep working, so the viewer decodes every past version. v1 links still render today.

</details>

<details>
<summary><b>Integrity block</b></summary>

Each session embeds a **SHA-256 hash** and an **HMAC-SHA256 signature** over the exact canonical data, plus start/end timestamps. Editing the file breaks the hash, so casual tampering is detectable.

Verify independently by recomputing SHA-256 over the exact `canonicalData` string (UTF-8) in the `.json`.

As stated above: the signing key ships in the mod, so this is tamper-*evidence*, not unforgeable proof.

</details>

---

## Building from source

```bash
./gradlew build
```

Output: `build/libs/combat-tracker-<modversion>+<mcversion>.jar`. Requires **JDK 21**.

Everything downstream of a recorded session — statistics, charts, link encoding — is deliberately free of any Minecraft import, so the whole report pipeline can be exercised without launching the game:

```bash
./gradlew reportPreview
```

That renders synthetic sessions to `build/preview/` as HTML reports plus a link fragment, and prints how link length and plotted-point count behave as sessions grow.

---

## Credits

- Jump-reset detection is adapted from [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).
- Dropping ping compensation follows [sootysplash/jump-reset](https://github.com/sootysplash/jump-reset), which compares tick counts on the client clock and needs no latency correction at all.
- The reach metric follows [Wolren/ReachDisplay](https://github.com/Wolren/ReachDisplay).

## License

[MIT](LICENSE) © 2026 builtdoor
