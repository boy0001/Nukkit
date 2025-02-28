package cn.nukkit.event.level;

import cn.nukkit.event.HandlerList;
import cn.nukkit.level.format.FullChunk;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ChunkPopulateEvent extends ChunkEvent {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public HandlerList getHandlerList() {
        return getHandlers();
    }

    public ChunkPopulateEvent(FullChunk chunk) {
        super(chunk);
    }

}