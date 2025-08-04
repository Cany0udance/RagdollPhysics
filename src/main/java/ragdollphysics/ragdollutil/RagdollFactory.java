package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.beyond.TimeEater;
import com.megacrit.cardcrawl.monsters.exordium.*;

/**
 * Factory for creating ragdoll physics instances.
 * Handles both skeleton-based and image-based monster ragdolls.
 */
public class RagdollFactory {
    private static boolean printFactoryLogs = false; // Set to true to enable factory operation logs
    private final String factoryId;
    private int ragdollsCreated = 0;

    // Physics constants
    private static final float MIN_FORCE_X = 600f;
    private static final float MAX_FORCE_X = 900f;
    private static final float MIN_FORCE_Y = 1000f;
    private static final float MAX_FORCE_Y = 1500f;
    private static final float DEFAULT_DEATH_TIMER = 2.5f;

    public RagdollFactory() {
        this.factoryId = "Factory_" + System.currentTimeMillis() % 10000;
        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] RagdollFactory initialized");
        }
    }

    /**
     * Calculate custom ground level for specific monsters
     */
    private float calculateGroundLevel(AbstractMonster monster) {
        float baseGroundLevel = monster.drawY;

        // Apply monster-specific ground level adjustments
        switch (monster.id) {
            case TheGuardian.ID:
                return baseGroundLevel + (100f * Settings.scale); // Raise Guardian's ground by 50 units
            case Cultist.ID:
                return baseGroundLevel + (20f * Settings.scale); // Raise Guardian's ground by 50 units
            default:
                return baseGroundLevel;
        }
    }

    /**
     * Main factory method - creates appropriate ragdoll type based on monster
     *
     * @param monster The monster to create a ragdoll for
     * @param reflectionHelper Helper for accessing monster internals
     * @return Created ragdoll instance
     * @throws Exception if ragdoll creation fails
     */
    public MultiBodyRagdoll createRagdoll(AbstractMonster monster, ReflectionHelper reflectionHelper) throws Exception {
        ragdollsCreated++;
        String monsterName = monster.id;

        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] === RAGDOLL CREATION #" + ragdollsCreated + " ===");
            BaseMod.logger.info("[" + factoryId + "] Creating ragdoll for " + monsterName
                    + " at position (" + monster.drawX + ", " + monster.drawY + ")");
        }

        try {
            MultiBodyRagdoll ragdoll;
            // Determine creation path based on monster type
            if (isImageBasedMonster(monster, reflectionHelper)) {
                ragdoll = createImageRagdoll(monster);
            } else {
                ragdoll = createSkeletonRagdoll(monster, reflectionHelper);
            }

            // Apply initial physics force
            applyInitialForce(ragdoll);

            // Set monster death timer
            monster.deathTimer = DEFAULT_DEATH_TIMER;

            if (printFactoryLogs) {
                BaseMod.logger.info("[" + factoryId + "] Ragdoll creation successful for " + monsterName);
            }
            return ragdoll;
        } catch (Exception e) {
            if (printFactoryLogs) {
                BaseMod.logger.error("[" + factoryId + "] Failed to create ragdoll for " + monsterName
                        + ": " + e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Create ragdoll for image-based monsters (like Hexaghost) - UPDATED
     */
    private MultiBodyRagdoll createImageRagdoll(AbstractMonster monster) throws Exception {
        String monsterName = monster.id;
        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] Creating image ragdoll for " + monsterName);
        }

        try {
            float customGroundLevel = calculateGroundLevel(monster);

            if (printFactoryLogs && customGroundLevel != monster.drawY) {
                BaseMod.logger.info("[" + factoryId + "] Applied custom ground level for " + monsterName
                        + ": " + monster.drawY + " -> " + customGroundLevel
                        + " (offset: " + (customGroundLevel - monster.drawY) + ")");
            }

            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    monster.drawX,
                    monster.drawY,
                    customGroundLevel,  // Use calculated ground level
                    monsterName,
                    monster
            );

            if (printFactoryLogs) {
                BaseMod.logger.info("[" + factoryId + "] Image ragdoll created successfully");
            }
            return ragdoll;
        } catch (Exception e) {
            throw new Exception("Failed to create image ragdoll: " + e.getMessage(), e);
        }
    }

    /**
     * Create ragdoll for skeleton-based monsters (normal case) - UPDATED
     */
    private MultiBodyRagdoll createSkeletonRagdoll(AbstractMonster monster, ReflectionHelper reflectionHelper) throws Exception {
        String monsterName = monster.id;
        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] Creating skeleton ragdoll for " + monsterName);
        }

        try {
            // Get skeleton components
            Skeleton skeleton = reflectionHelper.getSkeleton(monster);
            SkeletonRenderer sr = reflectionHelper.getSkeletonRenderer(monster);

            if (skeleton == null) {
                throw new Exception("Skeleton is null");
            }
            if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
                throw new Exception("Skeleton has no bones");
            }
            if (sr == null) {
                throw new Exception("SkeletonRenderer is null");
            }

            float customGroundLevel = calculateGroundLevel(monster);

            if (printFactoryLogs && customGroundLevel != monster.drawY) {
                BaseMod.logger.info("[" + factoryId + "] Applied custom ground level for " + monsterName
                        + ": " + monster.drawY + " -> " + customGroundLevel
                        + " (offset: " + (customGroundLevel - monster.drawY) + ")");
            }

            // Create the ragdoll with skeleton
            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    skeleton,
                    customGroundLevel,   // Use calculated ground level
                    monster.drawX,       // start X
                    monster.drawY,       // start Y
                    monsterName,
                    monster
            );

            // Initialize bone wobbles with hierarchy awareness
            initializeHierarchicalBoneWobbles(ragdoll, skeleton, monster);

            if (printFactoryLogs) {
                BaseMod.logger.info("[" + factoryId + "] Skeleton ragdoll created with "
                        + skeleton.getBones().size + " bones");
            }
            return ragdoll;
        } catch (Exception e) {
            throw new Exception("Failed to create skeleton ragdoll: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize bone wobbles with hierarchy-aware physics
     */
    private void initializeHierarchicalBoneWobbles(MultiBodyRagdoll ragdoll, Skeleton skeleton, AbstractMonster monster) {
        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] === HIERARCHY-AWARE BONE WOBBLE INITIALIZATION ===");
        }

        int visualLimbs = 0;
        int visualBones = 0;
        int controlBones = 0;
        int attachmentBones = 0;
        String monsterName = monster.id;

        for (Bone bone : skeleton.getBones()) {
            // Create bone wobble with hierarchy information
            BoneWobble wobble = new BoneWobble(bone.getRotation(), bone);

            // Apply depth-based reduction (more reasonable than original)
            float depthReduction = Math.min(wobble.chainDepth * 0.08f, 0.4f);
            wobble.angularVelocity = MathUtils.random(-360f, 360f) * (1.0f - depthReduction);

            // Check if bone has visual attachment
            boolean hasVisualAttachment = hasVisualAttachment(bone);

            // Categorize bones
            String boneName = bone.getData().getName().toLowerCase();

            // Check if this bone will become a detached attachment
            boolean willBeDetached = willBoneBeDetached(bone, skeleton, monsterName);
            boolean isVisualLimb = hasVisualAttachment
                    && boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");

            // Apply appropriate physics enhancement based on bone type
            if (willBeDetached) {
                // Bones with detached attachments get moderate enhancement
                wobble.angularVelocity *= MathUtils.random(1.2f, 1.8f);
                attachmentBones++;
                if (printFactoryLogs) {
                    BaseMod.logger.info("[" + factoryId + "] Enhanced attachment bone: "
                            + bone.getData().getName() + " with angVel: "
                            + String.format("%.1f", wobble.angularVelocity));
                }
            } else if (isVisualLimb) {
                // Visual limbs get enhanced motion with depth constraints
                wobble.angularVelocity *= MathUtils.random(1.8f, 2.5f) * (1.0f - depthReduction * 0.3f);
                visualLimbs++;
                if (printFactoryLogs) {
                    BaseMod.logger.info("[" + factoryId + "] Enhanced visual limb: "
                            + bone.getData().getName() + " with angVel: "
                            + String.format("%.1f", wobble.angularVelocity)
                            + ", depth: " + wobble.chainDepth);
                }
            } else if (hasVisualAttachment) {
                // Other visual bones get slight enhancement
                wobble.angularVelocity *= MathUtils.random(0.8f, 1.3f) * (1.0f - depthReduction * 0.2f);
                visualBones++;
            } else {
                // Animation control bones get reduced motion
                wobble.angularVelocity *= MathUtils.random(0.2f, 0.5f) * (1.0f - depthReduction * 0.5f);
                controlBones++;
            }

            // Apply minimal random initial rotation offset
            wobble.rotation += MathUtils.random(-2f, 2f);

            // Store the wobble in the ragdoll
            ragdoll.boneWobbles.put(bone, wobble);
        }

        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] Bone initialization complete - Visual Limbs: "
                    + visualLimbs + ", Visual Parts: " + visualBones + ", Control Bones: "
                    + controlBones + ", Attachment Bones: " + attachmentBones
                    + ", Total: " + ragdoll.boneWobbles.size());
        }
    }

    /**
     * Check if a bone has a visual attachment
     */
    private boolean hasVisualAttachment(Bone bone) {
        try {
            for (Slot slot : bone.getSkeleton().getSlots()) {
                if (slot.getBone() == bone && slot.getAttachment() != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true; // Default to visual if we can't determine
        }
        return false;
    }

    /**
     * Check if a bone will have its attachment detached for physics
     */
    private boolean willBoneBeDetached(Bone bone, Skeleton skeleton, String monsterName) {
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getBone() == bone && slot.getAttachment() instanceof RegionAttachment) {
                RegionAttachment regionAttachment = (RegionAttachment) slot.getAttachment();
                String attachmentName = regionAttachment.getName();
                if (AttachmentConfig.shouldDetachAttachment(monsterName, attachmentName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Apply initial physics force to the ragdoll
     */
    private void applyInitialForce(MultiBodyRagdoll ragdoll) {
        float forceX = MathUtils.random(MIN_FORCE_X, MAX_FORCE_X);
        float forceY = MathUtils.random(MIN_FORCE_Y, MAX_FORCE_Y);

        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] Applying initial force: (" + forceX + ", " + forceY + ")");
        }

        ragdoll.applyGlobalForce(forceX, forceY);

        // Initialize rotation tracking to prevent phantom rotation on first frame
        ragdoll.lastRotation = ragdoll.mainBody.rotation;
        ragdoll.totalRotationDegrees = 0f;
    }

    /**
     * Check if monster is image-based (has image but no skeleton)
     */
    private boolean isImageBasedMonster(AbstractMonster monster, ReflectionHelper reflectionHelper) {
        try {
            return reflectionHelper.getImage(monster) != null
                    && reflectionHelper.getSkeleton(monster) == null;
        } catch (Exception e) {
            BaseMod.logger.info("[" + factoryId + "] Could not determine monster type for "
                    + monster.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Create ragdoll with custom parameters (for testing/debugging)
     */
    public MultiBodyRagdoll createCustomRagdoll(AbstractMonster monster, ReflectionHelper reflectionHelper,
                                                float forceX, float forceY, float deathTimer) throws Exception {
        MultiBodyRagdoll ragdoll = createRagdoll(monster, reflectionHelper);

        // Override default force
        ragdoll.applyGlobalForce(forceX, forceY);

        // Override death timer
        monster.deathTimer = deathTimer;

        if (printFactoryLogs) {
            BaseMod.logger.info("[" + factoryId + "] Created custom ragdoll with force("
                    + forceX + ", " + forceY + "), timer=" + deathTimer);
        }

        return ragdoll;
    }

    /**
     * Get factory statistics
     */
    public FactoryStats getStats() {
        return new FactoryStats(ragdollsCreated);
    }

    /**
     * Statistics about factory operations
     */
    public static class FactoryStats {
        public final int ragdollsCreated;

        public FactoryStats(int ragdollsCreated) {
            this.ragdollsCreated = ragdollsCreated;
        }

        @Override
        public String toString() {
            return String.format("FactoryStats{ragdollsCreated=%d}", ragdollsCreated);
        }
    }
}