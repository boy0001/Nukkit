package cn.nukkit.network.protocol;

import cn.nukkit.utils.Binary;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class BatchPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.BATCH_PACKET;

    public byte[] payload;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        byte[] data = new byte[4]; //int should be the biggest
        int offset;
        int length;
        for (offset = 0; ; offset++, System.out.println("offset:" + offset)) {
            int b = this.getByte();
            //Zlib header
            if (b == 0x78) {
                this.setOffset(this.getOffset() - 1); //reset
                break;
            }

            if (offset >= 4) {
                return;
            }

            data[offset] = (byte) b;
        }


        if (offset == 1) {
            length = data[0] & 0xff;
        } else if (offset == 2) {
            length = Binary.readShort(data);
        } else if (offset == 4) {
            length = Binary.readInt(data);
        } else {
            return;
        }
        this.payload = this.get(length);
    }

    @Override
    public void encode() {
        this.reset();
        this.putInt(this.payload.length);
        this.put(this.payload);
    }
}
