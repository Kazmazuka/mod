package com.example.realisticview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * 一人称の手(持ち物)の慣性。視点を振ると手が遅れてついてくる。
 * 歩行時は頭の揺れに対して手が少し遅れて揺れる。
 */
public final class HandSway {
    private static float lastYaw, lastPitch, wy, wp, swayYaw, swayPitch;
    private static boolean has;
    private static long lastNs;

    private HandSway() {}

    public static void onRenderHand(RenderHandEvent e) {
        if (!Config.HAND_SWAY.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        update(mc);

        PoseStack ps = e.getPoseStack();
        ps.translate(HeadBob.handX(), HeadBob.handY(), 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(swayYaw));
        ps.mulPose(Axis.XP.rotationDegrees(swayPitch));
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
