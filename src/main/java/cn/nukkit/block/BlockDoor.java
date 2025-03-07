package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.sound.DoorSound;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class BlockDoor extends BlockTransparentMeta {

    protected BlockDoor(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }

    private int getFullDamage() {
        int damage = this.getDamage();
        boolean isUp = (damage & 0x08) > 0;

        int up;
        int down;
        if (isUp) {
            down = this.getSide(Vector3.SIDE_DOWN).getDamage();
            up = damage;
        } else {
            down = damage;
            up = this.getSide(Vector3.SIDE_UP).getDamage();
        }

        boolean isRight = (up & 0x01) > 0;

        return down & 0x07 | (isUp ? 8 : 0) | (isRight ? 0x10 : 0);
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {

        double f = 0.1875;
        int damage = this.getFullDamage();

        AxisAlignedBB bb = new AxisAlignedBB(
                this.x,
                this.y,
                this.z,
                this.x + 1,
                this.y + 2,
                this.z + 1
        );

        int j = damage & 0x03;
        boolean isOpen = ((damage & 0x04) > 0);
        boolean isRight = ((damage & 0x10) > 0);

        if (j == 0) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + f
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z + 1 - f,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z,
                        this.x + f,
                        this.y + 1,
                        this.z + 1
                );
            }
        } else if (j == 1) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x + 1 - f,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + f,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z,
                        this.x + 1,
                        this.y + 1,
                        this.z + f
                );
            }
        } else if (j == 2) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z + 1 - f,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + f
                    );
                }
            } else {
                bb.setBounds(
                        this.x + 1 - f,
                        this.y,
                        this.z,
                        this.x + 1,
                        this.y + 1,
                        this.z + 1
                );
            }
        } else if (j == 3) {
            if (isOpen) {
                if (!isRight) {
                    bb.setBounds(
                            this.x,
                            this.y,
                            this.z,
                            this.x + f,
                            this.y + 1,
                            this.z + 1
                    );
                } else {
                    bb.setBounds(
                            this.x + 1 - f,
                            this.y,
                            this.z,
                            this.x + 1,
                            this.y + 1,
                            this.z + 1
                    );
                }
            } else {
                bb.setBounds(
                        this.x,
                        this.y,
                        this.z + 1 - f,
                        this.x + 1,
                        this.y + 1,
                        this.z + 1
                );
            }
        }

        return bb;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.getSide(0).getId() == AIR) {
                if (this.getSide(1) instanceof BlockDoor) {
                    this.getLevel().setBlock(this.getSide(1), new BlockAir(), false);
                    this.getLevel().useBreakOn(this);
                }

                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, int face, double fx, double fy, double fz) {
        return this.place(item, block, target, face, fx, fy, fz, null);
    }

    @Override
    public boolean place(Item item, Block block, Block target, int face, double fx, double fy, double fz, Player player) {
        if (face == 1) {
            Block blockUp = this.getSide(1);
            Block blockDown = this.getSide(0);
            if (!blockUp.canBeReplaced() || blockDown.isTransparent()) {
                return false;
            }
            int direction = player != null ? player.getDirection() : 0;
            int[] faces = {3, 4, 2, 5};

            Block next = this.getSide(faces[((direction + 2) % 4)]);
            Block next2 = this.getSide(faces[direction]);
            int metaUp = 0x08;
            if (next.getId() == this.getId() || (!next2.isTransparent() && next.isTransparent())) { //Door hinge
                metaUp |= 0x01;
            }

            this.setDamage(direction & 0x03);
            this.getLevel().setBlock(block, this, true, true); //Bottom
            this.getLevel().setBlock(blockUp, Block.get(this.getId(), metaUp), true); //Top
            return true;
        }

        return false;
    }

    @Override
    public boolean onBreak(Item item) {
        if ((this.getDamage() & 0x08) == 0x08) {
            Block down = this.getSide(0);
            if (down.getId() == this.getId()) {
                this.getLevel().setBlock(down, new BlockAir(), true);
            }
        } else {
            Block up = this.getSide(1);
            if (up.getId() == this.getId()) {
                this.getLevel().setBlock(up, new BlockAir(), true);
            }
        }
        this.getLevel().setBlock(this, new BlockAir(), true);

        return true;
    }

    @Override
    public boolean onActivate(Item item) {
        return this.onActivate(item, null);
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (!this.toggle(player)) {
            return false;
        }

        this.level.addSound(new DoorSound(this));
        return true;
    }

    public boolean toggle(Player player) {
        DoorToggleEvent event = new DoorToggleEvent(this, player);
        this.getLevel().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        if ((this.meta & 0x08) == 0x08) { //Top
            Block down = this.getSide(Vector3.SIDE_DOWN);
            if (down.getId() != this.getId()) {
                return false;
            }

            this.getLevel().setBlock(down, Block.get(this.getId(), down.getDamage() ^ 0x04), true);
        } else { //Down
            this.meta ^= 0x04;
            this.getLevel().setBlock(this, this, true);
        }

        return true;
    }

    public boolean isOpen() {
        return (this.meta & 0x04) > 0;
    }
}
