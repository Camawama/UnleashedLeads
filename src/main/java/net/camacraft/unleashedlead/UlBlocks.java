package net.camacraft.unleashedlead;

import net.camacraft.unleashedlead.block.LeadCleatBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's one block: the lead cleat, and what leads tie to ({@link #isAnchor}). */
public final class UlBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UnleashedLead.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UnleashedLead.MOD_ID);

    public static final RegistryObject<Block> LEAD_CLEAT = BLOCKS.register("lead_cleat", () -> new LeadCleatBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noCollission().noOcclusion().strength(0.5F)
                    .sound(SoundType.CHAIN).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Item> LEAD_CLEAT_ITEM = ITEMS.register("lead_cleat",
            () -> new BlockItem(LEAD_CLEAT.get(), new Item.Properties()));

    /** Blocks a lead ties to as to a fence: every fence, and the cleat ({@code #unleashedlead:lead_anchors}). */
    public static final TagKey<Block> LEAD_ANCHORS = BlockTags.create(UnleashedLead.id("lead_anchors"));

    private UlBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(UlBlocks::onCreativeTabs);
    }

    /** Whether a lead ties to {@code state} as to a fence. */
    public static boolean isAnchor(BlockState state) {
        return state.is(BlockTags.FENCES) || state.is(LEAD_ANCHORS) || state.getBlock() instanceof LeadCleatBlock;
    }

    /** After the lead: the upgraded leads ({@link UlItems}), then the cleat. */
    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            Item after = Items.LEAD;
            for (Item item : new Item[]{UlItems.IRON_LEAD.get(), UlItems.DIAMOND_LEAD.get(), UlItems.NETHERITE_LEAD.get(),
                    LEAD_CLEAT_ITEM.get()}) {
                event.getEntries().putAfter(after.getDefaultInstance(), item.getDefaultInstance(),
                        net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                after = item;
            }
        }
    }
}
