package cn.nukkit.level.format.generic;

import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.utils.BufferedRandomAccessFile;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
abstract public class BaseRegionLoader {
    public static final int VERSION = 1;
    public static final byte COMPRESSION_GZIP = 1;
    public static final byte COMPRESSION_ZLIB = 2;
    public static final int MAX_SECTOR_LENGTH = 256 << 12;
    public static final int COMPRESSION_LEVEL = 7;

    protected int x;
    protected int z;
    protected String filePath;
    protected File file;
    protected BufferedRandomAccessFile randomAccessFile;
    protected int lastSector;
    protected LevelProvider levelProvider;
    protected final Map<Integer, Integer[]> locationTable = new ConcurrentHashMap<>(8, 0.9f, 1);

    public long lastUsed;

    public BaseRegionLoader(LevelProvider level, int regionX, int regionZ, String ext) {
        try {
            this.x = regionX;
            this.z = regionZ;
            this.levelProvider = level;
            this.filePath = this.levelProvider.getPath() + "region/r." + regionX + "." + regionZ + "." + ext;
            this.file = new File(this.filePath);
            boolean exists = this.file.exists();
            if (!exists) {
                file.createNewFile();
            }
            this.randomAccessFile = new BufferedRandomAccessFile(this.filePath, "rw", 1024);
            if (!exists) {
                this.createBlank();
            } else {
                if (randomAccessFile.length() < 8192) {
                    randomAccessFile.setLength(0);
                    randomAccessFile.setLength(8192);
                }
                this.loadLocationTable();
            }

            this.lastUsed = System.currentTimeMillis();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    protected abstract boolean isChunkGenerated(int index);

    public abstract BaseFullChunk readChunk(int x, int z) throws IOException;

    protected abstract BaseFullChunk unserializeChunk(byte[] data);

    public abstract boolean chunkExists(int x, int z);

    protected abstract void saveChunk(int x, int z, byte[] chunkData) throws IOException;

    public abstract void removeChunk(int x, int z);

    public abstract void writeChunk(FullChunk chunk) throws Exception;

    public abstract void close() throws IOException;

    protected abstract void loadLocationTable() throws IOException;

    public abstract int doSlowCleanUp() throws Exception;

    protected abstract void writeLocationIndex(int index) throws IOException;

    protected abstract void createBlank() throws IOException;

    public abstract int getX();

    public abstract int getZ();

}
