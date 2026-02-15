# Fight Caves Mod

Production-oriented Fight Caves implementation for Hytale using a data-driven 63-wave encounter.

## Key Features

- Solo-run session orchestration with queue support (single active run)
- Full 63-wave progression with kill-gated wave advancement
- NPC stand-in wave spawning with tracked cleanup and death/liveness checks
- Stance system for melee/ranged/magic mitigation
- Arena profile overrides (hybrid config + admin markers/spawn points)
- Command + NPC entry path
- Optional MultipleHUD wave panel with chat fallback and safe runtime disable
- Persistent player stats, run history, and pending reward claims
- Admin operations (`stop`, `complete`, `grant`, `resetstats`, `reload`)

## Commands

- `/fightcaves start`
- `/fightcaves leave`
- `/fightcaves status`
- `/fightcaves queue`
- `/fightcaves claim`
- `/fightcaves stance <melee|ranged|magic>`
- `/fightcaves admin stop`
- `/fightcaves admin skipwave`
- `/fightcaves admin complete <player>`
- `/fightcaves admin grant <player> <rewardId> <amount>`
- `/fightcaves admin resetstats <player>`
- `/fightcaves admin reload`
- `/fightcaves admin arena mark1`
- `/fightcaves admin arena mark2`
- `/fightcaves admin arena spawn add <id>`
- `/fightcaves admin arena spawn remove <id>`
- `/fightcaves admin arena spawn list`
- `/fightcaves admin arena save`
- `/fightcaves admin arena show`

## Arena Setup (Admin)

1. Stand at the first arena corner and run `/fightcaves admin arena mark1`.
2. Stand at the opposite corner and run `/fightcaves admin arena mark2`.
3. Add at least one wave spawn point:
   - `/fightcaves admin arena spawn add north`
   - `/fightcaves admin arena spawn add south`
4. Save bounds using `/fightcaves admin arena save`.
5. Verify with:
   - `/fightcaves admin arena show`
   - `/fightcaves admin arena spawn list`

Arena overrides persist to `fight_caves_arena_profile.json` in the plugin data directory.

## Build

From this folder:

- `./gradlew test`
- `./gradlew jar hytaleJar verifyHytaleJarMainClass verifyHytaleJarUiAssets installToHytaleMods verifyInstalledModJar`

Artifact outputs:

- `build/libs/fight-caves-<version>-core.jar` (core-only, not for Mods folder)
- `build/libs/fight-caves-<version>-hytale.jar` (plugin runtime jar for Mods folder)

The install task copies only the `-hytale.jar` artifact to your local `Hytale/UserData/Mods` directory and removes legacy non-hytale jars for this module.

## Runtime Verification

From this folder:

- `jar tf "$HOME/Library/Application Support/Hytale/UserData/Mods/fight-caves-<version>-hytale.jar" | rg 'FightCavesPlugin.class|manifest.json'`
- `rg -n 'FightCaves|Failed to load plugin|ClassNotFoundException' "$HOME/Library/Application Support/Hytale/UserData/Saves/TestWorld/logs"/*.log | tail -n 40`
- `jar tf "$HOME/Library/Application Support/Hytale/UserData/Mods/fight-caves-<version>-hytale.jar" | rg 'FightCavesEncounterService|FightCavesPlayerSyncSystem|Common/UI/Custom/Hud/FightCaves/Wave.ui'`
- `rg -n '\[FightCaves-UI\]|\[FightCaves-HUD\]|MultipleHUD bridge ready|HUD rendering disabled' "$HOME/Library/Application Support/Hytale/UserData/Saves/TestWorld/logs"/*.log | tail -n 80`

## HUD Safety Behavior

- If UI asset validation fails (`IncludesAssetPack=false` or missing wave HUD asset), Fight Caves still runs and falls back to chat updates.
- If MultipleHUD is missing/incompatible, Fight Caves still runs and falls back to chat updates.
- If MultipleHUD fails during runtime, HUD rendering is disabled for the session and gameplay continues.

From the workspace root (cross-module sanity check):

Prerequisite: ensure `./scripts/` is checked out from `https://github.com/Shieldudaram/scripts`.

- `./scripts/verify-hytale-mod-install.sh`
