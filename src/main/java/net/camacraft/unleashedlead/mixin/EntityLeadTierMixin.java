package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeadTier;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An upgraded lead drops as itself. Every lead that comes off an entity is dropped by that entity with
 * {@code spawnAtLocation(Items.LEAD)}: vanilla's {@code Mob.dropLeash} and its failed restore after a load,
 * Vanilla Backport's boats, this mod's boats, cars and ship knots. When the entity's lead is a tiered one
 * ({@link LeadTiers}) the drop is that tier's item instead, and the entity's lead tier is gone with it. Loot
 * and equipment drops go through the {@code ItemStack} overloads and are never touched.
 */
@Mixin(Entity.class)
public abstract class EntityLeadTierMixin {
    @Shadow
    public abstract ItemEntity spawnAtLocation(ItemStack stack, float offsetY);

    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void unleashedlead$dropTieredLead(ItemLike item, int offsetY, CallbackInfoReturnable<ItemEntity> cir) {
        if (item != Items.LEAD) return;
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide) return;
        LeadTier tier = LeadTiers.of(self);
        if (tier == LeadTier.PLAIN) return;
        LeadTiers.clear(self);
        cir.setReturnValue(this.spawnAtLocation(new ItemStack(tier.item()), (float) offsetY));
    }
}
