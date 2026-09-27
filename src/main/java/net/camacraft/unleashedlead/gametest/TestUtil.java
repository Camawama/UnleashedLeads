package net.camacraft.unleashedlead.gametest;

import com.mojang.authlib.GameProfile;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.Locale;
import java.util.UUID;

/** Shared setup for the gametests. Positions passed in are relative to the test's structure. */
final class TestUtil {
    /** A 24x16x24 plot with a stone floor at y = 0 (floor top at y = 1). */
    static final String PLATFORM = "platform";

    private TestUtil() {}

    /** A fence at {@code rel} with a knot on it. */
    static LeashFenceKnotEntity knot(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, Blocks.OAK_FENCE);
        return LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(rel));
    }

    /**
     * A mob with no AI at all standing at {@code rel}. Vanilla doesn't move a NoAI mob, not even
     * by its velocity, so this is for tests where nothing should move.
     */
    static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, Vec3 rel) {
        T mob = helper.spawn(type, rel);
        mob.setNoAi(true);
        return mob;
    }

    /**
     * A mob with its goals removed (no wandering) that still moves, falls and is pushed: for the
     * physics tests. It still walks after its holder, as vanilla leashed mobs do.
     */
    static <T extends Mob> T idle(GameTestHelper helper, EntityType<T> type, Vec3 rel) {
        T mob = helper.spawn(type, rel);
        mob.removeFreeWill();
        return mob;
    }

    /** A fake server player standing at {@code rel}, unique to the calling test. */
    static FakePlayer player(GameTestHelper helper, Vec3 rel) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, "ul_" + id.toString().substring(0, 8)));
        Vec3 abs = helper.absoluteVec(rel);
        player.setPos(abs.x, abs.y, abs.z);
        return player;
    }

    /**
     * Where to put the feet of an entity {@code height} tall, standing at relative height
     * {@code feetY}, so that its bounding-box centre is exactly {@code distance} from
     * {@code from}'s centre, along +X ({@code sign} +1) or -X (-1).
     */
    static Vec3 feetAtDistance(GameTestHelper helper, Entity from, double distance, double feetY, double height, int sign) {
        Vec3 c = helper.relativeVec(LeashGeometry.centre(from));
        double dy = feetY + height / 2.0 - c.y;
        double dx = Math.sqrt(Math.max(0.0, distance * distance - dy * dy));
        return new Vec3(c.x + sign * dx, feetY, c.z);
    }

    static double distance(Entity a, Entity b) {
        return LeashGeometry.centre(a).distanceTo(LeashGeometry.centre(b));
    }

    static int itemsNear(GameTestHelper helper, Entity at, Item item, double radius) {
        int count = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at.blockPosition()).inflate(radius))) {
            if (e.getItem().is(item)) count += e.getItem().getCount();
        }
        return count;
    }

    static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }
}
