package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Treats precise dinosaur bone boxes as moving platforms without turning
 * supported entities into passengers.
 */
public final class DinosaurTransportController {
    private static final double HORIZONTAL_TOLERANCE = 0.05;
    private static final double BELOW_FEET_TOLERANCE = 0.10;
    private static final double ABOVE_FEET_TOLERANCE = 0.08;
    private static final double SEARCH_ABOVE = 0.35;
    private static final double MAX_UPWARD_DETACH_SPEED = 0.10;
    private static final double MINIMUM_MOVEMENT_SQUARED = 1.0E-10;
    private static final double TRACKED_CONTACT_EXTRA_TOLERANCE = 0.12;
    private static final double BLOCK_SUPPORT_PROBE_DEPTH = 0.08;
    private static final double BLOCK_SUPPORT_INSET = 1.0E-4;
    private static final int SUPPORT_GRACE_TICKS = 20;
    private static final Map<Entity, DinosaurEntity> ATTACHMENT_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final DinosaurEntity parent;
    private final Map<Integer, Integer> supportedUntilTick =
            new HashMap<>();
    private final Map<Entity, TransportTrack> tracks =
            new IdentityHashMap<>();
    private final Set<Entity> activelyTransporting =
            java.util.Collections.newSetFromMap(
                    new IdentityHashMap<>());

    public DinosaurTransportController(DinosaurEntity parent) {
        this.parent = parent;
    }

    public List<SupportContact> findContacts(
            DinosaurPartEntity[] parts,
            DinosaurPoseTransforms.PoseSnapshot appliedPose,
            AABB appliedVisualBounds) {
        if (!this.parent.canBeWalkedOn()) {
            this.clearAttachments();
            return List.of();
        }
        AABB searchArea = appliedVisualBounds.inflate(
                HORIZONTAL_TOLERANCE,
                SEARCH_ABOVE,
                HORIZONTAL_TOLERANCE);
        Map<Entity, SupportContact> bestByEntity =
                new IdentityHashMap<>();
        Map<Entity, SupportContact> trackedByEntity =
                new IdentityHashMap<>();
        for (Entity entity : this.parent.level().getEntities(
                this.parent,
                searchArea,
                this::canTransport)) {
            if (entity.getDeltaMovement().y
                    > MAX_UPWARD_DETACH_SPEED) {
                continue;
            }
            AABB entityBounds = entity.getBoundingBox();
            AABB feet = new AABB(
                    entityBounds.minX,
                    entityBounds.minY - BELOW_FEET_TOLERANCE,
                    entityBounds.minZ,
                    entityBounds.maxX,
                    entityBounds.minY + ABOVE_FEET_TOLERANCE,
                    entityBounds.maxZ);
            for (DinosaurPartEntity part : parts) {
                if (!part.canBeCollidedWith(entity)) {
                    continue;
                }
                TransportTrack track = this.tracks.get(entity);
                boolean trackedPart = track != null
                        && track.partIndex() == part.partIndex();
                double extraTolerance = trackedPart
                                && !track.preserveNaturalMotion()
                        ? TRACKED_CONTACT_EXTRA_TOLERANCE
                        : 0.0;
                AABB broadContact = extraTolerance > 0.0
                        ? feet.inflate(
                                0.0,
                                extraTolerance,
                                0.0)
                        : feet;
                if (!broadContact.intersects(
                        part.getBoundingBox())) {
                    continue;
                }
                Vec3 anchor =
                        DinosaurPoseTransforms.hitboxBoxSupportPoint(
                                appliedPose,
                                part.boxConfig(),
                                entityBounds,
                                BELOW_FEET_TOLERANCE
                                        + extraTolerance,
                                ABOVE_FEET_TOLERANCE
                                        + extraTolerance);
                if (anchor == null) {
                    continue;
                }
                double verticalDistance = Math.abs(
                        entityBounds.minY - anchor.y);
                SupportContact candidate = new SupportContact(
                        entity,
                        part,
                        anchor,
                        verticalDistance,
                        true,
                        false);
                if (trackedPart) {
                    trackedByEntity.put(entity, candidate);
                    continue;
                }
                SupportContact current =
                        bestByEntity.get(entity);
                if (current == null
                        || verticalDistance
                                < current.verticalDistance()) {
                    bestByEntity.put(entity, candidate);
                }
            }
        }
        List<SupportContact> detectedContacts = new ArrayList<>();
        for (Entity entity : bestByEntity.keySet()) {
            detectedContacts.add(trackedByEntity.getOrDefault(
                    entity,
                    bestByEntity.get(entity)));
        }
        for (Map.Entry<Entity, SupportContact> entry
                : trackedByEntity.entrySet()) {
            if (!bestByEntity.containsKey(entry.getKey())) {
                detectedContacts.add(entry.getValue());
            }
        }

        List<SupportContact> contacts = new ArrayList<>();
        Set<Entity> detectedEntities =
                Collections.newSetFromMap(new IdentityHashMap<>());
        int expiration =
                this.parent.tickCount + SUPPORT_GRACE_TICKS;
        for (SupportContact contact : detectedContacts) {
            Entity entity = contact.entity();
            if (this.hasBlockSupport(entity)) {
                this.removeTrack(entity);
                continue;
            }
            this.claimAttachment(entity);
            detectedEntities.add(entity);
            contacts.add(contact);
            this.supportedUntilTick.put(
                    entity.getId(),
                    expiration);
            this.tracks.put(
                    entity,
                    new TransportTrack(
                            contact.part().partIndex(),
                            expiration,
                            contact.anchor(),
                            false));
        }

        /*
         * A moving/tilting bone can lift an occupant just beyond the narrow
         * contact tolerance for a frame. Continue following the last actual
         * anatomical part for a short, non-renewing window instead of
         * dropping the platform transform immediately.
         */
        for (Map.Entry<Entity, TransportTrack> entry
                : new ArrayList<>(this.tracks.entrySet())) {
            Entity entity = entry.getKey();
            TransportTrack track = entry.getValue();
            if (detectedEntities.contains(entity)) {
                continue;
            }
            if (!this.canTransport(entity)
                    || track.expiresAtTick() < this.parent.tickCount
                    || this.isAttachedToAnotherDinosaur(entity)
                    || this.hasBlockSupport(entity)) {
                this.removeTrack(entity);
                continue;
            }
            DinosaurPartEntity part = findPart(
                    parts,
                    track.partIndex());
            if (part == null) {
                this.removeTrack(entity);
                continue;
            }
            Vec3 surfaceAnchor =
                    DinosaurPoseTransforms.hitboxBoxTopSurfacePoint(
                            appliedPose,
                            part.boxConfig(),
                            entity.getBoundingBox());
            boolean remainsAboveTrackedPart = surfaceAnchor != null;
            Vec3 anchor = remainsAboveTrackedPart
                    ? surfaceAnchor
                    : track.anchor();
            boolean preserveNaturalMotion =
                    track.preserveNaturalMotion()
                            /*
                             * A precise client veto is authoritative only in
                             * the negative direction: it cannot create
                             * support, but it must be able to turn a tracked
                             * phantom AABB corner back into free movement.
                             */
                            || part.isSupportCollisionVetoed(entity)
                            || entity.getDeltaMovement().y
                                    > MAX_UPWARD_DETACH_SPEED
                            /*
                             * Walking beyond the oriented footprint is not a
                             * temporary vertical gap. Keep applying the
                             * reference-frame delta during the grace window,
                             * but release gravity immediately.
                             */
                            || !remainsAboveTrackedPart;
            contacts.add(new SupportContact(
                    entity,
                    part,
                    anchor,
                    Math.abs(entity.getBoundingBox().minY - anchor.y),
                    false,
                    preserveNaturalMotion));
            this.supportedUntilTick.put(
                    entity.getId(),
                    track.expiresAtTick());
            this.tracks.put(
                    entity,
                    new TransportTrack(
                            track.partIndex(),
                            track.expiresAtTick(),
                            anchor,
                            preserveNaturalMotion));
        }
        this.supportedUntilTick.values().removeIf(
                tick -> tick < this.parent.tickCount);
        return contacts;
    }

    public void transport(
            List<SupportContact> contacts,
            DinosaurPoseTransforms.PoseSnapshot previousPose,
            DinosaurPoseTransforms.PoseSnapshot currentPose) {
        for (SupportContact contact : contacts) {
            Entity entity = contact.entity();
            if (!this.shouldMoveLocally(entity)
                    || entity.isRemoved()
                    || contact.actualContact()
                            && !contact.part().canBeCollidedWith(entity)) {
                continue;
            }
            Vec3 destination =
                    DinosaurPoseTransforms.transportPoint(
                            previousPose,
                            currentPose,
                            contact.part().boxConfig(),
                            contact.anchor());
            Vec3 movement = destination.subtract(contact.anchor());
            if (movement.lengthSqr() > MINIMUM_MOVEMENT_SQUARED) {
                Vec3 naturalVelocity = entity.getDeltaMovement();
                double naturalFallDistance = entity.fallDistance;
                this.activelyTransporting.add(entity);
                try {
                    /*
                     * The part boxes already represent currentPose. Suppress
                     * those same boxes only while applying their platform
                     * displacement, otherwise an animated upward surface
                     * collides with the occupant it is carrying.
                     */
                    entity.move(MoverType.SHULKER, movement);
                } finally {
                    this.activelyTransporting.remove(entity);
                    if (contact.preserveNaturalMotion()) {
                        /*
                         * A reference-frame displacement must not become
                         * acceleration, braking or anti-gravity. Preserve the
                         * entity's own motion state while retaining only the
                         * positional delta contributed by the dinosaur.
                         */
                        entity.setDeltaMovement(naturalVelocity);
                        entity.fallDistance = naturalFallDistance;
                    }
                }
            }
            this.updateTrackAnchor(
                    entity,
                    contact.part().partIndex(),
                    destination);
            if (!contact.preserveNaturalMotion()) {
                /*
                 * A stable contact may briefly lose its exact intersection
                 * because the supporting bone tilted between ticks. Keep
                 * that case grounded. A jump latches preserveNaturalMotion
                 * and never enters this branch until strict contact resumes.
                 */
                entity.setOnGround(true);
                entity.resetFallDistance();
                Vec3 velocity = entity.getDeltaMovement();
                if (velocity.y < 0.0) {
                    entity.setDeltaMovement(
                            velocity.x,
                            0.0,
                            velocity.z);
                }
            }
        }
    }

    public boolean isSupported(Entity entity) {
        return this.supportedUntilTick.getOrDefault(
                        entity.getId(),
                        Integer.MIN_VALUE)
                >= this.parent.tickCount;
    }

    public boolean isActivelyTransporting(Entity entity) {
        return this.activelyTransporting.contains(entity);
    }

    private boolean canTransport(Entity entity) {
        return !entity.isRemoved()
                && entity != this.parent
                && !entity.isSpectator()
                && !this.parent.hasPassenger(entity)
                && !(entity instanceof DinosaurPartEntity part
                        && part.getParent() == this.parent);
    }

    private boolean hasBlockSupport(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        double minX = bounds.minX + BLOCK_SUPPORT_INSET;
        double maxX = bounds.maxX - BLOCK_SUPPORT_INSET;
        double minZ = bounds.minZ + BLOCK_SUPPORT_INSET;
        double maxZ = bounds.maxZ - BLOCK_SUPPORT_INSET;
        if (minX >= maxX || minZ >= maxZ) {
            return false;
        }
        AABB probe = new AABB(
                minX,
                bounds.minY - BLOCK_SUPPORT_PROBE_DEPTH,
                minZ,
                maxX,
                bounds.minY - 1.0E-6,
                maxZ);
        return entity.level()
                .findSupportingBlock(entity, probe)
                .isPresent();
    }

    private void claimAttachment(Entity entity) {
        ATTACHMENT_OWNERS.put(entity, this.parent);
    }

    private boolean isAttachedToAnotherDinosaur(Entity entity) {
        DinosaurEntity owner = ATTACHMENT_OWNERS.get(entity);
        return owner != null && owner != this.parent;
    }

    private void updateTrackAnchor(
            Entity entity,
            int partIndex,
            Vec3 anchor) {
        TransportTrack track = this.tracks.get(entity);
        if (track == null) {
            return;
        }
        this.tracks.put(
                entity,
                new TransportTrack(
                        partIndex,
                        track.expiresAtTick(),
                        anchor,
                        track.preserveNaturalMotion()));
    }

    private void removeTrack(Entity entity) {
        this.tracks.remove(entity);
        this.supportedUntilTick.remove(entity.getId());
        synchronized (ATTACHMENT_OWNERS) {
            if (ATTACHMENT_OWNERS.get(entity) == this.parent) {
                ATTACHMENT_OWNERS.remove(entity);
            }
        }
    }

    private void clearAttachments() {
        for (Entity entity : new ArrayList<>(this.tracks.keySet())) {
            this.removeTrack(entity);
        }
    }

    private static DinosaurPartEntity findPart(
            DinosaurPartEntity[] parts,
            int partIndex) {
        for (DinosaurPartEntity part : parts) {
            if (part.partIndex() == partIndex) {
                return part;
            }
        }
        return null;
    }

    private boolean shouldMoveLocally(Entity entity) {
        // The server transports every authoritative entity. On a client only
        // its local player is predicted; remote entities follow server
        // tracking and must not receive a second displacement.
        return !this.parent.level().isClientSide()
                || entity instanceof Player player
                        && player.isLocalPlayer();
    }

    public record SupportContact(
            Entity entity,
            DinosaurPartEntity part,
            Vec3 anchor,
            double verticalDistance,
            boolean actualContact,
            boolean preserveNaturalMotion) {
    }

    private record TransportTrack(
            int partIndex,
            int expiresAtTick,
            Vec3 anchor,
            boolean preserveNaturalMotion) {
    }
}
