package com.shieldudaram.fightcaves.session;

public interface FightCavesUiAdapter {

    void onRunStarted(String runId, String playerId, String playerName, int totalWaves);

    void onWaveChanged(String runId,
                       String playerId,
                       int waveNumber,
                       int totalWaves,
                       String phase,
                       int enemiesRemaining,
                       boolean bossWave);

    void onRunEnded(String runId,
                    String playerId,
                    String playerName,
                    boolean success,
                    String reason,
                    int highestWave,
                    int totalWaves);

    static FightCavesUiAdapter noop() {
        return new FightCavesUiAdapter() {
            @Override
            public void onRunStarted(String runId, String playerId, String playerName, int totalWaves) {
            }

            @Override
            public void onWaveChanged(String runId,
                                      String playerId,
                                      int waveNumber,
                                      int totalWaves,
                                      String phase,
                                      int enemiesRemaining,
                                      boolean bossWave) {
            }

            @Override
            public void onRunEnded(String runId,
                                   String playerId,
                                   String playerName,
                                   boolean success,
                                   String reason,
                                   int highestWave,
                                   int totalWaves) {
            }
        };
    }
}
