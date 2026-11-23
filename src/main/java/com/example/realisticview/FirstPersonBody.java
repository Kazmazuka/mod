package com.example.realisticview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 下を向いた時だけ自分のプレイヤーモデルを描画する。
 * 頭・腕は PlayerRendererMixin で非表示(保険としてスケール縮小も併用)。
 */
public final class FirstPersonBody {
    private static final float EPS = 0.0001f;
    private static boolean rendering;

    private FirstPersonBody() {}

    public static boolean isRenderingBody() {
        return rendering;
    }

    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !Config.SHOW_BODY.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Camera cam = e.getCamera();
        if (p == null || cam.isDetached() || cam.getEntity() != p || !mc.options.getCameraType().isFirstPerson()) return;
        if (p.isSpectator() || p.isInvisible() || p.isSleeping() || p.isScoping()) return;
        if (cam.getXRot() < Config.BODY_MIN_PITCH.get()) return; // 十分に下を向いていない時は描画しない

        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        EntityRenderer<?> r = d.getRenderer(p);
        if (!(r instanceof PlayerRenderer pr)) return;

        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);

        double x = Mth.lerp(pt, p.xo, p.getX());
        double y = Mth.lerp(pt, p.yo, p.getY());
        double z = Mth.lerp(pt, p.zo, p.getZ());
        Vec3 c = cam.getPosition();

        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        float pitchFactor = 0.35f + 0.65f * Mth.clamp((cam.getXRot() - 20f) / 50f, 0f, 1f);
        float back = Config.BODY_BACK_OFFSET.get().floatValue() * pitchFactor;
        double ox = Mth.sin(bodyYaw) * back;
        double oz = -Mth.cos(bodyYaw) * back;

        PlayerModel<AbstractClientPlayer> m = pr.getModel();
        ModelPart[] hidden = { m.head, m.hat, m.leftArm, m.rightArm, m.leftSleeve, m.rightSleeve };
        float[][] saved = new float[hidden.length][3];
        for (int i = 0; i < hidden.length; i++) {
            saved[i][0] = hidden[i].xScale;
            saved[i][1] = hidden[i].yScale;
            saved[i][2] = hidden[i].zScale;
            hidden[i].xScale = EPS;
            hidden[i].yScale = EPS;
            hidden[i].zScale = EPS;
        }

        rendering = true;
        try {
            PoseStack ps = e.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            d.render(p, x - c.x + ox, y - c.y, z - c.z + oz,
                    Mth.lerp(pt, p.yRotO, p.getYRot()), pt, ps, buffers, d.getPackedLightCoords(p, pt));
            buffers.endBatch();
        } finally {
            rendering = false;
            for (int i = 0; i < hidden.length; i++) {
                hidden[i].xScale = saved[i][0];
                hidden[i].yScale = saved[i][1];
                hidden[i].zScale = saved[i][2];
            }
        }
    }
}
