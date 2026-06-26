package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores world-level witch skill toggles.
 * 保存世界级魔女技能配置，例如禁用列表。
 */
public final class WitchWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<WitchWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("world"),
            WitchWorldComponent.class
    );

    private final World world;
    private final LinkedHashSet<Identifier> disabledSkills = new LinkedHashSet<>();

    public WitchWorldComponent(World world) {
        this.world = world;
    }

    public boolean isSkillEnabled(Identifier skillId) {
        return !disabledSkills.contains(skillId);
    }

    public void setSkillEnabled(Identifier skillId, boolean enabled) {
        boolean changed = enabled ? disabledSkills.remove(skillId) : disabledSkills.add(skillId);
        if (changed) {
            sync();
        }
    }

    public Set<Identifier> getDisabledSkillIds() {
        return Set.copyOf(disabledSkills);
    }

    public void clearRoundState() {
        sync();
    }

    public void sync() {
        KEY.sync(world);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        writeIdentifierSet(buf, disabledSkills);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        readIdentifierSet(buf, disabledSkills);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.put("DisabledSkills", toNbt(disabledSkills));
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        disabledSkills.clear();
        fromNbt(tag.getList("DisabledSkills", NbtElement.STRING_TYPE), disabledSkills);
    }

    private static NbtList toNbt(Collection<Identifier> ids) {
        NbtList list = new NbtList();
        for (Identifier id : ids) {
            list.add(NbtString.of(id.toString()));
        }
        return list;
    }

    private static void fromNbt(NbtList list, Set<Identifier> ids) {
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
    }

    private static void writeIdentifierSet(RegistryByteBuf buf, Collection<Identifier> ids) {
        buf.writeVarInt(ids.size());
        for (Identifier id : ids) {
            buf.writeString(id.toString());
        }
    }

    private static void readIdentifierSet(RegistryByteBuf buf, Set<Identifier> ids) {
        ids.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            Identifier id = Identifier.tryParse(buf.readString());
            if (id != null) {
                ids.add(id);
            }
        }
    }
}
