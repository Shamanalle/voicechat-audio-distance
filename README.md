# Voice Physics

**English** · [Русский](README.ru.md)

An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that makes voices behave like sound: they fade with distance, get muffled behind walls, come round corners through doorways and echo in caves. Servers get sound zones, game rules and wall muffling even for players without the addon.

[![Modrinth](https://img.shields.io/badge/Modrinth-download-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/project/u8mD6TL9)
[![CurseForge](https://img.shields.io/badge/CurseForge-download-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/projects/1712556)
[![Release](https://img.shields.io/github/v/release/Shamanalle/voice-physics?logo=github&color=brightgreen)](https://github.com/Shamanalle/voice-physics/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20%E2%80%93%2026.3-blue.svg)](#versions-and-files)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

| Distance | Walls | Monitor | Server profile |
|---|---|---|---|
| ![Distance tab: curve graph and presets](docs/images/ui-distance.png) | ![Walls tab: strength and materials](docs/images/ui-walls.png) | ![Monitor: nearby players and radar](docs/images/ui-monitor.png) | ![Settings locked by the server](docs/images/ui-server-enforced.png) |

## Contents

- [Quick start](#quick-start)
- [For players](#for-players)
- [For servers](#for-servers)
- [What works where](#what-works-where)
- [Versions and files](#versions-and-files)
- [Compatibility](#compatibility)
- [Commands](#commands)
- [Settings files](#settings-files)
- [Building](#building)

## Quick start

**Which file:** Fabric, Quilt and NeoForge 26.x get the full addon. Paper, Purpur and Spigot servers get the plugin. Forge, and NeoForge before 26.x, get a lite version with the distance curve only. The exact file for your version is in [Versions and files](#versions-and-files).

**Player**
1. Install [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and, on Fabric, [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the addon's `.jar` into `.minecraft/mods/`.
3. In game press `V` → **Voice Physics…** (also in Mod Menu, or on your own key in *Controls*). Changes are heard at once; *Cancel* undoes them.

**Server**
1. Fabric or NeoForge 26.x: put the same `.jar` into `mods/`. Paper, Purpur or Spigot: put `voice-physics-bukkit-*.jar` into `plugins/`. Simple Voice Chat must be installed.
2. Start the server once. Walls for players without the addon are already on.
3. Set up the rest with `/vcd` in game, on the *Server* tab of the settings screen, or in the [settings file](#server-file). The file is re-read without a restart.

## For players

These need the addon on your client and work on any server with Simple Voice Chat.

### Distance
- Three fade curves: like Simple Voice Chat (straight), realistic, or steep. Every curve fades out at the edge of the range instead of cutting off.
- You set how far voices stay at full volume, how fast they fade, how loud they stay at the edge, and how fast whispers fade.
- A live graph shows the volume at every distance and the players you hear right now. *Listen* plays a voice walking away along the curve.
- Presets, which adapt to each server's voice range:

  | Preset | For | Full volume up to |
  |---|---|---|
  | Vanilla | Exactly like Simple Voice Chat | half the range |
  | Realistic | Everyday play | about 12 blocks |
  | Clear | Events: everyone stays understandable | about 24 blocks |
  | Stealth | Hide-and-seek, horror | about 7 blocks |

### Walls
- A voice behind a wall is quieter and duller; the thicker the wall, the more.
- Materials differ: wool and metal block more than stone, glass and leaves less. Open doors, slabs, fences and carpets let sound past. Every material's weight can be changed on the *Materials* tab.
- Players side by side in a narrow tunnel hear each other clearly.

### Round corners
- A voice from the next room comes through the doorway or window, from its direction and at the length of the way round.
- The sharper the turn, the duller the voice. The direction glides as you walk past a doorway.
- The HUD shows *round a corner* for such voices.

### Echo, water and rain
- The echo depends on the place: a stone room rings briefly, a cave or hall for 2–3 seconds, a wooden house briefly and softly. Forests, fields and wool rooms have none.
- Near cliffs and in canyons the voice comes back a moment later.
- The speaker's surroundings count too: a friend in a cave echoes even when you are outside. A voice right next to you stays clear.
- Under water voices are dull and quieter.
- Rain and thunder cover far voices under the open sky.
- The *Effects* tab has a switch for each and shows what kind of place you are in.

### HUD
- A small panel in a screen corner: who is talking, how far away, from which side, and whether they whisper, are behind a wall or round a corner.
- While you talk: how many players hear you and how many cannot (no voice chat, sound off). In a Simple Voice Chat group it counts the group (needs the addon on the server).
- Modes: off, while someone talks, always. Size, background, a compact one-line mode. A key cycles the modes.

### Monitor and radar
- Everyone within voice range, talking or not: distance, direction, how loud they reach you and how much the walls take.
- Who has no voice chat, has it disconnected, turned the sound off or is in a group.
- A radar view shows the same from above.
- Invisible players, spectators and players hidden by vanish plugins are not shown.

### Also
- **Profile codes:** copy all your sound settings as one line and send it to a friend, who pastes it.
- **Colorblind colors** for the HUD, monitor and radar, on the *Monitor* tab.
- **Seven languages:** English, Russian, Ukrainian, German, Spanish, Brazilian Portuguese, Chinese (Simplified).

## For servers

Available in the Fabric and NeoForge 26.x mods and as a plugin for Paper, Purpur, Spigot and Bukkit.

- **Walls for everyone.** Players without the addon also hear voices muffled through walls. Load is capped: above 24 voices at once (adjustable) the rest pass unfiltered, and on any error the original audio is sent, so voice chat never goes silent.
- **Sound zones.** Draw a box in game with `/vcd zone`, use a whole world, or a WorldGuard region on Paper. A zone can:
  - change the voice range: a stage ×2, a library ×0.4, or a range in blocks;
  - be *isolated*: no voice gets in or out;
  - set its own wall strength, a constant echo (a cathedral) or none;
  - show a message on entering.
- **Game rules.** Sneaking players carry less far. Dead players are silent until they respawn. Spectators are heard only by spectators. An item in hand, such as a goat horn, works as a megaphone. You choose which rules also apply inside Simple Voice Chat groups.
- **One sound for everyone.** Offer the server's sound profile with a button, or enforce it while players are on the server (fair PvP and events). Lock all of it or only some parts: curve, walls, materials, effects.
- **No seeing through walls.** Turn off the monitor, the radar and nearby players in the HUD.
- **Require the addon.** Players with Simple Voice Chat but without the addon can get a download link once, on every join, or be kicked. Players without voice chat are never affected.
- **Admin tools.** A *Server* tab in the settings screen for operators, `/vcd debug <player>` to see whom a player hears and why not, and messages in each player's own language (all texts can be edited).

## What works where

| | Client only | Server only | Both |
|---|:---:|:---:|:---:|
| Distance curve, presets | ✅ | — | ✅ + server profile |
| Walls | ✅ for you | ✅ for players without the addon | ✅ |
| Round corners, echo, water, rain | ✅ | — | ✅ |
| HUD, monitor, radar | ✅ | — | ✅ + voice chat state of every player |
| Zones: range, walls, isolation | — | ✅ | ✅ |
| Zones: echo, message on entering | — | — | ✅ |
| Game rules, addon requirement, `/vcd` | — | ✅ | ✅ |
| Server tab, locked settings, monitor off | — | — | ✅ |
| Exact whisper range on the graph | approximate | — | ✅ |

With the addon on both sides the client muffles walls itself and the server skips those players, so nothing is muffled twice.

## Versions and files

| Loader | Minecraft | File | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voice-physics-fabric-2.1.0+mc1.20.1.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.20.2 – 1.20.4 | `voice-physics-fabric-2.1.0+mc1.20.2-1.20.4.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.20.5 – 1.20.6 | `voice-physics-fabric-2.1.0+mc1.20.5-1.20.6.jar` | 21+ | 2.5.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voice-physics-fabric-2.1.0+mc1.21.x.jar` | 21+ | 2.5.0+ |
| **Fabric** | 26.1 – 26.3 | `voice-physics-fabric-2.1.0+mc26.x.jar` | 25+ | 2.6.0+ |
| **NeoForge** | 26.1 – 26.3 | `voice-physics-neoforge-2.1.0+mc26.x.jar` | 25+ | 2.6.0+ |
| **Paper / Purpur / Spigot / Bukkit** | 1.20.1 – 26.3 | `voice-physics-bukkit-2.1.0.jar` | 17+ | Bukkit version |
| Forge (lite) | 1.20.1 | `voice-physics-forge-2.1.0+mc1.20.1.jar` | 17+ | 2.4.0+ |
| NeoForge / Forge (lite) | 1.20.2 – 1.20.4 | `voice-physics-{neoforge,forge}-2.1.0+mc1.20.2-1.20.4.jar` | 17+ | 2.4.0+ |
| NeoForge / Forge (lite) | 1.20.5 – 1.20.6 | `voice-physics-{neoforge,forge}-2.1.0+mc1.20.5-1.20.6.jar` | 21+ | 2.5.0+ |
| NeoForge / Forge (lite) | 1.21 – 1.21.11 | `voice-physics-{neoforge,forge}-2.1.0+mc1.21.x.jar` | 21+ | 2.5.0+ |
| Forge (lite) | 26.1 – 26.3 | `voice-physics-forge-2.1.0+mc26.x.jar` | 25+ | 2.6.0+ |

- **Fabric** and **NeoForge 26.x** are the full addon, for the client and the server. Fabric needs [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is optional.
- **The plugin** is the server side only. Players can join with the addon, without it, or without mods at all.
- **Lite** versions have the distance curve only, set in `config/vc-audio-distance.properties`: no settings screen, walls, effects, HUD or server side.

## Compatibility

- **Sound Physics Remastered:** when it is installed, the addon leaves walls, echo and water to it, so nothing is applied twice. Rain still works.
- **Simple Voice Chat groups:** a group hears its members anywhere, so walls and range do not apply inside it. Game rules apply only where the server turns them on.
- **Vanish plugins** (Paper): hidden players stay hidden in the monitor too.

## Commands

`/vcd` is for operators (level 2+) and the console. On Paper the permission is `vcd.admin`. Changes are saved to the settings file and reach players at once.

| Command | What it does |
|---|---|
| `/vcd` or `/vcd status` | Version, voice range, walls, players with the addon, profile, zones |
| `/vcd reload` | Re-read the settings file |
| `/vcd profile off\|suggest\|enforce` | How the server profile is offered |
| `/vcd preset vanilla\|realistic\|clear\|stealth\|custom` | The server's sound |
| `/vcd preset export` / `import <code>` | The server's profile as a profile code |
| `/vcd walls 0-100\|off` | Wall strength for everyone, in % |
| `/vcd serverwalls on\|off` | Walls for players without the addon |
| `/vcd lock all\|none\|curve,walls,materials,effects` | What players cannot change while the profile is enforced |
| `/vcd monitor on\|off` | Monitor, radar and nearby players in the HUD |
| `/vcd zones` | List zones |
| `/vcd zone pos1` / `pos2`, `/vcd zone create <name> [radius]` | Make a box zone from two corners, or around you |
| `/vcd zone set <name> <setting> <value\|default>` | `mode`, `preset`, `voice_range`, `whisper_range`, `range_multiplier`, `walls`, `echo`, `isolated`, `message`, `priority` |
| `/vcd zone delete <name>`, `/vcd zone info` | Remove a zone; which zone you are in |
| `/vcd rule sneak 0.1-1\|dead on\|off\|spectators on\|off\|megaphone <item>\|megaphone_range 1-10` | Game rules |
| `/vcd group dead\|spectators\|zones\|open_range on\|off` | Rules inside Simple Voice Chat groups |
| `/vcd require off\|suggest\|warn\|kick [version]` | Addon requirement |
| `/vcd debug <player>` | Whom a player hears, who hears them, and why not |

## Settings files

Everything here can also be set in game. Every key in the files has a comment in English and Russian.

### Client file

`config/vc-audio-distance.properties`

<details>
<summary>All client settings</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `distance_model` | `linear` / `realistic_inverse` / `exponential` | `linear` | Shape of the curve |
| `attenuation_factor` | 0 – 1 | 1.0 | How fast voices fade |
| `openal_reference_ratio` | 0.05 – 1 | 0.5 | Share of the range heard at full volume |
| `min_volume_fraction` | 0 – 0.5 | 0.0 | Volume at the edge of the range |
| `whisper_multiplier` | 0.5 – 2 | 1.0 | How fast whispers fade, relative to voices |
| `occlusion_enabled` | true / false | true | Walls muffle voices |
| `occlusion_strength` | 0 – 1 | 0.6 | How strongly |
| `material.<id>` | 0 – 3 | see the *Materials* tab | How much one block muffles; stone = 1 |
| `reverb_enabled` | true / false | true | Echo |
| `reverb_strength` | 0 – 1 | 0.6 | Echo strength |
| `underwater_enabled` | true / false | true | Dull voices under water |
| `weather_enabled` | true / false | true | Rain and thunder cover far voices |
| `diffraction_enabled` | true / false | true | Voices come round corners |
| `hud_mode` | `off` / `talking` / `always` | `talking` | When the HUD is shown |
| `hud_corner` | `top_left` / `top_right` / `bottom_left` / `bottom_right` | `top_right` | HUD corner |
| `hud_scale` | 0.5 – 1.5 | 1.0 | HUD size |
| `hud_background` | 0 – 1 | 0.55 | HUD background opacity |
| `hud_compact` | true / false | false | One HUD line for everyone talking |
| `colorblind` | true / false | false | Colors for red-green color blindness |

</details>

### Server file

`config/vc-audio-distance-server.properties` on Fabric and NeoForge, `plugins/VoicechatAudioDistance/vc-audio-distance-server.properties` on Paper. Changes apply within 2 seconds. The voice and whisper range itself is set in Simple Voice Chat (`max_voice_distance`, `whisper_distance`).

<details>
<summary>Walls</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `walls_strength` | 0 – 1 | 0.6 | How strongly walls muffle, for everyone; 0 turns walls off |
| `material.<id>` | 0 – 3 | stone 1.0, metal 1.3, earth 0.9, wood 0.7, wool 1.4, soft 1.2, glass 0.4, ice 0.7, door 0.6, leaves 0.15, thin 0.2, liquid 0.35, other 1.0 | How much one block muffles |
| `server_walls` | true / false | true | The server muffles walls for players without the addon |
| `server_walls_max_streams` | 0 – 512 | 24 | Most voices muffled at once; the rest pass unfiltered |

</details>

<details>
<summary>Server profile (players with the addon)</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | Players keep their settings / get a button to apply the profile / use it while on the server |
| `profile_locked` | `all`, `none`, or any of `curve,walls,materials,effects` | `all` | With `enforce`: what players cannot change |
| `profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` / `custom` | `custom` | The server's sound; `custom` uses the `profile.*` keys |
| `profile.distance_model`, `.attenuation_factor`, `.openal_reference_ratio`, `.min_volume_fraction`, `.whisper_multiplier` | as in the client file | client defaults | The custom curve |
| `profile.reverb_enabled`, `.reverb_strength`, `.underwater_enabled`, `.weather_enabled`, `.diffraction_enabled` | as in the client file | client defaults | Effects; always part of the profile, whatever the preset |
| `allow_monitor` | true / false | true | `false`: no monitor, radar or nearby players in the HUD |

</details>

<details>
<summary>Zones</summary>

| Key | Values | What it does |
|---|---|---|
| `zone.<kind>.<name>.voice_range`, `.whisper_range` | 1 – 1000 blocks | Voice and whisper range here |
| `zone.<kind>.<name>.range_multiplier` | 0.05 – 10 | Range times this: 2 = stage, 0.4 = library |
| `zone.<kind>.<name>.isolated` | true / false | No voice gets in or out |
| `zone.<kind>.<name>.walls_strength` | 0 – 1 | Wall strength here |
| `zone.<kind>.<name>.echo` | `auto` / `off` / 0.1 – 1 | Measured as usual / none / this much everywhere here |
| `zone.<kind>.<name>.profile_mode`, `.profile_preset` | as above | The profile here |
| `zone.<kind>.<name>.enter_message` | text | Shown on entering |
| `zone.<kind>.<name>.priority` | whole number | Where zones overlap the highest wins (default 0) |
| `zone.box.<name>.world`, `.from`, `.to` | world, `x,y,z`, `x,y,z` | The box; `/vcd zone create` writes it |

`<kind>` is `world`, `box` or `region` (WorldGuard, Paper). A world is its folder name on Paper (`world_nether`) and its dimension on Fabric and NeoForge (`the_nether`). On equal priority a region wins, then the smaller box. Anything a zone does not set comes from the rest of the file.

```properties
# A stage heard twice as far, and a soundproof booth
zone.box.stage.world=world
zone.box.stage.from=0,60,0
zone.box.stage.to=30,80,20
zone.box.stage.range_multiplier=2
zone.box.stage.enter_message=On stage: everyone hears you
zone.box.booth.world=world
zone.box.booth.from=40,60,0
zone.box.booth.to=44,64,4
zone.box.booth.isolated=true
```

</details>

<details>
<summary>Game rules and groups</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `sneak_range_multiplier` | 0.1 – 1 | 1 | Voice range while sneaking, times this |
| `dead_players_silent` | true / false | false | Dead players are silent until they respawn |
| `spectators_hear_only_spectators` | true / false | false | Spectators are heard only by spectators |
| `megaphone_item` | item id or empty | empty | Item that works as a megaphone, e.g. `minecraft:goat_horn` |
| `megaphone_multiplier` | 1 – 10 | 2.5 | Voice range with the megaphone, times this |
| `group_dead_silent` | true / false | false | Dead players are silent in their group too |
| `group_spectators_apart` | true / false | false | Spectators in a group are heard only by its spectators |
| `group_isolated_zones` | true / false | false | Isolated zones cut group voices too |
| `open_group_range` | true / false | true | In open groups, zone range, sneaking and the megaphone apply to the voice nearby players hear |

</details>

<details>
<summary>Addon requirement and messages</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `require_addon` | `off` / `suggest` / `warn` / `kick` | `off` | Players without the addon: nothing / one message per server start / a message on every join / disconnect |
| `min_addon_version` | version or empty | empty | Oldest addon version that counts |
| `addon_download_url` | link | GitHub releases | Where the message sends players |
| `messages_language` | `auto` / `en_us` / `ru_ru` / `uk_ua` / `de_de` / `es_es` / `pt_br` / `zh_cn` | `auto` | Language of `/vcd` replies and player messages; `auto` = each player's game language |

The texts are in `vc-audio-distance-lang/<language>.json` next to the settings file. Edit any line, or add a file such as `fr_fr.json` for a new language; missing lines fall back to the built-in ones.

</details>

## Building

JDK 25 is required.

```bash
git clone https://github.com/Shamanalle/voice-physics.git
cd voice-physics
./gradlew build   # every jar ends up in build/libs/
```

How the project is laid out and how to contribute: [CONTRIBUTING.md](CONTRIBUTING.md). Changes by version: [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © Shamanalle
