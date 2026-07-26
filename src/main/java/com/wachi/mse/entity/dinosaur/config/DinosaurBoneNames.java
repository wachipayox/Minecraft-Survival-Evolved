package com.wachi.mse.entity.dinosaur.config;

public record DinosaurBoneNames(String body) {
    public DinosaurBoneNames {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Body bone name must not be blank");
        }
    }
}
