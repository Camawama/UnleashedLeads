package net.camacraft.unleashedlead.mixin;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces 1.20.1's leash pull for pathfinding mobs (a velocity kick past 6 blocks, snapping at
 * 10) with {@link LeashPhysics}: free within 5 blocks, elastic past it, snapping at 12, measured
 * between bounding-box centres. Vanilla's own upkeep ({@code Mob.tickLeash}: re-attaching a saved
 * lead, letting go of a dead holder) still runs first; what is kept of the rest is vanilla's
 * behaviour: the home restriction around the holder, sitting pets not being pulled, horses and
 * camels reacting to the distance, and walking after the holder.
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

    @Inject(method = "tickLeash", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;tickLeash()V", shift = At.Shift.AFTER))
    private void unleashedlead$tickLeash(CallbackInfo ci) {
        ci.cancel();
        Entity holder = this.getLeashHolder();
        if (holder == null || holder.level() != this.level()) return;
        Vec3 holderCentre = LeashGeometry.centre(holder);
        this.restrictTo(BlockPos.containing(holderCentre), (int) LeashPhysics.freeLength(this));

        boolean sitting = (Object) this instanceof TamableAnimal tamable && tamable.isInSittingPose();
        LeashPhysics.Tick tick = LeashPhysics.tick(this, holder, !sitting);
        if (tick.snapped()) {
            this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            return;
        }
        if (sitting) return;
        this.onLeashDistance((float) tick.distance());
        if (!this.isLeashed()) return;

        // Walk after the holder, as vanilla does, whenever there is ground (or water) to walk on,
        // unless another mod owns this mob's motion.
        if (tick.distance() > 2.0 && this.shouldStayCloseToLeashHolder() && (this.onGround() || this.isInWater())
                && !PhysicsOwnership.claimedByOther(this, PhysicsAspect.MOTION, LeashPhysics.OWNER)) {
            this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            if (this.tickCount % 5 == 0 || this.getNavigation().isDone()) {
                Vec3 toward = holderCentre.subtract(LeashGeometry.centre(this)).normalize()
                        .scale(Math.max(tick.distance() - 2.0, 0.0));
                this.getNavigation().moveTo(this.getX() + toward.x, this.getY() + toward.y, this.getZ() + toward.z,
                        this.followLeashSpeed());
            }
        }
    }
}
