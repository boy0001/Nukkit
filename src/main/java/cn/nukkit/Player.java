package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockDoor;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.args.CommandArg;
import cn.nukkit.command.data.args.CommandArgBlockVector;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.*;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityEgg;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntitySnowball;
import cn.nukkit.event.block.ItemFrameDropItemEvent;
import cn.nukkit.event.block.SignChangeEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArrow;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemGlassBottle;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.food.Food;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.particle.CriticalParticle;
import cn.nukkit.level.sound.ExperienceOrbSound;
import cn.nukkit.level.sound.ItemFrameItemRemovedSound;
import cn.nukkit.level.sound.LaunchSound;
import cn.nukkit.math.*;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.timings.Timing;
import cn.nukkit.timings.Timings;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.Zlib;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * author: MagicDroidX & Box
 * Nukkit Project
 */
public class Player extends EntityHuman implements CommandSender, InventoryHolder, ChunkLoader, IPlayer {

    public static final int SURVIVAL = 0;
    public static final int CREATIVE = 1;
    public static final int ADVENTURE = 2;
    public static final int SPECTATOR = 3;
    public static final int VIEW = SPECTATOR;

    public static final int SURVIVAL_SLOTS = 36;
    public static final int CREATIVE_SLOTS = 112;

    public static final int CRAFTING_SMALL = 0;
    public static final int CRAFTING_BIG = 1;
    public static final int CRAFTING_ANVIL = 2;
    public static final int CRAFTING_ENCHANT = 3;

    public static final float DEFAULT_SPEED = 0.1f;
    public static final float MAXIMUM_SPEED = 0.5f;

    protected final SourceInterface interfaz;

    public boolean playedBefore;
    public boolean spawned = false;
    public boolean loggedIn = false;
    public int gamemode;
    public long lastBreak;

    protected volatile int windowCnt = 2;

    protected final Map<Inventory, Integer> windows = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<Integer, Inventory> windowIndex = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected volatile int messageCounter = 2;

    protected volatile int sendIndex = 0;

    private volatile String clientSecret;

    public volatile Vector3 speed = null;

    public volatile boolean blocked = false;

    //todo: achievements

    protected volatile SimpleTransactionGroup currentTransaction = null;

    public volatile int craftingType = CRAFTING_SMALL;

    protected volatile boolean isCrafting = false;

    public volatile long creationTime = 0;

    protected volatile long randomClientId;

    protected volatile double lastMovement = 0;

    protected volatile Vector3 forceMovement = null;

    protected volatile Vector3 teleportPosition = null;

    protected volatile boolean connected = true;
    protected final String ip;
    protected volatile boolean removeFormat = true;

    protected final int port;
    protected volatile String username;
    protected volatile String iusername;
    protected volatile String displayName;

    protected volatile int startAction = -1;

    protected volatile Vector3 sleeping = null;
    protected volatile Long clientID = null;

    private volatile Integer loaderId = null;

    protected volatile float stepHeight = 0.6f;

    public final Map<Long, Boolean> usedChunks = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected volatile int chunkLoadCount = 0;
    protected final Map<Long, Integer> loadQueue = new ConcurrentHashMap<>(8, 0.9f, 1);
    protected volatile int nextChunkOrderRun = 1;

    protected final Map<UUID, Player> hiddenPlayers = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected volatile Vector3 newPosition = null;

    protected volatile int chunkRadius;
    protected volatile int viewDistance;
    protected final int chunksPerTick;
    protected final int spawnThreshold;

    protected volatile Position spawnPosition = null;

    protected volatile int inAirTicks = 0;
    protected volatile int startAirTicks = 5;

    protected volatile AdventureSettings adventureSettings;

    private final Map<Integer, Boolean> needACK = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected boolean checkMovement = true;

    private final Map<Integer, Queue<DataPacket>> batchedPackets = new TreeMap<>();

    private final PermissibleBase perm;

    private volatile int exp = 0;
    private volatile int expLevel = 0;

    protected volatile PlayerFood foodData = null;

    private volatile Entity killer = null;

    private final AtomicReference<Locale> locale = new AtomicReference<>(null);

    private volatile int hash;

    private String buttonText = "Button";

    protected boolean enableClientCommand = true;

    public TranslationContainer getLeaveMessage() {
        return new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.left", this.getDisplayName());
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * This might disappear in the future.
     * Please use getUniqueId() instead (IP + clientId + name combo, in the future it'll change to real UUID for online auth)
     */
    @Deprecated
    public Long getClientId() {
        return randomClientId;
    }

    @Override
    public boolean isBanned() {
        return this.server.getNameBans().isBanned(this.getName().toLowerCase());
    }

    @Override
    public void setBanned(boolean value) {
        if (value) {
            this.server.getNameBans().addBan(this.getName(), null, null, null);
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "Banned by admin");
        } else {
            this.server.getNameBans().remove(this.getName());
        }
    }

    @Override
    public boolean isWhitelisted() {
        return this.server.isWhitelisted(this.getName().toLowerCase());
    }

    @Override
    public void setWhitelisted(boolean value) {
        if (value) {
            this.server.addWhitelist(this.getName().toLowerCase());
        } else {
            this.server.removeWhitelist(this.getName().toLowerCase());
        }
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public Long getFirstPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("firstPlayed") : null;
    }

    @Override
    public Long getLastPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("lastPlayed") : null;
    }

    @Override
    public boolean hasPlayedBefore() {
        return this.playedBefore;
    }

    public AdventureSettings getAdventureSettings() {
        return adventureSettings;
    }

    public void setAdventureSettings(AdventureSettings adventureSettings) {
        this.adventureSettings = adventureSettings.clone(this);
        adventureSettings.update();
    }

    public synchronized void resetInAirTicks() {
        this.inAirTicks = 0;
    }

    @Deprecated
    public void setAllowFlight(boolean value) {
        this.getAdventureSettings().setCanFly(value);
        this.getAdventureSettings().update();
    }

    @Deprecated
    public boolean getAllowFlight() {
        return this.getAdventureSettings().canFly();
    }

    @Deprecated
    public void setAutoJump(boolean value) {
        this.getAdventureSettings().setAutoJump(value);
        this.getAdventureSettings().update();
    }

    @Deprecated
    public boolean hasAutoJump() {
        return this.getAdventureSettings().isAutoJumpEnabled();
    }

    @Override
    public void spawnTo(Player player) {
        if (this.spawned && player.spawned && this.isAlive() && player.isAlive() && player.getLevel() == this.level && player.canSee(this) && !this.isSpectator()) {
            super.spawnTo(player);
        }
    }

    @Override
    public Server getServer() {
        return this.server;
    }

    public boolean getRemoveFormat() {
        return removeFormat;
    }

    public void setRemoveFormat() {
        this.setRemoveFormat(true);
    }

    public void setRemoveFormat(boolean remove) {
        this.removeFormat = remove;
    }

    public boolean canSee(Player player) {
        return !this.hiddenPlayers.containsKey(player.getUniqueId());
    }

    public void hidePlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.put(player.getUniqueId(), player);
        player.despawnFrom(this);
    }

    public void showPlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.spawnTo(this);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public synchronized void resetFallDistance() {
        super.resetFallDistance();
        if (this.inAirTicks != 0) {
            this.startAirTicks = 5;
        }
        this.inAirTicks = 0;
        this.highestPosition = this.y;
    }

    @Override
    public boolean isOnline() {
        return this.connected && this.loggedIn;
    }

    @Override
    public boolean isOp() {
        return this.server.isOp(this.getName());
    }

    @Override
    public void setOp(boolean value) {
        if (value == this.isOp()) {
            return;
        }

        if (value) {
            this.server.addOp(this.getName());
        } else {
            this.server.removeOp(this.getName());
        }

        this.recalculatePermissions();
        this.getAdventureSettings().update();
    }

    @Override
    public boolean isPermissionSet(String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return this.perm.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String name) {
        return this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.addAttachment(plugin, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.addAttachment(plugin, name, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public synchronized void recalculatePermissions() {
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);

        this.perm.recalculatePermissions();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        if (this.isEnableClientCommand()) this.sendCommandData();
    }

    public boolean isEnableClientCommand() {
        return this.enableClientCommand;
    }

    public void setEnableClientCommand(boolean enable) {
        this.enableClientCommand = enable;
        SetCommandsEnabledPacket pk = new SetCommandsEnabledPacket();
        pk.enabled = enable;
        this.dataPacket(pk);
        if (enable) this.sendCommandData();
    }

    public void sendCommandData() {
        AvailableCommandsPacket pk = new AvailableCommandsPacket();
        Map<String, CommandDataVersions> data = new HashMap<>();
        int count = 0;
        for (Command command : this.server.getCommandMap().getCommands().values()) {
            if (!command.testPermissionSilent(this)) {
                continue;
            }
            ++count;
            CommandDataVersions data0 = command.generateCustomCommandData(this);
            data.put(command.getName(), data0);
        }
        if (count > 0) {
            //TODO: structure checking
            pk.commands = new Gson().toJson(data);
            this.dataPacket(pk);
        }
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    public Player(SourceInterface interfaz, Long clientID, String ip, int port) {
        super(null, new CompoundTag());
        this.interfaz = interfaz;
        this.perm = new PermissibleBase(this);
        this.server = Server.getInstance();
        this.lastBreak = Long.MAX_VALUE;
        this.ip = ip;
        this.port = port;
        this.clientID = clientID;
        this.loaderId = Level.generateChunkLoaderId(this);
        this.chunksPerTick = (int) this.server.getConfig("chunk-sending.per-tick", 4);
        this.spawnThreshold = (int) this.server.getConfig("chunk-sending.spawn-threshold", 56);
        this.spawnPosition = null;
        this.gamemode = this.server.getGamemode();
        this.setLevel(this.server.getDefaultLevel());
        this.viewDistance = this.server.getViewDistance();
        this.chunkRadius = viewDistance;
        //this.newPosition = new Vector3(0, 0, 0);
        this.boundingBox = new AxisAlignedBB(0, 0, 0, 0, 0, 0);

        this.uuid = null;
        this.rawUUID = null;

        this.creationTime = System.currentTimeMillis();
    }

    public boolean isPlayer() {
        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.getDisplayName(), this.getSkin());
        }
    }

    @Override
    public void setSkin(Skin skin) {
        super.setSkin(skin);
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.getDisplayName(), skin);
        }
    }

    public String getAddress() {
        return this.ip;
    }

    public int getPort() {
        return port;
    }

    public synchronized Position getNextPosition() {
        return this.newPosition != null ? new Position(this.newPosition.x, this.newPosition.y, this.newPosition.z, this.level) : this.getPosition();
    }

    public boolean isSleeping() {
        return this.sleeping != null;
    }

    public int getInAirTicks() {
        return this.inAirTicks;
    }

    public String getButtonText() {
        return this.buttonText;
    }

    public void setButtonText(String text) {
        this.buttonText = text;
        this.setDataProperty(new StringEntityData(Entity.DATA_INTERACTIVE_TAG, this.buttonText));
    }

    @Override
    protected synchronized boolean switchLevel(Level targetLevel) {
        Level oldLevel = this.level;
        if (super.switchLevel(targetLevel)) {
            for (long index : new ArrayList<>(this.usedChunks.keySet())) {
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);
                this.unloadChunk(chunkX, chunkZ, oldLevel);
            }

            this.usedChunks.clear();
            SetTimePacket pk = new SetTimePacket();
            pk.time = this.level.getTime();
            pk.started = !this.level.stopTime;
            this.dataPacket(pk);
            return true;
        }
        return false;
    }

    public void unloadChunk(int x, int z) {
        this.unloadChunk(x, z, null);
    }

    public synchronized void unloadChunk(int x, int z, Level level) {
        level = level == null ? this.level : level;
        long index = Level.chunkHash(x, z);
        if (this.usedChunks.containsKey(index)) {
            for (Entity entity : level.getChunkEntities(x, z).values()) {
                if (entity != this) {
                    entity.despawnFrom(this);
                }
            }

            this.usedChunks.remove(index);
        }
        level.unregisterChunkLoader(this, x, z);
        this.loadQueue.remove(index);
    }

    public Position getSpawn() {
        Position tmp = this.spawnPosition;
        if (tmp != null && tmp.getLevel() != null) {
            return tmp;
        } else {
            return this.server.getDefaultLevel().getSafeSpawn();
        }
    }

    public synchronized void sendChunk(int x, int z, DataPacket packet) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), true);
        this.chunkLoadCount++;

        this.dataPacket(packet);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    public void sendChunk(int x, int z, byte[] payload) {
        this.sendChunk(x, z, payload, FullChunkDataPacket.ORDER_COLUMNS);
    }

    public synchronized void sendChunk(int x, int z, byte[] payload, byte ordering) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), true);
        this.chunkLoadCount++;

        FullChunkDataPacket pk = new FullChunkDataPacket();
        pk.chunkX = x;
        pk.chunkZ = z;
        pk.order = ordering;
        pk.data = payload;

        this.batchDataPacket(pk);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    protected synchronized void sendNextChunk() {
        if (!this.connected) {
            return;
        }
        Timings.playerChunkSendTimer.startTiming();

        int count = 0;

        List<Map.Entry<Long, Integer>> entryList = new ArrayList<>(this.loadQueue.entrySet());
        if (entryList.size() + chunkLoadCount > spawnThreshold) {
            entryList.sort(new Comparator<Map.Entry<Long, Integer>>() {
                @Override
                public int compare(Map.Entry<Long, Integer> o1, Map.Entry<Long, Integer> o2) {
                    return o1.getValue() - o2.getValue();
                }
            });
        }

        for (Map.Entry<Long, Integer> entry : entryList) {
            long index = entry.getKey();
            if (count >= this.chunksPerTick) {
                break;
            }
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);

            ++count;

            this.usedChunks.put(index, false);
            this.level.registerChunkLoader(this, chunkX, chunkZ, false);

            if (!this.level.populateChunk(chunkX, chunkZ)) {
                if (this.spawned && this.teleportPosition == null) {
                    continue;
                } else {
                    break;
                }
            }

            this.loadQueue.remove(index);

            PlayerChunkRequestEvent ev = new PlayerChunkRequestEvent(this, chunkX, chunkZ);
            this.server.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) {
                this.level.requestChunk(chunkX, chunkZ, this);
            }
        }
        if (this.chunkLoadCount >= this.spawnThreshold && !this.spawned && this.teleportPosition == null) {
            this.doFirstSpawn();
        }
        Timings.playerChunkSendTimer.stopTiming();
    }

    protected synchronized void doFirstSpawn() {
        this.spawned = true;

        this.server.sendRecipeList(this);
        this.getAdventureSettings().update();

        this.sendPotionEffects(this);
        this.sendData(this);
        this.inventory.sendContents(this);
        this.inventory.sendArmorContents(this);

        if (level.stopTime) {
            SetTimePacket setTimePacket = new SetTimePacket();
            setTimePacket.time = this.level.getTime();
            setTimePacket.started = !this.level.stopTime;
            this.dataPacket(setTimePacket);
        }


        Position pos = this.level.getSafeSpawn(this);

        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(this, pos);

        this.server.getPluginManager().callEvent(respawnEvent);

        pos = respawnEvent.getRespawnPosition();

        if (this.getHealth() <= 0) {
            RespawnPacket  respawnPacket = new RespawnPacket();
            pos = this.getSpawn();
            respawnPacket.x = (float) pos.x;
            respawnPacket.y = (float) pos.y;
            respawnPacket.z = (float) pos.z;
            this.dataPacket(respawnPacket);
        } else {
            RespawnPacket respawnPacket = new RespawnPacket();
            respawnPacket.x = (float) pos.x;
            respawnPacket.y = (float) pos.y;
            respawnPacket.z = (float) pos.z;
            this.dataPacket(respawnPacket);
        }

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.status = PlayStatusPacket.PLAYER_SPAWN;
        this.dataPacket(playStatusPacket);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this,
                new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.joined", new String[]{
                        this.getDisplayName()
                })
        );

        this.server.getPluginManager().callEvent(playerJoinEvent);

        if (playerJoinEvent.getJoinMessage().toString().trim().length() > 0) {
            this.server.broadcastMessage(playerJoinEvent.getJoinMessage());
        }

        this.noDamageTicks = 60;

        for (long index : this.usedChunks.keySet()) {
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            for (Entity entity : this.level.getChunkEntities(chunkX, chunkZ).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        int experience = this.getExperience();
        if (experience != 0) {
            this.sendExperience(experience);
        }

        int level = this.getExperienceLevel();
        if (level != 0) {
            this.sendExperienceLevel(this.getExperienceLevel());
        }

        this.teleport(pos, null); // Prevent PlayerTeleportEvent during player spawn

        if (!this.isSpectator()) {
            this.spawnToAll();
        }

        //todo Updater

        //Weather
        if (this.level.isRaining() || this.level.isThundering()) {
           this.getLevel().sendWeather(this);
        }

        //FoodLevel
        PlayerFood food = this.getFoodData();
        if (food.getLevel() != food.getMaxLevel()) {
            food.sendFoodLevel();
        }
    }

    protected synchronized boolean orderChunks() {
        if (!this.connected) {
            return false;
        }

        Timings.playerChunkOrderTimer.startTiming();

        this.nextChunkOrderRun = 200;

        loadQueue.clear();
        Map<Long, Boolean> lastChunk = new ConcurrentHashMap<>(this.usedChunks);

        int centerX = (int) this.x >> 4;
        int centerZ = (int) this.z >> 4;
        int count = 0;

        int radius = spawned ? this.chunkRadius : (int) Math.ceil(Math.sqrt(spawnThreshold));

        int radiusSqr = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            int chunkX = x + centerX;
            for (int z = -radius; z <= radius; z++) {
                int chunkZ = z + centerZ;
                int distanceSqr = x * x + z * z;
                if (distanceSqr <= radiusSqr) {
                    long index;
                    if (!(this.usedChunks.containsKey(index = Level.chunkHash(chunkX, chunkZ))) || !this.usedChunks.get(index)) {
                        this.loadQueue.put(index, distanceSqr);
                        count++;
                    }
                    lastChunk.remove(index);
                }
            }
        }

        for (long index : new ArrayList<>(lastChunk.keySet())) {
            this.unloadChunk(Level.getHashX(index), Level.getHashZ(index));
        }

        Timings.playerChunkOrderTimer.stopTiming();
        return true;
    }

    public boolean batchDataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        try (Timing timing = Timings.getSendDataPacketTiming(packet)) {
            DataPacketSendEvent event = new DataPacketSendEvent(this, packet);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                timing.stopTiming();
                return false;
            }
            Queue queue = Utils.getOrCreate(this.batchedPackets, ArrayDeque.class, packet.getChannel());
            queue.add(packet.clone());
        }
        return true;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     */
    public boolean dataPacket(DataPacket packet) {
        return this.dataPacket(packet, false) != -1;
    }

    public int dataPacket(DataPacket packet, boolean needACK) {
        if (!this.connected) {
            return -1;
        }

        try (Timing timing = Timings.getSendDataPacketTiming(packet)) {
            DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                timing.stopTiming();
                return -1;
            }

            Integer identifier = this.interfaz.putPacket(this, packet, needACK, false);

            if (needACK && identifier != null) {
                this.needACK.put(identifier, false);
                timing.stopTiming();
                return identifier;
            }
        }
        return 0;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     */
    public boolean directDataPacket(DataPacket packet) {
        return this.directDataPacket(packet, false) != -1;
    }

    public int directDataPacket(DataPacket packet, boolean needACK) {
        if (!this.connected) {
            return -1;
        }

        try (Timing timing = Timings.getSendDataPacketTiming(packet)) {
            DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                timing.stopTiming();
                return -1;
            }

            Integer identifier = this.interfaz.putPacket(this, packet, needACK, true);

            if (needACK && identifier != null) {
                this.needACK.put(identifier, false);
                timing.stopTiming();
                return identifier;
            }
        }
        return 0;
    }

    public int getPing() {
        return this.interfaz.getNetworkLatency(this);
    }

    public synchronized boolean sleepOn(Vector3 pos) {
        if (!this.isOnline()) {
            return false;
        }

        for (Entity p : this.level.getNearbyEntities(this.boundingBox.grow(2, 1, 2), this)) {
            if (p instanceof Player) {
                if (((Player) p).sleeping != null && pos.distance(((Player) p).sleeping) <= 0.1) {
                    return false;
                }
            }
        }

        PlayerBedEnterEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerBedEnterEvent(this, this.level.getBlock(pos)));
        if (ev.isCancelled()) {
            return false;
        }

        this.sleeping = pos.clone();
        this.teleport(new Location(pos.x + 0.5, pos.y - 0.5, pos.z + 0.5, this.yaw, this.pitch, this.level), null);

        this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, (int) pos.x, (int) pos.y, (int) pos.z));
        this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, true);

        this.setSpawn(pos);

        this.level.sleepTicks.set(60);

        return true;
    }

    public void setSpawn(Vector3 pos) {
        Level level;
        if (!(pos instanceof Position)) {
            level = this.level;
        } else {
            level = ((Position) pos).getLevel();
        }
        Position newSpawn = new Position(pos.x, pos.y, pos.z, level);
        this.spawnPosition = newSpawn;
        SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
        pk.x = (int) newSpawn.x;
        pk.y = (int) newSpawn.y;
        pk.z = (int) newSpawn.z;
        this.dataPacket(pk);
    }

    public synchronized void stopSleep() {
        if (this.sleeping != null) {
            this.server.getPluginManager().callEvent(new PlayerBedLeaveEvent(this, this.level.getBlock(this.sleeping)));

            this.sleeping = null;
            this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, 0, 0, 0));
            this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, false);


            this.level.sleepTicks.set(0);

            AnimatePacket pk = new AnimatePacket();
            pk.eid = 0;
            pk.action = 3; //Wake up
            this.dataPacket(pk);
        }
    }

    public int getGamemode() {
        return gamemode;
    }

    public boolean setGamemode(int gamemode) {
        return this.setGamemode(gamemode, false, null);
    }

    public boolean setGamemode(int gamemode, boolean clientSide) {
        return this.setGamemode(gamemode, clientSide, null);
    }

    public synchronized boolean setGamemode(int gamemode, boolean clientSide, AdventureSettings newSettings) {
        if (gamemode < 0 || gamemode > 3 || this.gamemode == gamemode) {
            return false;
        }

        if (newSettings == null) {
            newSettings = this.getAdventureSettings().clone(this);
            newSettings.setCanDestroyBlock((gamemode & 0x02) == 0);
            newSettings.setCanFly((gamemode & 0x01) > 0);
            newSettings.setNoclip(gamemode == 0x03);
            newSettings.setFlying(gamemode == 0x03);
        }

        PlayerGameModeChangeEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerGameModeChangeEvent(this, gamemode, newSettings));

        if (ev.isCancelled()) {
            return false;
        }

        this.gamemode = gamemode;

        if (this.isSpectator()) {
            this.keepMovement = true;
            this.despawnFromAll();
        } else {
            this.keepMovement = false;
            this.spawnToAll();
        }

        this.namedTag.putInt("playerGameType", this.gamemode);

        if (!clientSide) {
            SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
            pk.gamemode = this.gamemode & 0x01;
            this.dataPacket(pk);
        }

        this.setAdventureSettings(ev.getNewAdventureSettings());

        if (this.isSpectator()) {
            this.getAdventureSettings().setFlying(true);
            this.teleport(new Vector3(this.x, this.y + 0.1, this.z));

            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(containerSetContentPacket);
        } else {
            if (this.isSurvival()) {
                this.getAdventureSettings().setFlying(false);
            }
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            containerSetContentPacket.slots = Item.getCreativeItems().stream().toArray(Item[]::new);
            this.dataPacket(containerSetContentPacket);
        }

        this.resetFallDistance();

        this.inventory.sendContents(this);
        this.inventory.sendContents(this.getViewers().values());
        this.inventory.sendHeldItem(this.hasSpawned.values());

        return true;
    }

    @Deprecated
    public void sendSettings() {
        this.getAdventureSettings().update();
    }

    public boolean isSurvival() {
        return (this.gamemode & 0x01) == 0;
    }

    public boolean isCreative() {
        return (this.gamemode & 0x01) > 0;
    }

    public boolean isSpectator() {
        return this.gamemode == 3;
    }

    public boolean isAdventure() {
        return (this.gamemode & 0x02) > 0;
    }

    @Override
    public Item[] getDrops() {
        if (!this.isCreative()) {
            return super.getDrops();
        }

        return new Item[0];
    }

    @Override
    public boolean setDataProperty(EntityData data) {
        return setDataProperty(data, true);
    }

    @Override
    public boolean setDataProperty(EntityData data, boolean send) {
        if (super.setDataProperty(data, send)) {
            if (send) this.sendData(this, new EntityMetadata().put(this.getDataProperty(data.getId())));
            return true;
        }
        return false;
    }

    @Override
    protected synchronized void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (!this.onGround || movX != 0 || movY != 0 || movZ != 0) {
            boolean onGround = false;

            AxisAlignedBB bb = this.boundingBox.clone();
            bb.maxY = bb.minY + 0.5;
            bb.minY -= 1;

            AxisAlignedBB realBB = this.boundingBox.clone();
            realBB.maxY = realBB.minY + 0.1;
            realBB.minY -= 0.2;

            int minX = NukkitMath.floorDouble(bb.minX);
            int minY = NukkitMath.floorDouble(bb.minY);
            int minZ = NukkitMath.floorDouble(bb.minZ);
            int maxX = NukkitMath.ceilDouble(bb.maxX);
            int maxY = NukkitMath.ceilDouble(bb.maxY);
            int maxZ = NukkitMath.ceilDouble(bb.maxZ);

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.level.getBlock(x, y, z);
                        if (!block.canPassThrough() && block.collidesWithBB(realBB)) {
                            onGround = true;
                            break;
                        }
                    }
                }
            }
            this.onGround = onGround;
        }

        this.isCollided = this.onGround;
    }

    @Override
    protected void checkBlockCollision() {
        for (Block block : this.getBlocksAround()) {
            block.onEntityCollide(this);
        }
    }

    protected synchronized void checkNearEntities() {
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(1, 0.5, 1), this)) {
            entity.scheduleUpdate();

            if (!entity.isAlive() || !this.isAlive()) {
                continue;
            }

            if (entity instanceof EntityArrow && ((EntityArrow) entity).hadCollision) {
                ItemArrow item = new ItemArrow();
                synchronized (inventory) {
                    if (this.isSurvival() && !this.inventory.canAddItem(item)) {
                        continue;
                    }

                    InventoryPickupArrowEvent ev;
                    this.server.getPluginManager().callEvent(ev = new InventoryPickupArrowEvent(this.inventory, (EntityArrow) entity));
                    if (ev.isCancelled()) {
                        continue;
                    }
                    this.inventory.addItem(item.clone());
                }
                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);

                pk = new TakeItemEntityPacket();
                pk.entityId = 0;
                pk.target = entity.getId();
                this.dataPacket(pk);
                entity.kill();
            } else if (entity instanceof EntityItem) {
                if (((EntityItem) entity).getPickupDelay() <= 0) {
                    Item item = ((EntityItem) entity).getItem();

                    if (item != null) {
                        synchronized (inventory) {
                            if (this.isSurvival() && !this.inventory.canAddItem(item)) {
                                continue;
                            }
                            InventoryPickupItemEvent ev;
                            this.server.getPluginManager().callEvent(ev = new InventoryPickupItemEvent(this.inventory, (EntityItem) entity));
                            if (ev.isCancelled()) {
                                continue;
                            }
                            this.inventory.addItem(item.clone());
                        }

                        //todo: achievement
                        /*switch (item.getId()) {
                            case Item.WOOD:
                                this.awardAchievement("mineWood");
                                break;
                            case Item.DIAMOND:
                                this.awardAchievement("diamond");
                                break;
                        }*/

                        TakeItemEntityPacket pk = new TakeItemEntityPacket();
                        pk.entityId = this.getId();
                        pk.target = entity.getId();
                        Server.broadcastPacket(entity.getViewers().values(), pk);

                        pk = new TakeItemEntityPacket();
                        pk.entityId = 0;
                        pk.target = entity.getId();
                        this.dataPacket(pk);
                        entity.kill();
                    }
                }
            }
        }
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(3.5, 2, 3.5), this)) {
            entity.scheduleUpdate();

            if (!entity.isAlive() || !this.isAlive()) {
                continue;
            }
            if (entity instanceof EntityXPOrb) {
                EntityXPOrb xpOrb = (EntityXPOrb) entity;
                if (xpOrb.getPickupDelay() <= 0) {
                    int exp = xpOrb.getExp();
                    this.addExperience(exp);
                    entity.kill();
                    this.getLevel().addSound(new ExperienceOrbSound(this));
                    break;
                }
            }
        }
    }

    protected synchronized void processMovement(int tickDiff) {
        if (!this.isAlive() || !this.spawned || this.newPosition == null || this.teleportPosition != null) {
            return;
        }
        Vector3 newPos = this.newPosition;
        double distanceSquared = newPos.distanceSquared(this);
        boolean revert = false;
        if ((distanceSquared / ((double) (tickDiff * tickDiff))) > 100 && (newPos.y - this.y) > -5) {
            revert = true;
        } else {
            if (this.chunk == null || !this.chunk.isGenerated()) {
                BaseFullChunk chunk = this.level.getChunk((int) newPos.x >> 4, (int) newPos.z >> 4, false);
                if (chunk == null || !chunk.isGenerated()) {
                    revert = true;
                    this.nextChunkOrderRun = 0;
                } else {
                    if (this.chunk != null) {
                        this.chunk.removeEntity(this);
                    }
                    this.chunk = chunk;
                }
            }
        }

        double tdx = newPos.x - this.x;
        double tdz = newPos.z - this.z;
        double distance = Math.sqrt(tdx * tdx + tdz * tdz);

        if (!revert && distanceSquared != 0) {
            double dx = newPos.x - this.x;
            double dy = newPos.y - this.y;
            double dz = newPos.z - this.z;

            this.fastMove(dx, dy, dz);

            double diffX = this.x - newPos.x;
            double diffY = this.y - newPos.y;
            double diffZ = this.z - newPos.z;

            double yS = 0.5 + this.ySize;
            if (diffY >= -yS || diffY <= yS) {
                diffY = 0;
            }

            if (diffX != 0 || diffY != 0 || diffZ != 0) {
                if (this.checkMovement && !server.getAllowFlight() && this.isSurvival()) {
                    if (!this.isSleeping()) {
                        double diffHorizontalSqr = (diffX * diffX + diffZ * diffZ) / ((double) (tickDiff * tickDiff));
                        if (diffHorizontalSqr > 0.125) {
                            PlayerInvalidMoveEvent ev;
                            this.getServer().getPluginManager().callEvent(ev = new PlayerInvalidMoveEvent(this, true));
                            if (!ev.isCancelled()) {
                                revert = ev.isRevert();

                                if (revert) {
                                    this.server.getLogger().warning(this.getServer().getLanguage().translateString("nukkit.player.invalidMove", this.getName()));
                                }
                            }
                        }
                    }
                }


                this.x = newPos.x;
                this.y = newPos.y;
                this.z = newPos.z;
                double radius = this.getWidth() / 2;
                this.boundingBox.setBounds(this.x - radius, this.y, this.z - radius, this.x + radius, this.y + this.getHeight(), this.z + radius);
            }
        }

        Location from = new Location(
                this.lastX,
                this.lastY,
                this.lastZ,
                this.lastYaw,
                this.lastPitch,
                this.level);
        Location to = this.getLocation();

        if (!revert && (Math.pow(this.lastX - to.x, 2) + Math.pow(this.lastY - to.y, 2) + Math.pow(this.lastZ - to.z, 2)) > (1d / 16d) || (Math.abs(this.lastYaw - to.yaw) + Math.abs(this.lastPitch - to.pitch)) > 10) {
            boolean isFirst = this.firstMove;

            this.firstMove = false;
            this.lastX = to.x;
            this.lastY = to.y;
            this.lastZ = to.z;

            this.lastYaw = to.yaw;
            this.lastPitch = to.pitch;

            if (!isFirst) {
                PlayerMoveEvent ev = new PlayerMoveEvent(this, from, to);

                this.server.getPluginManager().callEvent(ev);

                if (!(revert = ev.isCancelled())) { //Yes, this is intended
                    if (!to.equals(ev.getTo())) { //If plugins modify the destination
                        this.teleport(ev.getTo(), null);
                    } else {
                        this.addMovement(this.x, this.y + this.getEyeHeight(), this.z, this.yaw, this.pitch, this.yaw);
                    }
                }
            }

            if (!this.isSpectator()) {
                this.checkNearEntities();
            }
            if (this.speed == null) speed = new Vector3(from.x - to.x, from.y - to.y, from.z - to.z);
            else this.speed.setComponents(from.x - to.x, from.y - to.y, from.z - to.z);
        } else {
            if (this.speed == null) speed = new Vector3(0, 0, 0);
            else this.speed.setComponents(0, 0, 0);
        }

        if (!revert && (this.isFoodEnabled() || this.getServer().getDifficulty() == 0)) {
            if ((this.isSurvival() || this.isAdventure())/* && !this.getRiddingOn() instanceof Entity*/) {

                //UpdateFoodExpLevel
                if (distance >= 0.05) {
                    double jump = 0;
                    double swimming = this.isInsideOfWater() ? 0.015 * distance : 0;
                    if (swimming != 0) distance = 0;
                    if (this.isSprinting()) {  //Running
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.7;
                        }
                        this.getFoodData().updateFoodExpLevel(0.1 * distance + jump + swimming);
                    } else {
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.2;
                        }
                        this.getFoodData().updateFoodExpLevel(0.01 * distance + jump + swimming);
                    }
                }
            }
        }

        if (revert) {

            this.lastX = from.x;
            this.lastY = from.y;
            this.lastZ = from.z;

            this.lastYaw = from.yaw;
            this.lastPitch = from.pitch;

            this.sendPosition(from, from.yaw, from.pitch, MovePlayerPacket.MODE_RESET);
            //this.sendSettings();
            this.forceMovement = new Vector3(from.x, from.y, from.z);
        } else {
            this.forceMovement = null;
            if (distanceSquared != 0 && this.nextChunkOrderRun > 20) {
                this.nextChunkOrderRun = 20;
            }
        }

        this.newPosition = null;
    }

    @Override
    public synchronized boolean setMotion(Vector3 motion) {
        if (super.setMotion(motion)) {
            if (this.chunk != null) {
                this.getLevel().addEntityMotion(this.chunk.getX(), this.chunk.getZ(), this.getId(), this.motionX, this.motionY, this.motionZ);  //Send to others
                SetEntityMotionPacket pk = new SetEntityMotionPacket();
                pk.eid = 0;
                pk.motionX = (float) motion.x;
                pk.motionY = (float) motion.y;
                pk.motionZ = (float) motion.z;
                this.dataPacket(pk);  //Send to self
            }

            if (this.motionY > 0) {
                //todo: check this
                this.startAirTicks = (int) ((-(Math.log(this.getGravity() / (this.getGravity() + this.getDrag() * this.motionY))) / this.getDrag()) * 2 + 5);
            }

            return true;
        }

        return false;
    }

    @Override
    public synchronized void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.level.addPlayerMovement(this.chunk.getX(), this.chunk.getZ(), this.id, x, y, z, yaw, pitch, this.isOnGround());
    }

    public void sendAttributes() {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entityId = 0;
        pk.entries = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0),
                Attribute.getAttribute(Attribute.MAX_HUNGER).setValue(this.getFoodData().getLevel()),
                Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(this.getMovementSpeed()),
                Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(this.getExperienceLevel()),
                Attribute.getAttribute(Attribute.EXPERIENCE).setValue(((float) this.getExperience()) / calculateRequireExperience(this.getExperienceLevel()))
        };
        this.dataPacket(pk);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.loggedIn) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return true;
        }

        this.messageCounter = 2;

        this.lastUpdate = currentTick;

        if (!this.isAlive() && this.spawned) {
            ++this.deadTicks;
            if (this.deadTicks >= 10) {
                this.despawnFromAll();
            }
            return true;
        }

        if (this.spawned) {
            this.processMovement(tickDiff);

            this.entityBaseTick(tickDiff);

            if (this.isOnFire() && this.lastUpdate % 10 == 0) {
                if (this.isCreative() && !this.isInsideOfFire()) {
                    this.extinguish();
                } else if (this.getLevel().isRaining()) {
                    if (this.getLevel().canBlockSeeSky(this)) {
                        this.extinguish();
                    }
                }
            }

            if (!this.isSpectator() && this.speed != null) {
                if (this.onGround) {
                    if (this.inAirTicks != 0) {
                        this.startAirTicks = 5;
                    }
                    this.inAirTicks = 0;
                    this.highestPosition = this.y;
                } else {
                    if (!server.getAllowFlight() && !this.getAdventureSettings().canFly() && this.inAirTicks > 10 && !this.isSleeping() && !this.isImmobile()) {
                        double expectedVelocity = (-this.getGravity()) / ((double) this.getDrag()) - ((-this.getGravity()) / ((double) this.getDrag())) * Math.exp(-((double) this.getDrag()) * ((double) (this.inAirTicks - this.startAirTicks)));
                        double diff = (this.speed.y - expectedVelocity) * (this.speed.y - expectedVelocity);

                        if (!this.hasEffect(Effect.JUMP) && diff > 0.6 && expectedVelocity < this.speed.y) {
                            if (this.inAirTicks < 100) {
                                //this.sendSettings();
                                this.setMotion(new Vector3(0, expectedVelocity, 0));
                            } else if (this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server")) {
                                return false;
                            }
                        }
                    }

                    if (this.y > highestPosition) {
                        this.highestPosition = this.y;
                    }

                    ++this.inAirTicks;

                }

                if (this.isSurvival() || this.isAdventure()) {
                    if (this.getFoodData() != null) this.getFoodData().update(tickDiff);
                }
            }
        }

        this.checkTeleportPosition();
        return true;
    }

    public synchronized void checkNetwork() {
        if (!this.isOnline()) {
            return;
        }

        if (!this.batchedPackets.isEmpty()) {
            for (int channel : this.batchedPackets.keySet()) {
                this.server.batchPackets(new Player[]{this}, batchedPackets.get(channel).stream().toArray(DataPacket[]::new), false);
            }
            this.batchedPackets.clear();
        }

        if (this.nextChunkOrderRun-- <= 0 || this.chunk == null) {
            this.orderChunks();
        }

        if (!this.loadQueue.isEmpty() || !this.spawned) {
            this.sendNextChunk();
        }
    }

    public boolean canInteract(Vector3 pos, double maxDistance) {
        return this.canInteract(pos, maxDistance, 0.5);
    }

    public boolean canInteract(Vector3 pos, double maxDistance, double maxDiff) {
        if (this.distanceSquared(pos) > maxDistance * maxDistance) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        double dot = dV.dot(new Vector2(this.x, this.z));
        double dot1 = dV.dot(new Vector2(pos.x, pos.z));
        return (dot1 - dot) >= -maxDiff;
    }

    public void onPlayerPreLogin() {
        //TODO: AUTHENTICATE
        this.tryAuthenticate();
    }

    public void tryAuthenticate() {
        PlayStatusPacket pk = new PlayStatusPacket();
        pk.status = PlayStatusPacket.LOGIN_SUCCESS;
        this.dataPacket(pk);
        this.authenticateCallback(true);
    }

    public void authenticateCallback(boolean valid) {
        //TODO add more stuff after authentication is available

        if (!valid) {
            this.close("", "disconnectionScreen.invalidSession");
            return;
        }

        this.processLogin();
    }

    protected synchronized void processLogin() {
        if (!this.server.isWhitelisted((this.getName()).toLowerCase())) {
            this.kick(PlayerKickEvent.Reason.NOT_WHITELISTED, "Server is white-listed");

            return;
        } else if (this.server.getNameBans().isBanned(this.getName().toLowerCase())) {
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned");
            return;
        } else if (this.server.getIPBans().isBanned(this.getAddress())) {
            this.kick(PlayerKickEvent.Reason.IP_BANNED, "You are banned");
            return;
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }
        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        for (Player p : new ArrayList<>(this.server.getOnlinePlayers().values())) {
            if (p != this && p.getName() != null && p.getName().equalsIgnoreCase(this.getName())) {
                if (!p.kick(PlayerKickEvent.Reason.NEW_CONNECTION, "logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Already connected");
                    return;
                }
            } else if (p.loggedIn && this.getUniqueId().equals(p.getUniqueId())) {
                if (!p.kick(PlayerKickEvent.Reason.NEW_CONNECTION, "logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Already connected");
                    return;
                }
            }
        }

        CompoundTag nbt = this.server.getOfflinePlayerData(this.username);
        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");

            return;
        }

        this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

        boolean alive = true;

        nbt.putString("NameTag", this.username);

        if (0 >= nbt.getShort("Health")) {
            alive = false;
        }

        int exp = nbt.getInt("EXP");
        int expLevel = nbt.getInt("expLevel");
        this.setExperience(exp, expLevel);

        this.gamemode = nbt.getInt("playerGameType") & 0x03;
        if (this.server.getForceGamemode()) {
            this.gamemode = this.server.getGamemode();
            nbt.putInt("playerGameType", this.gamemode);
        }

        this.adventureSettings = new AdventureSettings.Builder(this)
                .canDestroyBlock(!isAdventure())
                .autoJump(true)
                .canFly(isCreative())
                .noclip(isSpectator())
                .build();

        Level level;
        if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null || !alive) {
            this.setLevel(this.server.getDefaultLevel());
            nbt.putString("Level", this.level.getName());
            nbt.getList("Pos", DoubleTag.class)
                    .add(new DoubleTag("0", this.level.getSpawnLocation().x))
                    .add(new DoubleTag("1", this.level.getSpawnLocation().y))
                    .add(new DoubleTag("2", this.level.getSpawnLocation().z));
        } else {
            this.setLevel(level);
        }

        //todo achievement
        nbt.putLong("lastPlayed", System.currentTimeMillis() / 1000);

        if (this.server.getAutoSave()) {
            this.server.saveOfflinePlayerData(this.username, nbt, true);
        }

        ListTag<DoubleTag> posList = nbt.getList("Pos", DoubleTag.class);

        super.init(this.level.getChunk((int) posList.get(0).data >> 4, (int) posList.get(2).data >> 4, true), nbt);

        if (!this.namedTag.contains("foodLevel")) {
            this.namedTag.putInt("foodLevel", 20);
        }
        int foodLevel = this.namedTag.getInt("foodLevel");
        if (!this.namedTag.contains("FoodSaturationLevel")) {
            this.namedTag.putFloat("FoodSaturationLevel", 20);
        }
        float foodSaturationLevel = this.namedTag.getFloat("foodSaturationLevel");
        this.foodData = new PlayerFood(this, foodLevel, foodSaturationLevel);

        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());

            return;
        }

        this.server.addOnlinePlayer(this);
        this.loggedIn = true;

        if (this.isCreative()) {
            this.inventory.setHeldItemSlot(0);
        } else {
            this.inventory.setHeldItemSlot(this.inventory.getHotbarSlotIndex(0));
        }

        if (this.isSpectator()) this.keepMovement = true;

        PlayStatusPacket statusPacket = new PlayStatusPacket();
        statusPacket.status = PlayStatusPacket.LOGIN_SUCCESS;
        this.dataPacket(statusPacket);

        this.dataPacket(new ResourcePacksInfoPacket());

        if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
            this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
        }

        Position spawnPosition = this.getSpawn();

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.entityUniqueId = 0;
        startGamePacket.entityRuntimeId = 0;
        startGamePacket.x = (float) this.x;
        startGamePacket.y = (float) this.y;
        startGamePacket.z = (float) this.z;
        startGamePacket.seed = -1;
        startGamePacket.dimension = (byte) (this.level.getDimension() & 0xff);
        startGamePacket.gamemode = this.gamemode & 0x01;
        startGamePacket.difficulty = this.server.getDifficulty();
        startGamePacket.spawnX = (int) spawnPosition.x;
        startGamePacket.spawnY = (int) spawnPosition.y;
        startGamePacket.spawnZ = (int) spawnPosition.z;
        startGamePacket.hasAchievementsDisabled = true;
        startGamePacket.dayCycleStopTime = -1;
        startGamePacket.eduMode = false;
        startGamePacket.rainLevel = 0;
        startGamePacket.lightningLevel = 0;
        startGamePacket.commandsEnabled = true;
        startGamePacket.levelId = "";
        startGamePacket.worldName = this.level.getName();
        startGamePacket.generator = 1; //0 old, 1 infinite, 2 flat
        this.dataPacket(startGamePacket);

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        setTimePacket.started = !this.level.stopTime;
        this.dataPacket(setTimePacket);

        this.setMovementSpeed(DEFAULT_SPEED);
        this.sendAttributes();
        this.setNameTagVisible(true);
        this.setNameTagAlwaysVisible(true);

        this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logIn", new String[]{
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.ip,
                String.valueOf(this.port),
                String.valueOf(this.id),
                this.level.getName(),
                String.valueOf(NukkitMath.round(this.x, 4)),
                String.valueOf(NukkitMath.round(this.y, 4)),
                String.valueOf(NukkitMath.round(this.z, 4))
        }));

        if (this.isOp()) {
            this.setRemoveFormat(false);
        }

        if (this.gamemode == Player.SPECTATOR) {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(containerSetContentPacket);
        } else {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            containerSetContentPacket.slots = Item.getCreativeItems().stream().toArray(Item[]::new);
            this.dataPacket(containerSetContentPacket);
        }

        this.setEnableClientCommand(true);

        this.server.sendFullPlayerListData(this);

        this.forceMovement = this.teleportPosition = this.getPosition();

        this.server.onPlayerLogin(this);
    }

    public synchronized void handleDataPacket(DataPacket packet) {
        if (!connected) {
            return;
        }

        try (Timing timing = Timings.getReceiveDataPacketTiming(packet)) {
            DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                timing.stopTiming();
                return;
            }

            if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
                timing.stopTiming();
                this.server.getNetwork().processBatch((BatchPacket) packet, this);
                return;
            }

            packetswitch:
            switch (packet.pid()) {
                case ProtocolInfo.LOGIN_PACKET:
                    if (this.loggedIn) {
                        break;
                    }

                    LoginPacket loginPacket = (LoginPacket) packet;

                    String message;
                    if (loginPacket.getProtocol() != ProtocolInfo.CURRENT_PROTOCOL) {
                        if (loginPacket.getProtocol() < ProtocolInfo.CURRENT_PROTOCOL) {
                            message = "disconnectionScreen.outdatedClient";

                            PlayStatusPacket pk = new PlayStatusPacket();
                            pk.status = PlayStatusPacket.LOGIN_FAILED_CLIENT;
                            this.directDataPacket(pk);
                        } else {
                            message = "disconnectionScreen.outdatedServer";

                            PlayStatusPacket pk = new PlayStatusPacket();
                            pk.status = PlayStatusPacket.LOGIN_FAILED_SERVER;
                            this.directDataPacket(pk);
                        }
                        this.close("", message, false);
                        break;
                    }

                    this.username = TextFormat.clean(loginPacket.username);
                    this.displayName = this.username;
                    this.iusername = this.username.toLowerCase();
                    this.setDataProperty(new StringEntityData(DATA_NAMETAG, this.username), false);

                    if (this.server.getOnlinePlayers().size() >= this.server.getMaxPlayers() && this.kick(PlayerKickEvent.Reason.SERVER_FULL, "disconnectionScreen.serverFull", false)) {
                        break;
                    }

                    this.randomClientId = loginPacket.clientId;

                    this.uuid = loginPacket.clientUUID;
                    this.rawUUID = Binary.writeUUID(this.uuid);

                    boolean valid = true;
                    int len = loginPacket.username.length();
                    if (len > 16 || len < 3) {
                        valid = false;
                    }

                    for (int i = 0; i < len && valid; i++) {
                        char c = loginPacket.username.charAt(i);
                        if ((c >= 'a' && c <= 'z') ||
                                (c >= 'A' && c <= 'Z') ||
                                (c >= '0' && c <= '9') ||
                                c == '_' || c == ' '
                                ) {
                            continue;
                        }

                        valid = false;
                        break;
                    }

                    if (!valid || Objects.equals(this.iusername, "rcon") || Objects.equals(this.iusername, "console")) {
                        this.close("", "disconnectionScreen.invalidName");

                        break;
                    }

                    if (!loginPacket.skin.isValid()) {
                        this.close("", "disconnectionScreen.invalidSkin");
                        break;
                    } else {
                        this.setSkin(loginPacket.getSkin());
                    }

                    PlayerPreLoginEvent playerPreLoginEvent;
                    this.server.getPluginManager().callEvent(playerPreLoginEvent = new PlayerPreLoginEvent(this, "Plugin reason"));
                    if (playerPreLoginEvent.isCancelled()) {
                        this.close("", playerPreLoginEvent.getKickMessage());

                        break;
                    }

                    this.onPlayerPreLogin();

                    break;
                case ProtocolInfo.MOVE_PLAYER_PACKET:
                    if (this.teleportPosition != null) {
                        break;
                    }

                    MovePlayerPacket movePlayerPacket = (MovePlayerPacket) packet;
                    Vector3 newPos = new Vector3(movePlayerPacket.x, movePlayerPacket.y - this.getEyeHeight(), movePlayerPacket.z);

                    boolean revert = false;
                    if (!this.isAlive() || !this.spawned) {
                        revert = true;
                        this.forceMovement = new Vector3(this.x, this.y, this.z);
                    }

                    if (this.forceMovement != null && (newPos.distanceSquared(this.forceMovement) > 0.1 || revert)) {
                        this.sendPosition(this.forceMovement, movePlayerPacket.yaw, movePlayerPacket.pitch);
                    } else {

                        movePlayerPacket.yaw %= 360;
                        movePlayerPacket.pitch %= 360;

                        if (movePlayerPacket.yaw < 0) {
                            movePlayerPacket.yaw += 360;
                        }

                        this.setRotation(movePlayerPacket.yaw, movePlayerPacket.pitch);
                        this.newPosition = newPos;
                        this.forceMovement = null;
                    }

                    if (riding != null) {
                        if (riding instanceof EntityBoat) {
                            riding.setPositionAndRotation(null, movePlayerPacket.x, movePlayerPacket.y - 1, movePlayerPacket.z, (movePlayerPacket.headYaw + 90) % 360, 0);
                        }
                    }

                    break;
                case ProtocolInfo.ADVENTURE_SETTINGS_PACKET:
                    //TODO: player abilities, check for other changes
                    AdventureSettingsPacket adventureSettingsPacket = (AdventureSettingsPacket) packet;
                    if (adventureSettingsPacket.isFlying && !this.getAdventureSettings().canFly()) {
                        this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                        break;
                    }
                    PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(this, adventureSettingsPacket.isFlying);
                    this.server.getPluginManager().callEvent(playerToggleFlightEvent);
                    if (playerToggleFlightEvent.isCancelled()) {
                        this.getAdventureSettings().update();
                    } else {
                        this.getAdventureSettings().setFlying(playerToggleFlightEvent.isFlying());
                    }
                    break;
                case ProtocolInfo.MOB_EQUIPMENT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    MobEquipmentPacket mobEquipmentPacket = (MobEquipmentPacket) packet;

                    if (mobEquipmentPacket.slot == 0x28 || mobEquipmentPacket.slot == 0 || mobEquipmentPacket.slot == 255) {
                        mobEquipmentPacket.slot = -1;
                    } else {
                        mobEquipmentPacket.slot -= 9;
                    }

                    Item item;
                    int slot;
                    if (this.isCreative()) {
                        item = mobEquipmentPacket.item;
                        slot = Item.getCreativeItemIndex(item);
                    } else {
                        item = this.inventory.getItem(mobEquipmentPacket.slot);
                        slot = mobEquipmentPacket.slot;
                    }

                    if (mobEquipmentPacket.slot == -1) {
                        if (this.isCreative()) {
                            boolean found = false;
                            for (int i = 0; i < this.inventory.getHotbarSize(); ++i) {
                                if (this.inventory.getHotbarSlotIndex(i) == -1) {
                                    this.inventory.setHeldItemIndex(i);
                                    found = true;
                                    break;
                                }

                            }
                            if (!found) {
                                this.inventory.sendContents(this);
                                break;
                            }
                        } else {
                            if (mobEquipmentPacket.selectedSlot >= 0 && mobEquipmentPacket.selectedSlot < 9) {
                                this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                                this.inventory.setHeldItemSlot(mobEquipmentPacket.slot);
                            } else {
                                this.inventory.sendContents(this);
                                break;
                            }
                        }
                    } else if (item == null || slot == -1 || !item.deepEquals(mobEquipmentPacket.item)) {
                        this.inventory.sendContents(this);
                        break;
                    } else if (this.isCreative()) {
                        this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                        this.inventory.setItem(mobEquipmentPacket.selectedSlot, item);
                        this.inventory.setHeldItemSlot(mobEquipmentPacket.selectedSlot);
                    } else {
                        if (mobEquipmentPacket.selectedSlot >= 0 && mobEquipmentPacket.selectedSlot < this.inventory.getHotbarSize()) {
                            this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                            this.inventory.setHeldItemSlot(slot);
                        } else {
                            this.inventory.sendContents(this);
                            break;
                        }
                    }

                    this.inventory.sendHeldItem(this.hasSpawned.values());

                    this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
                    break;
                case ProtocolInfo.USE_ITEM_PACKET:
                    if (!this.spawned || !this.isAlive() || this.blocked) {
                        break;
                    }

                    UseItemPacket useItemPacket = (UseItemPacket) packet;

                    Vector3 blockVector = new Vector3(useItemPacket.x, useItemPacket.y, useItemPacket.z);

                    this.craftingType = CRAFTING_SMALL;

                    if (useItemPacket.face >= 0 && useItemPacket.face <= 5) {
                        this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);

                        if (!this.canInteract(blockVector.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 7)) {
                        } else if (this.isCreative()) {
                            Item i = this.inventory.getItemInHand();
                            if (this.level.useItemOn(blockVector, i, useItemPacket.face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, this) != null) {
                                break;
                            }
                        } else if (!this.inventory.getItemInHand().deepEquals(useItemPacket.item)) {
                            this.inventory.sendHeldItem(this);
                        } else {
                            item = this.inventory.getItemInHand();
                            Item oldItem = item.clone();
                            //TODO: Implement adventure mode checks
                            if ((item = this.level.useItemOn(blockVector, item, useItemPacket.face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, this)) != null) {
                                if (!item.deepEquals(oldItem) || item.getCount() != oldItem.getCount()) {
                                    this.inventory.setItemInHand(item);
                                    this.inventory.sendHeldItem(this.hasSpawned.values());
                                }
                                break;
                            }
                        }

                        this.inventory.sendHeldItem(this);

                        if (blockVector.distanceSquared(this) > 10000) {
                            break;
                        }

                        Block target = this.level.getBlock(blockVector);
                        Block block = target.getSide(useItemPacket.face);

                        if (target instanceof BlockDoor) {
                            BlockDoor door = (BlockDoor) target;

                            Block part;

                            if ((door.getDamage() & 0x08) > 0) { //up
                                part = target.getSide(Vector3.SIDE_DOWN);

                                if (part.getId() == target.getId()) {
                                    target = part;
                                }
                            }
                        }

                        this.level.sendBlocks(new Player[]{this}, new Block[]{target, block}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
                        break;
                    } else if (useItemPacket.face == -1) {
                        Vector3 aimPos = new Vector3(
                                -Math.sin(this.yaw / 180d * Math.PI) * Math.cos(this.pitch / 180d * Math.PI),
                                -Math.sin(this.pitch / 180d * Math.PI),
                                Math.cos(this.yaw / 180d * Math.PI) * Math.cos(this.pitch / 180d * Math.PI)
                        );

                        if (this.isCreative()) {
                            item = this.inventory.getItemInHand();
                        } else if (!this.inventory.getItemInHand().deepEquals(useItemPacket.item)) {
                            this.inventory.sendHeldItem(this);
                            break;
                        } else {
                            item = this.inventory.getItemInHand();
                        }

                        PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, item, aimPos, useItemPacket.face, PlayerInteractEvent.RIGHT_CLICK_AIR);

                        this.server.getPluginManager().callEvent(playerInteractEvent);

                        if (playerInteractEvent.isCancelled()) {
                            this.inventory.sendHeldItem(this);
                            break;
                        }

                        if (item.getId() == Item.SNOWBALL) {
                            CompoundTag nbt = new CompoundTag()
                                    .putList(new ListTag<DoubleTag>("Pos")
                                            .add(new DoubleTag("", x))
                                            .add(new DoubleTag("", y + this.getEyeHeight()))
                                            .add(new DoubleTag("", z)))
                                    .putList(new ListTag<DoubleTag>("Motion")
                                       /* .add(new DoubleTag("", aimPos.x))
                                        .add(new DoubleTag("", aimPos.y))
                                        .add(new DoubleTag("", aimPos.z)))*/
                                            .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                    .putList(new ListTag<FloatTag>("Rotation")
                                            .add(new FloatTag("", (float) yaw))
                                            .add(new FloatTag("", (float) pitch)));

                            float f = 1.5f;
                            EntitySnowball snowball = new EntitySnowball(this.chunk, nbt, this);

                            snowball.setMotion(snowball.getMotion().multiply(f));
                            if (this.isSurvival()) {
                                item.setCount(item.getCount() - 1);
                                this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                            }
                            if (snowball instanceof EntityProjectile) {
                                ProjectileLaunchEvent projectileLaunchEvent = new ProjectileLaunchEvent(snowball);
                                this.server.getPluginManager().callEvent(projectileLaunchEvent);
                                if (projectileLaunchEvent.isCancelled()) {
                                    snowball.kill();
                                } else {
                                    snowball.spawnToAll();
                                    this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                }
                            } else {
                                snowball.spawnToAll();
                            }
                        } else if (item.getId() == Item.EGG) {
                            CompoundTag nbt = new CompoundTag()
                                    .putList(new ListTag<DoubleTag>("Pos")
                                            .add(new DoubleTag("", x))
                                            .add(new DoubleTag("", y + this.getEyeHeight()))
                                            .add(new DoubleTag("", z)))
                                    .putList(new ListTag<DoubleTag>("Motion")
                                       /* .add(new DoubleTag("", aimPos.x))
                                        .add(new DoubleTag("", aimPos.y))
                                        .add(new DoubleTag("", aimPos.z)))*/
                                            .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                    .putList(new ListTag<FloatTag>("Rotation")
                                            .add(new FloatTag("", (float) yaw))
                                            .add(new FloatTag("", (float) pitch)));

                            float f = 1.5f;
                            EntityEgg egg = new EntityEgg(this.chunk, nbt, this);

                            egg.setMotion(egg.getMotion().multiply(f));
                            if (this.isSurvival()) {
                                item.setCount(item.getCount() - 1);
                                this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                            }
                            if (egg instanceof EntityProjectile) {
                                ProjectileLaunchEvent projectileLaunchEvent = new ProjectileLaunchEvent(egg);
                                this.server.getPluginManager().callEvent(projectileLaunchEvent);
                                if (projectileLaunchEvent.isCancelled()) {
                                    egg.kill();
                                } else {
                                    egg.spawnToAll();
                                    this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                }
                            } else {
                                egg.spawnToAll();
                            }
                        } else if (item.getId() == Item.EXPERIENCE_BOTTLE) {
                            CompoundTag nbt = new CompoundTag()
                                    .putList(new ListTag<DoubleTag>("Pos")
                                            .add(new DoubleTag("", x))
                                            .add(new DoubleTag("", y + this.getEyeHeight()))
                                            .add(new DoubleTag("", z)))
                                    .putList(new ListTag<DoubleTag>("Motion")
                                            .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                    .putList(new ListTag<FloatTag>("Rotation")
                                            .add(new FloatTag("", (float) yaw))
                                            .add(new FloatTag("", (float) pitch)))
                                    .putInt("Potion", item.getDamage());
                            double f = 1.5;
                            Entity bottle = new EntityExpBottle(this.chunk, nbt, this);
                            bottle.setMotion(bottle.getMotion().multiply(f));
                            if (this.isSurvival()) {
                                item.setCount(item.getCount() - 1);
                                this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                            }
                            if (bottle instanceof EntityProjectile) {
                                EntityProjectile bottleEntity = (EntityProjectile) bottle;
                                ProjectileLaunchEvent projectileEv = new ProjectileLaunchEvent(bottleEntity);
                                this.server.getPluginManager().callEvent(projectileEv);
                                if (projectileEv.isCancelled()) {
                                    bottle.kill();
                                } else {
                                    bottle.spawnToAll();
                                    this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                }
                            } else {
                                bottle.spawnToAll();
                            }
                        } else if (item.getId() == Item.SPLASH_POTION) {
                            CompoundTag nbt = new CompoundTag()
                                    .putList(new ListTag<DoubleTag>("Pos")
                                            .add(new DoubleTag("", x))
                                            .add(new DoubleTag("", y + this.getEyeHeight()))
                                            .add(new DoubleTag("", z)))
                                    .putList(new ListTag<DoubleTag>("Motion")
                                            .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                            .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                    .putList(new ListTag<FloatTag>("Rotation")
                                            .add(new FloatTag("", (float) yaw))
                                            .add(new FloatTag("", (float) pitch)))
                                    .putShort("PotionId", item.getDamage());
                            double f = 1.5;
                            Entity bottle = new EntityPotion(this.chunk, nbt, this);
                            bottle.setMotion(bottle.getMotion().multiply(f));
                            if (this.isSurvival()) {
                                item.setCount(item.getCount() - 1);
                                this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                            }
                            if (bottle instanceof EntityPotion) {
                                EntityPotion bottleEntity = (EntityPotion) bottle;
                                ProjectileLaunchEvent projectileEv = new ProjectileLaunchEvent(bottleEntity);
                                this.server.getPluginManager().callEvent(projectileEv);
                                if (projectileEv.isCancelled()) {
                                    bottle.kill();
                                } else {
                                    bottle.spawnToAll();
                                    this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                }
                            } else {
                                bottle.spawnToAll();
                            }
                        }

                        this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, true);
                        this.startAction = this.server.getTick();
                    }
                    break;
                case ProtocolInfo.PLAYER_ACTION_PACKET:
                    if (!this.spawned || this.blocked || (!this.isAlive() && ((PlayerActionPacket) packet).action != PlayerActionPacket.ACTION_RESPAWN && ((PlayerActionPacket) packet).action != PlayerActionPacket.ACTION_DIMENSION_CHANGE)) {
                        break;
                    }

                    ((PlayerActionPacket) packet).entityId = this.id;
                    Vector3 pos = new Vector3(((PlayerActionPacket) packet).x, ((PlayerActionPacket) packet).y, ((PlayerActionPacket) packet).z);

                    switch (((PlayerActionPacket) packet).action) {
                        case PlayerActionPacket.ACTION_START_BREAK:
                            if (this.lastBreak != Long.MAX_VALUE || pos.distanceSquared(this) > 10000) {
                                break;
                            }
                            Block target = this.level.getBlock(pos);
                            PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, this.inventory.getItemInHand(), target, ((PlayerActionPacket) packet).face, target.getId() == 0 ? PlayerInteractEvent.LEFT_CLICK_AIR : PlayerInteractEvent.LEFT_CLICK_BLOCK);
                            this.getServer().getPluginManager().callEvent(playerInteractEvent);
                            if (playerInteractEvent.isCancelled()) {
                                this.inventory.sendHeldItem(this);
                                break;
                            }
                            Block block = target.getSide(((PlayerActionPacket) packet).face);
                            if (block.getId() == Block.FIRE) {
                                this.level.setBlock(block, new BlockAir(), true);
                            }
                            this.lastBreak = System.currentTimeMillis();
                            break;

                        case PlayerActionPacket.ACTION_ABORT_BREAK:
                            this.lastBreak = Long.MAX_VALUE;
                            break;

                        case PlayerActionPacket.ACTION_RELEASE_ITEM:
                            if (this.startAction > -1 && this.getDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION)) {
                                if (this.inventory.getItemInHand().getId() == Item.BOW) {

                                    Item bow = this.inventory.getItemInHand();
                                    ItemArrow itemArrow = new ItemArrow();
                                    if (this.isSurvival() && !this.inventory.contains(itemArrow)) {
                                        this.inventory.sendContents(this);
                                        break;
                                    }

                                    double damage = 2;
                                    boolean flame = false;

                                    if (bow.hasEnchantments()) {
                                        Enchantment bowDamage = bow.getEnchantment(Enchantment.ID_BOW_POWER);

                                        if (bowDamage != null && bowDamage.getLevel() > 0) {
                                            damage += 0.25 * (bowDamage.getLevel() + 1);
                                        }

                                        Enchantment flameEnchant = bow.getEnchantment(Enchantment.ID_BOW_FLAME);
                                        flame = flameEnchant != null && flameEnchant.getLevel() > 0;
                                    }

                                    CompoundTag nbt = new CompoundTag()
                                            .putList(new ListTag<DoubleTag>("Pos")
                                                    .add(new DoubleTag("", x))
                                                    .add(new DoubleTag("", y + this.getEyeHeight()))
                                                    .add(new DoubleTag("", z)))
                                            .putList(new ListTag<DoubleTag>("Motion")
                                                    .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                                    .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                                    .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                            .putList(new ListTag<FloatTag>("Rotation")
                                                    .add(new FloatTag("", (float) yaw))
                                                    .add(new FloatTag("", (float) pitch)))
                                            .putShort("Fire", this.isOnFire() || flame ? 45 * 60 : 0)
                                            .putDouble("damage", damage);

                                    int diff = (this.server.getTick() - this.startAction);
                                    double p = (double) diff / 20;

                                    double f = Math.min((p * p + p * 2) / 3, 1) * 2;
                                    EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(this, bow, new EntityArrow(this.chunk, nbt, this, f == 2), f);

                                    if (f < 0.1 || diff < 5) {
                                        entityShootBowEvent.setCancelled();
                                    }

                                    this.server.getPluginManager().callEvent(entityShootBowEvent);
                                    if (entityShootBowEvent.isCancelled()) {
                                        entityShootBowEvent.getProjectile().kill();
                                        this.inventory.sendContents(this);
                                    } else {
                                        entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
                                        if (this.isSurvival()) {
                                            Enchantment infinity = null;

                                            if (!bow.hasEnchantments() || (infinity = bow.getEnchantment(Enchantment.ID_BOW_INFINITY)) == null || infinity.getLevel() <= 0)
                                                this.inventory.removeItem(itemArrow);

                                            if (!bow.isUnbreakable()) {
                                                Enchantment durability = bow.getEnchantment(Enchantment.ID_DURABILITY);
                                                if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= new Random().nextInt(100))) {
                                                    bow.setDamage(bow.getDamage() + 1);
                                                    if (bow.getDamage() >= 385) {
                                                        this.inventory.setItemInHand(new ItemBlock(new BlockAir(), 0, 0));
                                                    } else {
                                                        this.inventory.setItemInHand(bow);
                                                    }
                                                }
                                            }
                                        }
                                        if (entityShootBowEvent.getProjectile() instanceof EntityProjectile) {
                                            ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(entityShootBowEvent.getProjectile());
                                            this.server.getPluginManager().callEvent(projectev);
                                            if (projectev.isCancelled()) {
                                                entityShootBowEvent.getProjectile().kill();
                                            } else {
                                                entityShootBowEvent.getProjectile().spawnToAll();
                                                this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                            }
                                        } else {
                                            entityShootBowEvent.getProjectile().spawnToAll();
                                        }
                                    }
                                }
                            }
                            //milk removed here, see the section of food

                        case PlayerActionPacket.ACTION_STOP_SLEEPING:
                            this.stopSleep();
                            break;

                        case PlayerActionPacket.ACTION_RESPAWN:
                            if (!this.spawned || this.isAlive() || !this.isOnline()) {
                                break;
                            }

                            if (this.server.isHardcore()) {
                                this.setBanned(true);
                                break;
                            }

                            this.craftingType = CRAFTING_SMALL;

                            PlayerRespawnEvent playerRespawnEvent = new PlayerRespawnEvent(this, this.getSpawn());
                            this.server.getPluginManager().callEvent(playerRespawnEvent);

                            this.teleport(playerRespawnEvent.getRespawnPosition(), null);

                            this.setSprinting(false, true);
                            this.setSneaking(false);

                            this.extinguish();
                            this.setDataProperty(new ShortEntityData(Player.DATA_AIR, 400), false);
                            this.deadTicks = 0;
                            this.noDamageTicks = 60;

                            this.setHealth(this.getMaxHealth());
                            this.getFoodData().setLevel(20, 20);

                            this.removeAllEffects();
                            this.sendData(this);

                            this.setMovementSpeed(DEFAULT_SPEED);

                            this.getAdventureSettings().update();
                            this.inventory.sendContents(this);
                            this.inventory.sendArmorContents(this);

                            this.blocked = false;

                            this.spawnToAll();
                            this.scheduleUpdate();
                            break;

                        case PlayerActionPacket.ACTION_START_SPRINT:
                            PlayerToggleSprintEvent playerToggleSprintEvent = new PlayerToggleSprintEvent(this, true);
                            this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                            if (playerToggleSprintEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSprinting(true);
                            }
                            break packetswitch;

                        case PlayerActionPacket.ACTION_STOP_SPRINT:
                            playerToggleSprintEvent = new PlayerToggleSprintEvent(this, false);
                            this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                            if (playerToggleSprintEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSprinting(false);
                            }
                            break packetswitch;

                        case PlayerActionPacket.ACTION_START_SNEAK:
                            PlayerToggleSneakEvent playerToggleSneakEvent = new PlayerToggleSneakEvent(this, true);
                            this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                            if (playerToggleSneakEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSneaking(true);
                            }
                            break packetswitch;

                        case PlayerActionPacket.ACTION_STOP_SNEAK:
                            playerToggleSneakEvent = new PlayerToggleSneakEvent(this, false);
                            this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                            if (playerToggleSneakEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSneaking(false);
                            }
                            break packetswitch;
                    }

                    this.startAction = -1;
                    this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
                    break;
                case ProtocolInfo.REMOVE_BLOCK_PACKET:
                    if (!this.spawned || this.blocked || !this.isAlive()) {
                        break;
                    }
                    this.craftingType = CRAFTING_SMALL;

                    Vector3 vector = new Vector3(((RemoveBlockPacket) packet).x, ((RemoveBlockPacket) packet).y, ((RemoveBlockPacket) packet).z);

                    if (this.isCreative()) {
                        item = this.inventory.getItemInHand();
                    } else {
                        item = this.inventory.getItemInHand();
                    }

                    Item oldItem = item.clone();

                    if (this.canInteract(vector.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 7) && (item = this.level.useBreakOn(vector, item, this, true)) != null) {
                        if (this.isSurvival()) {
                            this.getFoodData().updateFoodExpLevel(0.025);
                            if (!item.deepEquals(oldItem) || item.getCount() != oldItem.getCount()) {
                                this.inventory.setItemInHand(item);
                                this.inventory.sendHeldItem(this.hasSpawned.values());
                            }
                        }
                        break;
                    }

                    this.inventory.sendContents(this);
                    Block target = this.level.getBlock(vector);
                    BlockEntity blockEntity = this.level.getBlockEntity(vector);

                    this.level.sendBlocks(new Player[]{this}, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

                    this.inventory.sendHeldItem(this);

                    if (blockEntity instanceof BlockEntitySpawnable) {
                        ((BlockEntitySpawnable) blockEntity).spawnTo(this);
                    }
                    break;

                case ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET:
                    break;

                case ProtocolInfo.INTERACT_PACKET:
                    if (!this.spawned || !this.isAlive() || this.blocked) {
                        break;
                    }
                    this.craftingType = CRAFTING_SMALL;
                    Entity targetEntity = this.level.getEntity(((InteractPacket) packet).target);
                    boolean cancelled = false;
                    if (targetEntity instanceof Player && !((boolean) this.server.getConfig("pvp", true))) {
                        cancelled = true;
                    }

                    if (((InteractPacket) packet).action == InteractPacket.ACTION_MOUSEOVER) {
                        this.getServer().getPluginManager().callEvent(new PlayerMouseOverEntityEvent(this, targetEntity));
                    } else {
                        if (targetEntity != null && this.isAlive() && targetEntity.isAlive()) {
                            if (this.getGamemode() == Player.VIEW) {
                                cancelled = true;
                            }
                            if (targetEntity instanceof EntityItem || targetEntity instanceof EntityArrow) {
                                this.kick(PlayerKickEvent.Reason.INVALID_PVE, "Attempting to attack an invalid entity");
                                this.server.getLogger().warning(this.getServer().getLanguage().translateString("nukkit.player.invalidEntity", this.getName()));
                                break;
                            }

                            item = this.inventory.getItemInHand();
                            float itemDamage = item.getAttackDamage();

                            for (Enchantment enchantment : item.getEnchantments()) {
                                itemDamage += enchantment.getDamageBonus(targetEntity);
                            }

                            HashMap<Integer, Float> damage = new HashMap<>();
                            damage.put(EntityDamageEvent.MODIFIER_BASE, itemDamage);

                            if (!this.canInteract(targetEntity, isCreative() ? 8 : 5)) {
                                cancelled = true;
                            } else if (targetEntity instanceof Player) {
                                if ((((Player) targetEntity).getGamemode() & 0x01) > 0) {
                                    break;
                                } else if (!this.server.getPropertyBoolean("pvp") || this.server.getDifficulty() == 0) {
                                    cancelled = true;
                                }
                            } else if (targetEntity instanceof EntityVehicle) {
                                SetEntityLinkPacket pk;
                                switch (((InteractPacket) packet).action) {
                                    case InteractPacket.ACTION_RIGHT_CLICK:
                                        cancelled = true;

                                        if (((EntityVehicle) targetEntity).linkedEntity != null) {
                                            break;
                                        }
                                        pk = new SetEntityLinkPacket();
                                        pk.rider = targetEntity.getId();
                                        pk.riding = this.id;
                                        pk.type = 2;
                                        Server.broadcastPacket(this.hasSpawned.values(), pk);

                                        pk = new SetEntityLinkPacket();
                                        pk.rider = targetEntity.getId();
                                        pk.riding = 0;
                                        pk.type = 2;
                                        dataPacket(pk);

                                        riding = targetEntity;
                                        ((EntityVehicle) targetEntity).linkedEntity = this;

                                        this.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
                                        break;
                                    case InteractPacket.ACTION_VEHICLE_EXIT:
                                        pk = new SetEntityLinkPacket();
                                        pk.rider = targetEntity.getId();
                                        pk.riding = this.id;
                                        pk.type = 3;
                                        Server.broadcastPacket(this.hasSpawned.values(), pk);

                                        pk = new SetEntityLinkPacket();
                                        pk.rider = targetEntity.getId();
                                        pk.riding = 0;
                                        pk.type = 3;
                                        dataPacket(pk);

                                        cancelled = true;
                                        riding = null;
                                        ((EntityVehicle) targetEntity).linkedEntity = null;
                                        this.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
                                        break;
                                }
                            }

                            EntityDamageByEntityEvent entityDamageByEntityEvent = new EntityDamageByEntityEvent(this, targetEntity, EntityDamageEvent.CAUSE_ENTITY_ATTACK, damage);
                            if (cancelled) {
                                entityDamageByEntityEvent.setCancelled();
                            }

                            targetEntity.attack(entityDamageByEntityEvent);

                            if (entityDamageByEntityEvent.isCancelled()) {
                                if (item.isTool() && this.isSurvival()) {
                                    this.inventory.sendContents(this);
                                }
                                break;
                            }

                            if (item.isTool() && this.isSurvival()) {
                                if (item.useOn(targetEntity) && item.getDamage() >= item.getMaxDurability()) {
                                    this.inventory.setItemInHand(new ItemBlock(new BlockAir()));
                                } else {
                                    this.inventory.setItemInHand(item);
                                }
                            }
                        }
                    }

                    break;
                case ProtocolInfo.ANIMATE_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    PlayerAnimationEvent animationEvent = new PlayerAnimationEvent(this, ((AnimatePacket) packet).action);
                    this.server.getPluginManager().callEvent(animationEvent);
                    if (animationEvent.isCancelled()) {
                        break;
                    }

                    AnimatePacket animatePacket = new AnimatePacket();
                    animatePacket.eid = this.getId();
                    animatePacket.action = animationEvent.getAnimationType();
                    Server.broadcastPacket(this.getViewers().values(), animatePacket);
                    break;
                case ProtocolInfo.SET_HEALTH_PACKET:
                    //use UpdateAttributePacket instead
                    break;

                case ProtocolInfo.ENTITY_EVENT_PACKET:
                    if (!this.spawned || this.blocked || !this.isAlive()) {
                        break;
                    }
                    this.craftingType = CRAFTING_SMALL;

                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false); //TODO: check if this should be true
                    EntityEventPacket entityEventPacket = (EntityEventPacket) packet;

                    switch (entityEventPacket.event) {
                        case EntityEventPacket.USE_ITEM: //Eating
                            Item itemInHand = this.inventory.getItemInHand();
                            PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(this, itemInHand);
                            this.server.getPluginManager().callEvent(consumeEvent);
                            if (consumeEvent.isCancelled()) {
                                this.inventory.sendContents(this);
                                break;
                            }

                            if (itemInHand.getId() == Item.POTION) {
                                Potion potion = Potion.getPotion(itemInHand.getDamage()).setSplash(false);

                                if (this.getGamemode() == SURVIVAL) {
                                    if (itemInHand.getCount() > 1) {
                                        ItemGlassBottle bottle = new ItemGlassBottle();
                                        this.inventory.addItem(bottle);
                                        --itemInHand.count;
                                    } else {
                                        itemInHand = new ItemGlassBottle();
                                    }
                                }

                                if (potion != null) {
                                    potion.applyPotion(this);
                                }

                            } else {
                                EntityEventPacket pk = new EntityEventPacket();
                                pk.eid = this.getId();
                                pk.event = EntityEventPacket.USE_ITEM;
                                this.dataPacket(pk);
                                Server.broadcastPacket(this.getViewers().values(), pk);

                                Food food = Food.getByRelative(itemInHand);
                                if (food != null) if (food.eatenBy(this)) --itemInHand.count;

                            }

                            this.inventory.setItemInHand(itemInHand);
                            this.inventory.sendHeldItem(this);

                            break;
                    }
                    break;
                case ProtocolInfo.DROP_ITEM_PACKET:
                    if (!this.spawned || this.blocked || !this.isAlive()) {
                        break;
                    }
                    DropItemPacket dropItem = (DropItemPacket) packet;

                    if (dropItem.item.getId() <= 0) {
                        break;
                    }

                    item = (this.isCreative() || this.inventory.contains(dropItem.item)) ? dropItem.item : this.inventory.getItemInHand();
                    PlayerDropItemEvent dropItemEvent = new PlayerDropItemEvent(this, item);
                    this.server.getPluginManager().callEvent(dropItemEvent);
                    if (dropItemEvent.isCancelled()) {
                        this.inventory.sendContents(this);
                        break;
                    }

                    if (!this.isCreative()) {
                        this.inventory.removeItem(item);
                    }
                    Vector3 motion = this.getDirectionVector().multiply(0.4);

                    this.level.dropItem(this.add(0, 1.3, 0), item, motion, 40);

                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);
                    break;
                case ProtocolInfo.COMMAND_STEP_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }
                    this.craftingType = 0;
                    CommandStepPacket commandStepPacket = (CommandStepPacket) packet;
                    String commandText = commandStepPacket.command;
                    Command command = this.getServer().getCommandMap().getCommand(commandText);
                    if (command != null) {
                        if (commandStepPacket.args != null && commandStepPacket.args.size() > 0) {
                            CommandParameter[] pars = command.getCommandParameters(commandStepPacket.overload);
                            if (pars != null) {
                                for (CommandParameter par : pars) {
                                    JsonElement arg = commandStepPacket.args.get(par.name);
                                    if (arg != null) {
                                        switch (par.type) {
                                            case CommandParameter.ARG_TYPE_TARGET:
                                                CommandArg rules = new Gson().fromJson(arg, CommandArg.class);
                                                commandText += " " + rules.getRules()[0].getValue();
                                                break;
                                            case CommandParameter.ARG_TYPE_BLOCK_POS:
                                                CommandArgBlockVector bv = new Gson().fromJson(arg, CommandArgBlockVector.class);
                                                commandText += " " + bv.getX() + " " + bv.getY() + " " + bv.getZ();
                                                break;
                                            case CommandParameter.ARG_TYPE_STRING:
                                            case CommandParameter.ARG_TYPE_STRING_ENUM:
                                            case CommandParameter.ARG_TYPE_RAW_TEXT:
                                                String string = new Gson().fromJson(arg, String.class);
                                                commandText += " " + string;
                                                break;
                                            default:
                                                commandText += " " + arg.toString();
                                                break;
                                        }
                                    }
                                }
                            } else {
                                this.sendMessage(this.getServer().getLanguage().translateString(command.getUsage()));
                            }
                        }
                    }
                    PlayerCommandPreprocessEvent playerCommandPreprocessEvent = new PlayerCommandPreprocessEvent(this, "/" + commandText);
                    this.server.getPluginManager().callEvent(playerCommandPreprocessEvent);
                    if (playerCommandPreprocessEvent.isCancelled()) {
                        break;
                    }

                    Timings.playerCommandTimer.startTiming();
                    this.server.dispatchCommand(playerCommandPreprocessEvent.getPlayer(), playerCommandPreprocessEvent.getMessage().substring(1));
                    Timings.playerCommandTimer.stopTiming();
                    break;
                case ProtocolInfo.TEXT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    this.craftingType = CRAFTING_SMALL;
                    TextPacket textPacket = (TextPacket) packet;

                    if (textPacket.type == TextPacket.TYPE_CHAT) {
                        textPacket.message = this.removeFormat ? TextFormat.clean(textPacket.message) : textPacket.message;
                        for (String msg : textPacket.message.split("\n")) {
                            if (!"".equals(msg.trim()) && msg.length() <= 255 && this.messageCounter-- > 0) {
                                if (msg.startsWith("./")) { //Command
                                    PlayerCommandPreprocessEvent commandPreprocessEvent = new PlayerCommandPreprocessEvent(this, msg);
                                    if (commandPreprocessEvent.getMessage().length() > 320) {
                                        commandPreprocessEvent.setCancelled();
                                    }
                                    this.server.getPluginManager().callEvent(commandPreprocessEvent);
                                    if (commandPreprocessEvent.isCancelled()) {
                                        break;
                                    }
                                    Timings.playerCommandTimer.startTiming();
                                    this.server.dispatchCommand(commandPreprocessEvent.getPlayer(), commandPreprocessEvent.getMessage().substring(2));
                                    Timings.playerCommandTimer.stopTiming();
                                } else { //Chat
                                    PlayerChatEvent chatEvent = new PlayerChatEvent(this, msg);
                                    this.server.getPluginManager().callEvent(chatEvent);
                                    if (!chatEvent.isCancelled()) {
                                        this.server.broadcastMessage(this.getServer().getLanguage().translateString(chatEvent.getFormat(), new String[]{chatEvent.getPlayer().getDisplayName(), chatEvent.getMessage()}), chatEvent.getRecipients());
                                    }
                                }
                            }
                        }
                    }
                    break;
                case ProtocolInfo.CONTAINER_CLOSE_PACKET:
                    ContainerClosePacket containerClosePacket = (ContainerClosePacket) packet;
                    if (!this.spawned || containerClosePacket.windowid == 0) {
                        break;
                    }
                    this.craftingType = CRAFTING_SMALL;
                    this.currentTransaction = null;
                    if (this.windowIndex.containsKey(containerClosePacket.windowid)) {
                        this.server.getPluginManager().callEvent(new InventoryCloseEvent(this.windowIndex.get(containerClosePacket.windowid), this));
                        this.removeWindow(this.windowIndex.get(containerClosePacket.windowid));
                    } else {
                        this.windowIndex.remove(containerClosePacket.windowid);
                    }
                    break;

                case ProtocolInfo.CRAFTING_EVENT_PACKET:
                    CraftingEventPacket craftingEventPacket = (CraftingEventPacket) packet;

                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    Recipe recipe = this.server.getCraftingManager().getRecipe(craftingEventPacket.id);

                    if (this.craftingType == CRAFTING_ANVIL) {
                        Inventory inv = this.windowIndex.get(craftingEventPacket.windowId);
                        AnvilInventory anvilInventory = inv instanceof AnvilInventory ? (AnvilInventory) inv : null;

                        if (anvilInventory == null) {
                            anvilInventory = null;

                            for (Inventory window : this.windowIndex.values()) {
                                if (window instanceof AnvilInventory) {
                                    anvilInventory = (AnvilInventory) window;
                                    break;
                                }
                            }

                            if (anvilInventory == null) { //If it'sf _still_ null, then the player doesn't have a valid anvil window, cannot proceed.
                                this.getServer().getLogger().debug("Couldn't find an anvil window for " + this.getName() + ", exiting");
                                this.inventory.sendContents(this);
                                break;
                            }
                        }

                        if (recipe == null) {
                            //Item renamed

                            if (!anvilInventory.onRename(this, craftingEventPacket.output[0])) {
                                this.getServer().getLogger().debug(this.getName() + " failed to rename an item in an anvil");
                                this.inventory.sendContents(this);
                            }
                        } else {
                            //TODO: Anvil crafting recipes
                        }
                        break;
                    } else if (!this.windowIndex.containsKey(craftingEventPacket.windowId)) {
                        this.inventory.sendContents(this);
                        containerClosePacket = new ContainerClosePacket();
                        containerClosePacket.windowid = craftingEventPacket.windowId;
                        this.dataPacket(containerClosePacket);
                        break;
                    }

                    if ((recipe == null) || (((recipe instanceof BigShapelessRecipe) || (recipe instanceof BigShapedRecipe)) && this.craftingType == CRAFTING_SMALL)) {
                        this.inventory.sendContents(this);
                        break;
                    }

                    for (int i = 0; i < craftingEventPacket.input.length; i++) {
                        Item inputItem = craftingEventPacket.input[i];
                        if (inputItem.getDamage() == -1 || inputItem.getDamage() == 0xffff) {
                            inputItem.setDamage(null);
                        }

                        if (i < 9 && inputItem.getId() > 0) {
                            inputItem.setCount(1);
                        }
                    }

                    boolean canCraft = true;

                    if (craftingEventPacket.input.length == 0) {
                        Recipe[] recipes = getServer().getCraftingManager().getRecipesByResult(craftingEventPacket.output[0]);

                        recipe = null;

                        ArrayList<Item> ingredientz = new ArrayList<>();

                        for (Recipe r : recipes) {
                            if (r instanceof ShapedRecipe) {
                                Map<Integer, Map<Integer, Item>> ingredients = ((ShapedRecipe) r).getIngredientMap();
                                for (Map<Integer, Item> map : ingredients.values()) {
                                    for (Item ingredient : map.values()) {
                                        if (ingredient != null && ingredient.getId() != Item.AIR) {
                                            if (!this.inventory.contains(ingredient)) {
                                                canCraft = false;
                                                break;
                                            }

                                            ingredientz.add(ingredient);
                                            this.inventory.removeItem(ingredient);
                                        }
                                    }
                                }

                                if (canCraft) {
                                    recipe = r;
                                    break;
                                }
                            }
                        }

                        if (recipe != null) {
                            CraftItemEvent craftItemEvent = new CraftItemEvent(this, ingredientz.stream().toArray(Item[]::new), recipe);
                            getServer().getPluginManager().callEvent(craftItemEvent);

                            if (craftItemEvent.isCancelled()) {
                                this.inventory.sendContents(this);
                                break;
                            }

                            this.inventory.addItem(recipe.getResult());
                        } else {
                            this.server.getLogger().debug("(1) Unmatched desktop recipe " + craftingEventPacket.id + " from player " + this.getName());
                            this.inventory.sendContents(this);
                        }
                    } else {

                        if (recipe instanceof ShapedRecipe) {
                            int offsetX = 0;
                            int offsetY = 0;

                            if (this.craftingType == CRAFTING_BIG) {
                                int minX = -1, minY = -1, maxX = 0, maxY = 0;
                                for (int x = 0; x < 3 && canCraft; ++x) {
                                    for (int y = 0; y < 3; ++y) {
                                        Item readItem = craftingEventPacket.input[y * 3 + x];
                                        if (readItem.getId() != Item.AIR) {
                                            if (minY == -1 || minY > y) {
                                                minY = y;
                                            }
                                            if (maxY < y) {
                                                maxY = y;
                                            }
                                            if (minX == -1) {
                                                minX = x;
                                            }
                                            if (maxX < x) {
                                                maxX = x;
                                            }
                                        }
                                    }
                                }
                                if (maxX == minX) {
                                    offsetX = minX;
                                }
                                if (maxY == minY) {
                                    offsetY = minY;
                                }
                            }

                            //To fix some items can't craft
                            for (int x = 0; x < 3 - offsetX && canCraft; ++x) {
                                for (int y = 0; y < 3 - offsetY; ++y) {
                                    item = craftingEventPacket.input[(y + offsetY) * 3 + (x + offsetX)];
                                    Item ingredient = ((ShapedRecipe) recipe).getIngredient(x, y);
                                    //todo: check this https://github.com/PocketMine/PocketMine-MP/commit/58709293cf4eee2e836a94226bbba4aca0f53908
                                    if (item.getCount() > 0) {
                                        if (ingredient == null || !ingredient.deepEquals(item, ingredient.hasMeta(), ingredient.getCompoundTag() != null)) {
                                            canCraft = false;
                                            break;
                                        }

                                    }
                                }
                            }

                            //If can't craft by auto resize, will try to craft this item in another way
                            if (!canCraft) {
                                canCraft = true;
                                for (int x = 0; x < 3 && canCraft; ++x) {
                                    for (int y = 0; y < 3; ++y) {
                                        item = craftingEventPacket.input[y * 3 + x];
                                        Item ingredient = ((ShapedRecipe) recipe).getIngredient(x, y);
                                        if (item.getCount() > 0) {
                                            if (ingredient == null || !ingredient.deepEquals(item, ingredient.hasMeta(), ingredient.getCompoundTag() != null)) {
                                                canCraft = false;
                                                break;
                                            }

                                        }
                                    }
                                }
                            }

                        } else if (recipe instanceof ShapelessRecipe) {
                            List<Item> needed = ((ShapelessRecipe) recipe).getIngredientList();

                            for (int x = 0; x < 3 && canCraft; ++x) {
                                for (int y = 0; y < 3; ++y) {
                                    item = craftingEventPacket.input[y * 3 + x].clone();

                                    for (Item n : new ArrayList<>(needed)) {
                                        if (n.deepEquals(item, n.hasMeta(), n.getCompoundTag() != null)) {
                                            int remove = Math.min(n.getCount(), item.getCount());
                                            n.setCount(n.getCount() - remove);
                                            item.setCount(item.getCount() - remove);

                                            if (n.getCount() == 0) {
                                                needed.remove(n);
                                            }
                                        }
                                    }

                                    if (item.getCount() > 0) {
                                        canCraft = false;
                                        break;
                                    }
                                }
                            }

                            if (!needed.isEmpty()) {
                                canCraft = false;
                            }
                        } else {
                            canCraft = false;
                        }

                        List<Item> ingredientsList = new ArrayList<>();
                        if (recipe instanceof ShapedRecipe) {
                            for (int x = 0; x < 3; x++) {
                                for (int y = 0; y < 3; y++) {
                                    Item need = ((ShapedRecipe) recipe).getIngredient(x, y);
                                    if (need.getId() == 0) {
                                        continue;
                                    }
                                    for (int count = need.getCount(); count > 0; count--) {
                                        Item needAdd = need.clone();
                                        //todo: check if there need to set item's count to 1, I'm too tired to check that today =w=
                                        needAdd.setCount(1);
                                        ingredientsList.add(needAdd);
                                    }
                                }
                            }
                        }
                        if (recipe instanceof ShapelessRecipe) {
                            List<Item> recipeItem = ((ShapelessRecipe) recipe).getIngredientList();
                            for (Item need : recipeItem) {
                                if (need.getId() == 0) {
                                    continue;
                                }
                                Item needAdd = need.clone();
                                //todo: check if there need to set item's count to 1, I'm too tired to check that today =w=
                                needAdd.setCount(1);
                                ingredientsList.add(needAdd);
                            }
                        }

                        Item[] ingredients = ingredientsList.stream().toArray(Item[]::new);

                        Item result = craftingEventPacket.output[0];

                        if (!canCraft || !recipe.getResult().deepEquals(result)) {
                            this.server.getLogger().debug("(2) Unmatched recipe " + recipe.getId() + " from player " + this.getName() + ": expected " + recipe.getResult() + ", got " + result + ", using: " + Arrays.asList(ingredients).toString());
                            this.inventory.sendContents(this);
                            break;
                        }

                        int[] used = new int[this.inventory.getSize()];

                        synchronized (inventory) {
                            for (Item ingredient : ingredients) {
                                slot = -1;
                                for (int index : this.inventory.getContents().keySet()) {
                                    Item i = this.inventory.getContents().get(index);
                                    if (ingredient.getId() != 0 && ingredient.deepEquals(i, ingredient.hasMeta()) && (i.getCount() - used[index]) >= 1) {
                                        slot = index;
                                        used[index]++;
                                        break;
                                    }
                                }

                                if (ingredient.getId() != 0 && slot == -1) {
                                    canCraft = false;
                                    break;
                                }
                            }

                            if (!canCraft) {
                                this.server.getLogger().debug("(3) Unmatched recipe " + recipe.getId() + " from player " + this.getName() + ": client does not have enough items, using: " + Arrays.asList(ingredients).toString());
                                this.inventory.sendContents(this);
                                break;
                            }
                            CraftItemEvent craftItemEvent;
                            this.server.getPluginManager().callEvent(craftItemEvent = new CraftItemEvent(this, ingredients, recipe));

                            if (craftItemEvent.isCancelled()) {
                                this.inventory.sendContents(this);
                                break;
                            }

                            for (int i = 0; i < used.length; i++) {
                                int count = used[i];
                                if (count == 0) {
                                    continue;
                                }

                                item = this.inventory.getItem(i);

                                Item newItem;
                                if (item.getCount() > count) {
                                    newItem = item.clone();
                                    newItem.setCount(item.getCount() - count);
                                } else {
                                    newItem = new ItemBlock(new BlockAir(), 0, 0);
                                }

                                this.inventory.setItem(i, newItem);
                            }

                            Item[] extraItem = this.inventory.addItem(recipe.getResult());
                            if (extraItem.length > 0) {
                                for (Item i : extraItem) {
                                    this.level.dropItem(this, i);
                                }
                            }
                        }
                    }
                    //todo: achievement

                    break;
                case ProtocolInfo.CONTAINER_SET_SLOT_PACKET:
                    if (!this.spawned || this.blocked || !this.isAlive()) {
                        break;
                    }

                    ContainerSetSlotPacket containerSetSlotPacket = (ContainerSetSlotPacket) packet;
                    if (containerSetSlotPacket.slot < 0) {
                        break;
                    }

                    BaseTransaction transaction;
                    if (containerSetSlotPacket.windowid == 0) { //Our inventory
                        if (containerSetSlotPacket.slot >= this.inventory.getSize()) {
                            break;
                        }
                        if (this.isCreative()) {
                            if (Item.getCreativeItemIndex(containerSetSlotPacket.item) != -1) {
                                this.inventory.setItem(containerSetSlotPacket.slot, containerSetSlotPacket.item);
                                this.inventory.setHotbarSlotIndex(containerSetSlotPacket.slot, containerSetSlotPacket.slot); //links hotbar[packet.slot] to slots[packet.slot]
                            }
                        }
                        transaction = new BaseTransaction(this.inventory, containerSetSlotPacket.slot, this.inventory.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                    } else if (containerSetSlotPacket.windowid == ContainerSetContentPacket.SPECIAL_ARMOR) { //Our armor
                        if (containerSetSlotPacket.slot >= 4) {
                            break;
                        }

                        transaction = new BaseTransaction(this.inventory, containerSetSlotPacket.slot + this.inventory.getSize(), this.inventory.getArmorItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                    } else if (this.windowIndex.containsKey(containerSetSlotPacket.windowid)) {
                        Inventory inv = this.windowIndex.get(containerSetSlotPacket.windowid);

                        if (!(inv instanceof AnvilInventory)) {
                            this.craftingType = CRAFTING_SMALL;
                        }

                        if (inv instanceof EnchantInventory && containerSetSlotPacket.item.hasEnchantments()) {
                            ((EnchantInventory) inv).onEnchant(this, inv.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                        }

                        transaction = new BaseTransaction(inv, containerSetSlotPacket.slot, inv.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                    } else {
                        break;
                    }

                    if (transaction.getSourceItem().deepEquals(transaction.getTargetItem()) && transaction.getTargetItem().getCount() == transaction.getSourceItem().getCount()) { //No changes!
                        //No changes, just a local inventory update sent by the server
                        break;
                    }


                    if (this.currentTransaction == null || this.currentTransaction.getCreationTime() < (System.currentTimeMillis() - 8 * 1000)) {
                        if (this.currentTransaction != null) {
                            for (Inventory inventory : this.currentTransaction.getInventories()) {
                                if (inventory instanceof PlayerInventory) {
                                    ((PlayerInventory) inventory).sendArmorContents(this);
                                }
                                inventory.sendContents(this);
                            }
                        }
                        this.currentTransaction = new SimpleTransactionGroup(this);
                    }

                    this.currentTransaction.addTransaction(transaction);

                    if (this.currentTransaction.canExecute() || this.isCreative()) {
                        //todo achievement
                        this.currentTransaction.execute(this.isCreative());

                        this.currentTransaction = null;
                    } else {
                        if (containerSetSlotPacket.item.getId() != 0) {
                            inventory.sendSlot(containerSetSlotPacket.hotbarSlot, this);
                            inventory.sendSlot(containerSetSlotPacket.slot, this);
                        }
                    }

                    break;
                case ProtocolInfo.BLOCK_ENTITY_DATA_PACKET:
                    if (!this.spawned || this.blocked || !this.isAlive()) {
                        break;
                    }
                    BlockEntityDataPacket blockEntityDataPacket = (BlockEntityDataPacket) packet;
                    this.craftingType = CRAFTING_SMALL;

                    pos = new Vector3(blockEntityDataPacket.x, blockEntityDataPacket.y, blockEntityDataPacket.z);
                    if (pos.distanceSquared(this) > 10000) {
                        break;
                    }

                    BlockEntity t = this.level.getBlockEntity(pos);
                    if (t instanceof BlockEntitySign) {
                        CompoundTag nbt;
                        try {
                            nbt = NBTIO.read(blockEntityDataPacket.namedTag, ByteOrder.LITTLE_ENDIAN, true);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        if (!BlockEntity.SIGN.equals(nbt.getString("id"))) {
                            ((BlockEntitySign) t).spawnTo(this);
                        } else {
                            SignChangeEvent signChangeEvent = new SignChangeEvent(t.getBlock(), this, new String[]{
                                    this.removeFormat ? TextFormat.clean(nbt.getString("Text1")) : nbt.getString("Text1"),
                                    this.removeFormat ? TextFormat.clean(nbt.getString("Text2")) : nbt.getString("Text2"),
                                    this.removeFormat ? TextFormat.clean(nbt.getString("Text3")) : nbt.getString("Text3"),
                                    this.removeFormat ? TextFormat.clean(nbt.getString("Text4")) : nbt.getString("Text4")
                            });

                            if (!t.namedTag.contains("Creator") || !Objects.equals(this.getUniqueId().toString(), t.namedTag.getString("Creator"))) {
                                signChangeEvent.setCancelled();
                            }

                            this.server.getPluginManager().callEvent(signChangeEvent);

                            if (!signChangeEvent.isCancelled()) {
                                ((BlockEntitySign) t).setText(signChangeEvent.getLine(0), signChangeEvent.getLine(1), signChangeEvent.getLine(2), signChangeEvent.getLine(3));
                            } else {
                                ((BlockEntitySign) t).spawnTo(this);
                            }

                        }
                    }
                    break;
                case ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET:
                    RequestChunkRadiusPacket requestChunkRadiusPacket = (RequestChunkRadiusPacket) packet;
                    ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
                    this.chunkRadius = Math.max(5, Math.min((int) requestChunkRadiusPacket.radius, this.viewDistance));
                    chunkRadiusUpdatePacket.radius = this.chunkRadius;
                    this.dataPacket(chunkRadiusUpdatePacket);
                    break;
                case ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET:
                    SetPlayerGameTypePacket setPlayerGameTypePacket = (SetPlayerGameTypePacket) packet;
                    if (setPlayerGameTypePacket.gamemode != this.gamemode) {
                        if (!this.hasPermission("nukkit.command.gamemode")) {
                            SetPlayerGameTypePacket setPlayerGameTypePacket1 = new SetPlayerGameTypePacket();
                            setPlayerGameTypePacket1.gamemode = this.gamemode & 0x01;
                            this.dataPacket(setPlayerGameTypePacket1);
                            this.getAdventureSettings().update();
                            break;
                        }
                        this.setGamemode(setPlayerGameTypePacket.gamemode, true);
                        Command.broadcastCommandMessage(this, new TranslationContainer("commands.gamemode.success.self", Server.getGamemodeString(this.gamemode)));
                    }
                    break;
                case ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET:
                    ItemFrameDropItemPacket itemFrameDropItemPacket = (ItemFrameDropItemPacket) packet;
                    Vector3 vector3 = new Vector3(itemFrameDropItemPacket.x, itemFrameDropItemPacket.y, itemFrameDropItemPacket.z);
                    BlockEntity blockEntityItemFrame = this.level.getBlockEntity(vector3);
                    BlockEntityItemFrame itemFrame = (BlockEntityItemFrame) blockEntityItemFrame;
                    if (itemFrame != null) {
                        Block block = itemFrame.getBlock();
                        Item itemDrop = itemFrame.getItem();
                        ItemFrameDropItemEvent itemFrameDropItemEvent = new ItemFrameDropItemEvent(this, block, itemFrame, itemDrop);
                        this.server.getPluginManager().callEvent(itemFrameDropItemEvent);
                        if (!itemFrameDropItemEvent.isCancelled()) {
                            if (itemDrop.getId() != Item.AIR) {
                                vector3.setComponents(itemFrame.x + 0.5, itemFrame.y, itemFrame.z + 0.5);
                                this.level.dropItem(vector3, itemDrop);
                                itemFrame.setItem(new ItemBlock(new BlockAir()));
                                itemFrame.setItemRotation(0);
                                this.getLevel().addSound(new ItemFrameItemRemovedSound(this));
                            }
                        } else {
                            itemFrame.spawnTo(this);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public boolean kick() {
        return this.kick("");
    }

    public boolean kick(String reason, boolean isAdmin) {
        return this.kick(PlayerKickEvent.Reason.UNKNOWN, reason, isAdmin);
    }

    public boolean kick(String reason) {
        return kick(PlayerKickEvent.Reason.UNKNOWN, reason);
    }

    public boolean kick(PlayerKickEvent.Reason reason) {
        return this.kick(reason, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString) {
        return this.kick(reason, reasonString, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, boolean isAdmin) {
        return this.kick(reason, reason.toString(), isAdmin);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString, boolean isAdmin) {
        PlayerKickEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerKickEvent(this, reason, this.getLeaveMessage()));
        if (!ev.isCancelled()) {
            String message;
            if (isAdmin) {
                if (!this.isBanned()) {
                    message = "Kicked by admin." + (!"".equals(reasonString) ? " Reason: " + reasonString : "");
                } else {
                    message = reasonString;
                }
            } else {
                if ("".equals(reasonString)) {
                    message = "disconnectionScreen.noReason";
                } else {
                    message = reasonString;
                }
            }

            this.close(ev.getQuitMessage(), message);

            return true;
        }

        return false;
    }

    @Override
    public void sendMessage(String message) {
        String[] mes = this.server.getLanguage().translateString(message).split("\\n");
        for (String m : mes) {
            if (!"".equals(m)) {
                TextPacket pk = new TextPacket();
                pk.type = TextPacket.TYPE_RAW;
                pk.message = m;
                this.dataPacket(pk);
            }
        }
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer) {
            this.sendTranslation(message.getText(), ((TranslationContainer) message).getParameters());
            return;
        }
        this.sendMessage(message.getText());
    }

    public void sendTranslation(String message) {
        this.sendTranslation(message, new String[0]);
    }

    public void sendTranslation(String message, String[] parameters) {
        TextPacket pk = new TextPacket();
        if (!this.server.isLanguageForced()) {
            pk.type = TextPacket.TYPE_TRANSLATION;
            pk.message = this.server.getLanguage().translateString(message, parameters, "nukkit.");
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = this.server.getLanguage().translateString(parameters[i], parameters, "nukkit.");

            }
            pk.parameters = parameters;
        } else {
            pk.type = TextPacket.TYPE_RAW;
            pk.message = this.server.getLanguage().translateString(message, parameters);
        }
        this.dataPacket(pk);
    }

    public void sendPopup(String message) {
        this.sendPopup(message, "");
    }

    public void sendPopup(String message, String subtitle) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_POPUP;
        pk.source = message;
        pk.message = subtitle;
        this.dataPacket(pk);
    }

    public void sendTip(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_TIP;
        pk.message = message;
        this.dataPacket(pk);
    }

    @Override
    public synchronized void close() {
        this.close("");
    }

    public synchronized void close(String message) {
        this.close(message, "generic");
    }

    public synchronized void close(String message, String reason) {
        this.close(message, reason, true);
    }

    public synchronized void close(String message, String reason, boolean notify) {
        this.close(new TextContainer(message), reason, notify);
    }

    public synchronized void close(TextContainer message) {
        this.close(message, "generic");
    }

    public synchronized void close(TextContainer message, String reason) {
        this.close(message, reason, true);
    }

    public synchronized void close(TextContainer message, String reason, boolean notify) {
        if (this.connected && !this.closed) {
            if (notify && reason.length() > 0) {
                DisconnectPacket pk = new DisconnectPacket();
                pk.message = reason;
                this.directDataPacket(pk);
            }

            this.connected = false;
            PlayerQuitEvent ev = null;
            if (this.getName() != null && this.getName().length() > 0) {
                this.server.getPluginManager().callEvent(ev = new PlayerQuitEvent(this, message, true, reason));
                if (this.loggedIn && ev.getAutoSave()) {
                    this.save();
                }
            }

            for (Player player : new ArrayList<>(this.server.getOnlinePlayers().values())) {
                if (!player.canSee(this)) {
                    player.showPlayer(this);
                }
            }

            this.hiddenPlayers.clear();

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                this.removeWindow(window);
            }

            for (long index : new ArrayList<>(this.usedChunks.keySet())) {
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);
                this.level.unregisterChunkLoader(this, chunkX, chunkZ);
                this.usedChunks.remove(index);
            }

            super.close();

            this.interfaz.close(this, notify ? reason : "");

            if (this.loggedIn) {
                this.server.removeOnlinePlayer(this);
            }

            this.loggedIn = false;

            if (ev != null && !Objects.equals(this.username, "") && this.spawned && !Objects.equals(ev.getQuitMessage().toString(), "")) {
                this.server.broadcastMessage(ev.getQuitMessage());
            }

            this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
            this.spawned = false;
            this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logOut", new String[]{
                    TextFormat.AQUA + (this.getName() == null ? "" : this.getName()) + TextFormat.WHITE,
                    this.ip,
                    String.valueOf(this.port),
                    this.getServer().getLanguage().translateString(reason)
            }));
            this.windows.clear();
            this.windowIndex.clear();
            this.usedChunks.clear();
            this.loadQueue.clear();
            this.hasSpawned.clear();
            this.spawnPosition = null;

            if (this.riding instanceof EntityVehicle) {
                ((EntityVehicle) this.riding).linkedEntity = null;
            }

            this.riding = null;
        }

        if (this.perm != null) {
            this.perm.clearPermissions();
        }

        if (this.inventory != null) {
            this.inventory = null;
            this.currentTransaction = null;
        }

        this.chunk = null;

        this.server.removePlayer(this);
    }

    public void save() {
        this.save(false);
    }

    public synchronized void save(boolean async) {
        if (this.closed) {
            throw new IllegalStateException("Tried to save closed player");
        }

        super.saveNBT();

        if (this.level != null) {
            this.namedTag.putString("Level", this.level.getFolderName());
            if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
                this.namedTag.putString("SpawnLevel", this.spawnPosition.getLevel().getFolderName());
                this.namedTag.putInt("SpawnX", (int) this.spawnPosition.x);
                this.namedTag.putInt("SpawnY", (int) this.spawnPosition.y);
                this.namedTag.putInt("SpawnZ", (int) this.spawnPosition.z);
            }

            //todo save achievement

            this.namedTag.putInt("playerGameType", this.gamemode);
            this.namedTag.putLong("lastPlayed", System.currentTimeMillis() / 1000);

            this.namedTag.putString("lastIP", this.getAddress());

            this.namedTag.putInt("EXP", this.getExperience());
            this.namedTag.putInt("expLevel", this.getExperienceLevel());

            this.namedTag.putInt("foodLevel", this.getFoodData().getLevel());
            this.namedTag.putFloat("foodSaturationLevel", this.getFoodData().getFoodSaturationLevel());

            if (!"".equals(this.username) && this.namedTag != null) {
                this.server.saveOfflinePlayerData(this.username, this.namedTag, async);
            }
        }
    }

    public String getName() {
        return this.username;
    }

    @Override
    public synchronized void kill() {
        if (!this.spawned) {
            return;
        }

        String message = "death.attack.generic";

        List<String> params = new ArrayList<>();
        params.add(this.getDisplayName());

        EntityDamageEvent cause = this.getLastDamageCause();

        switch (cause == null ? EntityDamageEvent.CAUSE_CUSTOM : cause.getCause()) {
            case EntityDamageEvent.CAUSE_ENTITY_ATTACK:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.player";
                        params.add(((Player) e).getDisplayName());
                        break;
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.mob";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                        break;
                    } else {
                        params.add("Unknown");
                    }
                }
                break;
            case EntityDamageEvent.CAUSE_PROJECTILE:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.arrow";
                        params.add(((Player) e).getDisplayName());
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.arrow";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                        break;
                    } else {
                        params.add("Unknown");
                    }
                }
                break;
            case EntityDamageEvent.CAUSE_SUICIDE:
                message = "death.attack.generic";
                break;
            case EntityDamageEvent.CAUSE_VOID:
                message = "death.attack.outOfWorld";
                break;
            case EntityDamageEvent.CAUSE_FALL:
                if (cause != null) {
                    if (cause.getFinalDamage() > 2) {
                        message = "death.fell.accident.generic";
                        break;
                    }
                }
                message = "death.attack.fall";
                break;

            case EntityDamageEvent.CAUSE_SUFFOCATION:
                message = "death.attack.inWall";
                break;

            case EntityDamageEvent.CAUSE_LAVA:
                message = "death.attack.lava";
                break;

            case EntityDamageEvent.CAUSE_FIRE:
                message = "death.attack.onFire";
                break;

            case EntityDamageEvent.CAUSE_FIRE_TICK:
                message = "death.attack.inFire";
                break;

            case EntityDamageEvent.CAUSE_DROWNING:
                message = "death.attack.drown";
                break;

            case EntityDamageEvent.CAUSE_CONTACT:
                if (cause instanceof EntityDamageByBlockEvent) {
                    if (((EntityDamageByBlockEvent) cause).getDamager().getId() == Block.CACTUS) {
                        message = "death.attack.cactus";
                    }
                }
                break;

            case EntityDamageEvent.CAUSE_BLOCK_EXPLOSION:
            case EntityDamageEvent.CAUSE_ENTITY_EXPLOSION:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.explosion.player";
                        params.add(((Player) e).getDisplayName());
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.explosion.player";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                        break;
                    }
                } else {
                    message = "death.attack.explosion";
                }
                break;

            case EntityDamageEvent.CAUSE_MAGIC:
                message = "death.attack.magic";
                break;

            case EntityDamageEvent.CAUSE_CUSTOM:
                break;

            default:

        }

        this.health = 0;
        this.scheduleUpdate();

        PlayerDeathEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerDeathEvent(this, this.getDrops(), new TranslationContainer(message, params.stream().toArray(String[]::new)), this.getExperienceLevel()));

        if (!ev.getKeepInventory()) {
            for (Item item : ev.getDrops()) {
                this.level.dropItem(this, item);
            }

            if (this.inventory != null) {
                this.inventory.clearAll();
            }
        }

        if (!ev.getKeepExperience()) {
            if (this.isSurvival() || this.isAdventure()) {
                int exp = ev.getExperience() * 7;
                if (exp > 100) exp = 100;
                int add = 1;
                for (int ii = 1; ii < exp; ii += add) {
                    this.getLevel().dropExpOrb(this, add);
                    add = new NukkitRandom().nextRange(1, 3);
                }
            }
            this.setExperience(0, 0);
        }

        if (!Objects.equals(ev.getDeathMessage().toString(), "")) {
            this.server.broadcast(ev.getDeathMessage(), Server.BROADCAST_CHANNEL_USERS);
        }


        RespawnPacket pk = new RespawnPacket();
        Position pos = this.getSpawn();
        pk.x = (float) pos.x;
        pk.y = (float) pos.y;
        pk.z = (float) pos.z;
        this.dataPacket(pk);
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(health);
        Attribute attr = Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0);
        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{attr};
            pk.entityId = 0;
            this.dataPacket(pk);
        }
    }

    public int getExperience() {
        return this.exp;
    }

    public int getExperienceLevel() {
        return this.expLevel;
    }

    public void addExperience(int add) {
        if (add == 0) return;
        int now = this.getExperience();
        int added = now + add;
        int level = this.getExperienceLevel();
        int most = calculateRequireExperience(level);
        while (added >= most) {  //Level Up!
            added = added - most;
            level++;
            most = calculateRequireExperience(level);
        }
        this.setExperience(added, level);
    }

    public static int calculateRequireExperience(int level) {
        if (level < 16) {
            return 2 * level + 7;
        } else if (level >= 17 && level <= 31) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    public void setExperience(int exp) {
        setExperience(exp, this.getExperienceLevel());
    }

    //todo something on performance, lots of exp orbs then lots of packets, could crash client

    public void setExperience(int exp, int level) {
        this.exp = exp;
        this.expLevel = level;

        this.sendExperienceLevel(level);
        this.sendExperience(exp);
    }

    public void sendExperience() {
        sendExperience(this.getExperience());
    }

    public void sendExperience(int exp) {
        if (this.spawned) {
            float percent = ((float) exp) / calculateRequireExperience(this.getExperienceLevel());
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE).setValue(percent));
        }
    }

    public void sendExperienceLevel() {
        sendExperienceLevel(this.getExperienceLevel());
    }

    public void sendExperienceLevel(int level) {
        if (this.spawned) {
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(level));
        }
    }

    public void setAttribute(Attribute attribute) {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entries = new Attribute[]{attribute};
        pk.entityId = 0;
        this.dataPacket(pk);
    }

    @Override
    public void setMovementSpeed(float speed) {
        super.setMovementSpeed(speed);
        if (this.spawned) {
            Attribute attribute = Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(speed);
            this.setAttribute(attribute);
        }
    }

    public Entity getKiller() {
        return killer;
    }

    @Override
    public void attack(EntityDamageEvent source) {
        if (!this.isAlive()) {
            return;
        }

        if (this.isCreative()
                && source.getCause() != EntityDamageEvent.CAUSE_MAGIC
                && source.getCause() != EntityDamageEvent.CAUSE_SUICIDE
                && source.getCause() != EntityDamageEvent.CAUSE_VOID
                ) {
            source.setCancelled();
        } else if (this.getAdventureSettings().canFly() && source.getCause() == EntityDamageEvent.CAUSE_FALL) {
            source.setCancelled();
        } else if (source.getCause() == EntityDamageEvent.CAUSE_FALL) {
            Position pos = getPosition().floor();
            if (this.getLevel().getBlockIdAt((int) pos.x, (int) pos.y - 1, (int) pos.z) == Block.SLIME_BLOCK) {
                if (!this.isSneaking()) {
                    source.setCancelled();
                    this.resetFallDistance();
                }
            }
        }

        if (source instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
            if (damager instanceof Player) {
                ((Player) damager).getFoodData().updateFoodExpLevel(0.3);
            }
            //暴击
            boolean add = false;
            if (!damager.onGround) {
                NukkitRandom random = new NukkitRandom();
                for (int i = 0; i < 5; i++) {
                    CriticalParticle par = new CriticalParticle(new Vector3(this.x + random.nextRange(-15, 15) / 10, this.y + random.nextRange(0, 20) / 10, this.z + random.nextRange(-15, 15) / 10));
                    this.getLevel().addParticle(par);
                }

                add = true;
            }
            if (add) source.setDamage((float) (source.getDamage() * 1.5));
        }

        super.attack(source);

        if (!source.isCancelled() && this.getLastDamageCause() == source && this.spawned) {
            this.getFoodData().updateFoodExpLevel(0.3);
            EntityEventPacket pk = new EntityEventPacket();
            pk.eid = 0;
            pk.event = EntityEventPacket.HURT_ANIMATION;
            this.dataPacket(pk);
        }
    }

    public void sendPosition(Vector3 pos) {
        this.sendPosition(pos, this.yaw);
    }

    public void sendPosition(Vector3 pos, double yaw) {
        this.sendPosition(pos, yaw, this.pitch);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch) {
        this.sendPosition(pos, yaw, pitch, MovePlayerPacket.MODE_NORMAL);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, byte mode) {
        this.sendPosition(pos, yaw, pitch, mode, null);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, byte mode, Player[] targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) pos.x;
        pk.y = (float) (pos.y + this.getEyeHeight());
        pk.z = (float) pos.z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;

        if (targets != null) {
            Server.broadcastPacket(targets, pk);
        } else {
            pk.eid = 0;
            this.dataPacket(pk);
        }
    }

    @Override
    protected synchronized void checkChunks() {
        if (this.chunk == null || (this.chunk.getX() != ((int) this.x >> 4) || this.chunk.getZ() != ((int) this.z >> 4))) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true);

            if (!this.justCreated) {
                Map<Integer, Player> newChunk = this.level.getChunkPlayers((int) this.x >> 4, (int) this.z >> 4);
                newChunk.remove(this.getLoaderId());

                //List<Player> reload = new ArrayList<>();
                for (Player player : new ArrayList<>(this.hasSpawned.values())) {
                    if (!newChunk.containsKey(player.getLoaderId())) {
                        this.despawnFrom(player);
                    } else {
                        newChunk.remove(player.getLoaderId());
                        //reload.add(player);
                    }
                }

                for (Player player : newChunk.values()) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    protected synchronized boolean checkTeleportPosition() {
        if (this.teleportPosition != null) {
            int chunkX = (int) this.teleportPosition.x >> 4;
            int chunkZ = (int) this.teleportPosition.z >> 4;

            for (int X = -1; X <= 1; ++X) {
                for (int Z = -1; Z <= 1; ++Z) {
                    long index = Level.chunkHash(chunkX + X, chunkZ + Z);
                    if (!this.usedChunks.containsKey(index) || !this.usedChunks.get(index)) {
                        return false;
                    }
                }
            }

            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);
            this.spawnToAll();
            this.forceMovement = this.teleportPosition;
            this.teleportPosition = null;

            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean teleport(Location location, TeleportCause cause) {
        if (!this.isOnline()) {
            return false;
        }

        Location from = this.getLocation();
        Location to = location;

        if (cause != null) {
            PlayerTeleportEvent event = new PlayerTeleportEvent(this, from, to, cause);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            to = event.getTo();
        }

        Position oldPos = this.getPosition();
        if (super.teleport(to, null)) { // null to prevent fire of duplicate EntityTeleportEvent

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                if (window == this.inventory) {
                    continue;
                }
                this.removeWindow(window);
            }

            this.teleportPosition = new Vector3(this.x, this.y, this.z);

            if (!this.checkTeleportPosition()) {
                this.forceMovement = oldPos;
            } else {
                this.spawnToAll();
            }

            this.resetFallDistance();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            //Weather
            this.getLevel().sendWeather(this);
            //Update time
            this.getLevel().sendTime(this);
            return true;
        }

        return false;
    }

    public void teleportImmediate(Location location) {
        this.teleportImmediate(location, TeleportCause.PLUGIN);
    }

    public synchronized void teleportImmediate(Location location, TeleportCause cause) {
        if (super.teleport(location, cause)) {

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                if (window == this.inventory) {
                    continue;
                }
                this.removeWindow(window);
            }

            this.forceMovement = new Vector3(this.x, this.y, this.z);
            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);

            this.resetFallDistance();
            this.orderChunks();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            //Weather
            this.getLevel().sendWeather(this);
            //Update time
            this.getLevel().sendTime(this);
        }
    }

    public int getWindowId(Inventory inventory) {
        Integer value = this.windows.get(inventory);
        return value != null ? value : -1;
    }

    public int addWindow(Inventory inventory) {
        return this.addWindow(inventory, null);
    }

    public synchronized int addWindow(Inventory inventory, Integer forceId) {
        synchronized (windows) {
            Integer value = this.windows.get(inventory);
            if (value != null) {
                return value;
            }
            if (this.windows.containsKey(inventory)) {
                return this.windows.get(inventory);
            }
            int cnt;
            if (forceId == null) {
                this.windowCnt = cnt = Math.max(2, ++this.windowCnt % 99);
            } else {
                cnt = forceId;
            }
            this.windowIndex.put(cnt, inventory);
            this.windows.put(inventory, cnt);
            if (inventory.open(this)) {
                return cnt;
            } else {
                this.removeWindow(inventory);

                return -1;
            }
        }
    }

    public void removeWindow(Inventory inventory) {
        synchronized (windows) {
            inventory.close(this);
            if (this.windows.containsKey(inventory)) {
                int id = this.windows.get(inventory);
                this.windows.remove(this.windowIndex.get(id));
                this.windowIndex.remove(id);
            }
        }
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        this.server.getPlayerMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        this.server.getPlayerMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @Override
    public void onChunkChanged(FullChunk chunk) {
        int dx = ((int) this.x >> 4) - chunk.getX();
        int dz = ((int) this.z >> 4) - chunk.getZ();
        this.loadQueue.put(Level.chunkHash(chunk.getX(), chunk.getZ()), dx * dx + dz * dz);
    }

    @Override
    public void onChunkLoaded(FullChunk chunk) {

    }

    @Override
    public void onChunkPopulated(FullChunk chunk) {

    }

    @Override
    public void onChunkUnloaded(FullChunk chunk) {

    }

    @Override
    public void onBlockChanged(Vector3 block) {

    }

    @Override
    public Integer getLoaderId() {
        return this.loaderId;
    }

    @Override
    public boolean isLoaderActive() {
        return this.isConnected();
    }


    public static BatchPacket getChunkCacheFromData(int chunkX, int chunkZ, byte[] payload) {
        return getChunkCacheFromData(chunkX, chunkZ, payload, FullChunkDataPacket.ORDER_COLUMNS);
    }

    public static BatchPacket getChunkCacheFromData(int chunkX, int chunkZ, byte[] payload, byte ordering) {
        FullChunkDataPacket pk = new FullChunkDataPacket();
        pk.chunkX = chunkX;
        pk.chunkZ = chunkZ;
        pk.order = ordering;
        pk.data = payload;
        pk.encode();

        BatchPacket batch = new BatchPacket();
        byte[][] batchPayload = new byte[2][];
        byte[] buf = pk.getBuffer();
        batchPayload[0] = Binary.writeUnsignedVarInt(buf.length);
        batchPayload[1] = buf;
        byte[] data = Binary.appendBytes(batchPayload);
        try {
            batch.payload = Zlib.deflate(data, Server.getInstance().networkCompressionLevel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        batch.encode();
        batch.isEncoded = true;
        return batch;
    }

    private boolean foodEnabled = true;

    public boolean isFoodEnabled() {
        return !(this.isCreative() || this.isSpectator()) && this.foodEnabled;
    }

    public void setFoodEnabled(boolean foodEnabled) {
        this.foodEnabled = foodEnabled;
    }

    public PlayerFood getFoodData() {
        return this.foodData;
    }

    //todo a lot on dimension

    public void setDimension(int dimension) {
        ChangeDimensionPacket pk = new ChangeDimensionPacket();
        pk.dimension = getLevel().getDimension();
        this.dataPacket(pk);
    }

    public void setCheckMovement(boolean checkMovement) {
        this.checkMovement = checkMovement;
    }

    public synchronized void setLocale(Locale locale) {
        this.locale.set(locale);
    }

    public synchronized Locale getLocale() {
        return this.locale.get();
    }

    public void setSprinting(boolean value, boolean setDefault) {
        super.setSprinting(value);
        if (setDefault) {
            this.movementSpeed = DEFAULT_SPEED;
        } else {
            float sprintSpeedChange = DEFAULT_SPEED * 0.3f;
            if (!value) sprintSpeedChange *= -1;
            this.movementSpeed += sprintSpeedChange;
        }
        this.setMovementSpeed(this.movementSpeed);
    }

    @Override
    public int hashCode() {
        if ((this.hash == 0) || (this.hash == 485)) {
            this.hash = (485 + (getUniqueId() != null ? getUniqueId().hashCode() : 0));
        }

        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Player)) {
            return false;
        }
        Player other = (Player) obj;
        return Objects.equals(this.getUniqueId(), other.getUniqueId()) && this.getId() == other.getId();
    }
}
