package cn.nukkit.timings;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


import static cn.nukkit.timings.Timings.fullServerTickTimer;
import static cn.nukkit.timings.TimingsManager.MINUTE_REPORTS;

/**
 * @author Tee7even
 */
public class TimingsHistory {
    public static long lastMinuteTime;
    public static long timedTicks;
    public static long playerTicks;
    public static long entityTicks;
    public static long tileEntityTicks;
    public static long activatedEntityTicks;

    private static int levelIdPool = 1;
    static Map<String, Integer> levelMap = new ConcurrentHashMap<>(8, 0.9f, 1);
    static Map<Integer, String> entityMap = new ConcurrentHashMap<>(8, 0.9f, 1);
    static Map<Integer, String> blockEntityMap = new ConcurrentHashMap<>(8, 0.9f, 1);

    private final long endTime;
    private final long startTime;
    private final long totalTicks;
    // Represents all time spent running the server this history
    private final long totalTime;
    private final MinuteReport[] minuteReports;

    private final TimingsHistoryEntry[] entries;
    private final JsonObject levels = new JsonObject();

    TimingsHistory() {
        this.endTime = System.currentTimeMillis() / 1000;
        this.startTime = TimingsManager.historyStart / 1000;

        if (timedTicks % 1200 != 0 || MINUTE_REPORTS.isEmpty()) {
            this.minuteReports = MINUTE_REPORTS.toArray(new MinuteReport[MINUTE_REPORTS.size() + 1]);
            this.minuteReports[this.minuteReports.length - 1] = new MinuteReport();
        } else {
            this.minuteReports = MINUTE_REPORTS.toArray(new MinuteReport[MINUTE_REPORTS.size()]);
        }

        long ticks = 0;
        for (MinuteReport mr : this.minuteReports) {
            ticks += mr.ticksRecord.timed;
        }

        this.totalTicks = ticks;
        this.totalTime = fullServerTickTimer.record.totalTime;
        this.entries = new TimingsHistoryEntry[TimingsManager.TIMINGS.size()];

        int i = 0;
        for (Timing timing : TimingsManager.TIMINGS) {
            this.entries[i++] = new TimingsHistoryEntry(timing);
        }

        final Map<Integer, AtomicInteger> entityCounts = new ConcurrentHashMap<>(8, 0.9f, 1);
        final Map<Integer, AtomicInteger> blockEntityCounts = new ConcurrentHashMap<>(8, 0.9f, 1);
        final Gson GSON = new Gson();
        // Information about all loaded entities/block entities
        for (Level level : Server.getInstance().getLevels().values()) {
            JsonArray jsonLevel = new JsonArray();
            for (FullChunk chunk : level.getChunks().values()) {
                entityCounts.clear();
                blockEntityCounts.clear();

                //count entities
                for (Entity entity : chunk.getEntities().values()) {
                    if (!entityCounts.containsKey(entity.getNetworkId()))
                        entityCounts.put(entity.getNetworkId(), new AtomicInteger(0));
                    entityCounts.get(entity.getNetworkId()).incrementAndGet();
                    entityMap.put(entity.getNetworkId(), entity.getClass().getSimpleName());
                }

                //count block entities
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!blockEntityCounts.containsKey(blockEntity.getBlock().getId()))
                        blockEntityCounts.put(blockEntity.getBlock().getId(), new AtomicInteger(0));
                    blockEntityCounts.get(blockEntity.getBlock().getId()).incrementAndGet();
                    blockEntityMap.put(blockEntity.getBlock().getId(), blockEntity.getClass().getSimpleName());
                }

                if (blockEntityCounts.isEmpty() && entityCounts.isEmpty()) {
                    continue;
                }

                JsonArray jsonChunk = new JsonArray();
                jsonChunk.add(chunk.getX());
                jsonChunk.add(chunk.getZ());
                jsonChunk.add(GSON.toJsonTree(JsonUtil.mapToObject(entityCounts.entrySet(), (entry) -> new JsonUtil.JSONPair(entry.getKey(), entry.getValue().get()))).getAsJsonObject());
                jsonChunk.add(GSON.toJsonTree(JsonUtil.mapToObject(blockEntityCounts.entrySet(), (entry) -> new JsonUtil.JSONPair(entry.getKey(), entry.getValue().get()))).getAsJsonObject());
                jsonLevel.add(jsonChunk);
            }

            if (!levelMap.containsKey(level.getName())) levelMap.put(level.getName(), levelIdPool++);
            levels.add(String.valueOf(levelMap.get(level.getName())), jsonLevel);
        }
    }

    static void resetTicks(boolean fullReset) {
        if (fullReset) {
            timedTicks = 0;
        }
        lastMinuteTime = System.nanoTime();
        playerTicks = 0;
        tileEntityTicks = 0;
        entityTicks = 0;
        activatedEntityTicks = 0;
    }

    JsonObject export() {
        JsonObject json = new JsonObject();
        json.addProperty("s", this.startTime);
        json.addProperty("e", this.endTime);
        json.addProperty("tk", this.totalTicks);
        json.addProperty("tm", this.totalTime);
        json.add("w", this.levels);
        json.add("h", JsonUtil.mapToArray(this.entries, (entry) -> {
            if (entry.data.count == 0) {
                return null;
            }
            return entry.export();
        }));
        json.add("mp", JsonUtil.mapToArray(this.minuteReports, MinuteReport::export));
        return json;
    }

    static class MinuteReport {
        final long time = System.currentTimeMillis() / 1000;

        final TicksRecord ticksRecord = new TicksRecord();
        final PingRecord pingRecord = new PingRecord();
        final TimingData fst = Timings.fullServerTickTimer.minuteData.clone();
        final double tps = 1E9 / (System.nanoTime() - lastMinuteTime) * this.ticksRecord.timed;
        final double usedMemory = Timings.fullServerTickTimer.avgUsedMemory;
        final double freeMemory = Timings.fullServerTickTimer.avgFreeMemory;
        final double loadAvg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

        JsonArray export() {
            return JsonUtil.toArray(this.time,
                    Math.round(this.tps * 100D) / 100D,
                    Math.round(this.pingRecord.avg * 100D) / 100D,
                    this.fst.export(),
                    JsonUtil.toArray(this.ticksRecord.timed,
                            this.ticksRecord.player,
                            this.ticksRecord.entity,
                            this.ticksRecord.activatedEntity,
                            this.ticksRecord.tileEntity),
                    this.usedMemory,
                    this.freeMemory,
                    this.loadAvg);
        }
    }

    private static class TicksRecord {
        final long timed;
        final long player;
        final long entity;
        final long activatedEntity;
        final long tileEntity;

        TicksRecord() {
            this.timed = timedTicks - (TimingsManager.MINUTE_REPORTS.size() * 1200);
            this.player = playerTicks;
            this.entity = entityTicks;
            this.activatedEntity = activatedEntityTicks;
            this.tileEntity = tileEntityTicks;
        }
    }

    private static class PingRecord {
        final double avg;

        PingRecord() {
            final Collection<Player> onlinePlayers = Server.getInstance().getOnlinePlayers().values();
            int totalPing = 0;
            for (Player player : onlinePlayers) {
                totalPing += player.getPing();
            }

            this.avg = onlinePlayers.isEmpty() ? 0 : totalPing / onlinePlayers.size();
        }
    }
}
