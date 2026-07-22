package dev.caecorthus.sparkwitch.util;

/**
 * Permission nodes for SparkWitch admin commands.
 * SparkWitch 管理员命令的权限节点，未安装权限插件时回退到默认 op 等级。
 */
public final class SparkWitchPermissions {
    public static final int DEFAULT_COMMAND_LEVEL = 2;
    public static final String COMMAND_SET_MANA = "sparkwitch.command.setmana";
    public static final String COMMAND_FORCE_ABILITY = "sparkwitch.command.forceability";
    public static final String COMMAND_FORCE_PROMOTION = "sparkwitch.command.forcepromotion";
    public static final String COMMAND_GHOST_CHANCE = "sparkwitch.command.ghostchance";
    public static final String COMMAND_GHOST_MIN_REQUIREMENT = "sparkwitch.command.ghostminrequirement";

    private SparkWitchPermissions() {
    }
}
