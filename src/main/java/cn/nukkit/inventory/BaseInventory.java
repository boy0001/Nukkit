package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.network.protocol.ContainerSetContentPacket;
import cn.nukkit.network.protocol.ContainerSetSlotPacket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class BaseInventory implements Inventory {

    protected final InventoryType type;

    protected int maxStackSize = Inventory.MAX_STACK;

    protected volatile int size;

    protected final String name;

    protected final String title;

    protected final Map<Integer, Item> slots = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<Player, Object> viewers = new ConcurrentHashMap<>();

    protected InventoryHolder holder;

    public BaseInventory(InventoryHolder holder, InventoryType type) {
        this(holder, type, new HashMap<>());
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items) {
        this(holder, type, items, null);
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items, Integer overrideSize) {
        this(holder, type, items, overrideSize, null);
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items, Integer overrideSize, String overrideTitle) {
        this.holder = holder;

        this.type = type;

        if (overrideSize != null) {
            this.size = overrideSize;
        } else {
            this.size = this.type.getDefaultSize();
        }

        if (overrideTitle != null) {
            this.title = overrideTitle;
        } else {
            this.title = this.type.getDefaultTitle();
        }

        this.name = this.type.getDefaultTitle();

        if (!(this instanceof DoubleChestInventory)) {
            this.setContents(items);
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Item getItem(int index) {
        Item item = this.slots.get(index);
        return item == null ? new ItemBlock(new BlockAir(), null, 0) : item.clone();
    }

    @Override
    public Map<Integer, Item> getContents() {
        return new HashMap<>(this.slots);
    }

    @Override
    public void setContents(Map<Integer, Item> items) {
        if (items.size() > this.size) {
            TreeMap<Integer, Item> newItems = new TreeMap<>();
            for (Map.Entry<Integer, Item> entry : items.entrySet()) {
                newItems.put(entry.getKey(), entry.getValue());
            }
            items = newItems;
            newItems = new TreeMap<>();
            int i = 0;
            for (Map.Entry<Integer, Item> entry : items.entrySet()) {
                newItems.put(entry.getKey(), entry.getValue());
                i++;
                if (i >= this.size) {
                    break;
                }
            }
            items = newItems;
        }

        for (int i = 0; i < this.size; ++i) {
            if (!items.containsKey(i)) {
                if (this.slots.containsKey(i)) {
                    this.clear(i);
                }
            } else {
                if (!this.setItem(i, items.get(i))) {
                    this.clear(i);
                }
            }
        }
    }

    @Override
    public synchronized boolean setItem(int index, Item item) {
        item = item.clone();
        if (index < 0 || index >= this.size) {
            return false;
        } else if (item.getId() == 0 || item.getCount() <= 0) {
            return this.clear(index);
        }

        InventoryHolder holder = this.getHolder();
        if (holder instanceof Entity) {
            EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent((Entity) holder, this.getItem(index), item, index);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                this.sendSlot(index, this.getViewers());
                return false;
            }

            item = ev.getNewItem();
        }

        Item old = this.getItem(index);
        this.slots.put(index, item.clone());
        this.onSlotChange(index, old);

        return true;
    }

    @Override
    public boolean contains(Item item) {
        int count = Math.max(1, item.getCount());
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        for (Item i : this.getContents().values()) {
            if (item.equals(i, checkDamage, checkTag)) {
                count -= i.getCount();
                if (count <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Map<Integer, Item> all(Item item) {
        Map<Integer, Item> slots = new HashMap<>();
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        Iterator<Map.Entry<Integer, Item>> iter = this.getContents().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Item> entry = iter.next();
            if (item.equals(entry.getValue(), checkDamage, checkTag)) {
                slots.put(entry.getKey(), entry.getValue());
            }
        }
        return slots;
    }

    @Override
    public synchronized void remove(Item item) {
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        Iterator<Map.Entry<Integer, Item>> iter = this.getContents().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Item> entry = iter.next();
            if (item.equals(entry.getValue(), checkDamage, checkTag)) {
                this.clear(entry.getKey());
            }
        }
    }

    @Override
    public int first(Item item) {
        int count = Math.max(1, item.getCount());
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        for (Map.Entry<Integer, Item> entry : this.getContents().entrySet()) {
            if (item.equals(entry.getValue(), checkDamage, checkTag) && entry.getValue().getCount() >= count) {
                return entry.getKey();
            }
        }

        return -1;
    }

    @Override
    public int firstEmpty(Item item) {
        for (int i = 0; i < this.size; ++i) {
            if (this.getItem(i).getId() == Item.AIR) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public boolean canAddItem(Item item) {
        item = item.clone();
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        for (int i = 0; i < this.getSize(); ++i) {
            Item slot = this.getItem(i);
            if (item.equals(slot, checkDamage, checkTag)) {
                int diff;
                if ((diff = slot.getMaxStackSize() - slot.getCount()) > 0) {
                    item.setCount(item.getCount() - diff);
                }
            } else if (slot.getId() == Item.AIR) {
                item.setCount(item.getCount() - this.getMaxStackSize());
            }

            if (item.getCount() <= 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized Item[] addItem(Item... slots) {
        List<Item> itemSlots = new ArrayList<>();
        for (Item slot : slots) {
            if (slot.getId() != 0 && slot.getCount() > 0) {
                itemSlots.add(slot.clone());
            }
        }

        List<Integer> emptySlots = new ArrayList<>();

        for (int i = 0; i < this.getSize(); ++i) {
            Item item = this.getItem(i);
            if (item.getId() == Item.AIR || item.getCount() <= 0) {
                emptySlots.add(i);
            }

            for (Item slot : new ArrayList<>(itemSlots)) {
                if (slot.equals(item) && item.getCount() < item.getMaxStackSize()) {
                    int amount = Math.min(item.getMaxStackSize() - item.getCount(), slot.getCount());
                    amount = Math.min(amount, this.getMaxStackSize());
                    if (amount > 0) {
                        slot.setCount(slot.getCount() - amount);
                        item.setCount(item.getCount() + amount);
                        this.setItem(i, item);
                        if (slot.getCount() <= 0) {
                            itemSlots.remove(slot);
                        }
                    }
                }
            }
            if (itemSlots.isEmpty()) {
                break;
            }
        }

        if (!itemSlots.isEmpty() && !emptySlots.isEmpty()) {
            for (int slotIndex : emptySlots) {
                if (!itemSlots.isEmpty()) {
                    Item slot = itemSlots.get(0);
                    int amount = Math.min(slot.getMaxStackSize(), slot.getCount());
                    amount = Math.min(amount, this.getMaxStackSize());
                    slot.setCount(slot.getCount() - amount);
                    Item item = slot.clone();
                    item.setCount(amount);
                    this.setItem(slotIndex, item);
                    if (slot.getCount() <= 0) {
                        itemSlots.remove(slot);
                    }
                }
            }
        }

        return itemSlots.stream().toArray(Item[]::new);
    }

    @Override
    public synchronized Item[] removeItem(Item... slots) {
        List<Item> itemSlots = new ArrayList<>();
        for (Item slot : slots) {
            if (slot.getId() != 0 && slot.getCount() > 0) {
                itemSlots.add(slot.clone());
            }
        }

        for (int i = 0; i < this.getSize(); ++i) {
            Item item = this.getItem(i);
            if (item.getId() == Item.AIR || item.getCount() <= 0) {
                continue;
            }

            for (Item slot : new ArrayList<>(itemSlots)) {
                if (slot.equals(item, item.hasMeta(), item.getCompoundTag() != null)) {
                    int amount = Math.min(item.getCount(), slot.getCount());
                    slot.setCount(slot.getCount() - amount);
                    item.setCount(item.getCount() - amount);
                    this.setItem(i, item);
                    if (slot.getCount() <= 0) {
                        itemSlots.remove(slot);
                    }

                }
            }

            if (itemSlots.size() == 0) {
                break;
            }
        }

        return itemSlots.stream().toArray(Item[]::new);
    }

    @Override
    public synchronized boolean clear(int index) {
        if (this.slots.containsKey(index)) {
            Item item = new ItemBlock(new BlockAir(), null, 0);
            Item old = this.slots.get(index);
            InventoryHolder holder = this.getHolder();
            if (holder instanceof Entity) {
                EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent((Entity) holder, old, item, index);
                Server.getInstance().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    this.sendSlot(index, this.getViewers());
                    return false;
                }
                item = ev.getNewItem();
            }

            if (item.getId() != Item.AIR) {
                this.slots.put(index, item.clone());
            } else {
                this.slots.remove(index);
            }

            this.onSlotChange(index, old);
        }

        return true;
    }

    @Override
    public synchronized void clearAll() {
        for (Integer index : this.getContents().keySet()) {
            this.clear(index);
        }
    }

    @Override
    public Set<Player> getViewers() {
        return viewers.keySet();
    }

    @Override
    public InventoryHolder getHolder() {
        return holder;
    }

    @Override
    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    @Override
    public boolean open(Player who) {
        InventoryOpenEvent ev = new InventoryOpenEvent(this, who);
        who.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }
        this.onOpen(who);

        return true;
    }

    @Override
    public synchronized void close(Player who) {
        this.onClose(who);
    }

    @Override
    public void onOpen(Player who) {
        this.viewers.put(who, who);
    }

    @Override
    public void onClose(Player who) {
        this.viewers.remove(who);
    }

    @Override
    public void onSlotChange(int index, Item before) {
        this.sendSlot(index, this.getViewers());
    }

    @Override
    public void sendContents(Player player) {
        this.sendContents(new Player[]{player});
    }

    @Override
    public void sendContents(Player[] players) {
        ContainerSetContentPacket pk = new ContainerSetContentPacket();
        pk.slots = new Item[this.getSize()];
        for (int i = 0; i < this.getSize(); ++i) {
            pk.slots[i] = this.getItem(i);
        }

        for (Player player : players) {
            int id = player.getWindowId(this);
            if (id == -1 || !player.spawned) {
                this.close(player);
                continue;
            }
            pk.windowid = (byte) id;
            player.dataPacket(pk);
        }
    }

    @Override
    public void sendContents(Collection<Player> players) {
        this.sendContents(players.stream().toArray(Player[]::new));
    }

    @Override
    public void sendSlot(int index, Player player) {
        this.sendSlot(index, new Player[]{player});
    }

    @Override
    public void sendSlot(int index, Player[] players) {
        ContainerSetSlotPacket pk = new ContainerSetSlotPacket();
        pk.slot = index;
        pk.item = this.getItem(index).clone();

        for (Player player : players) {
            int id = player.getWindowId(this);
            if (id == -1) {
                this.close(player);
                continue;
            }
            pk.windowid = (byte) id;
            player.dataPacket(pk);
        }
    }

    @Override
    public void sendSlot(int index, Collection<Player> players) {
        this.sendSlot(index, players.stream().toArray(Player[]::new));
    }

    @Override
    public InventoryType getType() {
        return type;
    }
}
