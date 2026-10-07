package dev.caecorthus.sparkwitch.client.scope;

import net.minecraft.client.gui.DrawContext;

/**
 * WP4-usec compile stub of the frozen WP4-scope API; the coordinator keeps WP4-scope's real file at merge.
 * WP4-usec 针对冻结的 WP4-scope API 的编译桩；合并时由协调者保留 WP4-scope 的真实文件。
 */
public interface ScopeProfile {
    float fovMultiplier();

    float sensitivityMultiplier();

    void drawReticle(DrawContext context, ScopeFrame frame);
}
