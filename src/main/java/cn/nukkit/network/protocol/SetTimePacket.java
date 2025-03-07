package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class SetTimePacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.SET_TIME_PACKET;

    public SetTimePacket() {
        super(6);
    }

    public int time;
    public boolean started = true;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.time);
        this.putBoolean(this.started);
    }

}
