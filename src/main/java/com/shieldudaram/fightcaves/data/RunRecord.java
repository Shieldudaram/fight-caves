package com.shieldudaram.fightcaves.data;

import java.util.ArrayList;
import java.util.List;

public final class RunRecord {
    public String runId;
    public String playerId;
    public String playerName;
    public long startedAt;
    public long endedAt;
    public long durationMs;
    public boolean success;
    public int highestWave;
    public String endReason;
    public List<String> adminEvents = new ArrayList<>();
    public List<RewardClaim> rewards = new ArrayList<>();
}
