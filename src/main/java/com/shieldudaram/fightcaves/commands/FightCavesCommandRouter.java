package com.shieldudaram.fightcaves.commands;

import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.data.RewardClaim;
import com.shieldudaram.fightcaves.session.FightCavesSessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FightCavesCommandRouter {
    private final FightCavesRuntime runtime;

    public FightCavesCommandRouter(FightCavesRuntime runtime) {
        this.runtime = runtime;
    }

    public CommandResult execute(String rawCommand, FightCavesCommandContext ctx) {
        try {
            String[] parts = split(rawCommand);
            if (parts.length == 0) {
                return help();
            }

            if (!isNamespace(parts[0])) {
                return CommandResult.error("Unsupported namespace. Use /fightcaves");
            }

            if (parts.length == 1) {
                return CommandResult.ok(runtime.formatStatus(ctx.senderId()));
            }

            String sub = parts[1].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "start" -> handleStart(ctx);
                case "leave" -> handleLeave(ctx);
                case "status" -> CommandResult.ok(runtime.formatStatus(ctx.senderId()));
                case "queue" -> CommandResult.ok(runtime.formatQueue());
                case "claim" -> handleClaim(ctx);
                case "stance" -> handleStance(ctx, parts);
                case "admin" -> handleAdmin(ctx, parts);
                default -> help();
            };
        } catch (Throwable t) {
            return CommandResult.error(t.getMessage() == null ? "Unexpected command error." : t.getMessage());
        }
    }

    private CommandResult handleStart(FightCavesCommandContext ctx) {
        if (!ctx.player()) {
            return CommandResult.error("Players only.");
        }

        FightCavesSessionService.StartResult start = runtime.startRun(ctx.senderId(), ctx.senderName(), "command");
        if (start.status() == FightCavesSessionService.StartStatus.QUEUED) {
            return CommandResult.ok(start.message() + " Position: " + start.queuePosition());
        }
        return start.status() == FightCavesSessionService.StartStatus.STARTED
                ? CommandResult.ok(start.message())
                : CommandResult.error(start.message());
    }

    private CommandResult handleLeave(FightCavesCommandContext ctx) {
        if (!ctx.player()) {
            return CommandResult.error("Players only.");
        }

        FightCavesSessionService.LeaveResult leave = runtime.leaveRun(ctx.senderId(), "leave_command");
        return switch (leave.status()) {
            case LEFT_ACTIVE, LEFT_QUEUE -> CommandResult.ok(leave.message());
            case NOT_FOUND -> CommandResult.error(leave.message());
        };
    }

    private CommandResult handleClaim(FightCavesCommandContext ctx) {
        if (!ctx.player()) {
            return CommandResult.error("Players only.");
        }
        List<RewardClaim> claims = runtime.claimRewards(ctx.senderId());
        if (claims.isEmpty()) {
            return CommandResult.ok("No pending rewards.");
        }

        List<String> parts = new ArrayList<>();
        for (RewardClaim claim : claims) {
            if (claim == null || claim.rewardId == null || claim.rewardId.isBlank()) {
                continue;
            }
            parts.add(claim.rewardId + " x" + claim.amount);
        }
        if (parts.isEmpty()) {
            return CommandResult.ok("No pending rewards.");
        }
        return CommandResult.ok("Claimed rewards: " + String.join(", ", parts));
    }

    private CommandResult handleStance(FightCavesCommandContext ctx, String[] parts) {
        if (!ctx.player()) {
            return CommandResult.error("Players only.");
        }
        if (parts.length < 3) {
            return CommandResult.ok("Current stance: " + runtime.getStance(ctx.senderId()) + " (usage: /fightcaves stance <melee|ranged|magic>)");
        }
        boolean updated = runtime.setStance(ctx.senderId(), parts[2]);
        if (!updated) {
            return CommandResult.error("Unknown stance. Use melee, ranged, or magic.");
        }
        return CommandResult.ok("Stance set to " + runtime.getStance(ctx.senderId()) + ".");
    }

    private CommandResult handleAdmin(FightCavesCommandContext ctx, String[] parts) {
        if (!ctx.admin()) {
            return CommandResult.error("Missing permission.");
        }
        if (parts.length < 3) {
            return CommandResult.error("Usage: /fightcaves admin <stop|skipwave|complete|grant|resetstats|reload|arena>");
        }

        String action = parts[2].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "stop" -> toCommand(runtime.adminStop());
            case "skipwave" -> toCommand(runtime.adminSkipWave());
            case "complete" -> {
                if (parts.length < 4) {
                    yield CommandResult.error("Usage: /fightcaves admin complete <player>");
                }
                String target = runtime.resolvePlayerId(parts[3]);
                if (target == null) {
                    yield CommandResult.error("Unknown player: " + parts[3]);
                }
                yield toCommand(runtime.adminComplete(target));
            }
            case "grant" -> {
                if (parts.length < 6) {
                    yield CommandResult.error("Usage: /fightcaves admin grant <player> <rewardId> <amount>");
                }
                String target = runtime.resolvePlayerId(parts[3]);
                if (target == null) {
                    yield CommandResult.error("Unknown player: " + parts[3]);
                }
                int amount;
                try {
                    amount = Integer.parseInt(parts[5]);
                } catch (NumberFormatException ignored) {
                    yield CommandResult.error("Amount must be a number.");
                }
                yield toCommand(runtime.adminGrant(target, parts[4], amount));
            }
            case "resetstats" -> {
                if (parts.length < 4) {
                    yield CommandResult.error("Usage: /fightcaves admin resetstats <player>");
                }
                String target = runtime.resolvePlayerId(parts[3]);
                if (target == null) {
                    target = parts[3];
                }
                yield toCommand(runtime.adminResetStats(target));
            }
            case "reload" -> {
                runtime.reload();
                yield CommandResult.ok("Reloaded fight caves config and content.");
            }
            case "arena" -> handleAdminArena(ctx, parts);
            default -> CommandResult.error("Usage: /fightcaves admin <stop|skipwave|complete|grant|resetstats|reload|arena>");
        };
    }

    private CommandResult handleAdminArena(FightCavesCommandContext ctx, String[] parts) {
        if (parts.length < 4) {
            return CommandResult.error("Usage: /fightcaves admin arena <mark1|mark2|spawn|save|show>");
        }

        String action = parts[3].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "mark1" -> {
                if (!ctx.player()) {
                    yield CommandResult.error("Players only.");
                }
                yield CommandResult.ok(runtime.adminArenaMark(ctx.senderId(), 1, ctx.position()));
            }
            case "mark2" -> {
                if (!ctx.player()) {
                    yield CommandResult.error("Players only.");
                }
                yield CommandResult.ok(runtime.adminArenaMark(ctx.senderId(), 2, ctx.position()));
            }
            case "spawn" -> handleAdminArenaSpawn(ctx, parts);
            case "save" -> CommandResult.ok(runtime.adminArenaSave(ctx.senderId()));
            case "show" -> CommandResult.ok(runtime.adminArenaShow());
            default -> CommandResult.error("Usage: /fightcaves admin arena <mark1|mark2|spawn|save|show>");
        };
    }

    private CommandResult handleAdminArenaSpawn(FightCavesCommandContext ctx, String[] parts) {
        if (parts.length < 5) {
            return CommandResult.error("Usage: /fightcaves admin arena spawn <add|remove|list> [id]");
        }

        String action = parts[4].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> {
                if (!ctx.player()) {
                    yield CommandResult.error("Players only.");
                }
                if (parts.length < 6) {
                    yield CommandResult.error("Usage: /fightcaves admin arena spawn add <id>");
                }
                yield CommandResult.ok(runtime.adminArenaSpawnAdd(parts[5], ctx.position()));
            }
            case "remove" -> {
                if (parts.length < 6) {
                    yield CommandResult.error("Usage: /fightcaves admin arena spawn remove <id>");
                }
                yield CommandResult.ok(runtime.adminArenaSpawnRemove(parts[5]));
            }
            case "list" -> CommandResult.ok(runtime.adminArenaSpawnList());
            default -> CommandResult.error("Usage: /fightcaves admin arena spawn <add|remove|list> [id]");
        };
    }

    private static CommandResult toCommand(FightCavesSessionService.AdminResult result) {
        return result.success() ? CommandResult.ok(result.message()) : CommandResult.error(result.message());
    }

    private static boolean isNamespace(String root) {
        if (root == null) {
            return false;
        }
        String normalized = root.toLowerCase(Locale.ROOT);
        return "fightcaves".equals(normalized)
                || "fight-caves".equals(normalized)
                || "caves".equals(normalized);
    }

    private static String[] split(String input) {
        if (input == null) {
            return new String[0];
        }
        String normalized = input.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    private static CommandResult help() {
        return CommandResult.ok(
                "Usage: /fightcaves <start|leave|status|queue|claim|stance|admin>"
        );
    }
}
