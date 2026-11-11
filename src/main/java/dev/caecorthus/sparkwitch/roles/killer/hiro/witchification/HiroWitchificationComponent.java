package dev.caecorthus.sparkwitch.roles.killer.hiro.witchification;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Owner-private 魔女化 (Witchification) progress of 二阶堂希罗, component
 * {@code sparkwitch:nikaido_hiro_witchification}: the kills counted so far, the threshold for this round
 * ({@code HiroRules.requiredKills(roundPlayers)}, 0 until the service sets it) and whether she has witchified. Synced
 * to its owner only; the component only stores state, {@link HiroWitchificationService} owns every rule. One more
 * field stays on the server and is saved but never sent: whether the 魔女化 derringer is still owed because no shown
 * inventory slot was free.
 * 二阶堂希罗仅本人可见的「魔女化」进度，组件 {@code sparkwitch:nikaido_hiro_witchification}：已计入的击杀数、本局所需击杀数
 * （{@code HiroRules.requiredKills(本局玩家数)}，服务写入前为 0）以及是否已魔女化。只同步给本人；组件只保存状态，全部规则由
 * {@link HiroWitchificationService} 负责。另有一个只留在服务端、存档但从不下发的字段：魔女化的德林加手枪是否因没有可见空栏位而
 * 尚未发放。
 */
public final class HiroWitchificationComponent implements AutoSyncedComponent {
    public static final ComponentKey<HiroWitchificationComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("nikaido_hiro_witchification"),
            HiroWitchificationComponent.class
    );
    private static final String KILLS = "Kills";
    private static final String REQUIRED = "Required";
    private static final String WITCHIFIED = "Witchified";
    private static final String DERRINGER_OWED = "DerringerOwed";

    private final PlayerEntity player;
    private int kills;
    private int required;
    private boolean witchified;
    private boolean derringerOwed;

    public HiroWitchificationComponent(PlayerEntity player) {
        this.player = player;
    }

    public int kills() {
        return kills;
    }

    /** Kills needed this round; 0 while unset. / 本局所需击杀数；未设置时为 0。 */
    public int required() {
        return required;
    }

    public boolean isWitchified() {
        return witchified;
    }

    /** Server only; always false on a client. / 仅服务端；客户端恒为 false。 */
    public boolean isDerringerOwed() {
        return derringerOwed;
    }

    /** Server only, no sync: the field never leaves the server. / 仅服务端，不同步：该字段不离开服务端。 */
    public void setDerringerOwed(boolean owed) {
        derringerOwed = owed;
    }

    /** Server only: writes the whole progress and syncs when it changed. / 仅服务端：写入全部进度，有变化时同步。 */
    public void set(int kills, int required, boolean witchified) {
        int nextKills = Math.max(0, kills);
        int nextRequired = Math.max(0, required);
        if (this.kills == nextKills && this.required == nextRequired && this.witchified == witchified) {
            return;
        }
        this.kills = nextKills;
        this.required = nextRequired;
        this.witchified = witchified;
        syncOwner();
    }

    /**
     * Server only: back to no kills, no threshold, not witchified, no owed derringer.
     * 仅服务端：清零击杀、所需数、魔女化标记与待发放的德林加手枪。
     */
    public void clear() {
        derringerOwed = false;
        set(0, 0, false);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (kills > 0) {
            tag.putInt(KILLS, kills);
        }
        if (required > 0) {
            tag.putInt(REQUIRED, required);
        }
        if (witchified) {
            tag.putBoolean(WITCHIFIED, true);
        }
        if (derringerOwed) {
            tag.putBoolean(DERRINGER_OWED, true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        kills = tag.contains(KILLS, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(KILLS)) : 0;
        required = tag.contains(REQUIRED, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(REQUIRED)) : 0;
        witchified = tag.getBoolean(WITCHIFIED);
        derringerOwed = tag.getBoolean(DERRINGER_OWED);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(kills);
        buf.writeVarInt(required);
        buf.writeBoolean(witchified);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        kills = Math.max(0, buf.readVarInt());
        required = Math.max(0, buf.readVarInt());
        witchified = buf.readBoolean();
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
