package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.commands.CommandResult;
import com.shieldudaram.fightcaves.commands.FightCavesCommandContext;

import javax.annotation.Nonnull;

public final class FightCavesCommand extends CommandBase {

    private final FightCavesRuntime runtime;

    public FightCavesCommand(FightCavesRuntime runtime) {
        super("fightcaves", "Fight Caves progression commands.");
        this.runtime = runtime;
        this.addAliases("fight-caves", "caves");
        this.setAllowsExtraArguments(true);
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        FightCavesCommandContext commandContext = toCommandContext(ctx, runtime.getAdminPermission());
        CommandResult result = runtime.handleCommand(normalizeRawCommand(ctx.getInputString()), commandContext);

        String prefix = runtime.getPrefix() + " ";
        String msg = result.message();
        if (msg == null || msg.isBlank()) {
            msg = result.success() ? "OK" : "Command failed.";
        }

        if (result.success()) {
            ctx.sendMessage(Message.raw(prefix + msg));
            return;
        }
        ctx.sendMessage(Message.raw(prefix + "Error: " + msg));
    }

    private static FightCavesCommandContext toCommandContext(CommandContext ctx, String adminPermission) {
        if (ctx == null) {
            return FightCavesCommandContext.console();
        }

        boolean isPlayer = ctx.isPlayer();
        String senderId = "console";
        String senderName = "console";
        boolean isAdmin = !isPlayer;
        FightCavesCommandContext.PositionSnapshot position = null;

        if (isPlayer) {
            Player player = ctx.senderAs(Player.class);
            if (player != null) {
                if (player.getPlayerRef() != null && player.getPlayerRef().getUuid() != null) {
                    senderId = player.getPlayerRef().getUuid().toString();
                }
                senderName = player.getDisplayName();
                isAdmin = player.hasPermission(adminPermission);
                var transform = player.getPlayerRef() == null ? null : player.getPlayerRef().getTransform();
                String world = player.getWorld() == null ? null : player.getWorld().getName();
                if (transform != null && transform.getPosition() != null && world != null && !world.isBlank()) {
                    position = new FightCavesCommandContext.PositionSnapshot(
                            world,
                            transform.getPosition().getX(),
                            transform.getPosition().getY(),
                            transform.getPosition().getZ(),
                            0f,
                            0f,
                            0f
                    );
                }
            }
        }

        return new FightCavesCommandContext(senderId, senderName, isPlayer, isAdmin, position);
    }

    private static String normalizeRawCommand(String input) {
        if (input == null) {
            return "/fightcaves";
        }
        String normalized = input.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return "/fightcaves";
        }

        String[] parts = normalized.split("\\s+");
        parts[0] = "fightcaves";
        return "/" + String.join(" ", parts);
    }
}
