package com.shieldudaram.fightcaves.commands;

public record CommandResult(boolean success, String message) {
    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, message);
    }
}
