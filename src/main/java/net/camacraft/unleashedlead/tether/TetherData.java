package net.camacraft.unleashedlead.tether;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every fence-to-fence rope of one level, saved with the level ({@code data/unleashedlead_tethers.dat}
 * in the dimension's folder). Ropes on Valkyrien Skies ships are in the same list: a ship lives in
 * its own dimension's shipyard.
 */
public final class TetherData extends SavedData {
    static final String NAME = "unleashedlead_tethers";

    final List<Tether> tethers = new ArrayList<>();

    public static TetherData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TetherData::load, TetherData::new, NAME);
    }

    public List<Tether> tethers() {
        return tethers;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Tether t : tethers) list.add(t.save());
        tag.put("Tethers", list);
        return tag;
    }

    /**
     * Saves the ropes {@code which} picks and loads them back in their place, as a server restart
     * would: those ropes then have to find their knots again. For the gametests, which share one
     * level, so each reloads only its own ropes.
     */
    public static void reload(ServerLevel level, Predicate<Tether> which) {
        TetherData data = get(level);
        for (int i = 0; i < data.tethers.size(); i++) {
            Tether t = data.tethers.get(i);
            if (!which.test(t)) continue;
            Tether loaded = Tether.load(t.save());
            if (loaded != null) data.tethers.set(i, loaded);
            else data.tethers.remove(i--);
        }
        data.setDirty();
    }

    static TetherData load(CompoundTag tag) {
        TetherData data = new TetherData();
        ListTag list = tag.getList("Tethers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Tether t = Tether.load(list.getCompound(i));
            if (t != null) data.tethers.add(t);
        }
        return data;
    }
}
