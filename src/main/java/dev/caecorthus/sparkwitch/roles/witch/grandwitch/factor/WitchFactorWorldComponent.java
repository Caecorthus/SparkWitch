package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/** Independent world storage; never append factor identities to the shared Witch packet.
 * 独立世界存储；绝不将因子身份加入共享魔女数据包。 */
public final class WitchFactorWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchFactorWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("witch_factor_world"), WitchFactorWorldComponent.class);
    private final World world;
    private final WitchFactorState state = new WitchFactorState();
    private final Set<UUID> visibleHolders = new HashSet<>();
    private final Map<UUID, Integer> viewerPermissions = new HashMap<>();

    public WitchFactorWorldComponent(World world) {
        this.world = world;
    }

    WitchFactorState state() { return state; }

    public boolean isFactorHolder(UUID player) {
        return world.isClient ? visibleHolders.contains(player) : state.factors().containsKey(player);
    }

    public void sync() {
        if (!world.isClient) {
            KEY.sync(world);
        }
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        WitchFactorService.tick(serverWorld, this);
        Map<UUID, Integer> current = new HashMap<>();
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            current.put(player.getUuid(), permission(player));
        }
        // Also revoke cached identities after role/death changes, even without a factor mutation.
        // 即使因子未变化，身份或生死变化后也撤销客户端缓存的身份列表。
        if (!current.equals(viewerPermissions)) {
            viewerPermissions.clear();
            viewerPermissions.putAll(current);
            sync();
        }
    }

    private int permission(ServerPlayerEntity recipient) {
        if (recipient.getWorld() != world || !state.active()
                || !GameWorldComponent.KEY.get(world).isRunning()
                || !GameFunctions.isPlayerPlayingAndAlive(recipient)
                || GameFunctions.isPlayerSpectatingOrCreative(recipient)) {
            return 0;
        }
        var role = GameWorldComponent.KEY.get(world).getRole(recipient);
        return role == SparkWitchRoles.accomplice() ? 2 : role == SparkWitchRoles.grandWitch() ? 1 : 0;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Empty packets to unauthorized viewers revoke old permission; they contain no identities.
        // 无权限者仅接收空包以撤销旧权限，包内不含任何身份。
        return recipient.getWorld() == world;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        int permission = permission(recipient);
        Set<UUID> visible = state.visibleFor(recipient.getUuid(), permission == 2, permission != 0);
        buf.writeVarInt(visible.size());
        visible.forEach(buf::writeUuid);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        visibleHolders.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            visibleHolders.add(buf.readUuid());
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("Active", state.active());
        tag.putInt("OpeningParticipants", state.openingParticipants());
        NbtList factors = new NbtList();
        state.factors().forEach((holder, factor) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Holder", holder);
            entry.putUuid("Owner", factor.owner());
            entry.putInt("ManaTicks", factor.manaTicks());
            entry.putInt("MoodTicks", factor.moodTicks());
            factors.add(entry);
        });
        tag.put("Factors", factors);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        state.clear();
        visibleHolders.clear();
        viewerPermissions.clear();
        if (!tag.getBoolean("Active")) {
            return;
        }
        state.begin(tag.getInt("OpeningParticipants"));
        NbtList factors = tag.getList("Factors", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < factors.size(); i++) {
            NbtCompound entry = factors.getCompound(i);
            if (entry.containsUuid("Holder") && entry.containsUuid("Owner")) {
                UUID holder = entry.getUuid("Holder");
                UUID owner = entry.getUuid("Owner");
                if (state.spread(owner, holder)) {
                    state.factors().put(holder, new WitchFactorState.Factor(owner,
                            entry.getInt("ManaTicks"), entry.getInt("MoodTicks")));
                }
            }
        }
    }
}
