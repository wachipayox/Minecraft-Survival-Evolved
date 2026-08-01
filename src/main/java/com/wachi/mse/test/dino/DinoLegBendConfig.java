package com.wachi.mse.test.dino;

import net.minecraft.util.Mth;

public class DinoLegBendConfig {

    float bendDirection = 1.0F;

    float chillBend = 35.0F * Mth.DEG_TO_RAD;
    float hangingBend = 140.0F * Mth.DEG_TO_RAD;

    float maxBend = 150.0F * Mth.DEG_TO_RAD;
    float minBend = 5.0F * Mth.DEG_TO_RAD;

    public DinoLegBendConfig bendDirection(float bendDirection) {
        this.bendDirection = Math.signum(bendDirection);
        return this;
    }

    public DinoLegBendConfig bendValues(Float minBend, Float maxBend, Float hangingBend, Float chillBend) {
        if(minBend != null) this.minBend = minBend;
        if(maxBend != null) this.maxBend = maxBend;
        if(hangingBend != null) this.hangingBend = hangingBend;
        if(chillBend != null) this.chillBend = chillBend;

        this.minBend = (float) Math.clamp(this.minBend, 0.0, this.maxBend);
        this.maxBend = Math.max(this.maxBend, this.minBend);
        this.hangingBend = Math.clamp(this.hangingBend, this.minBend, this.maxBend);
        this.chillBend = Math.clamp(this.chillBend, this.minBend, this.maxBend);

        return this;
    }

    public double getMinBend() {
        return minBend;
    }

    public double getMaxBend() {
        return maxBend;
    }

    public double getChillBend() {
        return chillBend;
    }

    public float getBendDirection() {
        return bendDirection;
    }

}
