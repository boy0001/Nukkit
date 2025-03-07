package cn.nukkit.level.format.mcregion;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.ByteArrayTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntArrayTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class Chunk extends BaseFullChunk {

    protected final CompoundTag nbt;

    public Chunk(LevelProvider level) {
        this(level, null);
    }

    public Chunk(Class<? extends LevelProvider> providerClass) {
        this((LevelProvider) null, null);
        this.providerClass = providerClass;
    }

    public Chunk(Class<? extends LevelProvider> providerClass, CompoundTag nbt) {
        this((LevelProvider) null, nbt);
        this.providerClass = providerClass;
    }

    public Chunk(LevelProvider level, CompoundTag nbt) {
        this.provider = level;
        if (level != null) {
            this.providerClass = level.getClass();
        }

        if (nbt == null) {
            this.nbt = new CompoundTag("Level");
            return;
        }

        this.nbt = nbt;

        if (!(this.nbt.contains("Entities") && (this.nbt.get("Entities") instanceof ListTag))) {
            this.nbt.putList(new ListTag<CompoundTag>("Entities"));
        }

        if (!(this.nbt.contains("TileEntities") && (this.nbt.get("TileEntities") instanceof ListTag))) {
            this.nbt.putList(new ListTag<CompoundTag>("TileEntities"));
        }

        if (!(this.nbt.contains("TileTicks") && (this.nbt.get("TileTicks") instanceof ListTag))) {
            this.nbt.putList(new ListTag<CompoundTag>("TileTicks"));
        }

        if (!(this.nbt.contains("BiomeColors") && (this.nbt.get("BiomeColors") instanceof IntArrayTag))) {
            this.nbt.putIntArray("BiomeColors", new int[256]);
        }

        if (!(this.nbt.contains("HeightMap") && (this.nbt.get("HeightMap") instanceof IntArrayTag))) {
            this.nbt.putIntArray("HeightMap", new int[256]);
        }

        if (!(this.nbt.contains("Blocks"))) {
            this.nbt.putByteArray("Blocks", new byte[32768]);
        }

        if (!(this.nbt.contains("Data"))) {
            this.nbt.putByteArray("Data", new byte[16384]);
            this.nbt.putByteArray("SkyLight", new byte[16384]);
            this.nbt.putByteArray("BlockLight", new byte[16384]);
        }

        Map<Integer, Integer> extraData = new ConcurrentHashMap<>(8, 0.9f, 1);

        if (!this.nbt.contains("ExtraData") || !(this.nbt.get("ExtraData") instanceof ByteArrayTag)) {
            this.nbt.putByteArray("ExtraData", Binary.writeInt(0));
        } else {
            BinaryStream stream = new BinaryStream(this.nbt.getByteArray("ExtraData"));
            for (int i = 0; i < stream.getInt(); i++) {
                int key = stream.getInt();
                extraData.put(key, stream.getShort());
            }
        }

        this.setPosition(this.nbt.getInt("xPos"), this.nbt.getInt("zPos"));
        this.blocks = this.nbt.getByteArray("Blocks");
        this.data = this.nbt.getByteArray("Data");
        this.skyLight = this.nbt.getByteArray("SkyLight");
        this.blockLight = this.nbt.getByteArray("BlockLight");
        int[] biomeColors = this.nbt.getIntArray("BiomeColors");
        if (biomeColors.length != 256) {
            biomeColors = new int[256];
            Arrays.fill(biomeColors, Binary.readInt(new byte[]{(byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0x00}));
        }
        this.biomeColors = biomeColors;
        int[] heightMap = this.nbt.getIntArray("HeightMap");
        if (heightMap.length != 256) {
            heightMap = new int[256];
            Arrays.fill(heightMap, 127);
        }
        this.heightMap = heightMap;

        this.extraData = extraData;

        this.NBTentities = ((ListTag<CompoundTag>) this.nbt.getList("Entities")).getAll();
        this.NBTtiles = ((ListTag<CompoundTag>) this.nbt.getList("TileEntities")).getAll();

        if (this.nbt.contains("Biomes")) {
            this.checkOldBiomes(this.nbt.getByteArray("Biomes"));
            this.nbt.remove("Biomes");
        }

        this.nbt.remove("Blocks");
        this.nbt.remove("Data");
        this.nbt.remove("SkyLight");
        this.nbt.remove("BlockLight");
        this.nbt.remove("BiomeColors");
        this.nbt.remove("HeightMap");
        this.nbt.remove("Biomes");
    }

    @Override
    public boolean isLightPopulated() {
        return this.nbt.getBoolean("LightPopulated");
    }

    @Override
    public void setLightPopulated() {
        this.setLightPopulated(true);
    }

    @Override
    public void setLightPopulated(boolean value) {
        this.nbt.putBoolean("LightPopulated", value);
        if (Server.getInstance().storeGeneratedChunks) {
            this.setChanged();
        }
    }

    @Override
    public boolean isPopulated() {
        return this.nbt.contains("TerrainPopulated") && this.nbt.getBoolean("TerrainPopulated");
    }

    @Override
    public void setPopulated() {
        this.setPopulated(true);
    }

    @Override
    public void setPopulated(boolean value) {
        this.nbt.putBoolean("TerrainPopulated", value);
        if (Server.getInstance().storeGeneratedChunks) {
            this.setChanged();
        }
    }

    @Override
    public boolean isGenerated() {
        if (this.nbt.contains("TerrainGenerated")) {
            return this.nbt.getBoolean("TerrainGenerated");
        } else if (this.nbt.contains("TerrainPopulated")) {
            return this.nbt.getBoolean("TerrainPopulated");
        }
        return false;
    }

    @Override
    public void setGenerated() {
        this.setGenerated(true);
    }

    @Override
    public void setGenerated(boolean value) {
        this.nbt.putBoolean("TerrainGenerated", value);
        if (Server.getInstance().storeGeneratedChunks) {
            this.setChanged();
        }
    }

    public static Chunk fromBinary(byte[] data) {
        return fromBinary(data, null);
    }

    public static Chunk fromBinary(byte[] data, LevelProvider provider) {
        try {
            CompoundTag chunk = NBTIO.read(new ByteArrayInputStream(Zlib.inflate(data)), ByteOrder.BIG_ENDIAN);

            if (!chunk.contains("Level") || !(chunk.get("Level") instanceof CompoundTag)) {
                return null;
            }
            return new Chunk(provider != null ? provider : McRegion.class.newInstance(), chunk.getCompound("Level"));
        } catch (Exception e) {
            return null;
        }
    }

    public static Chunk fromFastBinary(byte[] data) {
        return fromFastBinary(data, null);
    }

    public static Chunk fromFastBinary(byte[] data, LevelProvider provider) {
        try {
            int offset = 0;
            Chunk chunk = new Chunk(provider != null ? provider : McRegion.class.newInstance(), null);
            chunk.provider = provider;
            int x = Binary.readInt(Arrays.copyOfRange(data, offset, offset + 3));
            offset += 4;
            int z = Binary.readInt(Arrays.copyOfRange(data, offset, offset + 3));
            chunk.setPosition(x, z);
            offset += 4;
            chunk.blocks = Arrays.copyOfRange(data, offset, offset + 32767);
            offset += 32768;
            chunk.data = Arrays.copyOfRange(data, offset, offset + 16383);
            offset += 16384;
            chunk.skyLight = Arrays.copyOfRange(data, offset, offset + 16383);
            offset += 16384;
            chunk.blockLight = Arrays.copyOfRange(data, offset, offset + 16383);
            offset += 16384;
            chunk.heightMap = new int[256];
            for (int i = 0; i < 256; i++) {
                chunk.heightMap[i] = data[offset] & 0xff;
                offset++;
            }
            chunk.biomeColors = new int[256];
            for (int i = 0; i < 256; i++) {
                chunk.biomeColors[i] = Binary.readInt(Arrays.copyOfRange(data, offset, offset + 3));
                offset += 4;
            }
            byte flags = data[offset++];
            chunk.nbt.putByte("TerrainGenerated", (flags & 0b1));
            chunk.nbt.putByte("TerrainPopulated", ((flags >> 1) & 0b1));
            chunk.nbt.putByte("LightPopulated", ((flags >> 2) & 0b1));
            return chunk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public byte[] toFastBinary() {
        BinaryStream stream = new BinaryStream(new byte[65536]);
        stream.put(Binary.writeInt(this.getX()));
        stream.put(Binary.writeInt(this.getZ()));
        stream.put(this.getBlockIdArray());
        stream.put(this.getBlockDataArray());
        stream.put(this.getBlockSkyLightArray());
        stream.put(this.getBlockLightArray());
        for (int height : this.getHeightMapArray()) {
            stream.putByte((byte) (height & 0xff));
        }
        for (int color : this.getBiomeColorArray()) {
            stream.put(Binary.writeInt(color));
        }
        stream.putByte((byte) ((this.isLightPopulated() ? 1 << 2 : 0) + (this.isPopulated() ? 1 << 2 : 0) + (this.isGenerated() ? 1 : 0)));
        return stream.getBuffer();
    }

    @Override
    public byte[] toBinary() {
        CompoundTag nbt = this.getNBT().copy();

        nbt.putInt("xPos", this.getX());
        nbt.putInt("zPos", this.getZ());

        if (this.isGenerated()) {
            nbt.putByteArray("Blocks", this.getBlockIdArray());
            nbt.putByteArray("Data", this.getBlockDataArray());
            nbt.putByteArray("SkyLight", this.getBlockSkyLightArray());
            nbt.putByteArray("BlockLight", this.getBlockLightArray());
            nbt.putIntArray("BiomeColors", this.getBiomeColorArray());
            nbt.putIntArray("HeightMap", this.getHeightMapArray());
        }


        ArrayList<CompoundTag> entities = new ArrayList<>();
        for (Entity entity : this.getEntities().values()) {
            if (!(entity instanceof Player) && !entity.closed) {
                entity.saveNBT();
                entities.add(entity.namedTag);
            }
        }
        ListTag<CompoundTag> entityListTag = new ListTag<>("Entities");
        entityListTag.setAll(entities);
        nbt.putList(entityListTag);

        ArrayList<CompoundTag> tiles = new ArrayList<>();
        for (BlockEntity blockEntity : this.getBlockEntities().values()) {
            blockEntity.saveNBT();
            tiles.add(blockEntity.namedTag);
        }
        ListTag<CompoundTag> tileListTag = new ListTag<>("TileEntities");
        tileListTag.setAll(tiles);
        nbt.putList(tileListTag);

        BinaryStream extraData = new BinaryStream();
        Map<Integer, Integer> extraDataArray = this.getBlockExtraDataArray();
        extraData.putInt(extraDataArray.size());
        for (Integer key : extraDataArray.keySet()) {
            extraData.putInt(key);
            extraData.putShort(extraDataArray.get(key));
        }

        nbt.putByteArray("ExtraData", extraData.getBuffer());

        CompoundTag chunk = new CompoundTag("");
        chunk.putCompound("Level", nbt);

        try {
            return Zlib.deflate(NBTIO.write(chunk, ByteOrder.BIG_ENDIAN), RegionLoader.COMPRESSION_LEVEL);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CompoundTag getNBT() {
        return nbt;
    }

    public static Chunk getEmptyChunk(int chunkX, int chunkZ) {
        return getEmptyChunk(chunkX, chunkZ, null);
    }

    public static Chunk getEmptyChunk(int chunkX, int chunkZ, LevelProvider provider) {
        try {
            Chunk chunk;
            if (provider != null) {
                chunk = new Chunk(provider, null);
            } else {
                chunk = new Chunk(McRegion.class, null);
            }

            chunk.setPosition(chunkX, chunkZ);
            chunk.data = new byte[16384];
            chunk.blocks = new byte[32768];
            byte[] skyLight = new byte[16384];
            Arrays.fill(skyLight, (byte) 0xff);
            chunk.skyLight = skyLight;
            chunk.blockLight = chunk.data;

            chunk.heightMap = new int[256];
            chunk.biomeColors = new int[256];

            chunk.nbt.putByte("V", 1);
            chunk.nbt.putLong("InhabitedTime", 0);
            chunk.nbt.putBoolean("TerrainGenerated", false);
            chunk.nbt.putBoolean("TerrainPopulated", false);
            chunk.nbt.putBoolean("LightPopulated", false);

            return chunk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
