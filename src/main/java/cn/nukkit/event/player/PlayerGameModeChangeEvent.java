package cn.nukkit.event.player;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

public class PlayerGameModeChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public HandlerList getHandlerList() {
        return getHandlers();
    }

    protected final int gamemode;

    protected AdventureSettings newAdventureSettings;

    public PlayerGameModeChangeEvent(Player player, int newGameMode, AdventureSettings newAdventureSettings) {
        this.player = player;
        this.gamemode = newGameMode;
        this.newAdventureSettings = newAdventureSettings;
    }

    public int getNewGamemode() {
        return gamemode;
    }

    public AdventureSettings getNewAdventureSettings() {
        return newAdventureSettings;
    }

    public void setNewAdventureSettings(AdventureSettings newAdventureSettings) {
        this.newAdventureSettings = newAdventureSettings;
    }
}
