package dev.caecorthus.sparkwitch.impl;

public final class SparkWitchBuiltInSkills {
    private static boolean registered;

    private SparkWitchBuiltInSkills() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // v1 intentionally ships the active-skill pipeline without invented effects.
        // v1 只交付主动技能管线，不在没有设计稿时凭空发明技能效果。
    }
}
