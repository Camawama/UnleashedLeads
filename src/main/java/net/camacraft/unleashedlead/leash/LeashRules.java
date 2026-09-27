package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlSounds;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What using things on leashed entities, knots and fences does: the Java 1.21.6 / Bedrock rules.
 *
 * <p>Entity interactions ({@link #interact}) run from Forge's {@code EntityInteract} event at
 * HIGH priority, before the entity's own interaction (and before Vanilla Backport's handler, which
 * then never sees a use this mod handled), in Bedrock's order: sneak-use re-ties, shears cut,
 * using your own leashed entity unties it, a lead ties it. Each runs on both sides; only the server
 * changes anything, the client just predicts the arm swing.
 */
public final class LeashRules {
    /** How far from a holder to look for what it holds; past any snap distance, so nothing is missed. */
    public static final double SEARCH_RADIUS = 20.0;

    private LeashRules() {}

    // ------------------------------------------------------------------ entity use

    public static InteractionResult interact(Player player, Entity target, InteractionHand hand) {
        if (!target.isAlive() || player.isSpectator()) return InteractionResult.PASS;
        Level level = player.level();
        boolean server = !level.isClientSide;
        ItemStack stack = player.getItemInHand(hand);
        boolean mainHand = hand == InteractionHand.MAIN_HAND;

        // 1. Sneak-use a mob while holding leashed mobs: they are tied to it instead (1.21.6).
        if (mainHand && player.isSecondaryUseActive() && UlConfig.on(UlConfig.MOB_TO_MOB)
                && !(target instanceof LeashFenceKnotEntity) && Leashes.canHold(target)) {
            if (retie(player, target, server) > 0) return InteractionResult.sidedSuccess(!server);
        }

        // 2. Shears cut the target's lead and every lead tied to it (1.21.6, Bedrock 1.21.90).
        if (stack.is(Tags.Items.SHEARS) && UlConfig.on(UlConfig.SHEARS_SNIP)) {
            if (hasSnippable(player, target)) {
                if (server) {
                    if (snip(target, player) == 0) return InteractionResult.FAIL;
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                }
                return InteractionResult.sidedSuccess(!server);
            }
            // Only another player's lead is on it: never cut by hand (Bedrock). Vanilla leaves leads
            // alone anyway (a held sheep still gets sheared), but Vanilla Backport's shears rule would
            // cut it after us, so with it installed the use is refused outright.
            if (VanillaBackport.present() && Leashes.heldByOtherPlayer(target, player)) return InteractionResult.FAIL;
        }

        // 3. Using your own leashed entity takes the lead back.
        if (mainHand && Leashes.isLeashable(target) && Leashes.holder(target) == player) {
            if (server) {
                Leashes.detach(target, !player.getAbilities().instabuild);
                UlSounds.play(level, target.position(), UlSounds.LEAD_UNTIED, player);
                target.gameEvent(GameEvent.ENTITY_INTERACT, player);
            }
            return InteractionResult.sidedSuccess(!server);
        }

        // 4. A lead ties it to you; one tied to a knot or a mob is taken over (1.21.6).
        if (stack.is(Items.LEAD) && Leashes.isLeashable(target) && (Leashes.refused(target)
                || Leashes.heldByOtherPlayer(target, player) || (server && !mayGrab(player, target)))) {
            // Refused outright, and nobody else may tie it either: vanilla's own lead handling, or
            // Vanilla Backport's, whose canBeLeashed no longer refuses an entity already leashed
            // and would let a lead take it from another player.
            return InteractionResult.FAIL;
        }
        if (stack.is(Items.LEAD) && canAttach(player, target)) {
            if (server) {
                if (Leashes.holder(target) != null) Leashes.detach(target, true);
                Leashes.attach(target, player);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                UlSounds.play(level, target.position(), UlSounds.LEAD_TIED, player);
                target.gameEvent(GameEvent.ENTITY_INTERACT, player);
            }
            return InteractionResult.sidedSuccess(!server);
        }
        return InteractionResult.PASS;
    }

    /**
     * Ties every entity {@code player} holds (except {@code target} itself) to {@code target}.
     * Skips anything that would close a loop or snap straight away. Returns how many moved (or
     * would move, when {@code apply} is false).
     */
    public static int retie(Player player, Entity target, boolean apply) {
        if (apply && !mayGrab(player, target)) return 0;
        Vec3 targetCentre = LeashGeometry.centre(target);
        int moved = 0;
        for (Entity e : Leashes.leashedTo(player, SEARCH_RADIUS)) {
            if (e == target || Leashes.wouldLoop(e, target)) continue;
            if (LeashGeometry.centre(e).distanceTo(targetCentre) > LeashPhysics.snapDistance(e)) continue;
            if (apply) Leashes.attach(e, target);
            moved++;
        }
        if (apply && moved > 0) {
            UlSounds.play(player.level(), target.position(), UlSounds.LEAD_TIED, player);
            target.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return moved;
    }

    /** Whether {@code player} may tie a lead to {@code target} right now. */
    public static boolean canAttach(Player player, Entity target) {
        if (!Leashes.isLeashable(target) || Leashes.refused(target)) return false;
        Entity current = Leashes.holder(target);
        if (current instanceof Player) return false; // yours was handled as "untie"; others' can't be taken
        if (current != null && !UlConfig.on(UlConfig.RELEASH_TAKES_OVER)) return false;
        if (!player.level().isClientSide && !mayGrab(player, target)) return false;
        if (target instanceof Mob mob) return mobAccepts(mob, player);
        return true;
    }

    /** {@code Mob.canBeLeashed}, asked as if the mob were free (it refuses whenever it is leashed). */
    private static boolean mobAccepts(Mob mob, Player player) {
        Entity current = mob.getLeashHolder();
        if (current == null) return mob.canBeLeashed(player);
        if (mob.level().isClientSide) return !(mob instanceof Enemy);
        mob.dropLeash(false, false); // no packet, no item: undone on the next line
        boolean accepts = mob.canBeLeashed(player);
        mob.setLeashedTo(current, false);
        return accepts;
    }

    // ------------------------------------------------------------------ shears

    /** Whether shears on {@code target} would cut anything {@code player} may cut. */
    public static boolean hasSnippable(Player player, Entity target) {
        boolean own = Leashes.holder(target) != null && !Leashes.heldByOtherPlayer(target, player);
        return own || Leashes.holdsAny(target, SEARCH_RADIUS);
    }

    /**
     * Cuts {@code target}'s lead and every lead tied to it, dropping the leads. A knot left with
     * nothing on it goes too. {@code cause} is the player (null for a dispenser), whose rights are
     * checked: leads held by another player are never cut by hand (Bedrock).
     */
    public static int snip(Entity target, @Nullable Player cause) {
        Level level = target.level();
        if (level.isClientSide) return 0;
        if (cause != null && !mayGrab(cause, target)) return 0;
        int cut = 0;
        Entity holder = Leashes.holder(target);
        if (holder != null && (cause == null || !Leashes.heldByOtherPlayer(target, cause))) {
            Leashes.detach(target, true);
            cut++;
        }
        for (Entity e : Leashes.leashedTo(target, SEARCH_RADIUS)) {
            Leashes.detach(e, true);
            cut++;
        }
        if (cut == 0) return 0;
        if (target instanceof LeashFenceKnotEntity knot) {
            knot.dropItem(null); // the knot's own break sound
            knot.discard();
        }
        UlSounds.play(level, target.position(), UlSounds.SHEARS_SNIP, cause);
        UlSounds.play(level, target.position(), UlSounds.LEAD_UNTIED, cause);
        target.gameEvent(GameEvent.SHEAR, cause);
        return cut;
    }

    /** A dispenser holding shears, facing {@code pos}: cuts the leads of one entity there. */
    public static boolean dispenserSnip(ServerLevel level, BlockPos pos) {
        if (!UlConfig.on(UlConfig.DISPENSER_SHEARS)) return false;
        List<Entity> candidates = level.getEntities((Entity) null, new AABB(pos),
                e -> e.isAlive() && (Leashes.holder(e) != null || Leashes.holdsAny(e, SEARCH_RADIUS)));
        for (Entity e : candidates) {
            if (snip(e, null) > 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ knots and fences

    /**
     * Using anything on a fence (vanilla's {@code LeadItem.bindPlayerMobs}, reached from
     * {@code FenceBlock.use}). Ties every entity the player holds within reach, boats included,
     * to a knot there; with nothing to tie, takes the leads already on the fence's knot (1.21.6).
     * Measured in world space, so a fence on a Valkyrien Skies ship works.
     */
    public static InteractionResult useFence(Player player, Level level, BlockPos pos) {
        if (level.isClientSide) return InteractionResult.PASS;
        Vec3 fence = Ships.toWorld(level, Vec3.atCenterOf(pos));
        List<Entity> held = heldWithin(player, fence);
        if (!held.isEmpty()) {
            LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, pos);
            knot.playPlacementSound();
            for (Entity e : held) Leashes.attach(e, knot);
            UlSounds.play(level, fence, UlSounds.LEAD_TIED, player);
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
            return InteractionResult.SUCCESS;
        }
        if (UlConfig.on(UlConfig.KNOT_RULES) && !player.isSecondaryUseActive()) {
            LeashFenceKnotEntity knot = existingKnot(level, pos);
            if (knot != null && collect(player, knot) > 0) return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Using a knot (replaces {@code LeashFenceKnotEntity.interact} on the server). */
    public static InteractionResult useKnot(LeashFenceKnotEntity knot, Player player) {
        Level level = knot.level();
        Vec3 centre = LeashGeometry.centre(knot);
        List<Entity> held = heldWithin(player, centre);
        if (!held.isEmpty()) {
            for (Entity e : held) Leashes.attach(e, knot);
            UlSounds.play(level, centre, UlSounds.LEAD_TIED, player);
            knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
            return InteractionResult.CONSUME;
        }
        if (UlConfig.on(UlConfig.KNOT_RULES)) {
            if (player.isSecondaryUseActive()) return InteractionResult.CONSUME; // sneak-use only ties
            if (collect(player, knot) > 0) return InteractionResult.CONSUME;
        }
        // Nothing to tie, nothing taken: vanilla takes the knot down.
        knot.discard();
        if (player.getAbilities().instabuild) {
            for (Entity e : Leashes.leashedTo(knot, SEARCH_RADIUS)) Leashes.detach(e, false);
        }
        knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
        return InteractionResult.CONSUME;
    }

    /**
     * Server, every knot tick (from {@code mixin.HangingEntityMixin}). Returns true when the knot
     * was removed.
     * <ul>
     *   <li>Its fence is gone: it breaks at once, not on vanilla's 100-tick survival check
     *   (Bedrock 1.21.100). The leads drop at their entities on their next tick.</li>
     *   <li>Nothing has been tied to it for a while: it disappears (1.21.6 / Bedrock). A mob
     *   loading later with a saved lead to this fence re-creates it, as vanilla always has.</li>
     * </ul>
     */
    public static boolean knotTick(LeashFenceKnotEntity knot) {
        if (knot.isRemoved()) return false;
        if (UlConfig.on(UlConfig.KNOT_BREAKS_WITH_FENCE) && !knot.survives()) {
            knot.dropItem(null);
            knot.discard();
            return true;
        }
        if (UlConfig.on(UlConfig.KNOT_RULES) && knot.tickCount > EMPTY_KNOT_GRACE
                && knot.tickCount % 20 == 0 && !Leashes.holdsAny(knot, SEARCH_RADIUS)) {
            knot.dropItem(null);
            knot.discard();
            return true;
        }
        return false;
    }

    /** Ticks a knot may sit empty after appearing (world load, chunk load) before it counts as abandoned. */
    private static final int EMPTY_KNOT_GRACE = 100;

    /** Moves the knot's leads into the player's hand (those close enough); a knot left empty goes. */
    public static int collect(Player player, LeashFenceKnotEntity knot) {
        Vec3 at = LeashGeometry.centre(player);
        int taken = 0, left = 0;
        for (Entity e : Leashes.leashedTo(knot, SEARCH_RADIUS)) {
            if (LeashGeometry.centre(e).distanceTo(at) <= LeashPhysics.snapDistance(e) && !Leashes.refused(e)) {
                Leashes.attach(e, player);
                taken++;
            } else {
                left++;
            }
        }
        if (taken > 0) {
            UlSounds.play(knot.level(), LeashGeometry.centre(knot), UlSounds.LEAD_UNTIED, player);
            if (left == 0) {
                knot.dropItem(null);
                knot.discard();
            }
        }
        return taken;
    }

    /** The entities {@code player} holds whose lead would reach {@code point} without snapping. */
    private static List<Entity> heldWithin(Player player, Vec3 point) {
        List<Entity> held = Leashes.leashedTo(player, SEARCH_RADIUS);
        held.removeIf(e -> LeashGeometry.centre(e).distanceTo(point) > LeashPhysics.snapDistance(e));
        return held;
    }

    @Nullable
    private static LeashFenceKnotEntity existingKnot(Level level, BlockPos pos) {
        for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(pos).inflate(1.0))) {
            if (knot.getPos().equals(pos)) return knot;
        }
        return null;
    }

    // ------------------------------------------------------------------ fireworks

    /** Boosting with a firework while gliding breaks the leads the player holds (1.21.6). */
    public static int fireworkBoost(Player player) {
        if (player.level().isClientSide) return 0;
        int broken = 0;
        for (Entity e : Leashes.leashedTo(player, SEARCH_RADIUS * 2)) {
            Leashes.detach(e, true);
            broken++;
        }
        if (broken > 0) UlSounds.play(player.level(), player.position(), UlSounds.LEAD_BREAK, player);
        return broken;
    }

    // ------------------------------------------------------------------ rights

    /**
     * Whether {@code player} may grab or cut {@code target}: always for their own and unowned
     * things; for another player's pet (or anything another player leads), whatever the minigame
     * host says (Game Start's {@code MatchPolicies.canHarm}: pvp, teams, grace periods).
     */
    public static boolean mayGrab(Player player, Entity target) {
        if (player.level().isClientSide) return true;
        LivingEntity owner = target instanceof OwnableEntity ownable ? ownable.getOwner() : null;
        Entity holder = Leashes.holder(target);
        boolean someoneElses = (owner != null && owner != player) || (holder instanceof Player && holder != player);
        return !someoneElses || MatchPolicies.canHarm(player, target);
    }
}
