package cn.nukkit.level.format.generic;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.ChunkException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class BaseLevelProvider implements LevelProvider {
    protected Level level;

    protected final String path;

    protected CompoundTag levelData;

    protected final Map<Long, BaseRegionLoader> regions = new ConcurrentHashMap<>(8, 0.9f, 1);
    protected final Map<Long, BaseFullChunk> chunks = new ConcurrentHashMap<>(8, 0.9f, 1);

    public BaseLevelProvider(Level level, String path) throws IOException {
        this.level = level;
        this.path = path;
        File file_path = new File(this.path);
        if (!file_path.exists()) {
            file_path.mkdirs();
        }
        this.levelData = initLevelData();
    }

    public static int getRegionIndexX(int chunkX) {
        return chunkX >> 5;
    }

    public static int getRegionIndexZ(int chunkZ) {
        return chunkZ >> 5;
    }

    public abstract CompoundTag initLevelData() throws IOException;

    public abstract BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create);

    @Override
    public void unloadChunks() {
        Iterator<Map.Entry<Long, BaseFullChunk>> iter = chunks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, BaseFullChunk> entry = iter.next();
            long index = entry.getKey();
            BaseFullChunk chunk = entry.getValue();
            chunk.unload(true, false);
            iter.remove();
        }
    }

    @Override
    public String getGenerator() {
        return this.levelData.getString("generatorName");
    }

    @Override
    public Map<String, Object> getGeneratorOptions() {
        return new ConcurrentHashMap<String, Object>(8, 0.9f, 1) {
            {
                put("preset", levelData.getString("generatorOptions"));
            }
        };
    }

    @Override
    public Map<Long, BaseFullChunk> getLoadedChunks() {
        return this.chunks;
    }

    @Override
    public boolean isChunkLoaded(int X, int Z) {
        return this.chunks.containsKey(Level.chunkHash(X, Z));
    }

    @Override
    public boolean isChunkLoaded(long hash) {
        return this.chunks.containsKey(hash);
    }

    @Override
    public void saveChunks() {
        for (BaseFullChunk chunk : this.chunks.values()) {
            if (chunk.resetChanged() != 0) {
                this.saveChunk(chunk.getX(), chunk.getZ());
            }
        }
    }

    @Override
    public void doGarbageCollection() {
        int limit = (int) (System.currentTimeMillis() - 50);
        for (Map.Entry<Long, BaseRegionLoader> entry : this.regions.entrySet()) {
            long index = entry.getKey();
            BaseRegionLoader region = entry.getValue();
            if (region.lastUsed <= limit) {
                try {
                    region.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                lastRegion = null;
                this.regions.remove(index);
            }
        }
    }

    public BaseRegionLoader getRegion(int x, int z) {
        long index = Level.chunkHash(x, z);
        return this.regions.get(index);
    }

    protected volatile BaseRegionLoader lastRegion;

    @Override
    public synchronized void close() {
        this.unloadChunks();
        Iterator<Map.Entry<Long, BaseRegionLoader>> iter = this.regions.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, BaseRegionLoader> entry = iter.next();
            long index = entry.getKey();
            BaseRegionLoader region = entry.getValue();
            try {
                region.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            lastRegion = null;
            iter.remove();
        }
        this.level = null;
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ) {
        return this.loadChunk(chunkX, chunkZ, false);
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ, boolean create) {
        long index = Level.chunkHash(chunkX, chunkZ);
        if (this.chunks.containsKey(index)) {
            return true;
        }
        return loadChunk(index, chunkX, chunkZ, create) != null;
    }

    @Override
    public boolean unloadChunk(int X, int Z) {
        return this.unloadChunk(X, Z, true);
    }

    @Override
    public boolean unloadChunk(int X, int Z, boolean safe) {
        long index = Level.chunkHash(X, Z);
        BaseFullChunk chunk = this.chunks.get(index);
        if (chunk != null && chunk.unload(false, safe)) {
            lastChunk = null;
            this.chunks.remove(index, chunk);
            return true;
        }
        return false;
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    private volatile BaseFullChunk lastChunk;

    @Override
    public BaseFullChunk getLoadedChunk(int chunkX, int chunkZ) {
        BaseFullChunk tmp = lastChunk;
        if (tmp != null && tmp.getX() == chunkX && tmp.getZ() == chunkZ) {
            return tmp;
        }
        long index = Level.chunkHash(chunkX, chunkZ);
        lastChunk = tmp = chunks.get(index);
        return tmp;
    }

    @Override
    public BaseFullChunk getLoadedChunk(long hash) {
        BaseFullChunk tmp = lastChunk;
        if (tmp != null && tmp.getIndex() == hash) {
            return tmp;
        }
        lastChunk = tmp = chunks.get(hash);
        return tmp;
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ, boolean create) {
        BaseFullChunk tmp = lastChunk;
        if (tmp != null && tmp.getX() == chunkX && tmp.getZ() == chunkZ) {
            return tmp;
        }
        long index = Level.chunkHash(chunkX, chunkZ);
        lastChunk = tmp = chunks.get(index);
        if (tmp != null) {
            return tmp;
        } else {
            tmp = this.loadChunk(index, chunkX, chunkZ, create);
            lastChunk = tmp;
            return tmp;
        }
    }

    @Override
    public void setChunk(int chunkX, int chunkZ, FullChunk chunk) {
        if (!(chunk instanceof BaseFullChunk)) {
            throw new ChunkException("Invalid Chunk class");
        }
        chunk.setProvider(this);
        chunk.setX(chunkX);
        chunk.setZ(chunkZ);
        long index = Level.chunkHash(chunkX, chunkZ);
        if (this.chunks.containsKey(index) && !this.chunks.get(index).equals(chunk)) {
            this.unloadChunk(chunkX, chunkZ, false);
        }
        this.chunks.put(index, (BaseFullChunk) chunk);
    }

    @Override
    public boolean isChunkPopulated(int chunkX, int chunkZ) {
        BaseFullChunk chunk = this.getChunk(chunkX, chunkZ);
        return chunk != null && chunk.isPopulated();
    }

    @Override
    public String getPath() {
        return path;
    }

    public Server getServer() {
        return this.level.getServer();
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public String getName() {
        return this.levelData.getString("LevelName");
    }

    @Override
    public boolean isRaining() {
        return this.levelData.getBoolean("raining");
    }

    @Override
    public void setRaining(boolean raining) {
        this.levelData.putBoolean("raining", raining);
    }

    @Override
    public int getRainTime() {
        return this.levelData.getInt("rainTime");
    }

    @Override
    public void setRainTime(int rainTime) {
        this.levelData.putInt("rainTime", rainTime);
    }

    @Override
    public boolean isThundering() {
        return this.levelData.getBoolean("thundering");
    }

    @Override
    public void setThundering(boolean thundering) {
        this.levelData.putBoolean("thundering", thundering);
    }

    @Override
    public int getThunderTime() {
        return this.levelData.getInt("thunderTime");
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.levelData.putInt("thunderTime", thunderTime);
    }

    @Override
    public long getCurrentTick() {
        return this.levelData.getLong("Time");
    }

    @Override
    public void setCurrentTick(long currentTick) {
        this.levelData.putLong("Time", currentTick);
    }

    @Override
    public long getTime() {
        return this.levelData.getLong("DayTime");
    }

    @Override
    public void setTime(long value) {
        this.levelData.putLong("DayTime", value);
    }

    @Override
    public long getSeed() {
        return this.levelData.getLong("RandomSeed");
    }

    @Override
    public void setSeed(long value) {
        this.levelData.putLong("RandomSeed", value);
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(this.levelData.getInt("SpawnX"), this.levelData.getInt("SpawnY"), this.levelData.getInt("SpawnZ"));
    }

    @Override
    public void setSpawn(Vector3 pos) {
        this.levelData.putInt("SpawnX", (int) pos.x);
        this.levelData.putInt("SpawnY", (int) pos.y);
        this.levelData.putInt("SpawnZ", (int) pos.z);
    }

    public CompoundTag getLevelData() {
        return levelData;
    }

    @Override
    public void saveLevelData() {
        try {
            NBTIO.writeGZIPCompressed(new CompoundTag().putCompound("Data", this.levelData), new FileOutputStream(this.getPath() + "level.dat"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateLevelName(String name) {
        if (!this.getName().equals(name)) {
            this.levelData.putString("LevelName", name);
        }
    }
}
