package dev.caecorthus.sparkwitch.client.seeker.console;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Role-owned Seeker Console (keys {@code screen.sparkwitch.seeker_console.*}; never the witch skill panel): status
 * row, Control Car, View Camera, Recall Car (remote), Police Network.
 * TODO(WP-11): implement. / 待 WP-11 实现。
 * 职业自有的搜寻者控制台（键名 {@code screen.sparkwitch.seeker_console.*}；绝不使用魔女技能面板）：
 * 状态行、操控小车、查看摄像头、远程回收小车、警察网络。
 */
public class SeekerConsoleScreen extends Screen {
    private final Hand hand;

    public SeekerConsoleScreen(Hand hand) {
        super(Text.translatable("screen.sparkwitch.seeker_console.title"));
        this.hand = hand;
    }

    public Hand hand() {
        return hand;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
