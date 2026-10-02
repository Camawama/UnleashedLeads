package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlSounds;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.tether.Knots;
import net.camacraft.unleashedlead.tether.Tethers;
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
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

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

        // 4. A lead ties it to you; one tied to a knot or a mob is taken over (1.21.6), and one in a
        // chain of mobs brings the chain along (mobChains).
        if (stack.is(Items.LEAD) && Leashes.isLeashable(target) && (Leashes.refused(target)
                || Leashes.heldByOtherPlayer(target, player) || (server && !mayGrab(player, target)))) {
            // Refused outright, and nobody else may tie it either: vanilla's own lead handling, or
            // Vanilla Backport's, whose canBeLeashed no longer refuses an entity already leashed
            // and would let a lead take it from another player.
            return InteractionResult.FAIL;
        }
        if (stack.is(Items.LEAD) && canAttach(player, target)) {
            if (server) {
                ChainTake chain = takeChain(player, target);
                if (chain == ChainTake.NONE) {
                    if (Leashes.holder(target) != null) Leashes.detach(target, true);
                    Leashes.attach(target, player);
                }
                // a chain you already held just changed hands: your lead moved, no new one is used
                if (chain != ChainTake.MOVED && !player.getAbilities().instabuild) stack.shrink(1);
                UlSounds.play(level, target.position(), UlSounds.LEAD_TIED, player);
                target.gameEvent(GameEvent.ENTITY_INTERACT, player);
            }
            return InteractionResult.sidedSuccess(!server);
        }

        // 5. A knot: tie what you hold to it, start a rope from it, take its leads, or take it down
        // (the whole of vanilla's LeashFenceKnotEntity.interact, which never runs after this). Done
        // here, in the interaction event, rather than by cancelling that method at its head.
        if (target instanceof LeashFenceKnotEntity knot) {
            return server ? useKnot(knot, player, hand) : InteractionResult.SUCCESS;
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
            if (LeashGeometry.span(e, LeashGeometry.centre(e), target, targetCentre) > LeashPhysics.snapDistance(e, target)) continue;
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
        if (current != null && !UlConfig.on(UlConfig.RELEASH_TAKES_OVER) && chainToTake(player, target) == null) return false;
        if (!player.level().isClientSide && !mayGrab(player, target)) return false;
        if (target instanceof Mob mob) return mobAccepts(mob, player);
        return true;
    }

    /** What a lead used on a mob in a chain of mobs did ({@link #takeChain}). */
    public enum ChainTake {
        /** Not a chain it can take: the ordinary lead rules apply. */
        NONE,
        /** The chain was free: it is yours now, on the new lead. */
        TAKEN,
        /** You already held the chain at its other end: your lead moved to this mob. */
        MOVED
    }

    /**
     * A lead used on a mob that another mob holds takes the whole chain rather than untying it
     * (mobChains): the lead goes on that mob, and every lead from it up the chain is turned to
     * hang from the mob below, so it now leads the chain and nothing is dropped. A mob can be held
     * by only one thing, so this is the only way to hold a chain by any mob of it. Only for a
     * chain that hangs from nothing or from you; one tied to a fence or held by someone else is
     * taken over as before (just this mob, its old lead dropped).
     */
    public static ChainTake takeChain(Player player, Entity target) {
        List<Entity> path = chainToTake(player, target);
        if (path == null) return ChainTake.NONE;
        boolean yours = Leashes.holder(path.get(path.size() - 1)) == player;
        Leashes.attach(target, player);
        for (int i = 1; i < path.size(); i++) Leashes.attach(path.get(i), path.get(i - 1));
        return yours ? ChainTake.MOVED : ChainTake.TAKEN;
    }

    /**
     * The chain a lead on {@code target} would take: {@code target} and its holders up to the top
     * of the chain, or null when the rule doesn't apply (not held by a mob, the chain tied to a
     * fence or to someone else, a mob of it {@code player} may not grab, mobChains off).
     */
    @Nullable
    private static List<Entity> chainToTake(Player player, Entity target) {
        if (!UlConfig.on(UlConfig.MOB_CHAINS) || !(target instanceof Mob) || !(Leashes.holder(target) instanceof Mob)) return null;
        List<Entity> path = LeashChains.pathUp(target);
        if (path == null || path.size() < 2) return null;
        Entity top = Leashes.holder(path.get(path.size() - 1));
        if (top != null && top != player) return null;
        if (!player.level().isClientSide) {
            for (Entity e : path) {
                if (!mayGrab(player, e)) return null;
            }
        }
        return path;
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
        return own || Leashes.holdsAny(target, SEARCH_RADIUS) || hasRopes(target);
    }

    /** A knot with fence-to-fence ropes on it (server side; the client doesn't keep them per knot). */
    private static boolean hasRopes(Entity target) {
        return target instanceof LeashFenceKnotEntity knot && knot.level() instanceof ServerLevel level
                && Tethers.touches(level, knot.getPos());
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
        if (target instanceof LeashFenceKnotEntity knot && level instanceof ServerLevel serverLevel) {
            cut += Tethers.cut(serverLevel, knot.getPos(), cause);
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
                e -> e.isAlive() && (Leashes.holder(e) != null || Leashes.holdsAny(e, SEARCH_RADIUS) || hasRopes(e)));
        for (Entity e : candidates) {
            if (snip(e, null) > 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ knots and fences

    /**
     * Using anything on a fence (vanilla's {@code LeadItem.bindPlayerMobs}, reached from
     * {@code FenceBlock.use} and {@code LeadItem.useOn}), in this order:
     * <ol>
     *   <li>Ties every entity the player holds within reach, boats included, and every
     *   fence-to-fence rope they carry, to a knot there.</li>
     *   <li>With a lead in {@code hand}: ties a new rope to the fence, whose free end the player
     *   now carries to another fence ({@link Tethers#start}).</li>
     *   <li>Otherwise, not sneaking: takes the leads and ropes already on the fence's knot
     *   (1.21.6).</li>
     * </ol>
     * Measured in world space, so a fence on a Valkyrien Skies ship works. {@code hand} is null
     * when the caller doesn't say (another mod calling {@code bindPlayerMobs}): no rope is started.
     */
    public static InteractionResult useFence(Player player, Level level, BlockPos pos, @Nullable InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        // With nothing (or no lead) in the main hand the client can't tell the fence will be used, so
        // it tries the off hand straight after, in the same tick: that second use must not undo the
        // first (take back what was just tied) or add to it.
        if (hand == InteractionHand.OFF_HAND && usedJustNow(player, level, pos)) return InteractionResult.PASS;
        InteractionResult result = useFenceNow(player, serverLevel, pos, hand);
        if (result.consumesAction()) markUsed(player, level, pos);
        return result;
    }

    /** As {@link #useFence(Player, Level, BlockPos, InteractionHand)}, with no hand known. */
    public static InteractionResult useFence(Player player, Level level, BlockPos pos) {
        return useFence(player, level, pos, null);
    }

    /** The hand the current fence use was made with, while a fence or lead use is running. */
    private static final ThreadLocal<InteractionHand> USING_HAND = new ThreadLocal<>();

    /** Runs {@code use} (which reaches {@code LeadItem.bindPlayerMobs}) knowing it was made with {@code hand}. */
    public static InteractionResult withHand(InteractionHand hand, Supplier<InteractionResult> use) {
        InteractionHand before = USING_HAND.get();
        USING_HAND.set(hand);
        try {
            return use.get();
        } finally {
            if (before == null) USING_HAND.remove();
            else USING_HAND.set(before);
        }
    }

    /** {@code LeadItem.bindPlayerMobs}, whoever calls it: {@link #useFence}, with the hand if a fence or lead use gave it. */
    public static InteractionResult bindPlayerMobs(Player player, Level level, BlockPos pos) {
        return useFence(player, level, pos, USING_HAND.get());
    }

    private static InteractionResult useFenceNow(Player player, ServerLevel level, BlockPos pos, @Nullable InteractionHand hand) {
        Vec3 fence = Ships.toWorld(level, Vec3.atCenterOf(pos));
        List<Entity> held = heldWithin(player, fence);
        boolean carrying = !Tethers.carriedBy(level, player).isEmpty();
        if (!held.isEmpty() || carrying) {
            LeashFenceKnotEntity knot = Knots.getOrCreate(level, pos);
            int[] ropes = carrying ? Tethers.tieCarried(level, player, knot) : new int[2];
            if (!held.isEmpty() || ropes[0] > 0) {
                knot.playPlacementSound();
                for (Entity e : held) Leashes.attach(e, knot);
                UlSounds.play(level, fence, UlSounds.LEAD_TIED, player);
                level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
                return InteractionResult.SUCCESS;
            }
            if (ropes[1] > 0) return InteractionResult.SUCCESS; // a rope brought back to its own fence: taken off
        }
        if (hand != null && Tethers.start(level, pos, player, hand)) return InteractionResult.SUCCESS;
        if (UlConfig.on(UlConfig.KNOT_RULES) && !player.isSecondaryUseActive()) {
            LeashFenceKnotEntity knot = Knots.find(level, pos);
            if (knot != null && collect(player, knot) > 0) return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Using a knot (vanilla's {@code LeashFenceKnotEntity.interact}, which this replaces on the
     * server): the fence's rules, then, with nothing to tie, start or take, vanilla's own: the
     * knot comes down.
     */
    public static InteractionResult useKnot(LeashFenceKnotEntity knot, Player player, @Nullable InteractionHand hand) {
        if (!(knot.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Vec3 centre = LeashGeometry.centre(knot);
        List<Entity> held = heldWithin(player, centre);
        int[] ropes = Tethers.tieCarried(level, player, knot);
        if (!held.isEmpty() || ropes[0] > 0) {
            for (Entity e : held) Leashes.attach(e, knot);
            UlSounds.play(level, centre, UlSounds.LEAD_TIED, player);
            knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
            return InteractionResult.CONSUME;
        }
        if (ropes[1] > 0) return InteractionResult.CONSUME; // a rope brought back to its own knot: taken off
        if (hand != null && Tethers.start(level, knot.getPos(), player, hand)) return InteractionResult.CONSUME;
        if (UlConfig.on(UlConfig.KNOT_RULES)) {
            if (player.isSecondaryUseActive()) return InteractionResult.CONSUME; // sneak-use only ties
            if (collect(player, knot) > 0) return InteractionResult.CONSUME;
        }
        // Nothing to tie, nothing taken: vanilla takes the knot down (its ropes drop with it).
        knot.discard();
        if (player.getAbilities().instabuild) {
            for (Entity e : Leashes.leashedTo(knot, SEARCH_RADIUS)) Leashes.detach(e, false);
        }
        knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
        return InteractionResult.CONSUME;
    }

    /** As {@link #useKnot(LeashFenceKnotEntity, Player, InteractionHand)} with the main hand. */
    public static InteractionResult useKnot(LeashFenceKnotEntity knot, Player player) {
        return useKnot(knot, player, InteractionHand.MAIN_HAND);
    }

    /**
     * Server, every knot tick (from {@code mixin.HangingEntityMixin}). Returns true when the knot
     * was removed.
     * <ul>
     *   <li>Its fence is gone: it breaks at once, not on vanilla's 100-tick survival check
     *   (Bedrock 1.21.100). The leads drop at their entities on their next tick.</li>
     *   <li>Nothing has been tied to it for a while: it disappears (1.21.6 / Bedrock). A mob
     *   loading later with a saved lead to this fence re-creates it, as vanilla always has. A
     *   fence-to-fence rope counts as something tied to it. A knot on a Valkyrien Skies ship that
     *   isn't loaded is left alone: with no place in the world, it can't look for what is tied to it.</li>
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
                && knot.tickCount % 20 == 0 && LeashGeometry.placed(knot)
                && !Leashes.holdsAny(knot, SEARCH_RADIUS) && !hasRopes(knot)) {
            knot.dropItem(null);
            knot.discard();
            return true;
        }
        return false;
    }

    /** Ticks a knot may sit empty after appearing (world load, chunk load) before it counts as abandoned. */
    private static final int EMPTY_KNOT_GRACE = 100;

    /**
     * Moves the knot's leads into the player's hand (those close enough), and the knot's end of
     * each fence-to-fence rope on it; a knot left empty goes.
     */
    public static int collect(Player player, LeashFenceKnotEntity knot) {
        Vec3 at = LeashGeometry.centre(player);
        int taken = 0, left = 0;
        for (Entity e : Leashes.leashedTo(knot, SEARCH_RADIUS)) {
            if (LeashGeometry.span(e, LeashGeometry.centre(e), player, at) <= LeashPhysics.snapDistance(e, player) && !Leashes.refused(e)) {
                Leashes.attach(e, player);
                taken++;
            } else {
                left++;
            }
        }
        if (knot.level() instanceof ServerLevel level) {
            int[] ropes = Tethers.collect(level, player, knot);
            taken += ropes[0];
            left += ropes[1];
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
        held.removeIf(e -> LeashGeometry.span(e, LeashGeometry.centre(e), null, point) > LeashPhysics.snapDistance(e, null));
        return held;
    }

    /** Where and when each player last used a fence to some effect (for the off-hand double use). */
    private static final Map<Player, UseMark> LAST_USE = new WeakHashMap<>();

    private record UseMark(BlockPos pos, long time) {}

    private static void markUsed(Player player, Level level, BlockPos pos) {
        LAST_USE.put(player, new UseMark(pos.immutable(), level.getGameTime()));
    }

    private static boolean usedJustNow(Player player, Level level, BlockPos pos) {
        UseMark mark = LAST_USE.get(player);
        return mark != null && mark.pos.equals(pos) && level.getGameTime() - mark.time <= 1L;
    }

    // ------------------------------------------------------------------ fireworks

    /** Boosting with a firework while gliding breaks the leads the player holds (1.21.6), and the ropes they carry. */
    public static int fireworkBoost(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        int broken = 0;
        for (Entity e : Leashes.leashedTo(player, SEARCH_RADIUS * 2)) {
            Leashes.detach(e, true);
            broken++;
        }
        broken += Tethers.breakCarried(level, player);
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
