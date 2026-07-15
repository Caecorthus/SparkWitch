package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistEffects;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Independent owner-private state for Hunter traps, trap injuries, and exact trap-poison attribution.
 * 猎人捕兽夹、夹伤与精确毒源归属的独立组件；同步数据只发给玩家本人。
 */
public final class HunterPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<HunterPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("hunter"),
            HunterPlayerComponent.class
    );
    private static final Identifier FRACTURE_SPEED_MODIFIER_ID = SparkWitch.id("fracture_speed");

    private final PlayerEntity player;
    private final HunterInjuryState injury = new HunterInjuryState();
    private final List<UUID> ownedTrapUuids = new ArrayList<>();
    private @Nullable Identifier activePoisonSource;
    private @Nullable HunterPoisonAttribution trapPoisonAttribution;
    private int appliedFractureLayers = -1;

    public HunterPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public HunterInjuryState.FractureApplication applyTrapInjury() {
        injury.rootFor(HunterRules.TRAP_ROOT_TICKS);
        HunterInjuryState.FractureApplication application;
        if (player.hasStatusEffect(OrthopedistEffects.boneSetting())) {
            player.removeStatusEffect(OrthopedistEffects.boneSetting());
            application = injury.addFractureLayerWithBoneSetting(HunterRules.FRACTURE_LAYER_TICKS);
        } else {
            application = injury.addFractureLayer(HunterRules.FRACTURE_LAYER_TICKS);
        }
        refreshFractureState();
        sync();
        return application;
    }

    public boolean healOneFractureLayer() {
        if (!injury.healOneFractureLayer()) {
            return false;
        }
        refreshFractureState();
        sync();
        return true;
    }

    public boolean isRooted() {
        return injury.rootTicks() > 0;
    }

    public int getRootTicks() {
        return injury.rootTicks();
    }

    public int getFractureLayers() {
        return injury.fractureLayers();
    }

    public void registerTrap(UUID trapUuid) {
        if (!ownedTrapUuids.contains(trapUuid)) {
            ownedTrapUuids.add(trapUuid);
        }
    }

    public void unregisterTrap(UUID trapUuid) {
        ownedTrapUuids.remove(trapUuid);
    }

    /** Returns and forgets the oldest traps until one new placement fits the two-trap cap. */
    public List<UUID> removeOldestTrapsAtCapacity() {
        List<UUID> removed = new ArrayList<>();
        while (ownedTrapUuids.size() >= HunterRules.MAX_OWNED_TRAPS) {
            removed.add(ownedTrapUuids.removeFirst());
        }
        return removed;
    }

    public void onAcceptedPoisonApplication(@Nullable Identifier source) {
        activePoisonSource = source;
        if (!HunterTrapEntity.POISON_SOURCE.equals(source)) {
            trapPoisonAttribution = null;
        }
    }

    public void setTrapPoisonAttribution(
            @Nullable UUID placerUuid,
            @Nullable UUID poisonerUuid,
            long appliedAtTick,
            int poisonTicks
    ) {
        if (!HunterTrapEntity.POISON_SOURCE.equals(activePoisonSource)) {
            trapPoisonAttribution = null;
            return;
        }
        trapPoisonAttribution = HunterPoisonAttribution.forTrap(
                placerUuid,
                poisonerUuid,
                appliedAtTick,
                poisonTicks
        );
    }

    public boolean hasConfirmedTrapPoisonDeath(long currentTick, @Nullable UUID livePoisonerUuid) {
        return HunterTrapEntity.POISON_SOURCE.equals(activePoisonSource)
                && trapPoisonAttribution != null
                && trapPoisonAttribution.matchesConfirmedPoisonDeath(true, currentTick, livePoisonerUuid);
    }

    @Nullable
    public HunterPoisonAttribution getTrapPoisonAttribution() {
        return trapPoisonAttribution;
    }

    public void clearPoisonAttribution() {
        activePoisonSource = null;
        trapPoisonAttribution = null;
    }

    public void reset() {
        injury.clear();
        ownedTrapUuids.clear();
        clearPoisonAttribution();
        clearFractureState();
        sync();
    }

    @Override
    public void serverTick() {
        boolean rootWasActive = injury.rootTicks() > 0;
        HunterInjuryState.TickResult tickResult = injury.tick();
        boolean injuryActive = rootWasActive || injury.fractureLayers() > 0;

        if (rootWasActive) {
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
        }
        if (injuryActive) {
            lockSprintAndStamina();
        }
        refreshFractureState();

        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(player);
        if (trapPoisonAttribution != null && (poison.poisonTicks < 0 || poison.poisoner == null)) {
            clearPoisonAttribution();
        }

        if (tickResult.fractureExpired()
                || rootWasActive && injury.rootTicks() == 0
                || player.age % 20 == 0 && injuryActive) {
            sync();
        }
    }

    private void lockSprintAndStamina() {
        player.setSprinting(false);
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        if (stamina.isInfiniteStamina()) {
            return;
        }
        boolean changed = false;
        if (stamina.getSprintingTicks() != 0.0F) {
            stamina.setSprintingTicks(0.0F);
            changed = true;
        }
        if (!stamina.isExhausted()) {
            stamina.setExhausted(true);
            changed = true;
        }
        if (changed) {
            stamina.sync();
        }
    }

    private void refreshFractureState() {
        int layers = injury.fractureLayers();
        if (layers <= 0) {
            clearFractureState();
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(
                HunterEffects.fracture(),
                10,
                layers - 1,
                false,
                true,
                true
        ));
        if (layers != appliedFractureLayers) {
            applyFractureSpeedModifier(layers);
            appliedFractureLayers = layers;
        }
    }

    private void applyFractureSpeedModifier(int layers) {
        var movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        movementSpeed.removeModifier(FRACTURE_SPEED_MODIFIER_ID);
        movementSpeed.addPersistentModifier(new EntityAttributeModifier(
                FRACTURE_SPEED_MODIFIER_ID,
                -HunterRules.SLOW_PER_FRACTURE_LAYER * layers,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private void clearFractureState() {
        if (player.hasStatusEffect(HunterEffects.fracture())) {
            player.removeStatusEffect(HunterEffects.fracture());
        }
        var movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(FRACTURE_SPEED_MODIFIER_ID);
        }
        appliedFractureLayers = 0;
    }

    private void sync() {
        if (!player.getWorld().isClient) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(injury.rootTicks());
        buf.writeVarInt(injury.fractureLayers());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        injury.restoreRootTicks(buf.readVarInt());
        int layers = Math.min(buf.readVarInt(), HunterRules.MAX_FRACTURE_LAYERS);
        List<Integer> timers = new ArrayList<>(layers);
        for (int index = 0; index < layers; index++) {
            timers.add(HunterRules.FRACTURE_LAYER_TICKS);
        }
        injury.restoreFractureTimers(timers);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("RootTicks", injury.rootTicks());
        tag.put("FractureTimers", intList(injury.fractureTimers()));
        tag.put("OwnedTraps", uuidList(ownedTrapUuids));
        if (activePoisonSource != null) {
            tag.putString("ActivePoisonSource", activePoisonSource.toString());
        }
        if (trapPoisonAttribution != null) {
            if (trapPoisonAttribution.placerUuid() != null) {
                tag.putUuid("TrapPoisonPlacer", trapPoisonAttribution.placerUuid());
            }
            if (trapPoisonAttribution.poisonerUuid() != null) {
                tag.putUuid("TrapPoisoner", trapPoisonAttribution.poisonerUuid());
            }
            tag.putLong("TrapPoisonExpiry", trapPoisonAttribution.expectedExpiryTick());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        injury.restoreRootTicks(tag.getInt("RootTicks"));
        injury.restoreFractureTimers(readIntList(tag.getList("FractureTimers", NbtElement.INT_TYPE)));
        ownedTrapUuids.clear();
        ownedTrapUuids.addAll(readUuidList(tag.getList("OwnedTraps", NbtElement.STRING_TYPE)));
        activePoisonSource = Identifier.tryParse(tag.getString("ActivePoisonSource"));
        if (tag.contains("TrapPoisonExpiry")) {
            trapPoisonAttribution = new HunterPoisonAttribution(
                    tag.containsUuid("TrapPoisonPlacer") ? tag.getUuid("TrapPoisonPlacer") : null,
                    tag.containsUuid("TrapPoisoner") ? tag.getUuid("TrapPoisoner") : null,
                    tag.getLong("TrapPoisonExpiry")
            );
        } else {
            trapPoisonAttribution = null;
        }
        appliedFractureLayers = -1;
    }

    private static NbtList intList(List<Integer> values) {
        NbtList list = new NbtList();
        values.forEach(value -> list.add(net.minecraft.nbt.NbtInt.of(value)));
        return list;
    }

    private static List<Integer> readIntList(NbtList list) {
        List<Integer> values = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            values.add(list.getInt(index));
        }
        return values;
    }

    private static NbtList uuidList(List<UUID> uuids) {
        NbtList list = new NbtList();
        uuids.stream().map(UUID::toString).map(NbtString::of).forEach(list::add);
        return list;
    }

    private static List<UUID> readUuidList(NbtList list) {
        List<UUID> uuids = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            try {
                uuids.add(UUID.fromString(list.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Malformed stale ownership data must not block a world load.
                // 损坏的旧捕兽夹归属数据不能阻止世界加载。
            }
        }
        return uuids;
    }
}
