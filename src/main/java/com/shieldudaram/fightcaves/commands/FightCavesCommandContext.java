package com.shieldudaram.fightcaves.commands;

public record FightCavesCommandContext(String senderId,
                                       String senderName,
                                       boolean player,
                                       boolean admin,
                                       PositionSnapshot position) {

    public record PositionSnapshot(String world,
                                   double x,
                                   double y,
                                   double z,
                                   float pitch,
                                   float yaw,
                                   float roll) {
        public int blockX() {
            return (int) Math.floor(x);
        }

        public int blockY() {
            return (int) Math.floor(y);
        }

        public int blockZ() {
            return (int) Math.floor(z);
        }
    }

    public static FightCavesCommandContext console() {
        return new FightCavesCommandContext("console", "console", false, true, null);
    }
}
