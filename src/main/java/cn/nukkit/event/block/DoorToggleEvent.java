package cn.nukkit.event.block;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Created by Snake1999 on 2016/1/22.
 * Package cn.nukkit.event.block in project nukkit.
 */
public class DoorToggleEvent extends BlockUpdateEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public HandlerList getHandlerList() {
        return getHandlers();
    }

    private Player player;

    public DoorToggleEvent(Block block, Player player) {
        super(block);
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
