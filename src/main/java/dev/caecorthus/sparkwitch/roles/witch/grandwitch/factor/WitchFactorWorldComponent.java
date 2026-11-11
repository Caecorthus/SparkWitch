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
    /** Client: players this recipient sees in their role colour. / 客户端：当前接收者以职业色看到的玩家。 */
    private final Set<UUID> visibleRoleColored = new HashSet<>();
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

    /**
     * One recipient's packet contents and their wire format. The server builds it only through {@link #sanitized}, so
     * an unauthorized recipient gets no id of any kind. Layout: holder list, the two view bits, then the role-colour
     * list (appended 2026-11-11; a packet that ends after the bits still reads, as an empty list).
     * 单个接收者的数据包内容及其线上格式。服务端只经 {@link #sanitized} 构造，未获准的接收者拿不到任何 id。布局：持有者列表、
     * 两个视图标记，然后是职业色列表（2026-11-11 追加；在标记处结束的数据包仍可读取，按空列表处理）。
     */
    record View(boolean authorized, boolean exposeEmma, Set<UUID> holders, Set<UUID> roleColored) {
        static final View UNAUTHORIZED = new View(false, false, Set.of(), Set.of());

        static View sanitized(boolean authorized, boolean pink, Set<UUID> mature, Set<UUID> roleColored) {
            return authorized ? new View(true, pink, mature, roleColored) : UNAUTHORIZED;
        }

        void encode(RegistryByteBuf buf) {
            // Preserve the original list prefix; the appended bits distinguish an empty authorized network.
            // 保留原有列表前缀；追加权限位可区分获准但为空的网络。
            buf.writeVarInt(holders.size());
            holders.forEach(buf::writeUuid);
            buf.writeBoolean(authorized);
            buf.writeBoolean(exposeEmma);
            buf.writeVarInt(roleColored.size());
            roleColored.forEach(buf::writeUuid);
        }

        /** Client: ids are dropped again unless the view bit is set. / 客户端：视图标记未置位时再次丢弃所有 id。 */
        static View decode(RegistryByteBuf buf) {
            Set<UUID> holders = readIds(buf);
            boolean authorized = buf.isReadable() && buf.readBoolean();
            boolean exposeEmma = buf.isReadable() && buf.readBoolean();
            Set<UUID> roleColored = buf.isReadable() ? readIds(buf) : Set.of();
            return new View(authorized, exposeEmma, authorized ? holders : Set.of(), authorized ? roleColored : Set.of());
        }

        private static Set<UUID> readIds(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > buf.readableBytes() / 16) throw new IllegalArgumentException("Invalid factor view");
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) ids.add(buf.readUuid());
            return ids;
        }
    }
    private static final View UNAUTHORIZED = View.UNAUTHORIZED;

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
    /** Client: this recipient's sanitized role-colour set; server: the ledger. / 客户端：当前接收者获准的职业色集合；服务端：账本。 */
    public boolean isRoleColored(UUID player) {
        return world.isClient ? visibleRoleColored.contains(player) : state.roleColored().contains(player);
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

    /**
     * Living role-coloured players of this world. The ledger set is empty in almost every round, so this is not shared
     * across the recipients of one sync the way the mature set is.
     * 本世界存活的职业色玩家。该账本集合在绝大多数对局中为空，因此不像成熟集合那样在一次同步的接收者之间共用。
     */
    private Set<UUID> livingRoleColored() {
        if (state.roleColored().isEmpty()) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (UUID id : state.roleColored()) {
            if (world.getPlayerByUuid(id) instanceof ServerPlayerEntity player && WitchFactorService.alive(player)) {
                result.add(id);
            }
        }
        return Set.copyOf(result);
    }

    private View view(ServerPlayerEntity recipient, Set<UUID> mature) {
        boolean authorized = recipient.getWorld() == world && WitchFactorService.isNetworkViewer(recipient);
        boolean pink = authorized && (GameWorldComponent.KEY.get(world).getRole(recipient) == SparkWitchRoles.grandWitch()
                || state.mature(recipient.getUuid()));
        return View.sanitized(authorized, pink, mature, authorized ? livingRoleColored() : Set.of());
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
        view.encode(buf);
    }

    @Override public void applySyncPacket(RegistryByteBuf buf) {
        View view = View.decode(buf);
        visibleHolders.clear();
        visibleHolders.addAll(view.holders());
        visibleRoleColored.clear();
        visibleRoleColored.addAll(view.roleColored());
        networkView = view.authorized();
        exposeEmma = view.exposeEmma();
    }

    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        WitchFactorCodec.write(tag, settings, state);
    }

    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        visibleHolders.clear();
        visibleRoleColored.clear();
        sentViews.clear();
        networkView = false;
        exposeEmma = false;
        settings = WitchFactorCodec.read(tag, state);
    }
}
