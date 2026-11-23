package com.example.realisticview;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * 人型モデル(プレイヤー・ゾンビ・スケルトンなど)の歩行アニメを自然にする補正。
 *  - 肩のひねり(腕と逆位相で胴体が回る。腕は肩の回転に追従)
 *  - 歩幅ごとの上下動と頭の小さなうなずき
 *  - 前に振り出す足を持ち上げる(足上げ)
 *  - 腕の動きが足より少し遅れる(フォロースルー)
 *  - 速度に応じた軽い前傾
 */
public final class Locomotion {
    private Locomotion() {}

    public static void apply(HumanoidModel<?> m, LivingEntity e, float limbSwing, float limbSwingAmount) {
        if (!Config.SMOOTH_ANIMATION.get()) return;
        if (m.riding || e.isSleeping() || e.isFallFlying() || e.isVisuallySwimming()) return;
        Pose pose = e.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return;

        float amt = Mth.clamp(limbSwingAmount, 0f, 1f) * Config.ANIMATION_STRENGTH.get().floatValue();
        if (amt < 0.01f) return;

        float s = limbSwing * 0.6662f;
        float cs = Mth.cos(s);
        float ss = Mth.sin(s);
        float c2 = Mth.cos(2f * s);

        // 肩のひねり(右腕が前に出る時、右肩も前へ)
        float twist = -cs * 0.12f * amt;
        float zShift = 5f * Mth.sin(twist);
        m.body.yRot += twist;
        m.rightArm.yRot += twist;
        m.leftArm.yRot += twist;
        m.rightArm.z += zShift;
        m.leftArm.z -= zShift;

        // 前傾
        m.body.xRot += 0.05f * amt;

        // 歩幅ごとの上下動
        float dy = 0.55f * c2 * amt;
        m.head.y += dy;
        m.body.y += dy;
        m.rightArm.y += dy;
        m.leftArm.y += dy;
        m.head.xRot += 0.03f * c2 * amt;

        // 足上げ(前に振り出す足だけ持ち上げる)
        m.rightLeg.y -= Math.max(0f, -m.rightLeg.xRot) * 0.9f;
        m.leftLeg.y -= Math.max(0f, -m.leftLeg.xRot) * 0.9f;

        // 腕のフォロースルー(位相の遅れ)
        boolean freeArms = m.attackTime <= 0f
                && (m.rightArmPose == HumanoidModel.ArmPose.EMPTY || m.rightArmPose == HumanoidModel.ArmPose.ITEM)
                && (m.leftArmPose == HumanoidModel.ArmPose.EMPTY || m.leftArmPose == HumanoidModel.ArmPose.ITEM);
        if (freeArms) {
            m.rightArm.xRot -= ss * 0.18f * amt;
            m.leftArm.xRot += ss * 0.18f * amt;
        }

        // 帽子レイヤーは setupAnim 内で先に頭からコピー済みなので再コピー
        m.hat.copyFrom(m.head);
    }
}
