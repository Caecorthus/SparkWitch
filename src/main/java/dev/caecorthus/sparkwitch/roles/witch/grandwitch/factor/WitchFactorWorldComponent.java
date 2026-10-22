package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
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
    /**
     * Server: the view last written to each recipient in this world. Every packet (ours and CCA's join/respawn/world-change
     * sync) goes through {@link #write}, so this is exactly what each client holds.
     * 服务端：最近写给本世界每个接收者的视图。所有数据包（本组件的与 CCA 在加入/重生/换世界时的同步）都经过 {@link #write}，
     * 因此它恰好等于各客户端持有的内容。
     */
    private final Map<UUID, View> sentViews = new HashMap<>();
    /** Server: the mature set shared by every recipient of one {@link #sync()}. / 服务端：一次同步内所有接收者共用的成熟集合。 */
    private @Nullable Set<UUID> syncMature;

    private record View(boolean authorized, boolean exposeEmma, Set<UUID> holders) { }
    private static final View UNAUTHORIZED = new View(false, false, Set.of());

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
    public void sync() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        // Recipients cannot change the ledger, so one mature set serves the whole sync. / 接收者不会改变账本，一次同步共用一份成熟集合。
        syncMature = matureLivingHolders(serverWorld);
        try { KEY.sync(world); } finally { syncMature = null; }
    }

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

    /**
     * Sends each player its view only when it differs from what that client holds. An inactive ledger authorizes nobody
     * ({@code isNetworkViewer} reads {@code state.active()}), so the lobby and other worlds skip the per-player rebuild.
     * 只在视图与该客户端持有的内容不同时才发给该玩家。未激活的账本不授权任何人（{@code isNetworkViewer} 读取
     * {@code state.active()}），因此大厅和其他世界跳过逐玩家重建。
     */
    @Override public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        WitchFactorService.tick(serverWorld, this);
        List<ServerPlayerEntity> players = serverWorld.getPlayers();
        boolean active = state.active();
        Set<UUID> mature = active ? matureLivingHolders(serverWorld) : Set.of();
        for (ServerPlayerEntity player : players) {
            View view = active ? view(player, mature) : UNAUTHORIZED;
            if (!view.equals(sentViews.get(player.getUuid()))) {
                KEY.syncWith(player, (ComponentProvider) world, (buf, recipient) -> write(buf, recipient, view), this);
            }
        }
        // Every present player now has an entry, so a larger map holds players who left. / 在场玩家现均有条目，多出的即已离开者。
        if (sentViews.size() > players.size()) {
            Set<UUID> present = new HashSet<>();
            for (ServerPlayerEntity player : players) present.add(player.getUuid());
            sentViews.keySet().retainAll(present);
        }
    }

    @Override public boolean shouldSyncWith(ServerPlayerEntity recipient) { return recipient.getWorld() == world; }

    @Override public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        write(buf, recipient, view(recipient, syncMature != null ? syncMature : matureLivingHolders((ServerWorld) world)));
    }

    private void write(RegistryByteBuf buf, ServerPlayerEntity recipient, View view) {
        sentViews.put(recipient.getUuid(), view);
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
        sentViews.clear();
        networkView = false;
        exposeEmma = false;
        settings = WitchFactorCodec.read(tag, state);
    }
}
