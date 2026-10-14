package net.camacraft.unleashedlead.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.leash.LeashChains;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.LeashTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces 1.20.1's leash pull for pathfinding mobs (a velocity kick past 6 blocks, snapping at
 * 10) with {@link LeashPhysics}: free within 5 blocks, elastic past it, snapping at 12, measured
 * between bounding-box centres. Vanilla's own upkeep ({@code Mob.tickLeash}: re-attaching a saved
 * lead, letting go of a dead holder) still runs first; what is kept of the rest is vanilla's
 * behaviour: the home restriction around the holder, sitting pets not being pulled, horses and
 * camels reacting to the distance, and walking after the holder.
 *
 * <p>How: vanilla's pull, snap and walk all sit inside {@code if (holder != null)}, right after it
 * reads the holder. That read is wrapped: this mod's leash tick runs there, and vanilla is handed
 * no holder, so its own code does nothing. The method is never cancelled or returned from early,
 * so an injection pair another mod puts in it (at its head and its returns) stays whole.
 *
 * <p>Not applied when Vanilla Backport is installed: it replaces this same method with its own
 * leash physics (see {@code UlMixinPlugin}).
 */
@Mixin(PathfinderMob.class)
public abstract class PathfinderMobMixin extends Mob {
    protected PathfinderMobMixin(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    @Shadow
    protected abstract boolean shouldStayCloseToLeashHolder();

    @Shadow
    protected abstract double followLeashSpeed();

    @Shadow
    protected abstract void onLeashDistance(float distance);

    @WrapOperation(method = "tickLeash",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getLeashHolder()Lnet/minecraft/world/entity/Entity;"))
    @Nullable
    private Entity unleashedlead$tickLeash(PathfinderMob self, Operation<Entity> original) {
        unleashedlead$leashTick(original.call(self));
        return null; // vanilla's pull, 10-block snap and walk below have nothing to act on
    }

    @Unique
    private void unleashedlead$leashTick(@Nullable Entity holder) {
        if (holder == null || holder.level() != this.level()) return;
        Vec3 holderCentre = LeashGeometry.knownCentre(holder);
        if (holderCentre == null) return; // a knot on a ship that isn't loaded: the lead waits for it
        this.restrictTo(BlockPos.containing(holderCentre), LeashPhysics.homeRadius(this, holder));

        boolean sitting = (Object) this instanceof TamableAnimal tamable && tamable.isInSittingPose();
        LeashPhysics.Tick tick = LeashPhysics.tick(this, holder, !sitting, true);
        if (tick.snapped()) {
            this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            return;
        }
        if (sitting) return;
        // as on a normal lead: a horse or camel reacts past 6 blocks, 6/16 on a lead at 1/16
        this.onLeashDistance((float) tick.normalDistance());
        if (!this.isLeashed()) return;

        // Walk after the holder, as vanilla does, whenever there is ground (or water) to walk on,
        // unless another mod owns this mob's motion.
        double stop = LeashPhysics.WALK_STOP * tick.reach(); // 2 blocks short, less on a small lead
        // In a chain of mobs dragged from this mob's side, the holder comes to it instead (LeashChains).
        if (tick.distance() > stop && this.shouldStayCloseToLeashHolder() && (this.onGround() || this.isInWater())
                && !PhysicsOwnership.claimedByOther(this, PhysicsAspect.MOTION, LeashPhysics.OWNER)
                && !LeashChains.holderFollows(this, holder)) {
            this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            // at the holder's pace (ledHurry), not vanilla's: a led cow fell 1.3 m/s behind a walking player
            double pace = LeashTicker.paceFor((PathfinderMob) (Object) this, holder, tick.distance(), stop, this.followLeashSpeed());
            if (this.tickCount % LeashTicker.WALK_RENEW_TICKS == 0 || this.getNavigation().isDone()) {
                // to where the holder will be at the next renewal: a mob keeping a moving holder's pace spun round otherwise
                Vec3 toward = LeashTicker.walkStep(holder, LeashGeometry.centre(this), holderCentre, tick.distance(), stop);
                this.getNavigation().moveTo(this.getX() + toward.x, this.getY() + toward.y, this.getZ() + toward.z, pace);
            } else {
                this.getNavigation().setSpeedModifier(pace);
            }
        }
    }
}
