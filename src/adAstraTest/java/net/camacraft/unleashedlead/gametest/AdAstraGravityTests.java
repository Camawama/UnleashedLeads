package net.camacraft.unleashedlead.gametest;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravitySources;
import net.camacraft.unleashedlead.UnleashedLead;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * The opt-in Ad Astra smoke test (-PadAstra): the real Ad Astra 1.15.21 on the dev runtime, and
 * pigs in its real dimensions. Camafy reads Ad Astra only by reflection, so this is the check that
 * the bridge finds its API: an orbit is weightless (Ad Astra floats the pig), the Moon is a sixth
 * of Earth (the pig falls slowly, and Ad Astra's pre-move lift is what Camafy says), and the
 * overworld is exactly Earth.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class AdAstraGravityTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("UnleashedLead/test");

    @GameTest(template = PLATFORM, timeoutTicks = 1200)
    public static void adAstraOrbitAndMoonThroughCamafy(GameTestHelper helper) {
        ServerLevel orbit = level(helper, "earth_orbit");
        ServerLevel moon = level(helper, "moon");
        if (orbit == null || moon == null) {
            helper.fail("Ad Astra's dimensions aren't loaded (orbit " + orbit + ", moon " + moon + ")");
            return;
        }
        if (!GravitySources.adAstraActive()) {
            helper.fail("Camafy's Ad Astra bridge is not active");
            return;
        }
        Pig floating = pigHigh(orbit);
        Pig lunar = pigHigh(moon);
        Pig home = TestUtil.still(helper, EntityType.PIG, new Vec3(12.5, 1.0, 12.5));
        double[] start = {floating.getY(), lunar.getY()};

        StringBuilder problems = new StringBuilder();
        float orbitFraction = GravitySources.adAstraFraction(floating);
        float moonFraction = GravitySources.adAstraFraction(lunar);
        double expectedMoon = 1.622 / 9.807;
        check(problems, orbitFraction == 0.0F, "orbit fraction " + orbitFraction);
        check(problems, Gravity.strength(floating) == 0.0 && Gravity.isWeightless(floating), "orbit strength " + Gravity.strength(floating));
        check(problems, Gravity.ambientStrength(orbit, floating.position()) == 0.0, "orbit ambient " + Gravity.ambientStrength(orbit, floating.position()));
        check(problems, Math.abs(moonFraction - expectedMoon) < 1.0E-3, "moon fraction " + moonFraction);
        check(problems, Math.abs(Gravity.strength(lunar) - expectedMoon) < 1.0E-3, "moon strength " + Gravity.strength(lunar));
        check(problems, Math.abs(Gravity.preMoveLift(lunar) - 0.08 * (1.0 - expectedMoon)) < 1.0E-4, "moon pre-move lift " + Gravity.preMoveLift(lunar));
        check(problems, Gravity.preMoveLift(floating) == 0.0, "orbit pre-move lift " + Gravity.preMoveLift(floating));
        check(problems, Gravity.strength(home) == 1.0 && Gravity.preMoveLift(home) == 0.0, "overworld strength " + Gravity.strength(home));
        if (problems.length() > 0) {
            cleanup(floating, lunar);
            helper.fail(problems.toString());
            return;
        }

        // The dimensions' chunks load (and generate: the Moon's terrain takes a while on a fresh
        // world) in the background, and a pig only ticks once its chunk does: each is watched from
        // its own first tick, until both have had 60.
        helper.onEachTick(() -> {
            if (floating.tickCount == 1) start[0] = floating.getY();
            if (lunar.tickCount == 1) start[1] = lunar.getY();
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(floating.tickCount >= 60 && lunar.tickCount >= 60,
                        "waiting for both pigs' chunks to tick (orbit " + floating.tickCount + ", moon " + lunar.tickCount + ")"))
                .thenExecute(() -> {
                    double sank = start[0] - floating.getY();
                    double fell = start[1] - lunar.getY();
                    LOGGER.info("[UnleashedLead/test] Ad Astra: orbit pig sank {} over {} ticks, moon pig fell {} over {} ticks",
                            fmt(sank), floating.tickCount, fmt(fell), lunar.tickCount);
                    cleanup(floating, lunar);
                    // What Ad Astra's travel hook makes of the Moon over those ticks (lift 0.08 x (1 - f)
                    // before the move, vanilla's 0.08 and drag after it), and Earth for comparison; an
                    // orbit doesn't move the pig at all.
                    double moonDrop = drop(lunar.tickCount - 1, 0.08 * expectedMoon);
                    double earthDrop = drop(lunar.tickCount - 1, 0.08);
                    if (Math.abs(sank) > 0.05) {
                        helper.fail("the pig in orbit moved " + fmt(sank) + " vertically");
                    } else if (!(fell > 0.6 * moonDrop && fell < 1.5 * moonDrop)) {
                        helper.fail("the pig on the Moon fell " + fmt(fell) + " over " + lunar.tickCount + " ticks; the Moon drops "
                                + fmt(moonDrop) + ", Earth " + fmt(earthDrop));
                    }
                })
                .thenSucceed();
    }

    /** How far a pig falls from rest in {@code ticks} ticks at {@code g} blocks/tick^2, vanilla drag. */
    private static double drop(int ticks, double g) {
        double v = 0.0, d = 0.0;
        for (int i = 0; i < ticks; i++) {
            d -= v;
            v = (v - g) * 0.98;
        }
        return d;
    }

    private static ServerLevel level(GameTestHelper helper, String path) {
        return helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("ad_astra", path)));
    }

    /** A pig in the open air high over the dimension's origin, its chunk forced to tick. */
    private static Pig pigHigh(ServerLevel level) {
        level.setChunkForced(0, 0, true);
        level.getChunk(0, 0); // loaded (and generated) now, not some ticks later
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(8.5, 250.0, 8.5, 0.0F, 0.0F);
        pig.setNoAi(false);
        pig.removeFreeWill();
        level.addFreshEntity(pig);
        return pig;
    }

    private static void cleanup(Pig... pigs) {
        for (Pig pig : pigs) {
            Level level = pig.level();
            pig.discard();
            if (level instanceof ServerLevel server) server.setChunkForced(0, 0, false);
        }
    }

    private static void check(StringBuilder problems, boolean ok, String what) {
        if (!ok) problems.append(problems.length() > 0 ? "; " : "").append(what);
    }
}
