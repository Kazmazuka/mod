package com.example.realisticview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * 一人称の手の演出。
 *  - 視点を振ると手が遅れてついてくる(慣性)
 *  - 歩行で手が頭より少し遅れて揺れる
 *  - 攻撃・設置スイングに滑らかな弧と手首のひねりを足す
 *  - Qキーでアイテムを投げる時の「引いて→振り出し→戻る」腕の動き
 */
public final class HandSway {
    private static float lastYaw, lastPitch, wy, wp, swayYaw, swayPitch;
    private static boolean has;
    private static long lastNs;

    private static boolean throwing;
    private static long throwStartNs;
    private static boolean wasDropDown;

    private static final float THROW_SECONDS = 0.34f;

    private HandSway() {}

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            wasDropDown = false;
            throwing = false;
            return;
        }
        boolean down = mc.options.keyDrop.isDown() && mc.screen == null;
        if (Config.HAND_SWAY.get() && down && !p.isSpectator() && !p.getMainHandItem().isEmpty()
                && (!wasDropDown || !throwing)) {
            throwing = true;
            throwStartNs = Util.getNanos();
        }
        wasDropDown = down;
    }

    public static void onRenderHand(RenderHandEvent e) {
        if (!Config.HAND_SWAY.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        update(mc);

        PoseStack ps = e.getPoseStack();
        ps.translate(HeadBob.handX(), HeadBob.handY(), 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(swayYaw));
        ps.mulPose(Axis.XP.rotationDegrees(swayPitch));

        // スイング(攻撃・設置)に滑らかな弧を足す
        float sp = e.getSwingProgress();
        if (sp > 0f && sp < 1f) {
            float arc = (float) Math.sin(sp * Math.PI);
            ps.translate(0.03f * arc, -0.02f * arc, 0.0);
            ps.mulPose(Axis.ZP.rotationDegrees(-7f * arc));
        }

        // 投げる動作
        if (throwing && e.getHand() == InteractionHand.MAIN_HAND) {
            float t = (Util.getNanos() - throwStartNs) / 1.0e9f / THROW_SECONDS;
            if (t >= 1f) {
                throwing = false;
            } else {
                float ang, ty, tz;
                if (t < 0.28f) {                         // 引く
                    float u = smooth(t / 0.28f);
                    ang = -10f * u;
                    ty = -0.03f * u;
                    tz = 0.05f * u;
                } else if (t < 0.55f) {                  // 振り出し
                    float v = smooth((t - 0.28f) / 0.27f);
                    ang = -10f + 38f * v;
                    ty = -0.03f + 0.07f * v;
                    tz = 0.05f - 0.19f * v;
                } else {                                 // 戻る
                    float w = smooth((t - 0.55f) / 0.45f);
                    ang = 28f * (1f - w);
                    ty = 0.04f * (1f - w);
                    tz = -0.14f * (1f - w);
                }
                ps.translate(0.0, ty, tz);
                ps.mulPose(Axis.XP.rotationDegrees(ang));
            }
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static void update(Minecraft mc) {
        long now = Util.getNanos();
        if (has && now - lastNs < 1_000_000L) return; // 同じフレームの2本目の手
        float dt = has ? Mth.clamp((now - lastNs) / 1.0e9f, 0.0005f, 0.1f) : 0.016f;
        lastNs = now;

        Camera cam = mc.gameRenderer.getMainCamera();
        float yaw = cam.getYRot() - HeadBob.yawOut();
        float pitch = cam.getXRot() - HeadBob.pitchOut();

        if (has) {
            float vy = Mth.wrapDegrees(yaw - lastYaw) / dt;
            float vp = (pitch - lastPitch) / dt;
            float k = 1f - (float) Math.exp(-dt * 14f);
            wy += (vy - wy) * k;
            wp += (vp - wp) * k;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        has = true;

        swayYaw = Mth.clamp(wy * 0.012f, -7f, 7f);
        swayPitch = Mth.clamp(wp * 0.012f, -5f, 5f);
    }
}
