package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Vanilla Backport's happy ghast facing the same way on clients as on the server, so the boat it carries isn't
 * drawn crooked against it (the owner, 2026-10-03).
 *
 * <p>Why it drifted: the server sends a mob's turn once (a {@code PosRot} or {@code Rot} packet) and then only its
 * moves ({@code Pos}), and 1.20.1's client hands every move packet to {@code lerpTo} with the facing the mob has
 * right now. A {@code Pos} handled in the same client tick as the turn (two server ticks of packets after any lag
 * spike, or right after the ghast appears while chunks load) replaced the turn before it was drawn at all. The server
 * counts the turn as sent, so the client kept the old facing until the ghast turned again: in the pack harness 43 ticks
 * at 70 degrees off, 80 at 84 degrees. 1.21.6 does the same, and Vanilla Backport copies its sync faithfully.
 * <ul>
 *   <li>Client ({@code ghastKeepTurns}): a packet that only moves the ghast keeps the turn still being drawn, and one
 *   that only turns it keeps the move still being drawn (each passes the other part as the entity has it now).</li>
 *   <li>Server ({@code ghastFacingResync}): every two seconds the ghast's facing is sent again to whoever watches it,
 *   so a turn lost any other way is put right within that. The rider's own client ignores it (it steers the ghast).</li>
 * </ul>
 * Applied only with Vanilla Backport ({@code UlMixinPlugin}), and only to its happy ghast; harmless if Vanilla
 * Backport changes its sync (it only keeps a turn or a move that was sent, and resends what the server has).
 */
@Mixin(LivingEntity.class)
public abstract class VbHappyGhastSyncMixin {
    @Unique
    private static final int UNLEASHEDLEAD$RESYNC_TICKS = 40;

    @Shadow
    protected int lerpSteps;
    @Shadow
    protected double lerpX;
    @Shadow
    protected double lerpY;
    @Shadow
    protected double lerpZ;
    @Shadow
    protected double lerpYRot;
    @Shadow
    protected double lerpXRot;

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void unleashedlead$keepPendingTurn(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport,
                                               CallbackInfo ci) {
        if (this.lerpSteps <= 0 || teleport) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide || !VanillaBackport.isHappyGhast(self) || !UlConfig.on(UlConfig.GHAST_KEEP_TURNS)) return;
        // a packet that only moves it passes the facing it has now; one that only turns it passes where it is now
        boolean keepTurn = yRot == self.getYRot() && xRot == self.getXRot();
        boolean keepMove = x == self.getX() && y == self.getY() && z == self.getZ();
        if (!keepTurn && !keepMove) return;
        if (!keepMove) {
            this.lerpX = x;
            this.lerpY = y;
            this.lerpZ = z;
        }
        if (!keepTurn) {
            this.lerpYRot = yRot;
            this.lerpXRot = xRot;
        }
        this.lerpSteps = steps;
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void unleashedlead$resyncFacing(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.tickCount % UNLEASHEDLEAD$RESYNC_TICKS != 0 || !(self.level() instanceof ServerLevel level)) return;
        if (!VanillaBackport.isHappyGhast(self) || !UlConfig.on(UlConfig.GHAST_FACING_RESYNC)) return;
        level.getChunkSource().broadcast(self, new ClientboundMoveEntityPacket.Rot(self.getId(),
                unleashedlead$pack(self.getYRot()), unleashedlead$pack(self.getXRot()), self.onGround()));
        level.getChunkSource().broadcast(self, new ClientboundRotateHeadPacket(self, unleashedlead$pack(self.getYHeadRot())));
    }

    @Unique
    private static byte unleashedlead$pack(float degrees) {
        return (byte) Mth.floor(degrees * 256.0F / 360.0F);
    }
}
