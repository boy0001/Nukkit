package cn.nukkit.raknet.protocol.packet;

import cn.nukkit.raknet.protocol.DataPacket;
import cn.nukkit.raknet.protocol.Packet;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class DATA_PACKET_4 extends DataPacket {
    public static final byte ID = (byte) 0x84;

    public DATA_PACKET_4() {
        super(64);
    }

    @Override
    public int getBlockSize() {
        return 512;
    }

    @Override
    public byte getID() {
        return ID;
    }

    public static final class Factory implements Packet.PacketFactory {

        @Override
        public Packet create() {
            return new DATA_PACKET_4();
        }

    }

}
