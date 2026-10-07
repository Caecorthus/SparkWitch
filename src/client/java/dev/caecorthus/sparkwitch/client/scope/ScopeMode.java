package dev.caecorthus.sparkwitch.client.scope;

/**
 * The player's scope picture (owner decision Q3, Options → Accessibility). {@link #ZOOM_BLUR} is the default: the
 * whole view zooms and the area outside the lens is blurred. {@link #PICTURE_IN_PICTURE} keeps a sharp 1x view around a
 * second, magnified render inside the lens; it is experimental and costs FPS, falls back to {@link #ZOOM_BLUR} under a
 * shader pack, and until the PiP renderer lands (WP4b) it always renders as {@link #ZOOM_BLUR}
 * ({@link ScopeClient#effectiveMode()} reports what actually renders).
 * 玩家的开镜画面（所有者决定 Q3，选项 → 辅助功能）。{@link #ZOOM_BLUR} 为默认：整个画面放大、镜外模糊。
 * {@link #PICTURE_IN_PICTURE} 镜外保持清晰的 1 倍画面，镜内再渲染一次放大画面；属于实验功能且会掉帧，开光影包时退回
 * {@link #ZOOM_BLUR}；在画中画渲染器（WP4b）完成前始终按 {@link #ZOOM_BLUR} 渲染（{@link ScopeClient#effectiveMode()}
 * 报告实际渲染的模式）。
 */
public enum ScopeMode {
    ZOOM_BLUR,
    PICTURE_IN_PICTURE
}
