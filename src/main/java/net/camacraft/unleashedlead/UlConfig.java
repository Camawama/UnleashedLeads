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
    public static final ForgeConfigSpec.BooleanValue LEASH_VEHICLES;
    public static final ForgeConfigSpec.BooleanValue PULL_DRIVEN_VEHICLES;
    public static final ForgeConfigSpec.BooleanValue MASS_LEADS;
    public static final ForgeConfigSpec.DoubleValue LED_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue LED_HURRY;
    public static final ForgeConfigSpec.DoubleValue UNTAMED_LED_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue UNTAMED_TOWED_HURRY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LED_BY_NATURE;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_HOLDERS_HOLD;
    public static final ForgeConfigSpec.BooleanValue MOB_TO_MOB;
    public static final ForgeConfigSpec.BooleanValue MOB_CHAINS;
    public static final ForgeConfigSpec.BooleanValue SHEARS_SNIP;
    public static final ForgeConfigSpec.BooleanValue DISPENSER_SHEARS;
    public static final ForgeConfigSpec.BooleanValue KNOT_BREAKS_WITH_FENCE;
    public static final ForgeConfigSpec.BooleanValue KNOT_RULES;
    public static final ForgeConfigSpec.BooleanValue FIREWORK_BREAKS_LEADS;
    public static final ForgeConfigSpec.BooleanValue RELEASH_TAKES_OVER;
    public static final ForgeConfigSpec.BooleanValue LEAD_SOUNDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NEVER_LEASH;
    public static final ForgeConfigSpec.BooleanValue TETHERS;
    public static final ForgeConfigSpec.DoubleValue TETHER_STIFFNESS;
    public static final ForgeConfigSpec.DoubleValue TETHER_DAMPING;
    public static final ForgeConfigSpec.DoubleValue TETHER_SWAY_DAMPING;
    public static final ForgeConfigSpec.DoubleValue TETHER_BREAKING_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue TETHER_SLACK;
    public static final ForgeConfigSpec.DoubleValue IRON_LEAD_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue DIAMOND_LEAD_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue NETHERITE_LEAD_STRENGTH;

    // ------------------------------------------------------------------ client

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ROPE_RENDERING;
    public static final ForgeConfigSpec.BooleanValue GHAST_KEEP_TURNS;
    public static final ForgeConfigSpec.BooleanValue GHAST_FACING_RESYNC;
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
    public static final ForgeConfigSpec.ConfigValue<String> IRON_ROPE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DIAMOND_ROPE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> NETHERITE_ROPE_COLOR;

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
        PULL_DRIVEN_VEHICLES = s.comment("Let a lead pull an Automobility vehicle while a player drives it: a car on a lead is towed",
                        "even with someone at the wheel, and can't drive further than its lead from a fence. Applied on the",
                        "driving player's client, where the car is simulated, through MKW Mobility.")
                .define("pullDrivenVehicles", true);
        MASS_LEADS = s.comment("Leads pull both ends by their masses (Camafy's weights), momentum kept: a player leading a",
                        "mob heavier than themselves is slowed and held (an iron golem or a parked car holds them at the",
                        "lead's length), a light mob barely slows them; a vehicle or a ridden horse feels what it tows; a",
                        "lead to a knot on a Valkyrien Skies ship pulls the ship at the knot. Fence knots in the world don't",
                        "move. Chains of mobs keep their own rules. Off: leads only pull the leashed entity, as before.")
                .define("massLeads", true);
        LED_WEIGHT = s.comment("How much of its weight a TAMED led animal (a pet, a tamed horse, donkey, mule or llama, or anything in",
                        "ledByNature) puts against a taut lead (with massLeads), 0 to 1, walking after its holder on the ground or",
                        "swimming: it is led rather than dragged. An upgraded lead wrangles easier: this is divided by its strength",
                        "squared (iron 1.5: by 2.25, diamond: by 6.25, netherite: by 16). Vehicles, boats, mobs without AI, sitting",
                        "pets and anything in the air always count their whole weight.")
                .defineInRange("ledWeight", 0.3, 0.0, 1.0);
        UNTAMED_LED_WEIGHT = s.comment("The same for an UNTAMED led animal (a cow, a pig, a wild horse): wrangling one on a plain lead is real",
                        "effort, easier with every tier (divided by the lead's strength squared: a cow is 650 kg on a plain lead, 289",
                        "on iron, 104 on diamond, 41 on netherite).")
                .defineInRange("untamedLedWeight", 1.0, 0.0, 1.0);
        LED_HURRY = s.comment("Tamed led animals keep pace with their holder: walking after it, one speeds up to the holder's speed, up to",
                        "this many times its normal leash-follow speed (2 is most animals' panic speed), and keeps walking while the",
                        "lead is taut. Tamed horses, donkeys, mules, llamas and camels keep up with a sprinting player whatever their",
                        "own speed. Untamed ones walk at vanilla's pace and are dragged on a taut lead. 1 = vanilla's pace for all.")
                .defineInRange("ledHurry", 2.5, 1.0, 8.0);
        UNTAMED_TOWED_HURRY = s.comment("An untamed animal towed by a vehicle (a car is far stronger than a player) runs along rather than be",
                        "dragged: it keeps walking on a taut lead, up to this many times its leash-follow speed. On a plain lead it",
                        "still weighs its whole weight on the car. 1 = dragged like by a player.")
                .defineInRange("untamedTowedHurry", 2.0, 1.0, 8.0);
        LED_BY_NATURE = s.comment("Entities that are led like tamed animals even untamed: ids, #tags, wildcards or /regex/.")
                .defineListAllowEmpty(List.of("ledByNature"),
                        () -> List.of("minecraft:allay", "minecraft:camel", "vanillabackport:happy_ghast"), o -> o instanceof String);
        CREATIVE_HOLDERS_HOLD = s.comment("A player in creative mode or spectating is never pulled by what they lead (by mass): it just follows.")
                .define("creativeHoldersHold", true);
        s.pop();

        s.comment("Bedrock and 1.21 to 1.21.6 lead features.").push("features");
        LEASH_BOATS = s.comment("Boats, chest boats and bamboo rafts can be leashed (1.21). Left to Vanilla Backport",
                        "when it is installed, since it leashes boats itself.")
                .define("leashBoats", true);
        LEASH_VEHICLES = s.comment("Automobility's vehicles (RIAutomobility's included) can be leashed and towed. A kart in an MKW",
                        "race can't be.")
                .define("leashVehicles", true);
        MOB_TO_MOB = s.comment("Sneak-use a mob while you hold leashed mobs to tie them to it (1.21.6).")
                .define("mobToMob", true);
        MOB_CHAINS = s.comment("Chains of mobs pull both ways: a lead between two mobs pulls whichever end is further from",
                        "what moves the chain (a player's lead, a fence, a grab by another mod), so pulling any mob of",
                        "a chain drags the rest, and two free mobs pull each other by their mass. A lead used on a mob",
                        "in a chain that hangs from nothing (or from you) takes the whole chain, its leads turned to",
                        "follow that mob, instead of untying it. False: a lead only ever pulls the mob it is tied to.")
                .define("mobChains", true);
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

        s.comment("Leads tied between two fences: use a lead on one fence, then use the fence it should run to.",
                "When either end is on a Valkyrien Skies ship the rope holds the ship back: it is slack up to its",
                "length, then pulls with a spring and damper at the knot, and snaps when the pull passes the",
                "breaking strength. Several ropes on one ship add up; each snaps on its own.").push("tethers");
        TETHERS = s.comment("Leads can be tied from fence to fence, and such ropes pull on ships.",
                        "False: no new fence-to-fence ropes; existing ones stay but pull on nothing, and snap once",
                        "stretched (snapDistance - freeLength) blocks past their length.")
                .define("enabled", true);
        TETHER_STIFFNESS = s.comment("How hard a stretched rope pulls, in newtons per block of stretch on a 1-tonne ship.",
                        "It grows with the square root of the ship's mass (a 100 t ship gets 10 times this), so",
                        "a small boat is held firmly and a big ship stretches the rope further.")
                .defineInRange("stiffness", 20000.0, 0.0, 1.0E9);
        TETHER_DAMPING = s.comment("How much a stretched rope resists being stretched faster, as a share of critical",
                        "damping (0 = a bouncy spring, 1 = no bounce at all).")
                .defineInRange("damping", 0.5, 0.0, 2.0);
        TETHER_SWAY_DAMPING = s.comment("How fast a taut rope takes the sideways sway out of its two ends moving against each other,",
                        "per second (the share of that motion lost each second, as the rope's fibres and knots rub).",
                        "Without it a chain of ships hung from a frozen one swings and jerks for good; at 4 the swing",
                        "of a chain of six dies down within half a minute, a single ship's within seconds. 0: none.")
                .defineInRange("swayDamping", 4.0, 0.0, 20.0);
        TETHER_BREAKING_STRENGTH = s.comment("Pull, in newtons, at which a rope on a ship snaps (the lead drops where it broke).",
                        "The default holds a small boat under full engine power and a big ship drifting, and",
                        "snaps when a ship rams away: a 1,000 t ship at about 1.3 m/s, a 100 t ship at about 7,",
                        "a 60 t one at about 11. A steady pull (an engine) must pass it outright.")
                .defineInRange("breakingStrength", 1000000.0, 1.0, 1.0E12);
        TETHER_SLACK = s.comment("Rope added to the distance between the fences when a rope is tied, in blocks.")
                .defineInRange("slack", 0.5, 0.0, 16.0);
        s.pop();

        s.comment("Upgraded leads, made at the smithing table with no template: lead + iron ingot, iron lead + diamond,",
                "diamond lead + netherite ingot. They are leads in every way (same free length, pull and tow length) and",
                "only harder to break. Each number is how much stronger than a plain lead: how far past its free length",
                "the lead stretches before it snaps (a plain lead snaps 7 blocks past its 5 free, at 12; at 1.5 that is",
                "5 + 7 x 1.5 = 15.5), and how hard a fence-to-fence rope of it on a ship may pull (tethers.breakingStrength",
                "times this). A carried rope snaps that much later too, but the longest rope that can be tied stays the",
                "snap distance.").push("upgradedLeads");
        IRON_LEAD_STRENGTH = s.comment("Iron lead (snaps at 15.5 blocks by default).")
                .defineInRange("ironStrength", 1.5, 1.0, 64.0);
        DIAMOND_LEAD_STRENGTH = s.comment("Diamond lead (snaps at 22.5 blocks by default).")
                .defineInRange("diamondStrength", 2.5, 1.0, 64.0);
        NETHERITE_LEAD_STRENGTH = s.comment("Netherite lead (snaps at 33 blocks by default; the item also survives fire and lava).")
                .defineInRange("netheriteStrength", 4.0, 1.0, 64.0);
        s.pop();

        s.comment("Vanilla Backport compatibility.").push("vanillaBackport");
        GHAST_FACING_RESYNC = s.comment("Resend each happy ghast's facing to its watchers every 2 seconds, so a turn a client missed never",
                        "leaves its ghast (and the boat it carries) drawn crooked for long. One small packet per ghast.")
                .define("ghastFacingResync", true);
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
        ROPE_GRAVITY = r.comment("How hard the rope sags (blocks/tick^2) under ordinary gravity. Follows each end's gravity direction,",
                        "scaled by the local strength: a sixth on the Moon, none in space, where slack floats.")
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
        IRON_ROPE_COLOR = r.comment("Colour of an iron lead's rope as #RRGGBB.")
                .define("ironColor", "#A9ADB1");
        DIAMOND_ROPE_COLOR = r.comment("Colour of a diamond lead's rope as #RRGGBB.")
                .define("diamondColor", "#3FCFC0");
        NETHERITE_ROPE_COLOR = r.comment("Colour of a netherite lead's rope as #RRGGBB.")
                .define("netheriteColor", "#4A4043");
        r.pop();

        r.comment("Vanilla Backport compatibility.").push("vanillaBackport");
        GHAST_KEEP_TURNS = r.comment("A happy ghast's turn still being drawn survives a packet that only moves it (1.20.1 replaced the",
                        "turn with the current facing, and after a lag spike the ghast stayed facing the old way until it turned",
                        "again, its boat drawn crooked against it).")
                .define("ghastKeepTurns", true);
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

    @SuppressWarnings("deprecation")
    private static final IdMatcher.Cached<EntityType<?>> LED_BY_NATURE_MATCHER =
            IdMatcher.cached(BuiltInRegistries.ENTITY_TYPE, "ledByNature", LED_BY_NATURE::get);

    /** Whether {@code type} is led like a tamed animal even untamed ({@code physics.ledByNature}). */
    public static boolean ledByNature(EntityType<?> type) {
        return LED_BY_NATURE_MATCHER.matches(type);
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
