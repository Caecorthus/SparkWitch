package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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

/** Full ledger stays server-side; packets carry only authorized mature relationships.
 * 完整账本只留在服务端；数据包仅携带获准的成熟关系。 */
public final class WitchFactorWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchFactorWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("witch_factor_world"), WitchFactorWorldComponent.class);
    private final World world;
    private final WitchFactorState state = new WitchFactorState();
    private WitchFactorSettings settings = WitchFactorSettings.DEFAULT;
    private final Set<UUID> visibleHolders = new HashSet<>();
    private boolean networkView;
    private boolean exposeEmma;
    private Map<UUID, View> previousViews = Map.of();

    private record View(boolean authorized, boolean exposeEmma, Set<UUID> holders) { }

    public WitchFactorWorldComponent(World world) { this.world = world; }
    WitchFactorState state() { return state; }
    public WitchFactorSettings settings() { return settings; }
    public void setSettings(WitchFactorSettings value) {
        if (!world.isClient) settings = java.util.Objects.requireNonNull(value);
    }
    public boolean hasNetworkView() { return networkView; }
    public boolean mayExposeEmma() { return exposeEmma; }
    public boolean isFactorHolder(UUID player) {
        return world.isClient ? visibleHolders.contains(player) : state.factors().containsKey(player);
    }
    public void sync() { if (!world.isClient) KEY.sync(world); }

    private Set<UUID> matureLivingHolders(ServerWorld serverWorld) {
        Set<UUID> result = new HashSet<>();
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (state.mature(player.getUuid()) && WitchFactorService.alive(player)) result.add(player.getUuid());
        }
        return Set.copyOf(result);
    }

    private View view(ServerPlayerEntity recipient, Set<UUID> mature) {
        boolean authorized = recipient.getWorld() == world && WitchFactorService.isNetworkViewer(recipient);
        boolean pink = authorized && (GameWorldComponent.KEY.get(world).getRole(recipient) == SparkWitchRoles.grandWitch()
                || state.mature(recipient.getUuid()));
        return new View(authorized, pink, authorized ? mature : Set.of());
    }

    @Override public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        WitchFactorService.tick(serverWorld, this);
        Set<UUID> mature = matureLivingHolders(serverWorld);
        Map<UUID, View> views = new HashMap<>();
        for (ServerPlayerEntity player : serverWorld.getPlayers()) views.put(player.getUuid(), view(player, mature));
        if (!views.equals(previousViews)) {
            previousViews = views;
            sync();
        }
    }

    @Override public boolean shouldSyncWith(ServerPlayerEntity recipient) { return recipient.getWorld() == world; }

    @Override public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        View view = view(recipient, matureLivingHolders((ServerWorld) world));
        // Preserve the original list prefix; the appended bits distinguish an empty authorized network.
        // 保留原有列表前缀；追加权限位可区分获准但为空的网络。
        buf.writeVarInt(view.holders().size());
        view.holders().forEach(buf::writeUuid);
        buf.writeBoolean(view.authorized());
        buf.writeBoolean(view.exposeEmma());
    }

    @Override public void applySyncPacket(RegistryByteBuf buf) {
        visibleHolders.clear();
        int count = buf.readVarInt();
        if (count < 0 || count > buf.readableBytes() / 16) throw new IllegalArgumentException("Invalid factor view");
        for (int i = 0; i < count; i++) visibleHolders.add(buf.readUuid());
        networkView = buf.isReadable() && buf.readBoolean();
        exposeEmma = buf.isReadable() && buf.readBoolean();
        if (!networkView) visibleHolders.clear();
    }

    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        WitchFactorCodec.write(tag, settings, state);
    }

    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        visibleHolders.clear();
        previousViews = Map.of();
        networkView = false;
        exposeEmma = false;
        settings = WitchFactorCodec.read(tag, state);
    }
}
