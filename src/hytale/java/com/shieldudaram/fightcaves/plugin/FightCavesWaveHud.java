package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class FightCavesWaveHud extends CustomUIHud {

    private boolean visible;
    private String waveText = "";

    public FightCavesWaveHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public void show(String waveText) {
        this.visible = true;
        this.waveText = waveText == null ? "" : waveText;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        if (!visible) {
            return;
        }
        ui.append("Hud/FightCaves/Wave.ui");
        ui.set("#WaveLabel.Text", waveText);
        ui.set("#WaveLabel.TextSpans", Message.raw(waveText));
    }
}
