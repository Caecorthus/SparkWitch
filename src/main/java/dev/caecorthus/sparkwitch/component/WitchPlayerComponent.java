package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityWindowRules;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodSkillService;
import dev.caecorthus.sparkwitch.mana.WitchManaRules;
import dev.doctor4t.wathe.api.event.PsychoType;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
        return WitchFactionRules.isCeremonialSwordUnlocked(grandWitchCeremonialSwordTasks);
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

    public int getActiveSkillWindowTicks() {
        return Math.max(
                Math.max(
                        Math.max(ceremonialSwordTicks, apprenticeEffectiveWindowTicks()),
                        pigChaseEffectiveWindowTicks()
                ),
                deathRayTicks
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
        int normalized = WitchFactionRules.clampCeremonialSwordTaskProgress(completedTasks);
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
        startDeferredCooldown();
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

    public void beginPigChaseFreeze(int freezeTicks, int chaseTicks, double x, double y, double z) {
        pigChaseFreezeTicks = Math.max(0, freezeTicks);
        pigChaseQueuedTicks = Math.max(0, chaseTicks);
        pigChaseTicks = 0;
        pigChaseFreezeX = x;
        pigChaseFreezeY = y;
        pigChaseFreezeZ = z;
        pigChaseOwnsPsycho = false;
        if (PigGodRules.shouldStartChaseImmediately(pigChaseFreezeTicks, pigChaseQueuedTicks)
                && player instanceof ServerPlayerEntity serverPlayer) {
            startPigChase(serverPlayer);
        }
        sync();
    }

    public void beginDeathRayWindow(int durationTicks, int charges) {
        deathRayTicks = Math.max(0, durationTicks);
        deathRayCharges = deathRayTicks > 0 ? Math.max(0, charges) : 0;
        sync();
    }

    public void consumeDeathRayCharge(int cooldownTicks) {
        if (!hasActiveDeathRay()) {
            return;
        }
        deathRayCharges--;
        if (deathRayCharges <= 0) {
            finishDeathRayWindow(cooldownTicks);
            return;
        }
        sync();
    }

    public void finishDeathRayWindow(int cooldownTicks) {
        deathRayTicks = 0;
        deathRayCharges = 0;
        deferredCooldownTicks = Math.max(deferredCooldownTicks, Math.max(0, cooldownTicks));
        startDeferredCooldown();
        sync();
    }

    public void clearDeathRayWindow() {
        if (deathRayTicks == 0 && deathRayCharges == 0) {
            return;
        }
        deathRayTicks = 0;
        deathRayCharges = 0;
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
            startDeferredCooldown();
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
                && deferredCooldownTicks == 0) {
            return;
        }
        boolean hadPigChaseSound = hasActivePigChaseState() || pigChaseOwnsPsycho;
        if (hadPigChaseSound) {
            stopPigChaseSound();
        }
        stopGrandWitchCeremonialSwordBgm();
        clearPigChasePsycho();
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
        tickCeremonialSwordWindow();
        tickApprenticeSkillWindows();
        tickPigChaseState();
        tickDeathRayState();
        tickCooldown();
        tickManaRegeneration();
    }

    private void tickCeremonialSwordWindow() {
        if (ceremonialSwordTicks <= 0) {
            return;
        }
        ceremonialSwordTicks--;
        if (ceremonialSwordTicks == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            GrandWitchActiveSkillService.finishCeremonialSwordWindow(serverPlayer, this);
            return;
        }
        if (ceremonialSwordTicks % 20 == 0) {
            sync();
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

    private void tickApprenticeSkillWindows() {
        if (!hasApprenticeWindowState()) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        tickApprenticeSkillWindows(role, GameFunctions.isPlayerPlayingAndAlive(serverPlayer), serverPlayer);
    }

    void tickApprenticeSkillWindows(Role role, boolean playingAndAlive, @Nullable ServerPlayerEntity serverPlayer) {
        if (!hasApprenticeWindowState()) {
            return;
        }

        if (role != dev.caecorthus.sparkwitch.SparkWitchRoles.apprenticeWitch()
                || !playingAndAlive) {
            clearApprenticeSkillWindows();
            return;
        }

        int activeBeforeTick = apprenticeEffectiveWindowTicks();
        boolean shouldSync = false;
        if (mightyForceTicks > 0) {
            mightyForceTicks--;
            shouldSync |= mightyForceTicks == 0 || mightyForceTicks % 20 == 0;
        }
        if (swiftStepTicks > 0) {
            swiftStepTicks--;
            shouldSync |= swiftStepTicks == 0 || swiftStepTicks % 20 == 0;
        }
        if (murderSenseTicks > 0) {
            murderSenseTicks--;
            shouldSync |= murderSenseTicks == 0 || murderSenseTicks % 20 == 0;
        }
        if (healingTicks > 0) {
            healingTicks--;
            healingPulseTicks++;
            if (healingPulseTicks >= 20 && serverPlayer != null) {
                healingPulseTicks = 0;
                HealingAbility.applyPulse(serverPlayer);
            }
            shouldSync |= healingTicks == 0 || healingTicks % 20 == 0;
        }
        if (clairvoyanceSelfTicks > 0) {
            clairvoyanceSelfTicks--;
            shouldSync |= clairvoyanceSelfTicks == 0 || clairvoyanceSelfTicks % 20 == 0;
        }
        if (clairvoyanceOthersTicks > 0) {
            clairvoyanceOthersTicks--;
            shouldSync |= clairvoyanceOthersTicks == 0 || clairvoyanceOthersTicks % 20 == 0;
        }
        if (ApprenticeAbilityWindowRules.shouldStartDeferredCooldown(
                activeBeforeTick,
                apprenticeEffectiveWindowTicks(),
                deferredCooldownTicks
        )) {
            startDeferredCooldown();
            shouldSync = true;
        }
        if (shouldSync) {
            sync();
        }
    }

    private void clearApprenticeSkillWindows() {
        mightyForceTicks = 0;
        swiftStepTicks = 0;
        murderSenseTicks = 0;
        healingTicks = 0;
        healingPulseTicks = 0;
        clairvoyanceSelfTicks = 0;
        clairvoyanceOthersTicks = 0;
        deferredCooldownTicks = 0;
        sync();
    }

    private boolean hasApprenticeWindowState() {
        return mightyForceTicks > 0
                || swiftStepTicks > 0
                || murderSenseTicks > 0
                || healingTicks > 0
                || clairvoyanceSelfTicks > 0
                || clairvoyanceOthersTicks > 0
                || deferredCooldownTicks > 0;
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

    private void startDeferredCooldown() {
        if (deferredCooldownTicks <= 0) {
            return;
        }
        cooldownTicks = Math.max(cooldownTicks, deferredCooldownTicks);
        deferredCooldownTicks = 0;
    }

    private void tickPigChaseState() {
        if (!hasActivePigChaseState()) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!PigGodRules.isPigGod(role) || !GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            clearPigChaseState();
            return;
        }

        int activeBeforeTick = pigChaseEffectiveWindowTicks();
        boolean shouldSync = false;
        if (pigChaseFreezeTicks > 0) {
            holdPigChaseFreeze(serverPlayer);
            pigChaseFreezeTicks--;
            if (pigChaseFreezeTicks == 0) {
                startPigChase(serverPlayer);
                shouldSync = true;
            } else {
                shouldSync |= pigChaseFreezeTicks % 20 == 0;
            }
        } else if (pigChaseQueuedTicks > 0) {
            startPigChase(serverPlayer);
            shouldSync = true;
        } else if (pigChaseTicks > 0) {
            pigChaseTicks--;
            if (pigChaseTicks == 0) {
                stopPigChaseSound();
                clearPigChasePsycho();
                shouldSync = true;
            } else {
                shouldSync |= pigChaseTicks % 20 == 0;
            }
        }
        if (activeBeforeTick > 0 && pigChaseEffectiveWindowTicks() == 0) {
            startDeferredCooldown();
            shouldSync = true;
        }
        if (shouldSync) {
            sync();
        }
    }

    private void holdPigChaseFreeze(ServerPlayerEntity serverPlayer) {
        serverPlayer.teleport(pigChaseFreezeX, pigChaseFreezeY, pigChaseFreezeZ, false);
        serverPlayer.setVelocity(0, 0, 0);
        serverPlayer.velocityModified = true;
    }

    private void startPigChase(ServerPlayerEntity serverPlayer) {
        pigChaseTicks = pigChaseQueuedTicks;
        pigChaseQueuedTicks = 0;
        if (pigChaseTicks <= 0) {
            return;
        }

        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(serverPlayer);
        if (psycho.getPsychoTicks() <= 0) {
            pigChaseOwnsPsycho = psycho.startPsycho(PsychoType.VISIBLE_QUIET);
        }
        if (!pigChaseOwnsPsycho && psycho.getPsychoTicks() <= 0) {
            stopPigChaseSound();
            pigChaseTicks = 0;
            return;
        }
        psycho.setPsychoTicks(pigChaseTicks);
        serverPlayer.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                pigChaseTicks,
                PigGodRules.SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    public void clearPigChaseState() {
        if (!hasActivePigChaseState() && !pigChaseOwnsPsycho) {
            return;
        }
        stopPigChaseSound();
        clearPigChasePsycho();
        pigChaseFreezeTicks = 0;
        pigChaseQueuedTicks = 0;
        pigChaseTicks = 0;
        pigChaseOwnsPsycho = false;
        sync();
    }

    private void clearPigChasePsycho() {
        if (!pigChaseOwnsPsycho || !(player instanceof ServerPlayerEntity serverPlayer)) {
            pigChaseOwnsPsycho = false;
            return;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(serverPlayer);
        if (psycho.getPsychoTicks() > 0) {
            psycho.stopPsycho();
        }
        pigChaseOwnsPsycho = false;
    }

    private void stopPigChaseSound() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PigGodSkillService.stopChaseSound(
                    serverPlayer.getServerWorld(),
                    pigChaseFreezeX,
                    pigChaseFreezeY,
                    pigChaseFreezeZ
            );
        }
    }

    private void stopGrandWitchCeremonialSwordBgm() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            WitchWorldComponent.KEY.get(serverPlayer.getServerWorld())
                    .stopGrandWitchCeremonialSwordBgm(serverPlayer.getUuid());
        }
    }

    private int pigChaseEffectiveWindowTicks() {
        return pigChaseFreezeTicks + pigChaseQueuedTicks + pigChaseTicks;
    }

    private void tickDeathRayState() {
        if (deathRayTicks <= 0 && deathRayCharges <= 0) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!MurderousWitchDeathRayRules.canSelect(role) || !GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            deathRayTicks = 0;
            deathRayCharges = 0;
            deferredCooldownTicks = 0;
            sync();
            return;
        }

        if (deathRayTicks > 0) {
            deathRayTicks--;
        }
        if (deathRayTicks <= 0 || deathRayCharges <= 0) {
            finishDeathRayWindow(MurderousWitchDeathRayRules.COOLDOWN_TICKS);
            return;
        }
        if (deathRayTicks % 20 == 0) {
            sync();
        }
    }

    private void tickManaRegeneration() {
        if (!manaEnabled || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!WitchManaRules.isManaRole(role)) {
            clearMana();
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(serverPlayer) || !WitchManaRules.canRegenerateNaturally(role)) {
            manaRegenerationTicks = 0;
            return;
        }
        if (mana >= WitchManaRules.naturalCap(role)) {
            manaRegenerationTicks = 0;
            return;
        }

        manaRegenerationTicks++;
        if (manaRegenerationTicks >= WitchManaRules.regenerationIntervalTicks(role)) {
            manaRegenerationTicks = 0;
            int regenerated = WitchManaRules.applyNaturalRegeneration(mana, role);
            if (regenerated != mana) {
                mana = regenerated;
                sync();
            }
        }
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
