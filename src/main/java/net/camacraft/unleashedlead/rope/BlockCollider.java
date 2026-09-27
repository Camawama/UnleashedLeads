package net.camacraft.unleashedlead.rope;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A level's blocks as the rope solver sees them, cached for one tick (a rope asks about the
 * same few cells many times per tick). Works on either side: the client renderer uses it, and
 * so do the gametests, against a server level.
 *
 * <p>Uses each block's collision shape, except where that shape is taller than the block itself
 * (fences, walls, fence gates stand 1.5 high to stop jumping mobs): there the rope would float half
 * a block above the visible post, so it drapes over the block's outline instead. Blocks without
 * collision (grass, flowers, fluids) let the rope through.
 */
public final class BlockCollider implements RopeSolver.Collider {
    private static final double[] EMPTY = new double[0];

    private final Long2ObjectOpenHashMap<double[]> cache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private BlockGetter level;

    /** Starts a new tick against {@code level}. */
    public void begin(BlockGetter level) {
        this.level = level;
        cache.clear();
    }

    @Override
    public void boxes(int bx, int by, int bz, RopeSolver.BoxSink sink) {
        if (level == null) return;
        long key = BlockPos.asLong(bx, by, bz);
        double[] boxes = cache.get(key);
        if (boxes == null) {
            boxes = compute(bx, by, bz);
            cache.put(key, boxes);
        }
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            sink.box(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3], boxes[i + 4], boxes[i + 5]);
        }
    }

    private double[] compute(int bx, int by, int bz) {
        cursor.set(bx, by, bz);
        BlockState state = level.getBlockState(cursor);
        if (state.isAir()) return EMPTY;
        VoxelShape shape = state.getCollisionShape(level, cursor, CollisionContext.empty());
        if (shape.isEmpty()) return EMPTY;
        if (shape.max(Direction.Axis.Y) > 1.0) shape = state.getShape(level, cursor, CollisionContext.empty());
        if (shape.isEmpty()) return EMPTY;
        List<AABB> parts = shape.toAabbs();
        double[] out = new double[parts.size() * 6];
        int o = 0;
        for (AABB box : parts) {
            out[o++] = box.minX + bx;
            out[o++] = box.minY + by;
            out[o++] = box.minZ + bz;
            out[o++] = box.maxX + bx;
            out[o++] = box.maxY + by;
            out[o++] = box.maxZ + bz;
        }
        return out;
    }
}
