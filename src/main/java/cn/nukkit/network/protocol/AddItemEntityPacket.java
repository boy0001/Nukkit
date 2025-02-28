package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class AddItemEntityPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.ADD_ITEM_ENTITY_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public Item item;
    public float x;
    public float y;
    public float z;
    public float speedX;
    public float speedY;
    public float speedZ;

    public AddItemEntityPacket() {
        super(null);
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        if (this.item == null) {
            this.setBuffer(new byte[35]);
        } else {
            this.setBuffer(new byte[40]);
        }
        this.reset();
        this.putVarLong(this.entityUniqueId);
        this.putVarLong(this.entityRuntimeId);
        this.putSlot(this.item);
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
    }
}
