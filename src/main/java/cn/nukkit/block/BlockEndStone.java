package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

/**
 * Created on 2015/12/1 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockEndStone extends BlockSolid {

    public BlockEndStone() {
    }

    @Override
    public String getName() {
        return "End Stone";
    }

    @Override
    public int getId() {
        return END_STONE;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 45;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int[][] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() > ItemTool.TIER_WOODEN) {
            return new int[][]{
                    {Item.END_STONE, 0, 1}
            };
        } else {
            return new int[0][0];
        }
    }
}
