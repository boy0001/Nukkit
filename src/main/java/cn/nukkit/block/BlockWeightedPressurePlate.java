package cn.nukkit.block;

/**
 * Created by Snake1999 on 2016/1/11.
 * Package cn.nukkit.block in project nukkit
 */
public abstract class BlockWeightedPressurePlate extends BlockTransparentMeta {

    private byte powerLevel;
    private boolean powerSource;

    @Override
    public int getPowerLevel() {
        return powerLevel;
    }

    @Override
    public void setPowerLevel(int powerLevel) {
        this.powerLevel = (byte) powerLevel;
    }

    @Override
    public void setPowerSource(boolean powerSource) {
        this.powerSource = powerSource;
    }

    @Override
    public boolean isPowerSource() {
        return powerSource;
    }

    public BlockWeightedPressurePlate(int meta) {
        super(meta);
    }

    public BlockWeightedPressurePlate() {
        this(0);
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }
}
