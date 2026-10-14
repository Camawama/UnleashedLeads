package net.camacraft.unleashedlead.block;

import net.camacraft.unleashedlead.leash.LeadTier;
import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lead cleat: a small iron horn fixed to any face of a block, that leads tie to exactly as to a
 * fence (held mobs, a lead started from it, a fence-to-fence rope's end, the knot's rules), without a
 * fence standing up. It has no collision at all, so on a Valkyrien Skies ship's hull it snags nothing
 * and nobody trips on it; it only takes the one cell any block takes. Its knot sits on the horn
 * ({@code mixin.LeashKnotMixin}).
 */
public class LeadCleatBlock extends FaceAttachedHorizontalDirectionalBlock {
    // the horn runs across the facing: along x for north/south, along z for east/west
    private static final VoxelShape FLOOR_X = Block.box(3, 0, 6, 13, 4, 10);
    private static final VoxelShape FLOOR_Z = Block.box(6, 0, 3, 10, 4, 13);
    private static final VoxelShape CEILING_X = Block.box(3, 12, 6, 13, 16, 10);
    private static final VoxelShape CEILING_Z = Block.box(6, 12, 3, 10, 16, 13);
    // on a wall the cleat is on the side of the block it faces away from (as a button)
    private static final VoxelShape WALL_NORTH = Block.box(3, 6, 12, 13, 10, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(3, 6, 0, 13, 10, 4);
    private static final VoxelShape WALL_EAST = Block.box(0, 6, 3, 4, 10, 13);
    private static final VoxelShape WALL_WEST = Block.box(12, 6, 3, 16, 10, 13);

    public LeadCleatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.Z;
        return switch (state.getValue(FACE)) {
            case FLOOR -> alongX ? FLOOR_X : FLOOR_Z;
            case CEILING -> alongX ? CEILING_X : CEILING_Z;
            case WALL -> switch (facing) {
                case SOUTH -> WALL_SOUTH;
                case EAST -> WALL_EAST;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Using it is using a fence: tie what you hold, start a rope with a lead, take its leads back. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return LeadTier.isLead(player.getItemInHand(hand)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return LeashRules.useFence(player, level, pos, hand);
    }

    /** Half a lead knot's height (its entity is 0.5 tall): the knot's box is centred on the horn. */
    private static final double KNOT_HALF_HEIGHT = 0.25;

    /**
     * Where the knot entity stands on the cleat {@code state} at {@code pos} (world or shipyard
     * coordinates, as {@code pos}): its box centred on the horn. Also where a fence-to-fence rope pulls,
     * as a fence's rope pulls at its knot's position. Null if {@code state} isn't a cleat.
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3 knotPosition(BlockPos pos, BlockState state) {
        Vec3 horn = hornCentre(state);
        return horn == null ? null : new Vec3(pos.getX() + horn.x, pos.getY() + horn.y - KNOT_HALF_HEIGHT, pos.getZ() + horn.z);
    }

    /**
     * Where a lead's knot sits on the cleat (block-local, the middle of the horn), or null if
     * {@code state} isn't a cleat.
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3 hornCentre(BlockState state) {
        if (!(state.getBlock() instanceof LeadCleatBlock)) return null;
        return switch (state.getValue(FACE)) {
            case FLOOR -> new Vec3(0.5, 3.0 / 16.0, 0.5);
            case CEILING -> new Vec3(0.5, 13.0 / 16.0, 0.5);
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> new Vec3(0.5, 0.5, 2.0 / 16.0);
                case EAST -> new Vec3(2.0 / 16.0, 0.5, 0.5);
                case WEST -> new Vec3(14.0 / 16.0, 0.5, 0.5);
                default -> new Vec3(0.5, 0.5, 14.0 / 16.0);
            };
        };
    }
}
