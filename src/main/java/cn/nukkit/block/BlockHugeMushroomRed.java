package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.NukkitRandom;

/**
 * Created by Pub4Game on 28.01.2016.
 */
public class BlockHugeMushroomRed extends BlockSolidMeta {

    public BlockHugeMushroomRed() {
        this(0);
    }

    public BlockHugeMushroomRed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Red Mushroom Block";
    }

    @Override
    public int getId() {
        return RED_MUSHROOM_BLOCK;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public double getHardness() {
        return 0.2;
    }

    @Override
    public double getResistance() {
        return 1;
    }

    @Override
    public int[][] getDrops(Item item) {
        if (new NukkitRandom().nextRange(1, 20) == 0) {
            return new int[][]{
                    {Item.RED_MUSHROOM, this.meta & 0x03, 1}
            };
        } else {
            return new int[0][0];
        }
    }
}
