package com.wachi.mse.entity.dinosaur.procedural;

/**
 * Cervical rotation relative to the rendered body. Target values come from
 * vanilla-synchronized entity rotations; current values may be render-smoothed.
 */
public record DinosaurOrientationPose(
        float yawRadians,
        float pitchRadians,
        float targetYawRadians,
        float targetPitchRadians) {
    public DinosaurOrientationPose withSmoothedValues(float yaw, float pitch) {
        return new DinosaurOrientationPose(
                yaw,
                pitch,
                this.targetYawRadians,
                this.targetPitchRadians);
    }
}
