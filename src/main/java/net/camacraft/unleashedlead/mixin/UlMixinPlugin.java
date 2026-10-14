package net.camacraft.unleashedlead.mixin;

import net.camacraft.camafy.compat.ModPresence;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Decides, once at startup, which mixins apply depending on whether Vanilla Backport is
 * installed. It owns the leash model for boats and the leash tick of {@code PathfinderMob}s (it
 * cancels {@code PathfinderMob.tickLeash} at its head), so ours for those stand down and
 * {@code VbLeashableMixin} plugs into its hooks instead; its leash renderer is switched off by
 * ours only when it is there to switch off.
 */
public final class UlMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "net.camacraft.unleashedlead.mixin.";
    /** Only without Vanilla Backport. */
    private static final Set<String> WITHOUT_VB = Set.of("PathfinderMobMixin", "BoatMixin");
    /** Only with Vanilla Backport. */
    private static final Set<String> WITH_VB = Set.of("VbLeashableMixin", "VbHappyGhastSyncMixin", "VbHappyGhastLeadTierMixin",
            "client.VbLeashFeatureRendererMixin");
    /** Only with Automobility. */
    private static final Set<String> WITH_AUTOMOBILITY = Set.of("compat.AutomobileLeashMixin");

    private boolean vanillaBackport;

    @Override
    public void onLoad(String mixinPackage) {
        vanillaBackport = detect();
    }

    /** Vanilla Backport's mod id (the same constant as {@code compat.VanillaBackport.MOD_ID}). */
    private static final String VANILLA_BACKPORT = "vanillabackport";

    /**
     * Camafy's {@code ModPresence} answers from FML's loading mod list this early; if Camafy's
     * classes can't be reached from here for any reason, ask FML directly. Deliberately touches
     * no class of this mod that names a Minecraft type: loading one now would load it before the
     * mixins that target it.
     */
    private static boolean detect() {
        return present(VANILLA_BACKPORT);
    }

    private static boolean present(String modId) {
        try {
            return ModPresence.isLoaded(modId);
        } catch (Throwable t) {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById(modId) != null;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName;
        if (WITHOUT_VB.contains(name)) return !vanillaBackport;
        if (WITH_VB.contains(name)) return vanillaBackport;
        if (WITH_AUTOMOBILITY.contains(name)) return present("automobility");
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
