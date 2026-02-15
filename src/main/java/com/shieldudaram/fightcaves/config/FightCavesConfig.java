package com.shieldudaram.fightcaves.config;

import com.shieldudaram.fightcaves.arena.SpawnPoint;

import java.util.ArrayList;
import java.util.List;

public final class FightCavesConfig {

    public int version = 1;
    public Entry entry = new Entry();
    public Arena arena = new Arena();
    public Session session = new Session();
    public Combat combat = new Combat();
    public Rewards rewards = new Rewards();
    public Persistence persistence = new Persistence();
    public Npc npc = new Npc();
    public Messages messages = new Messages();
    public Ui ui = new Ui();
    public Ops ops = new Ops();

    public static final class Entry {
        public boolean commandEnabled = true;
        public boolean npcEnabled = true;
    }

    public static final class Arena {
        public String world = "world";
        public int[] boundsMin = new int[]{0, 64, 0};
        public int[] boundsMax = new int[]{32, 96, 32};
        public int[] playerSpawn = new int[]{1024, 64, 1024};
        public List<SpawnPoint> spawnPoints = new ArrayList<>(List.of(
                new SpawnPoint("default_1", new int[]{1024, 64, 1024})
        ));
        public boolean returnPlayerOnExit = true;

        // Legacy fallback keys (kept for backward compatibility).
        public String templateWorld = "world";
        public int[] templateMin = new int[]{0, 64, 0};
        public int[] templateMax = new int[]{32, 96, 32};
        public int[] activeOrigin = new int[]{1024, 64, 1024};
        public int[] spectatorSpawn = new int[]{1032, 68, 1032};
    }

    public static final class Session {
        public int maxConcurrentRuns = 1;
        public String disconnectBehavior = "fail_run";
        public boolean queueEnabled = true;
        public int queueLimit = 64;
        public int startGraceSeconds = 3;
    }

    public static final class Combat {
        public List<String> attackTypes = new ArrayList<>(List.of("melee", "ranged", "magic"));
        public int preWavePrepSeconds = 6;
        public float correctStanceDamageMultiplier = 0.55f;
        public float wrongStanceDamageMultiplier = 1.00f;
        public int targetRunMinutesMin = 35;
        public int targetRunMinutesMax = 50;
        public int firstClearRatePercentMin = 20;
        public int firstClearRatePercentMax = 30;
        public String timingStrictness = "moderately_strict";
        public String waveAdvanceMode = "kills_only";
    }

    public static final class Rewards {
        public boolean partialFailureEnabled = true;
        public String farmControl = "none";
        public String completionTokenId = "fight_caves_token";
    }

    public static final class Persistence {
        public String playerStatsFile = "fight_caves_player_stats.json";
        public String runHistoryFile = "fight_caves_run_history.jsonl";
        public String claimsFile = "fight_caves_claims.json";
        public String activeRunMarkerFile = "fight_caves_active_run.json";
    }

    public static final class Npc {
        public boolean enabled = true;
        public String npcTypeId = "realmruler:fight_caves_guide";
        public String fallbackInteractionItemId = "realmruler:fight_caves_totem";
    }

    public static final class Messages {
        public String prefix = "[FightCaves]";
        public String locale = "en_us";
    }

    public static final class Ui {
        public boolean waveHudEnabled = true;
        public String waveHudMode = "multiplehud_optional";
        public boolean chatFallbackOnWaveChange = true;
    }

    public static final class Ops {
        public String adminPermission = "fightcaves.admin";
    }
}
