package com.wachi.mse.entity.dinosaur.config;

import java.util.List;

public record DinosaurProceduralConfig(
        DinosaurBoneNames bones,
        SupportProbe frontLeft,
        SupportProbe frontRight,
        SupportProbe backLeft,
        SupportProbe backRight,
        double sampleAbove,
        double sampleBelow,
        float maxPitchRadians,
        float maxRollRadians,
        float slopeDeadzoneRadians,
        float smoothingResponsePerSecond) {
    public static final DinosaurProceduralConfig PROTOTYPE = new DinosaurProceduralConfig(
            new DinosaurBoneNames(
                    "body",
                    "foot_front_left",
                    "foot_front_right",
                    "foot_back_left",
                    "foot_back_right"),
            new SupportProbe(SupportPoint.FRONT_LEFT, 4.0 / 16.0, -9.0 / 16.0),
            new SupportProbe(SupportPoint.FRONT_RIGHT, -4.0 / 16.0, -9.0 / 16.0),
            new SupportProbe(SupportPoint.BACK_LEFT, 4.0 / 16.0, 9.0 / 16.0),
            new SupportProbe(SupportPoint.BACK_RIGHT, -4.0 / 16.0, 9.0 / 16.0),
            1.25,
            1.5,
            (float) Math.toRadians(18.0),
            (float) Math.toRadians(15.0),
            (float) Math.toRadians(0.5),
            9.0F);

    public DinosaurProceduralConfig {
        if (sampleAbove < 0.0 || sampleBelow < 0.0) {
            throw new IllegalArgumentException("Terrain sample distances must be non-negative");
        }
        if (maxPitchRadians < 0.0F || maxRollRadians < 0.0F) {
            throw new IllegalArgumentException("Procedural angle limits must be non-negative");
        }
        if (slopeDeadzoneRadians < 0.0F || smoothingResponsePerSecond <= 0.0F) {
            throw new IllegalArgumentException("Procedural smoothing values are invalid");
        }
    }

    public List<SupportProbe> supportProbes() {
        return List.of(this.frontLeft, this.frontRight, this.backLeft, this.backRight);
    }

    public enum SupportPoint {
        FRONT_LEFT("FL"),
        FRONT_RIGHT("FR"),
        BACK_LEFT("BL"),
        BACK_RIGHT("BR");

        private final String shortName;

        SupportPoint(String shortName) {
            this.shortName = shortName;
        }

        public String shortName() {
            return this.shortName;
        }
    }

    public record SupportProbe(SupportPoint point, double modelXOffset, double modelZOffset) {
    }
}
