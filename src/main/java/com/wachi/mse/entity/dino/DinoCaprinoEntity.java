package com.wachi.mse.entity.dino;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public class DinoCaprinoEntity extends PathfinderMob {

    DinoCaprinoPart head1, head2;
    DinoCaprinoPart[] caprinoParts;

    public DinoCaprinoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setupParts();
    }

    public void setupParts(){
        caprinoParts = new DinoCaprinoPart[]{

                //torso1
                new DinoCaprinoPart(this,
                        aabb -> {
                            var lkAngle = getLookAngle().normalize();

                            return aabb.getCenter()
                                    .add(0, -1, 0)
                                    .add(lkAngle.multiply(-1, -1, -1));
                        }, 2, 2.5f),

                //torso2
                new DinoCaprinoPart(this,
                        aabb -> {
                            var lkAngle = getLookAngle().normalize();

                            return aabb.getCenter()
                                    .add(0, -1, 0)
                                    .add(lkAngle.multiply(1, 1, 1));
                        }, 1, 2.5f),

                //head
                new DinoCaprinoPart(this,
                        aabb -> {
                            var lkAngle = getLookAngle().normalize();

                            return aabb.getCenter()
                                    .add(lkAngle.multiply(3, 3, 3));
                        }, 1, 1)
        };
    }

    public void tickParts(){
        for (DinoCaprinoPart caprinoPart : caprinoParts) {
            caprinoPart.updatePos();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        updatePartIds(id);
    }

    private void updatePartIds(int id){
        if(caprinoParts == null) return;

        for (int i = 0; i < this.caprinoParts.length; i++)
            this.caprinoParts[i].setId(id + i + 1);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);

        updatePartIds(packet.getId());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if(isDeadOrDying()) return;
        tickParts();
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public DinoCaprinoPart @NonNull [] getParts() {
        return caprinoParts;
    }
}
