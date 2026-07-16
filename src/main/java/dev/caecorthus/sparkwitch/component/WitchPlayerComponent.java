package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.mana.WitchManaRules;
import dev.caecorthus.sparkwitch.mana.WitchManaService;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityWindowRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodChaseRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerState;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintAbilityService;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintPlayerState;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaParryRuntime;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stores per-round SparkWitch player state.
 * 保存玩家本局 SparkWitch 运行态，包括主动技能、冷却和魔力值。
 */
public final class WitchPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("player"),
            WitchPlayerComponent.class
    );

    // Package-private only for component-local codec Modules that preserve sync and NBT schemas.
    // 仅供 component 包内 codec Module 使用，用来保持同步包和 NBT schema 的 Locality。
    final PlayerEntity player;
    Identifier activeSkillId;
    Identifier forcedSkillId;
    int cooldownTicks;
    boolean manaEnabled;
    int mana;
    int manaRegenerationTicks;
    int ceremonialSwordTicks;
    int ceremonialSwordSlot = -1;
    int grandWitchCeremonialSwordTasks;
    int mightyForceTicks;
    int swiftStepTicks;
    int murderSenseTicks;
    int healingTicks;
    int healingPulseTicks;
    int clairvoyanceSelfTicks;
    int clairvoyanceOthersTicks;
    int deferredCooldownTicks;
    int pigChaseFreezeTicks;
    int pigChaseQueuedTicks;
    int pigChaseTicks;
    double pigChaseFreezeX;
    double pigChaseFreezeY;
    double pigChaseFreezeZ;
    boolean pigChaseOwnsPsycho;
    int deathRayTicks;
    int deathRayCharges;
    private final SaintPlayerState saintState = new SaintPlayerState();
    int ninjaParryTicks;
    private final ProphetPlayerState prophetState = new ProphetPlayerState();

    public WitchPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable Identifier getActiveSkillId() {
        return activeSkillId;
    }

    public @Nullable Identifier getForcedSkillId() {
        return forcedSkillId;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean hasManaSystem() {
        return manaEnabled;
    }

    public int getMana() {
        return mana;
    }

    public int incrementManaRegenerationTicks() {
        return ++manaRegenerationTicks;
    }

    public void resetManaRegenerationTicks() {
        manaRegenerationTicks = 0;
    }

    public int getCeremonialSwordTicks() {
        return ceremonialSwordTicks;
    }

    public int getCeremonialSwordSlot() {
        return ceremonialSwordSlot;
    }

    public int getGrandWitchCeremonialSwordTasks() {
        return grandWitchCeremonialSwordTasks;
    }

    public boolean hasUnlockedGrandWitchCeremonialSword() {
        return GrandWitchRules.isCeremonialSwordUnlocked(grandWitchCeremonialSwordTasks);
    }

    public int getMightyForceTicks() {
        return mightyForceTicks;
    }

    public int getSwiftStepTicks() {
        return swiftStepTicks;
    }

    public int getMurderSenseTicks() {
        return murderSenseTicks;
    }

    public int getHealingTicks() {
        return healingTicks;
    }

    public int getClairvoyanceSelfTicks() {
        return clairvoyanceSelfTicks;
    }

    public int getClairvoyanceOthersTicks() {
        return clairvoyanceOthersTicks;
    }

    public int getPigChaseFreezeTicks() {
        return pigChaseFreezeTicks;
    }

    public int getPigChaseTicks() {
        return pigChaseTicks;
    }

    public int getDeathRayTicks() {
        return deathRayTicks;
    }

    public int getDeathRayCharges() {
        return deathRayCharges;
    }

    public SaintPlayerState getSaintState() {
        return saintState;
    }

    public ProphetPlayerState getProphetState() {
        return prophetState;
    }

    public int getNinjaParryTicks() {
        return ninjaParryTicks;
    }

    public boolean isNinjaParryActive() {
        return ninjaParryTicks > 0;
    }

    public int getDeathOmenTicks() {
        return prophetState.remainingTicks();
    }

    public boolean isDeathOmenActive() {
        return prophetState.isActive();
    }

    public boolean isDeathOmenBody(UUID bodyUuid) {
        return prophetState.containsBody(bodyUuid);
    }

    public int decrementCeremonialSwordTicks() {
        if (ceremonialSwordTicks > 0) {
            ceremonialSwordTicks--;
        }
        return ceremonialSwordTicks;
    }

    public int decrementDeathRayTicks() {
        if (deathRayTicks > 0) {
            deathRayTicks--;
        }
        return deathRayTicks;
    }

    public int decrementDeathRayCharges() {
        if (deathRayCharges > 0) {
            deathRayCharges--;
        }
        return deathRayCharges;
    }

    public int getActiveSkillWindowTicks() {
        int existingWindowTicks = Math.max(
                Math.max(
                        Math.max(ceremonialSwordTicks, apprenticeEffectiveWindowTicks()),
                        pigChaseEffectiveWindowTicks()
                ),
                deathRayTicks
        );
        int registeredWindowTicks = WitchSkillRegistry.activeWindowTicks(activeSkillId, player);
        return Math.max(
                Math.max(Math.max(existingWindowTicks, ninjaParryTicks), prophetState.remainingTicks()),
                registeredWindowTicks
        );
    }

    public boolean hasSkill() {
        return activeSkillId != null;
    }

    public boolean hasActiveCeremonialSword() {
        return ceremonialSwordTicks > 0 && ceremonialSwordSlot >= 0;
    }

    public boolean hasActiveMightyForce() {
        return mightyForceTicks > 0;
    }

    public boolean hasDeferredCooldown() {
        return deferredCooldownTicks > 0;
    }

    public boolean hasActivePigChaseState() {
        return pigChaseFreezeTicks > 0 || pigChaseQueuedTicks > 0 || pigChaseTicks > 0;
    }

    public boolean isPigChaseFreezeActive() {
        return pigChaseFreezeTicks > 0;
    }

    public boolean isPigChaseActive() {
        return pigChaseTicks > 0;
    }

    public boolean hasActiveDeathRay() {
        return deathRayTicks > 0 && deathRayCharges > 0;
    }

    /**
     * Exposes only the Apprentice window state needed by its owning runtime Module.
     * 只向预备魔女运行时 Module 暴露其窗口 tick 所需的状态快照。
     */
    public ApprenticeAbilityWindowRules.WindowState apprenticeWindowState() {
        return new ApprenticeAbilityWindowRules.WindowState(
                mightyForceTicks,
                swiftStepTicks,
                murderSenseTicks,
                healingTicks,
                healingPulseTicks,
                clairvoyanceSelfTicks,
                clairvoyanceOthersTicks,
                deferredCooldownTicks
        );
    }

    public void applyApprenticeWindowState(ApprenticeAbilityWindowRules.WindowState state) {
        mightyForceTicks = state.mightyForceTicks();
        swiftStepTicks = state.swiftStepTicks();
        murderSenseTicks = state.murderSenseTicks();
        healingTicks = state.healingTicks();
        healingPulseTicks = state.healingPulseTicks();
        clairvoyanceSelfTicks = state.clairvoyanceSelfTicks();
        clairvoyanceOthersTicks = state.clairvoyanceOthersTicks();
        deferredCooldownTicks = state.deferredCooldownTicks();
    }

    public void clearApprenticeWindowState() {
        mightyForceTicks = 0;
        swiftStepTicks = 0;
        murderSenseTicks = 0;
        healingTicks = 0;
        healingPulseTicks = 0;
        clairvoyanceSelfTicks = 0;
        clairvoyanceOthersTicks = 0;
        deferredCooldownTicks = 0;
    }

    public boolean hasApprenticeWindowState() {
        return mightyForceTicks > 0
                || swiftStepTicks > 0
                || murderSenseTicks > 0
                || healingTicks > 0
                || clairvoyanceSelfTicks > 0
                || clairvoyanceOthersTicks > 0
                || deferredCooldownTicks > 0;
    }

    /**
     * Exposes the stored Pig Chase fields without moving their packet or NBT ownership.
     * 暴露已保存的皮革噶追杀字段，但不转移其同步包或 NBT 所有权。
     */
    public PigChaseState pigChaseState() {
        return new PigChaseState(
                pigChaseFreezeTicks,
                pigChaseQueuedTicks,
                pigChaseTicks,
                pigChaseFreezeX,
                pigChaseFreezeY,
                pigChaseFreezeZ,
                pigChaseOwnsPsycho
        );
    }

    public void applyPigChaseState(PigChaseState state) {
        pigChaseFreezeTicks = state.freezeTicks();
        pigChaseQueuedTicks = state.queuedTicks();
        pigChaseTicks = state.chaseTicks();
        pigChaseFreezeX = state.freezeX();
        pigChaseFreezeY = state.freezeY();
        pigChaseFreezeZ = state.freezeZ();
        pigChaseOwnsPsycho = state.ownsPsycho();
    }

    public record PigChaseState(
            int freezeTicks,
            int queuedTicks,
            int chaseTicks,
            double freezeX,
            double freezeY,
            double freezeZ,
            boolean ownsPsycho
    ) {
    }

    public void setActiveSkill(@Nullable Identifier activeSkillId) {
        if (this.activeSkillId == null ? activeSkillId == null : this.activeSkillId.equals(activeSkillId)) {
            return;
        }
        this.activeSkillId = activeSkillId;
        sync();
    }

    /**
     * Stores a next-round admin ability lock without exposing it in normal client sync.
     * 保存下一局管理员锁定的能力，不通过普通客户端同步包展示。
     */
    public boolean setForcedSkill(@Nullable Identifier forcedSkillId) {
        if (this.forcedSkillId == null ? forcedSkillId == null : this.forcedSkillId.equals(forcedSkillId)) {
            return false;
        }
        this.forcedSkillId = forcedSkillId;
        return true;
    }

    public boolean clearForcedSkill() {
        return setForcedSkill(null);
    }

    public void setCooldownTicks(int cooldownTicks) {
        int normalized = Math.max(0, cooldownTicks);
        if (this.cooldownTicks == normalized) {
            return;
        }
        this.cooldownTicks = normalized;
        sync();
    }

    public void initializeMana() {
        boolean changed = !manaEnabled || mana != WitchManaRules.INITIAL_MANA || manaRegenerationTicks != 0;
        manaEnabled = true;
        mana = WitchManaRules.INITIAL_MANA;
        manaRegenerationTicks = 0;
        if (changed) {
            sync();
        }
    }

    public void clearMana() {
        if (!manaEnabled && mana == 0 && manaRegenerationTicks == 0) {
            return;
        }
        manaEnabled = false;
        mana = 0;
        manaRegenerationTicks = 0;
        sync();
    }

    public void addMana(int amount) {
        if (!manaEnabled || amount <= 0) {
            return;
        }
        int normalized = Math.max(0, mana + amount);
        if (mana == normalized) {
            return;
        }
        mana = normalized;
        sync();
    }

    /**
     * Sets mana directly for admin commands.
     * 供管理员命令直接设定魔力值，并重置自然回复计时。
     */
    public void setMana(int amount) {
        int normalized = Math.max(0, amount);
        boolean changed = !manaEnabled || mana != normalized || manaRegenerationTicks != 0;
        manaEnabled = true;
        mana = normalized;
        manaRegenerationTicks = 0;
        if (changed) {
            sync();
        }
    }

    public boolean spendMana(int amount) {
        if (!manaEnabled || amount <= 0 || mana < amount) {
            return false;
        }
        mana -= amount;
        sync();
        return true;
    }

    public void beginCeremonialSwordWindow(int slot, int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        int normalizedSlot = normalizedDuration > 0 ? Math.max(0, slot) : -1;
        if (ceremonialSwordTicks == normalizedDuration && ceremonialSwordSlot == normalizedSlot) {
            return;
        }
        ceremonialSwordTicks = normalizedDuration;
        ceremonialSwordSlot = normalizedSlot;
        sync();
    }

    public void recordGrandWitchCeremonialSwordTask() {
        setGrandWitchCeremonialSwordTasks(grandWitchCeremonialSwordTasks + 1);
    }

    private void setGrandWitchCeremonialSwordTasks(int completedTasks) {
        int normalized = GrandWitchRules.clampCeremonialSwordTaskProgress(completedTasks);
        if (grandWitchCeremonialSwordTasks == normalized) {
            return;
        }
        grandWitchCeremonialSwordTasks = normalized;
        sync();
    }

    public void completeCeremonialSwordWindow(int cooldownTicks) {
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        sync();
    }

    public void clearCeremonialSwordWindow() {
        if (ceremonialSwordTicks == 0 && ceremonialSwordSlot < 0) {
            return;
        }
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
        sync();
    }

    public void beginMightyForceWindow(int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        if (mightyForceTicks == normalizedDuration) {
            return;
        }
        mightyForceTicks = normalizedDuration;
        sync();
    }

    public void clearMightyForceWindow() {
        if (mightyForceTicks == 0) {
            return;
        }
        mightyForceTicks = 0;
        startDeferredCooldownNow();
        sync();
    }

    public void beginSwiftStep(int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        if (swiftStepTicks == normalizedDuration) {
            return;
        }
        swiftStepTicks = normalizedDuration;
        sync();
    }

    public void beginMurderSense(int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        if (murderSenseTicks == normalizedDuration) {
            return;
        }
        murderSenseTicks = normalizedDuration;
        sync();
    }

    public void beginHealingAura(int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        if (healingTicks == normalizedDuration && healingPulseTicks == 0) {
            return;
        }
        healingTicks = normalizedDuration;
        healingPulseTicks = 0;
        sync();
    }

    public void beginClairvoyance(int selfTicks, int othersTicks) {
        int normalizedSelfTicks = Math.max(0, selfTicks);
        int normalizedOthersTicks = Math.max(0, othersTicks);
        if (clairvoyanceSelfTicks == normalizedSelfTicks && clairvoyanceOthersTicks == normalizedOthersTicks) {
            return;
        }
        clairvoyanceSelfTicks = normalizedSelfTicks;
        clairvoyanceOthersTicks = normalizedOthersTicks;
        sync();
    }

    public void beginDeathRayWindow(int durationTicks, int charges) {
        deathRayTicks = Math.max(0, durationTicks);
        deathRayCharges = deathRayTicks > 0 ? Math.max(0, charges) : 0;
        sync();
    }

    public void finishDeathRayWindowState(int cooldownTicks) {
        deathRayTicks = 0;
        deathRayCharges = 0;
        deferredCooldownTicks = Math.max(deferredCooldownTicks, Math.max(0, cooldownTicks));
        startDeferredCooldownNow();
    }

    public void resetDeathRayWindowState() {
        deathRayTicks = 0;
        deathRayCharges = 0;
    }

    public void clearDeferredCooldownState() {
        deferredCooldownTicks = 0;
    }

    public void clearDeathRayWindow() {
        if (deathRayTicks == 0 && deathRayCharges == 0) {
            return;
        }
        deathRayTicks = 0;
        deathRayCharges = 0;
        sync();
    }

    public void beginNinjaParryWindow(int durationTicks) {
        int normalized = Math.max(0, durationTicks);
        if (ninjaParryTicks == normalized) {
            return;
        }
        ninjaParryTicks = normalized;
        sync();
    }

    public int decrementNinjaParryTicks() {
        if (ninjaParryTicks > 0) {
            ninjaParryTicks--;
        }
        return ninjaParryTicks;
    }

    /**
     * Consumes or expires the parry and starts its already-deferred shared cooldown.
     * 消耗或结束格挡窗口，并启动已延后的共享冷却。
     */
    public void finishNinjaParryWindow() {
        if (ninjaParryTicks <= 0 && deferredCooldownTicks <= 0) {
            return;
        }
        ninjaParryTicks = 0;
        startDeferredCooldownNow();
        sync();
    }

    public void clearNinjaParryWindow() {
        if (ninjaParryTicks <= 0) {
            return;
        }
        ninjaParryTicks = 0;
        deferredCooldownTicks = 0;
        sync();
    }

    public void beginDeathOmenWindow(int durationTicks) {
        prophetState.begin(durationTicks);
        sync();
    }

    public boolean recordDeathOmenBody(UUID bodyUuid) {
        if (!prophetState.recordBody(bodyUuid)) {
            return false;
        }
        sync();
        return true;
    }

    public void tickDeathOmenWindow() {
        ProphetPlayerState.TickOutcome outcome = prophetState.tick();
        if (outcome == ProphetPlayerState.TickOutcome.FINISHED) {
            startDeferredCooldownNow();
            sync();
        } else if (outcome == ProphetPlayerState.TickOutcome.SYNC) {
            sync();
        }
    }

    public void cancelDeathOmenWindow() {
        if (!prophetState.cancel()) {
            return;
        }
        deferredCooldownTicks = 0;
        sync();
    }

    /**
     * Arms cooldown for effects whose visible window must finish before cooldown begins.
     * 为“技能结束后才冷却”的效果保存待启动冷却，等有效窗口归零后再正式计时。
     */
    public void deferCooldownUntilActiveWindowEnds(int cooldownTicks) {
        int normalized = Math.max(0, cooldownTicks);
        if (normalized == 0) {
            deferredCooldownTicks = 0;
            return;
        }
        deferredCooldownTicks = normalized;
        if (getActiveSkillWindowTicks() <= 0) {
            startDeferredCooldownNow();
        }
        sync();
    }

    public void clear() {
        if (activeSkillId == null
                && cooldownTicks <= 0
                && !manaEnabled
                && mana == 0
                && manaRegenerationTicks == 0
                && ceremonialSwordTicks == 0
                && ceremonialSwordSlot < 0
                && grandWitchCeremonialSwordTasks == 0
                && mightyForceTicks == 0
                && swiftStepTicks == 0
                && murderSenseTicks == 0
                && healingTicks == 0
                && healingPulseTicks == 0
                && clairvoyanceSelfTicks == 0
                && clairvoyanceOthersTicks == 0
                && pigChaseFreezeTicks == 0
                && pigChaseQueuedTicks == 0
                && pigChaseTicks == 0
                && !pigChaseOwnsPsycho
                && deathRayTicks == 0
                && deathRayCharges == 0
                && deferredCooldownTicks == 0
                && saintState.isEmpty()
                && ninjaParryTicks == 0
                && prophetState.isEmpty()) {
            return;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            // Preserve reset ordering: Pig sound, Grand Witch BGM/world sync, then owned psycho state.
            // 保持重置顺序：先停皮革噶声音，再停大魔女 BGM/世界同步，最后清理自有疯魔状态。
            PigGodChaseRuntime.stopSoundBeforeComponentReset(serverPlayer, this);
            GrandWitchActiveSkillService.stopCeremonialSwordBgm(serverPlayer);
            PigGodChaseRuntime.clearPsychoBeforeComponentReset(serverPlayer, this);
        }
        activeSkillId = null;
        cooldownTicks = 0;
        manaEnabled = false;
        mana = 0;
        manaRegenerationTicks = 0;
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
        grandWitchCeremonialSwordTasks = 0;
        mightyForceTicks = 0;
        swiftStepTicks = 0;
        murderSenseTicks = 0;
        healingTicks = 0;
        healingPulseTicks = 0;
        clairvoyanceSelfTicks = 0;
        clairvoyanceOthersTicks = 0;
        pigChaseFreezeTicks = 0;
        pigChaseQueuedTicks = 0;
        pigChaseTicks = 0;
        pigChaseOwnsPsycho = false;
        deathRayTicks = 0;
        deathRayCharges = 0;
        deferredCooldownTicks = 0;
        saintState.clear();
        ninjaParryTicks = 0;
        prophetState.clear();
        sync();
    }

    public void sync() {
        if (player != null) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
    }

    @Override
    public void serverTick() {
        ServerPlayerEntity serverPlayer = player instanceof ServerPlayerEntity current ? current : null;
        if (serverPlayer != null) {
            GrandWitchActiveSkillService.tickCeremonialSwordWindow(serverPlayer, this);
            ApprenticeAbilityRuntime.tick(serverPlayer, this);
            PigGodChaseRuntime.tick(serverPlayer, this);
            MurderousWitchDeathRayService.tickWindow(serverPlayer, this);
            NinjaParryRuntime.tick(serverPlayer, this);
        }
        tickCooldown();
        if (serverPlayer != null) {
            ProphetRuntime.tick(serverPlayer, this);
            WitchManaService.tickRegeneration(serverPlayer, this);
            SaintAbilityService.tick(serverPlayer, this);
        }
    }

    private void tickCooldown() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
                sync();
            }
        }
    }

    private int apprenticeEffectiveWindowTicks() {
        return ApprenticeAbilityWindowRules.effectiveWindowTicks(
                mightyForceTicks,
                swiftStepTicks,
                murderSenseTicks,
                healingTicks,
                clairvoyanceSelfTicks,
                clairvoyanceOthersTicks
        );
    }

    public void startDeferredCooldownNow() {
        if (deferredCooldownTicks <= 0) {
            return;
        }
        cooldownTicks = Math.max(cooldownTicks, deferredCooldownTicks);
        deferredCooldownTicks = 0;
    }

    private int pigChaseEffectiveWindowTicks() {
        return pigChaseFreezeTicks + pigChaseQueuedTicks + pigChaseTicks;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        WitchPlayerSyncCodec.write(this, buf, recipient);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        WitchPlayerSyncCodec.read(this, buf);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        WitchPlayerNbtCodec.write(this, tag);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        WitchPlayerNbtCodec.read(this, tag);
    }
}
