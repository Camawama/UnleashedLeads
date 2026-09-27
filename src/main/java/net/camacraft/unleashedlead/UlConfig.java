package net.camacraft.unleashedlead;

import net.camacraft.camafy.config.ConfigUtil;
import net.camacraft.camafy.config.IdMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Three files:
 * <ul>
 *   <li>{@code unleashedlead-common.toml}: the lead recipe, which is decided while datapacks load,
 *   before any world config exists.</li>
 *   <li>{@code serverconfig/unleashedlead-server.toml}: leash physics and the gameplay features.
 *   Forge sends it to every client that joins, which matters because a boat a player drives is
 *   simulated on that player's client with these same numbers.</li>
 *   <li>{@code unleashedlead-client.toml}: how the rope looks.</li>
 * </ul>
 * Every read goes through {@link ConfigUtil#getOr}, so code that runs before a file has loaded
 * (a client on the title screen, a gametest booting) sees the defaults instead of throwing.
 */
public final class UlConfig {
    private UlConfig() {}

    // ------------------------------------------------------------------ common

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue NEW_LEAD_RECIPE;

    // ------------------------------------------------------------------ server

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.BooleanValue PHYSICS;
    public static final ForgeConfigSpec.DoubleValue FREE_LENGTH;
    public static final ForgeConfigSpec.DoubleValue SNAP_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue STIFFNESS;
    public static final ForgeConfigSpec.DoubleValue RADIAL_DAMPING;
    public static final ForgeConfigSpec.DoubleValue MAX_PULL;
    public static final ForgeConfigSpec.BooleanValue NO_FALL_DAMAGE_WHILE_HELD;
    public static final ForgeConfigSpec.BooleanValue FACE_HOLDER;
    public static final ForgeConfigSpec.BooleanValue PULL_PLAYER_BOATS;
    public static final ForgeConfigSpec.BooleanValue LEASH_BOATS;
    public static final ForgeConfigSpec.BooleanValue MOB_TO_MOB;
    public static final ForgeConfigSpec.BooleanValue SHEARS_SNIP;
    public static final ForgeConfigSpec.BooleanValue DISPENSER_SHEARS;
    public static final ForgeConfigSpec.BooleanValue KNOT_BREAKS_WITH_FENCE;
    public static final ForgeConfigSpec.BooleanValue KNOT_RULES;
    public static final ForgeConfigSpec.BooleanValue FIREWORK_BREAKS_LEADS;
    public static final ForgeConfigSpec.BooleanValue RELEASH_TAKES_OVER;
    public static final ForgeConfigSpec.BooleanValue LEAD_SOUNDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NEVER_LEASH;

    // ------------------------------------------------------------------ client

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ROPE_RENDERING;
    public static final ForgeConfigSpec.DoubleValue ROPE_THICKNESS;
    public static final ForgeConfigSpec.DoubleValue ROPE_SEGMENT_LENGTH;
    public static final ForgeConfigSpec.IntValue ROPE_MAX_POINTS;
    public static final ForgeConfigSpec.BooleanValue ROPE_COLLISIONS;
    public static final ForgeConfigSpec.DoubleValue ROPE_GRAVITY;
    public static final ForgeConfigSpec.DoubleValue ROPE_DAMPING;
    public static final ForgeConfigSpec.DoubleValue ROPE_SLACK;
    public static final ForgeConfigSpec.DoubleValue ROPE_SIMULATION_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue ROPE_RENDER_DISTANCE;
    public static final ForgeConfigSpec.IntValue ROPE_MAX_SIMULATED;
    public static final ForgeConfigSpec.ConfigValue<String> ROPE_COLOR;
    public static final ForgeConfigSpec.DoubleValue ROPE_STRIPE_SHADE;

    static {
        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("recipe");
        NEW_LEAD_RECIPE = c.comment(
                        "Use the 1.21.6 lead recipe: 5 string make 2 leads (the slimeball in the middle is",
                        "replaced by string). False keeps the old slimeball recipe. Applies on the next",
                        "world load or /reload.")
                .define("newLeadRecipe", true);
        c.pop();
        COMMON_SPEC = c.build();

        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        s.comment("Leash physics (Bedrock / Java 1.21.6 style). Distances are measured between the centres",
                "of the two entities' bounding boxes.").push("physics");
        PHYSICS = s.comment("Elastic leash physics instead of 1.20.1's velocity kick. False keeps vanilla's pull",
                        "(the snap distance below still applies).")
                .define("enabled", true);
        FREE_LENGTH = s.comment("How far a leashed entity can roam before the lead starts pulling (1.21.6: 5).")
                .defineInRange("freeLength", 5.0, 1.0, 64.0);
        SNAP_DISTANCE = s.comment("Distance at which the lead snaps (Bedrock and Java 1.21.6: 12; vanilla 1.20.1: 10).")
                .defineInRange("snapDistance", 12.0, 2.0, 128.0);
        STIFFNESS = s.comment("Spring strength past the free length: speed added toward the holder per tick, per",
                        "block of stretch (blocks/tick^2 per block).")
                .defineInRange("stiffness", 0.05, 0.0, 2.0);
        RADIAL_DAMPING = s.comment("Share of the speed AWAY from the holder that a taut lead takes out each tick.",
                        "1 is an unstretchable rope, lower is bouncier. Speed across the rope is never touched,",
                        "which is what lets a hanging mob swing like a pendulum.")
                .defineInRange("radialDamping", 0.5, 0.0, 1.0);
        MAX_PULL = s.comment("Most speed the lead can add in one tick (blocks/tick).")
                .defineInRange("maxPull", 0.6, 0.0, 4.0);
        NO_FALL_DAMAGE_WHILE_HELD = s.comment("A taut lead carrying an entity's weight resets its fall distance, so hanging",
                        "or being lowered on a lead never builds up fall damage.")
                .define("noFallDamageWhileHeld", true);
        FACE_HOLDER = s.comment("Entities dragged by a taut lead turn to face the holder (1.21.6).")
                .define("faceHolder", true);
        PULL_PLAYER_BOATS = s.comment("Let a lead pull a boat a player is steering. Off by default: in Bedrock and Java 1.21.6",
                        "a lead has no effect on a player-driven boat (it still snaps at the snap distance).",
                        "When on, the pull is applied on the driving player's client, where the boat is simulated.")
                .define("pullPlayerDrivenBoats", false);
        s.pop();

        s.comment("Bedrock and 1.21 to 1.21.6 lead features.").push("features");
        LEASH_BOATS = s.comment("Boats, chest boats and bamboo rafts can be leashed (1.21). Left to Vanilla Backport",
                        "when it is installed, since it leashes boats itself.")
                .define("leashBoats", true);
        MOB_TO_MOB = s.comment("Sneak-use a mob while you hold leashed mobs to tie them to it (1.21.6).")
                .define("mobToMob", true);
        SHEARS_SNIP = s.comment("Using shears on an entity cuts its lead and every lead tied to it (1.21.6).")
                .define("shearsSnip", true);
        DISPENSER_SHEARS = s.comment("A dispenser with shears cuts the leads of the entities in front of it (1.21.6).")
                .define("dispenserShears", true);
        KNOT_BREAKS_WITH_FENCE = s.comment("A lead knot breaks the moment its fence is gone, instead of up to 5 seconds later.")
                .define("knotBreaksWithFence", true);
        KNOT_RULES = s.comment("1.21.6 knot rules: using a knot (or its fence) with no leads to tie takes the knot's leads",
                        "into your hand, sneak-use only ties, and a knot with nothing tied to it disappears.")
                .define("knotRules", true);
        FIREWORK_BREAKS_LEADS = s.comment("Boosting with a firework rocket while gliding breaks the leads you hold (1.21.6).")
                .define("fireworkBreaksLeads", true);
        RELEASH_TAKES_OVER = s.comment("Using a lead on an entity already tied to a knot or a mob unties it and ties it to",
                        "you (1.21.6). Entities held by another player can never be taken.")
                .define("releashTakesOver", true);
        LEAD_SOUNDS = s.comment("Play the lead tied / untied / break sounds (1.21.6).")
                .define("leadSounds", true);
        NEVER_LEASH = s.comment("Entities that can never be leashed: ids, #tags, wildcards or /regex/.",
                        "Entities whose motion another mod has claimed (MKW karts and drivers, grappling players)",
                        "are refused regardless.")
                .defineListAllowEmpty(List.of("neverLeash"), List::of, o -> o instanceof String);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder r = new ForgeConfigSpec.Builder();
        r.comment("The simulated rope drawn for every lead.").push("rope");
        ROPE_RENDERING = r.comment("Draw leads as a simulated rope. False draws vanilla's leash line.")
                .define("enabled", true);
        ROPE_THICKNESS = r.comment("Rope width in blocks (vanilla 1.20.1: 0.025, Java 1.21.6: 0.05).")
                .defineInRange("thickness", 0.05, 0.005, 0.5);
        ROPE_SEGMENT_LENGTH = r.comment("Length of one simulated segment in blocks. Smaller is smoother and costs more.")
                .defineInRange("segmentLength", 0.3, 0.05, 2.0);
        ROPE_MAX_POINTS = r.comment("Most points one rope is made of.")
                .defineInRange("maxPoints", 40, 4, 256);
        ROPE_COLLISIONS = r.comment("Let the rope rest on and drape over blocks.")
                .define("collisions", true);
        ROPE_GRAVITY = r.comment("How hard the rope sags (blocks/tick^2). Follows each end's gravity direction.")
                .defineInRange("gravity", 0.05, 0.0, 1.0);
        ROPE_DAMPING = r.comment("Share of the rope's sway lost per tick.")
                .defineInRange("damping", 0.06, 0.0, 1.0);
        ROPE_SLACK = r.comment("How much of the unused lead hangs as slack when the ends are closer than the free",
                        "length (0 = always straight, 1 = the whole free length).")
                .defineInRange("slack", 0.35, 0.0, 1.0);
        ROPE_SIMULATION_DISTANCE = r.comment("Ropes further than this from the camera are drawn as a fixed curve instead of simulated.")
                .defineInRange("simulationDistance", 48.0, 0.0, 512.0);
        ROPE_RENDER_DISTANCE = r.comment("Ropes further than this from the camera are not drawn.")
                .defineInRange("renderDistance", 96.0, 8.0, 1024.0);
        ROPE_MAX_SIMULATED = r.comment("Most ropes simulated at once; the nearest win, the rest are drawn as a fixed curve.")
                .defineInRange("maxSimulated", 64, 0, 4096);
        ROPE_COLOR = r.comment("Rope colour as #RRGGBB (vanilla lead brown: #7F664C).")
                .define("color", "#7F664C");
        ROPE_STRIPE_SHADE = r.comment("Brightness of every other stripe, for vanilla's two-tone lead look (1 = no stripes).")
                .defineInRange("stripeShade", 0.7, 0.0, 1.0);
        r.pop();
        CLIENT_SPEC = r.build();
    }

    /** Forge marks the vanilla registry deprecated in favour of its wrapper; ids and tags are the same, and Camafy's matcher wants a {@code Registry}. */
    @SuppressWarnings("deprecation")
    private static final IdMatcher.Cached<EntityType<?>> NEVER_LEASH_MATCHER =
            IdMatcher.cached(BuiltInRegistries.ENTITY_TYPE, "neverLeash", NEVER_LEASH::get);

    public static boolean neverLeash(EntityType<?> type) {
        return NEVER_LEASH_MATCHER.matches(type);
    }

    public static boolean on(ForgeConfigSpec.BooleanValue value) {
        return ConfigUtil.getOr(value);
    }

    public static double num(ForgeConfigSpec.DoubleValue value) {
        return ConfigUtil.getOr(value);
    }

    public static int num(ForgeConfigSpec.IntValue value) {
        return ConfigUtil.getOr(value);
    }
}
