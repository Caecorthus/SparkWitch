package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Pure tuning constants for Apprentice Witch skills.
 * 预备魔女技能的纯数值配置集中在这里，避免注册、组件和效果逻辑各写一份。
 */
public final class ApprenticeWitchSkillRules {
    public static final Identifier MIGHTY_FORCE_ID = SparkWitch.id("mighty_force");
    public static final Identifier SWIFT_STEP_ID = SparkWitch.id("swift_step");
    public static final Identifier MURDER_SENSE_ID = SparkWitch.id("murder_sense");
    public static final Identifier HEALING_ID = SparkWitch.id("healing");
    public static final Identifier CLAIRVOYANCE_ID = SparkWitch.id("clairvoyance");

    public static final List<Identifier> SKILL_IDS = List.of(
            MIGHTY_FORCE_ID,
            SWIFT_STEP_ID,
            MURDER_SENSE_ID,
            HEALING_ID,
            CLAIRVOYANCE_ID
    );

    public static final int INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);

    public static final int MIGHTY_FORCE_MANA_COST = 100;
    public static final int MIGHTY_FORCE_WINDOW_TICKS = GameConstants.getInTicks(0, 10);
    public static final int MIGHTY_FORCE_COOLDOWN_TICKS = GameConstants.getInTicks(5, 0);
    public static final double MIGHTY_FORCE_KNOCKBACK_STRENGTH = 10.0;

    public static final int SWIFT_STEP_MANA_COST = 50;
    public static final int SWIFT_STEP_DURATION_TICKS = GameConstants.getInTicks(0, 5);
    public static final int SWIFT_STEP_COOLDOWN_TICKS = GameConstants.getInTicks(2, 0);
    public static final int SWIFT_STEP_AMPLIFIER = 2;

    public static final int MURDER_SENSE_MANA_COST = 80;
    public static final int MURDER_SENSE_DURATION_TICKS = GameConstants.getInTicks(0, 15);
    public static final int MURDER_SENSE_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final double MURDER_SENSE_RANGE_BLOCKS = 20.0;
    public static final int MURDER_SENSE_COLOR = 0xFF3030;

    public static final int HEALING_MANA_COST = 60;
    public static final int HEALING_DURATION_TICKS = GameConstants.getInTicks(0, 20);
    public static final int HEALING_COOLDOWN_TICKS = GameConstants.getInTicks(2, 0);
    public static final double HEALING_RANGE_BLOCKS = 8.0;
    public static final float HEALING_MOOD_PER_SECOND = 0.03f;

    public static final int CLAIRVOYANCE_MANA_COST = 100;
    public static final int CLAIRVOYANCE_SELF_TICKS = GameConstants.getInTicks(0, 30);
    public static final int CLAIRVOYANCE_OTHERS_TICKS = GameConstants.getInTicks(0, 10);
    public static final int CLAIRVOYANCE_COOLDOWN_TICKS = GameConstants.getInTicks(3, 0);
    public static final int CLAIRVOYANCE_SELF_COLOR = 0x7EE8FF;
    public static final int CLAIRVOYANCE_TARGET_COLOR = 0xFFFFFF;

    public static final Set<Identifier> DANGEROUS_ITEM_IDS = Set.of(
            Identifier.of("wathe", "revolver"),
            Identifier.of("wathe", "derringer"),
            Identifier.of("noellesroles", "demon_hunter_pistol"),
            Identifier.of("wathe", "knife"),
            Identifier.of("wathe", "bat"),
            Identifier.of("wathe", "grenade"),
            Identifier.of("wathe", "poison_vial"),
            Identifier.of("wathe", "scorpion"),
            Identifier.of("noellesroles", "poison_needle"),
            Identifier.of("noellesroles", "poison_gas_bomb"),
            Identifier.of("noellesroles", "throwing_axe"),
            SparkWitch.id("ceremonial_sword"),
            SparkWitch.id("fire_poker")
    );

    private ApprenticeWitchSkillRules() {
    }
}
