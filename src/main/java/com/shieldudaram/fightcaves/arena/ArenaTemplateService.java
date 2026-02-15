package com.shieldudaram.fightcaves.arena;

import com.shieldudaram.fightcaves.config.FightCavesConfig;

public class ArenaTemplateService {

    public Result prepareForRun(String runId, FightCavesConfig config) {
        // Placeholder implementation for now. Real block/region cloning belongs in the Hytale adapter.
        return Result.ok("prepared");
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
