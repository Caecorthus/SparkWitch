package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
 * Owner-private Specter second-skill state: unlock, active window, cooldown and the Specter-owned Otherworld shield.
 * <ul>
 *   <li>Unlock: 120 s after role assignment.</li>
 *   <li>Active: 30 s with Speed II, invisibility and the shield; the 120 s cooldown starts when the window ends.</li>
 *   <li>Shield: a layer count of its own, never Wathe psycho mode, so the hotbar, the global psycho counter and every
 *       client's psycho presentation stay untouched. {@code GameFunctionsSpecterShieldMixin} spends one layer per
 *       absorbed lethal hit; the layers clear with the window, death, reset, round end and role change.</li>
 *   <li>Timers are server-authoritative, synced to the owner once per second and counted down locally for the HUD.</li>
 * </ul>
 * 幽魂二技能的本人状态：解锁、持续、冷却与幽魂自有的里世界护盾。分配职业 120 秒后解锁；持续 30 秒（速度 II、隐身与护盾）；
 * 持续结束时才开始 120 秒冷却。护盾是独立的层数，绝不借用 Wathe 疯魔模式，因此快捷栏、全局疯魔计数与所有客户端的疯魔表现都
 * 不受影响；{@code GameFunctionsSpecterShieldMixin} 每吸收一次致命攻击消耗 1 层；窗口结束、死亡、重置、回合结束与职业变更时清空。
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
    private static final String SHIELD_LAYERS = "SecondSkillShieldLayers";
    private static final int SYNC_INTERVAL_TICKS = 20;
    // The cast's invisibility and the window count down in the same tick, at most a tick apart.
    // 施放时的隐身与窗口在同一 tick 中倒数，最多相差一刻。
    private static final int INVISIBILITY_MATCH_SLACK_TICKS = 2;
    // A natural knife cooldown is at least 10 s, far above this tolerance for tick-order drift.
    // 小刀自然冷却至少 10 秒，远大于这里为 tick 顺序误差留出的容差。
    private static final int KNIFE_KEPT_SLACK_TICKS = 2;

    private final PlayerEntity player;
    private int unlockTicks;
    private int activeTicks;
    /** After the window: the running cooldown. During it: the pending post-window floor (forced raises only). */
    private int cooldownTicks;
    private int shieldLayers;
    private int syncTicks;
    /** Server world tick until which only a SparkFactionAPI-forced knife cooldown remains; never saved. */
    private long knifeForcedUntilTick;

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

    /** Remaining Otherworld shield layers; synced to the owner for the HUD. / 剩余里世界护盾层数；同步给本人供 HUD 使用。 */
    public int getShieldLayers() {
        return Math.max(0, shieldLayers);
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
        if (unlockTicks == nextUnlock && activeTicks == 0 && cooldownTicks == 0 && shieldLayers == 0) {
            return;
        }
        endWindowEffects();
        unlockTicks = nextUnlock;
        activeTicks = 0;
        cooldownTicks = 0;
        shieldLayers = 0;
        syncTicks = 0;
        syncOwner();
    }

    /**
     * Server only: opens the 30 s window with Speed II, invisibility and {@code shieldLayers} shield layers. The
     * cooldown starts when the window closes.
     * 仅服务端：开启 30 秒窗口，给予速度 II、隐身与 {@code shieldLayers} 层护盾；窗口结束时才开始冷却。
     */
    public boolean activate(int shieldLayers) {
        if (!canActivate()) {
            return false;
        }
        activeTicks = SpecterRules.SECOND_SKILL_DURATION_TICKS;
        cooldownTicks = 0;
        this.shieldLayers = Math.max(0, shieldLayers);
        knifeForcedUntilTick = 0L;
        applySpeedModifier();
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                SpecterRules.SECOND_SKILL_DURATION_TICKS,
                0,
                true,
                false,
                true
        ));
        syncTicks = 0;
        syncOwner();
        return true;
    }

    /**
     * Server only, from the shield mixin: spends one layer of an open window. False when none is left, so the kill goes
     * on exactly as without the shield.
     * 仅服务端，由护盾 mixin 调用：消耗开启窗口中的 1 层护盾。没有剩余时返回 false，击杀按无护盾时原样继续。
     */
    public boolean consumeShieldLayer() {
        if (activeTicks <= 0 || shieldLayers <= 0) {
            return false;
        }
        shieldLayers--;
        syncOwner();
        return true;
    }

    /**
     * Forced-cooldown read ({@code SparkWitchForcedCooldowns#specterOtherworld}): ticks until the skill is ready.
     * Outside the window this includes the unlock countdown; inside it, the rest of the window plus the cooldown that
     * will start when it ends (the larger of the nominal 120 s and any pending forced floor).
     * 强制冷却读取（{@code SparkWitchForcedCooldowns#specterOtherworld}）：距技能就绪的刻数。窗口外包含解锁倒计时；窗口内为
     * 剩余窗口时长加上窗口结束时将开始的冷却（标准 120 秒与已挂起的强制下限中的较大者）。
     */
    public int forcedCooldownRemainingTicks() {
        if (activeTicks > 0) {
            long total = (long) activeTicks
                    + Math.max(getCooldownTicks(), SpecterRules.SECOND_SKILL_COOLDOWN_TICKS);
            return (int) Math.min(Integer.MAX_VALUE, total);
        }
        return Math.max(getUnlockTicks(), getCooldownTicks());
    }

    /**
     * Server only, forced-cooldown write: afterwards at least {@code ticks} remain until ready; it never shortens and
     * syncs the owner. During the window the extra time becomes the pending post-window cooldown, so a running
     * Otherworld (speed, shield, invisibility, world window) is never cut short.
     * 仅服务端，强制冷却写入：之后距就绪至少剩 {@code ticks}；绝不缩短，并同步给本人。窗口期间多出的时间成为挂起的窗口后冷却，
     * 因此进行中的里世界（速度、护盾、隐身、世界窗口）绝不会被提前结束。
     */
    public void setForcedCooldownRemainingTicks(int ticks) {
        int target = Math.max(0, ticks);
        int next = activeTicks > 0 ? Math.max(cooldownTicks, target - activeTicks) : Math.max(cooldownTicks, target);
        if (next == cooldownTicks) {
            return;
        }
        cooldownTicks = next;
        syncTicks = 0;
        syncOwner();
    }

    /** Clears on death, reset or round end. / 死亡、重置或回合结束时清空。 */
    public void clear() {
        if (unlockTicks == 0 && activeTicks == 0 && cooldownTicks == 0 && shieldLayers == 0) {
            return;
        }
        endWindowEffects();
        unlockTicks = 0;
        activeTicks = 0;
        cooldownTicks = 0;
        shieldLayers = 0;
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
        // SparkTraits Last Stand holds the player as a spectator for a few seconds before reviving them, and the death
        // hook keeps this state through that intercepted death: nothing is cleared, and the timers keep counting so
        // the window still ends with the world window and the cast's invisibility.
        // SparkTraits 背水一战会在复活前把玩家以旁观者身份保持数秒，死亡钩子在该被拦截的死亡中保留本状态：这里不清空任何东西，
        // 计时照常倒数，使窗口仍与世界窗口及施放时的隐身同时结束。
        boolean lastStandPending = SparkTraitsAbyssListenerBridge.isLastStandPending(owner);
        if (!lastStandPending
                && (!GameFunctions.isPlayerPlayingAndAlive(owner)
                || GameFunctions.isPlayerSpectatingOrCreative(owner))) {
            clear();
            return;
        }

        boolean finishedActive = false;
        if (unlockTicks > 0) {
            unlockTicks--;
        }
        if (activeTicks > 0) {
            applySpeedModifier();
            if (!lastStandPending) {
                keepKnifeReady(owner);
            }
            activeTicks--;
            if (activeTicks <= 0) {
                activeTicks = 0;
                cooldownTicks = Math.max(cooldownTicks, SpecterRules.SECOND_SKILL_COOLDOWN_TICKS);
                finishedActive = true;
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (finishedActive) {
            removeSpeedModifier();
            shieldLayers = 0;
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
                cooldownTicks = Math.max(cooldownTicks, SpecterRules.SECOND_SKILL_COOLDOWN_TICKS);
                shieldLayers = 0;
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
        if (shieldLayers > 0) {
            tag.putInt(SHIELD_LAYERS, shieldLayers);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        unlockTicks = readTicks(tag, UNLOCK_TICKS);
        activeTicks = readTicks(tag, ACTIVE_TICKS);
        cooldownTicks = readTicks(tag, COOLDOWN_TICKS);
        // Layers only exist inside a window. / 护盾只存在于窗口之内。
        shieldLayers = activeTicks > 0 ? readTicks(tag, SHIELD_LAYERS) : 0;
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
        buf.writeVarInt(getShieldLayers());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        unlockTicks = Math.max(0, buf.readVarInt());
        activeTicks = Math.max(0, buf.readVarInt());
        cooldownTicks = Math.max(0, buf.readVarInt());
        shieldLayers = Math.max(0, buf.readVarInt());
    }

    /**
     * The Otherworld keeps the Wathe knife ready: each new natural cooldown (the one Wathe's receiver writes after a
     * stab) is dropped through SparkFactionAPI {@code clearItemKeepingForced}, which keeps any forced cooldown (Fiend
     * aura, Abyss Listener, Knight seal, …). While only that forced part is left, nothing is rewritten.
     * 里世界让 Wathe 小刀保持就绪：每次新的自然冷却（Wathe 接收器在刺击后写入的冷却）都经 SparkFactionAPI
     * {@code clearItemKeepingForced} 清除，该接口保留所有强制冷却（魔人光环、聆渊者、骑士封印等）。只剩强制部分时不再重写。
     */
    private void keepKnifeReady(ServerPlayerEntity owner) {
        int remaining = ForcedCooldowns.itemRemainingTicks(owner, WatheItems.KNIFE);
        if (remaining <= 0) {
            return;
        }
        long now = owner.getServerWorld().getTime();
        if (remaining <= knifeForcedUntilTick - now + KNIFE_KEPT_SLACK_TICKS) {
            return;
        }
        knifeForcedUntilTick = now + ForcedCooldowns.clearItemKeepingForced(owner, WatheItems.KNIFE);
    }

    /** Removes what a window grants: speed and, when cut short, the cast's invisibility. / 移除窗口给予的效果：速度，以及提前结束时施放的隐身。 */
    private void endWindowEffects() {
        removeSpeedModifier();
        if (activeTicks > 0) {
            removeCastInvisibility();
        }
        knifeForcedUntilTick = 0L;
    }

    /**
     * Only the invisibility this window granted (ambient, level I, ending with the window); a longer or stronger one from
     * another source is kept.
     * 只移除本窗口给予的隐身（环境效果、I 级、与窗口同时结束）；其他来源更长或更强的隐身保留。
     */
    private void removeCastInvisibility() {
        StatusEffectInstance invisibility = player.getStatusEffect(StatusEffects.INVISIBILITY);
        if (invisibility != null
                && invisibility.isAmbient()
                && invisibility.getAmplifier() == 0
                && !invisibility.isInfinite()
                && invisibility.getDuration() <= activeTicks + INVISIBILITY_MATCH_SLACK_TICKS) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
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

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
