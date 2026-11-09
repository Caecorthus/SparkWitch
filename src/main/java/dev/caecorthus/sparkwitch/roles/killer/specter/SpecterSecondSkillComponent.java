package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Owner-private Specter second-skill state: unlock, active window and cooldown.
 * <ul>
 *   <li>Unlock: 120 s after role assignment.</li>
 *   <li>Active: 30 s; the 120 s cooldown starts when the active window ends.</li>
 *   <li>Timers are server-authoritative, synced to the owner once per second and counted down locally for the HUD.</li>
 * </ul>
 * 幽魂二技能的本人状态：解锁、持续与冷却。分配职业 120 秒后解锁；持续 30 秒；持续结束时才开始 120 秒冷却。
 * 计时由服务端权威，每秒同步给本人，客户端本地倒计时供 HUD 平滑显示。
 */
public final class SpecterSecondSkillComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SpecterSecondSkillComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("specter_second_skill"),
            SpecterSecondSkillComponent.class
    );
    private static final String UNLOCK_TICKS = "SecondSkillUnlockTicks";
    private static final String ACTIVE_TICKS = "SecondSkillActiveTicks";
    private static final String COOLDOWN_TICKS = "SecondSkillCooldownTicks";
    private static final String OWNS_PSYCHO = "SecondSkillOwnsPsycho";
    private static final int SYNC_INTERVAL_TICKS = 20;
    // Keep Wathe's psycho timer above zero for the whole window; stop it ourselves so PUBLIC's counter never runs.
    // 让 Wathe 的疯魔计时在整段窗口内保持大于 0；由我们自己清零，避免 PUBLIC 类型的计数被扣减。
    private static final int PSYCHO_TOP_UP_BELOW_TICKS = 40;

    private final PlayerEntity player;
    private int unlockTicks;
    private int activeTicks;
    private int cooldownTicks;
    private boolean ownsPsycho;
    private int syncTicks;

    public SpecterSecondSkillComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getUnlockTicks() {
        return Math.max(0, unlockTicks);
    }

    public int getActiveTicks() {
        return Math.max(0, activeTicks);
    }

    public int getCooldownTicks() {
        return Math.max(0, cooldownTicks);
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    public boolean canActivate() {
        return unlockTicks <= 0 && activeTicks <= 0 && cooldownTicks <= 0;
    }

    /** Server only: resets the second-skill state for the assigned role and syncs. / 仅服务端：按分配的职业重置二技能状态并同步。 */
    public void assignForRole(@Nullable Role role) {
        int nextUnlock = SpecterRules.isSpecter(role) ? SpecterRules.SECOND_SKILL_UNLOCK_TICKS : 0;
        if (unlockTicks == nextUnlock && activeTicks == 0 && cooldownTicks == 0 && !ownsPsycho) {
            return;
        }
        removeSpeedModifier();
        clearPsychoShield();
        unlockTicks = nextUnlock;
        activeTicks = 0;
        cooldownTicks = 0;
        syncTicks = 0;
        syncOwner();
    }

    /** Server only: opens the 30 s window. The cooldown starts when the window closes. / 仅服务端：开启 30 秒窗口，结束时才开始冷却。 */
    public boolean activate() {
        if (!canActivate()) {
            return false;
        }
        activeTicks = SpecterRules.SECOND_SKILL_DURATION_TICKS;
        applySpeedModifier();
        syncTicks = 0;
        syncOwner();
        return true;
    }

    /**
     * Server only: grants Wathe's built-in psycho armour as the shield. Never takes over a psycho state another source
     * already owns. / 仅服务端：直接用 Wathe 自带的疯魔护盾作为本技能护盾；其他来源已在运行的疯魔不接管。
     */
    public void grantPsychoShield(int layers) {
        if (layers <= 0 || ownsPsycho) {
            return;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) {
            return;
        }
        psycho.setPsychoTicks(SpecterRules.SECOND_SKILL_DURATION_TICKS + PSYCHO_TOP_UP_BELOW_TICKS);
        psycho.setArmour(layers);
        ownsPsycho = true;
        syncOwner();
    }

    /** Clears on death, reset or round end. / 死亡、重置或回合结束时清空。 */
    public void clear() {
        if (unlockTicks == 0 && activeTicks == 0 && cooldownTicks == 0 && !ownsPsycho) {
            return;
        }
        removeSpeedModifier();
        clearPsychoShield();
        unlockTicks = 0;
        activeTicks = 0;
        cooldownTicks = 0;
        syncTicks = 0;
        syncOwner();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        if (!SpecterRules.isSpecter(game.getRole(owner))) {
            clear();
            return;
        }
        // Roles are assigned before the round is RUNNING. Do not let this pre-game window wipe the 120 s unlock countdown.
        // 角色在回合进入 RUNNING 之前就已分配；这段时间不要清空 120 秒解锁倒计时。
        if (!game.isRunning()) {
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(owner)
                || GameFunctions.isPlayerSpectatingOrCreative(owner)) {
            clear();
            return;
        }

        boolean finishedActive = false;
        if (unlockTicks > 0) {
            unlockTicks--;
        }
        if (activeTicks > 0) {
            applySpeedModifier();
            maintainPsychoShield();
            // The Otherworld keeps the knife ready every tick; other weapons keep their own cooldowns.
            // 里世界每 tick 清空小刀使用后的冷却；其他武器的冷却保持原样。
            if (owner.getItemCooldownManager().isCoolingDown(WatheItems.KNIFE)) {
                owner.getItemCooldownManager().remove(WatheItems.KNIFE);
            }
            // SparkTraits' Close Quarters guard reads its own forced melee floor, not the vanilla cooldown.
            // SparkTraits 的近身守卫读自己的强制近战下限，不是原版冷却。
            SparkTraitsKillerBridge.clearForcedMeleeCooldown(owner, WatheItems.KNIFE);
            activeTicks--;
            if (activeTicks <= 0) {
                activeTicks = 0;
                cooldownTicks = SpecterRules.SECOND_SKILL_COOLDOWN_TICKS;
                finishedActive = true;
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (finishedActive) {
            removeSpeedModifier();
            clearPsychoShield();
            // Recall the peachwood swords immediately instead of waiting for the sweep. / 立刻收回桃木剑，不等周期检查。
            SpecterPeachwoodService.refresh(owner.getServerWorld());
            syncTicks = 0;
            syncOwner();
            return;
        }
        if (++syncTicks < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTicks = 0;
        if (unlockTicks > 0 || activeTicks > 0 || cooldownTicks > 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        if (unlockTicks > 0) {
            unlockTicks--;
        }
        if (activeTicks > 0) {
            activeTicks--;
            if (activeTicks <= 0) {
                activeTicks = 0;
                cooldownTicks = SpecterRules.SECOND_SKILL_COOLDOWN_TICKS;
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (unlockTicks > 0) {
            tag.putInt(UNLOCK_TICKS, unlockTicks);
        }
        if (activeTicks > 0) {
            tag.putInt(ACTIVE_TICKS, activeTicks);
        }
        if (cooldownTicks > 0) {
            tag.putInt(COOLDOWN_TICKS, cooldownTicks);
        }
        if (ownsPsycho) {
            tag.putBoolean(OWNS_PSYCHO, true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        unlockTicks = readTicks(tag, UNLOCK_TICKS);
        activeTicks = readTicks(tag, ACTIVE_TICKS);
        cooldownTicks = readTicks(tag, COOLDOWN_TICKS);
        ownsPsycho = tag.getBoolean(OWNS_PSYCHO);
    }

    private static int readTicks(NbtCompound tag, String key) {
        return tag.contains(key, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(key)) : 0;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getUnlockTicks());
        buf.writeVarInt(getActiveTicks());
        buf.writeVarInt(getCooldownTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        unlockTicks = Math.max(0, buf.readVarInt());
        activeTicks = Math.max(0, buf.readVarInt());
        cooldownTicks = Math.max(0, buf.readVarInt());
    }

    private void applySpeedModifier() {
        var movement = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement == null || movement.hasModifier(SpecterRules.SECOND_SKILL_SPEED_MODIFIER_ID)) {
            return;
        }
        movement.addTemporaryModifier(new EntityAttributeModifier(
                SpecterRules.SECOND_SKILL_SPEED_MODIFIER_ID,
                SpecterRules.SECOND_SKILL_SPEED_BONUS,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private void removeSpeedModifier() {
        var movement = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement != null && movement.hasModifier(SpecterRules.SECOND_SKILL_SPEED_MODIFIER_ID)) {
            movement.removeModifier(SpecterRules.SECOND_SKILL_SPEED_MODIFIER_ID);
        }
    }

    /** Keeps Wathe's psycho timer alive without letting it reach zero and call stopPsycho. / 维持疯魔计时，避免其自然归零触发 stopPsycho。 */
    private void maintainPsychoShield() {
        if (!ownsPsycho) {
            return;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() <= 0) {
            // Another path stopped it; nothing left to own. / 已被其他路径停止，不再持有。
            ownsPsycho = false;
            return;
        }
        if (psycho.getPsychoTicks() < PSYCHO_TOP_UP_BELOW_TICKS) {
            psycho.setPsychoTicks(SpecterRules.SECOND_SKILL_DURATION_TICKS + PSYCHO_TOP_UP_BELOW_TICKS);
        }
    }

    /** Zeroes the armour and timer ourselves so PUBLIC's psychosActive counter is never touched. / 自行清零护甲与计时，避免扣减 PUBLIC 的疯魔计数。 */
    private void clearPsychoShield() {
        if (!ownsPsycho) {
            return;
        }
        ownsPsycho = false;
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        psycho.setArmour(0);
        if (psycho.getPsychoTicks() > 0) {
            psycho.setPsychoTicks(0);
        }
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
