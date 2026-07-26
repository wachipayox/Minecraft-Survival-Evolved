package com.wachi.mse.entity.dinosaur;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;

/**
 * Implemented by every dinosaur species that uses terrain-aware procedural
 * posing. Each species owns its geometry, gait and reach configuration.
 */
public interface ProceduralDinosaur {
    DinosaurProceduralConfig proceduralConfig();
}
