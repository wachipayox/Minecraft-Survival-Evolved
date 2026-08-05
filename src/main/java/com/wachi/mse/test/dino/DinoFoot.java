package com.wachi.mse.test.dino;

import com.wachi.mse.test.collide.Capsule;
import com.wachi.mse.test.collide.IGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class DinoFoot<T extends DinoEntity> {

    public record DFootPose(
            float ankleXRot,
            float extensionXRot
    ){

    }

    public record DFootGeometry(
            Vec3 ankle,
            Vec3 extensionEnd,
            Vec3 footEnd
    ) implements IGeometry {

        @Override
        public List<Capsule> getCollisions(double thickness) {
            return List.of();
        }
    }

    protected Vec3 anklePos;

    protected final double extensionLength, extensionThickness, footFrontLength, footBackLength, footHeight;

    private final DinoLeg<T> parent;

    public DFootPose actualPose;

    public DinoFoot(
            Vec3 anklePos,
            double extensionLength,
            double extensionThickness,
            double footFrontLength,
            double footBackLength,
            double footHeight,
            DinoLeg<T> parent
    ) {
        this.anklePos = anklePos;

        this.extensionLength = extensionLength;
        this.extensionThickness = extensionThickness;
        this.footFrontLength = footFrontLength;
        this.footBackLength = footBackLength;
        this.footHeight = footHeight;

        this.parent = parent;

        actualPose = new DFootPose(0, 0);
    }

    public Vec3 getAnklePos(float partialTicks) {
        var ankleOffset = anklePos.toVector3f()
                .rotateY(parent.getYRot(partialTicks));

        return parent.calculateActualGeometry(partialTicks).end()
                .add(ankleOffset.x(), ankleOffset.y(), ankleOffset.z()
                );
    }

    public DFootGeometry calculateActualGeometry(float partialTicks) {
        return calculateGeometry(actualPose, partialTicks);
    }

    public DFootGeometry calculateGeometry(DFootPose pose, float partialTicks) {
        var anklePos = getAnklePos(partialTicks);

        var extensionOffset = new Vector3f(
                0, (float) -extensionLength, 0
        )
                .rotateX(pose.ankleXRot)
                .rotateY(parent.getYRot(partialTicks)
                );

        Vec3 extension = anklePos.add(
                extensionOffset.x(), extensionOffset.y(), extensionOffset.z()
        );

        var footOffset = new Vector3f(
                0, (float) -(footHeight / 2), 0
        )
                .rotateX(pose.ankleXRot + pose.extensionXRot)
                .rotateY(parent.getYRot(partialTicks)
                );

        Vec3 foot = extension.add(
                footOffset.x(),
                footOffset.y(),
                footOffset.z()
        );

        return new DFootGeometry(
                anklePos,
                extension,
                foot //TODO ver si funciona
        );
    }

    private DinoLegPair<T> getParentHips(){
        return parent.parent;
    }

    private T getDinoParent(){
        return parent.getDinoParent();
    }

}
