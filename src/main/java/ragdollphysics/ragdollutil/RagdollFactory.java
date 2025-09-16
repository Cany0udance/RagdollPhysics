package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.beyond.SnakeDagger;
import com.megacrit.cardcrawl.monsters.beyond.TimeEater;
import com.megacrit.cardcrawl.monsters.city.BronzeOrb;
import com.megacrit.cardcrawl.monsters.city.Byrd;
import com.megacrit.cardcrawl.monsters.exordium.*;
import ragdollphysics.RagdollPhysics;

/**
 * Factory for creating ragdoll physics instances.
 * Handles both skeleton-based and image-based monster ragdolls.
 */
public class RagdollFactory {

    // ================================
    // PHYSICS CONSTANTS
    // ================================

    private static final float MIN_FORCE_X = 600f;
    private static final float MAX_FORCE_X = 900f;
    private static final float MIN_FORCE_Y = 1000f;
    private static final float MAX_FORCE_Y = 1500f;
    private static final float DEFAULT_DEATH_TIMER = 2.5f;

    // Bone enhancement multipliers
    private static final float ATTACHMENT_BONE_MULTIPLIER_MIN = 1.2f;
    private static final float ATTACHMENT_BONE_MULTIPLIER_MAX = 1.8f;
    private static final float VISUAL_LIMB_MULTIPLIER_MIN = 1.8f;
    private static final float VISUAL_LIMB_MULTIPLIER_MAX = 2.5f;
    private static final float VISUAL_BONE_MULTIPLIER_MIN = 0.8f;
    private static final float VISUAL_BONE_MULTIPLIER_MAX = 1.3f;
    private static final float CONTROL_BONE_MULTIPLIER_MIN = 0.2f;
    private static final float CONTROL_BONE_MULTIPLIER_MAX = 0.5f;

    // Depth reduction factors
    private static final float CHAIN_DEPTH_REDUCTION_FACTOR = 0.08f;
    private static final float MAX_DEPTH_REDUCTION = 0.4f;
    private static final float VISUAL_LIMB_DEPTH_IMPACT = 0.3f;
    private static final float VISUAL_BONE_DEPTH_IMPACT = 0.2f;
    private static final float CONTROL_BONE_DEPTH_IMPACT = 0.5f;

    // ================================
    // INSTANCE STATE
    // ================================

    private final String factoryId;
    private int ragdollsCreated = 0;

    // ================================
    // CONSTRUCTOR
    // ================================

    public RagdollFactory() {
        this.factoryId = "Factory_" + System.currentTimeMillis() % 10000;
    }

    // ================================
    // MAIN FACTORY METHOD
    // ================================

    /**
     * Main factory method - creates appropriate ragdoll type based on monster
     */
    public MultiBodyRagdoll createRagdoll(AbstractMonster monster, ReflectionHelper reflectionHelper) throws Exception {
        ragdollsCreated++;

        try {
            MultiBodyRagdoll ragdoll;

            // Determine creation path based on monster type
            if (isImageBasedMonster(monster, reflectionHelper)) {
                // Check if image physics is enabled
                if (!ragdollphysics.RagdollPhysics.enableImageRagdolls) {
                    return null; // Skip creation when image ragdolls disabled
                }
                ragdoll = createImageRagdoll(monster);
            } else {
                ragdoll = createSkeletonRagdoll(monster, reflectionHelper);
            }

            // Apply initial physics and setup
            applyInitialForce(ragdoll);
            monster.deathTimer = DEFAULT_DEATH_TIMER;

            return ragdoll;

        } catch (Exception e) {
            throw new Exception("Failed to create ragdoll for " + monster.id + ": " + e.getMessage(), e);
        }
    }

    // ================================
    // PLAYER FACTORY METHOD
    // ================================
    /**
     * Main factory method for players - creates appropriate ragdoll type based on player
     */
    public MultiBodyRagdoll createPlayerRagdoll(AbstractPlayer player, ReflectionHelper reflectionHelper) throws Exception {
        ragdollsCreated++;

        try {
            MultiBodyRagdoll ragdoll;

            // Determine creation path based on player type
            if (isImageBasedPlayer(player, reflectionHelper)) {
                // Check if image physics is enabled
                if (!ragdollphysics.RagdollPhysics.enableImageRagdolls) {
                    return null; // Skip creation when image ragdolls disabled
                }
                ragdoll = createPlayerImageRagdoll(player);
            } else {
                ragdoll = createPlayerSkeletonRagdoll(player, reflectionHelper);
            }

            // Apply initial physics and setup
            applyInitialPlayerForce(ragdoll);

            return ragdoll;

        } catch (Exception e) {
            throw new Exception("Failed to create player ragdoll for " + player.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // ================================
    // RAGDOLL CREATION METHODS
    // ================================

    /**
     * Create ragdoll for image-based monsters (like Hexaghost)
     */
    private MultiBodyRagdoll createImageRagdoll(AbstractMonster monster) throws Exception {
        try {
            float customGroundLevel = calculateGroundLevel(monster);

            return new MultiBodyRagdoll(
                    monster.hb.cX,
                    monster.hb.cY,
                    customGroundLevel,
                    monster.id,
                    monster
            );

        } catch (Exception e) {
            throw new Exception("Failed to create image ragdoll: " + e.getMessage(), e);
        }
    }

    /**
     * Create ragdoll for skeleton-based monsters (normal case)
     */
    private MultiBodyRagdoll createSkeletonRagdoll(AbstractMonster monster, ReflectionHelper reflectionHelper) throws Exception {
        try {
            // Get skeleton components
            Skeleton skeleton = reflectionHelper.getSkeleton(monster);
            SkeletonRenderer sr = reflectionHelper.getSkeletonRenderer(monster);

            validateSkeletonComponents(skeleton, sr);

            float customGroundLevel = calculateGroundLevel(monster);

            // Create the ragdoll with skeleton
            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    skeleton,
                    customGroundLevel,
                    monster.drawX,
                    monster.drawY,
                    monster.id,
                    monster
            );

            // Initialize bone wobbles with hierarchy awareness
            initializeHierarchicalBoneWobbles(ragdoll, skeleton, monster);

            return ragdoll;

        } catch (Exception e) {
            throw new Exception("Failed to create skeleton ragdoll: " + e.getMessage(), e);
        }
    }

    /**
     * Validate skeleton components are present and valid
     */
    private void validateSkeletonComponents(Skeleton skeleton, SkeletonRenderer sr) throws Exception {
        if (skeleton == null) {
            throw new Exception("Skeleton is null");
        }
        if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
            throw new Exception("Skeleton has no bones");
        }
        if (sr == null) {
            throw new Exception("SkeletonRenderer is null");
        }
    }

    // ================================
    // PLAYER RAGDOLL CREATION METHODS
    // ================================
    /**
     * Create ragdoll for image-based players
     */
    private MultiBodyRagdoll createPlayerImageRagdoll(AbstractPlayer player) throws Exception {
        try {
            float customGroundLevel = calculatePlayerGroundLevel(player);

            return new MultiBodyRagdoll(
                    player.drawX,
                    player.drawY,
                    customGroundLevel,
                    player.getClass().getSimpleName(), // Use class name as ID for players
                    player
            );

        } catch (Exception e) {
            throw new Exception("Failed to create player image ragdoll: " + e.getMessage(), e);
        }
    }

    /**
     * Create ragdoll for skeleton-based players (normal case)
     */
    private MultiBodyRagdoll createPlayerSkeletonRagdoll(AbstractPlayer player, ReflectionHelper reflectionHelper) throws Exception {
        try {
            // Get skeleton components
            Skeleton skeleton = reflectionHelper.getSkeleton(player);
            SkeletonRenderer sr = reflectionHelper.getSkeletonRenderer(player);

            validateSkeletonComponents(skeleton, sr);

            float customGroundLevel = calculatePlayerGroundLevel(player);

            // Create the ragdoll with skeleton
            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    skeleton,
                    customGroundLevel,
                    player.drawX,
                    player.drawY,
                    player.getClass().getSimpleName(), // Use class name as ID for players
                    player
            );

            // Initialize bone wobbles with hierarchy awareness
            initializePlayerHierarchicalBoneWobbles(ragdoll, skeleton, player);

            return ragdoll;

        } catch (Exception e) {
            throw new Exception("Failed to create player skeleton ragdoll: " + e.getMessage(), e);
        }
    }


    // ================================
    // BONE WOBBLE INITIALIZATION
    // ================================

    /**
     * Initialize bone wobbles with hierarchy-aware physics
     */
    private void initializeHierarchicalBoneWobbles(MultiBodyRagdoll ragdoll, Skeleton skeleton, AbstractMonster monster) {
        float overkillDamage = OverkillTracker.getOverkillDamage(monster);
        String monsterName = monster.id;

        for (Bone bone : skeleton.getBones()) {
            BoneWobble wobble = new BoneWobble(bone.getRotation(), bone);

            // Apply depth-based reduction
            float depthReduction = Math.min(wobble.chainDepth * CHAIN_DEPTH_REDUCTION_FACTOR, MAX_DEPTH_REDUCTION);
            wobble.angularVelocity = MathUtils.random(-360f, 360f) * (1.0f - depthReduction);

            // Determine bone characteristics
            boolean hasVisualAttachment = hasVisualAttachment(bone);
            boolean willBeDetached = willBoneBeDetached(bone, skeleton, monsterName, overkillDamage);
            boolean isVisualLimb = hasVisualAttachment && isLimbBone(bone);

            // Apply appropriate physics enhancement
            applyBoneEnhancement(wobble, willBeDetached, isVisualLimb, hasVisualAttachment, depthReduction);

            // Apply minimal random initial rotation offset
            wobble.rotation += MathUtils.random(-2f, 2f);

            // Store the wobble in the ragdoll
            ragdoll.boneWobbles.put(bone, wobble);
        }
    }

    /**
     * Apply physics enhancement based on bone type
     */
    private void applyBoneEnhancement(BoneWobble wobble, boolean willBeDetached, boolean isVisualLimb,
                                      boolean hasVisualAttachment, float depthReduction) {

        if (willBeDetached) {
            // Bones with detached attachments get moderate enhancement
            wobble.angularVelocity *= MathUtils.random(ATTACHMENT_BONE_MULTIPLIER_MIN, ATTACHMENT_BONE_MULTIPLIER_MAX);

        } else if (isVisualLimb) {
            // Visual limbs get enhanced motion with depth constraints
            float multiplier = MathUtils.random(VISUAL_LIMB_MULTIPLIER_MIN, VISUAL_LIMB_MULTIPLIER_MAX);
            wobble.angularVelocity *= multiplier * (1.0f - depthReduction * VISUAL_LIMB_DEPTH_IMPACT);

        } else if (hasVisualAttachment) {
            // Other visual bones get slight enhancement
            float multiplier = MathUtils.random(VISUAL_BONE_MULTIPLIER_MIN, VISUAL_BONE_MULTIPLIER_MAX);
            wobble.angularVelocity *= multiplier * (1.0f - depthReduction * VISUAL_BONE_DEPTH_IMPACT);

        } else {
            // Animation control bones get reduced motion
            float multiplier = MathUtils.random(CONTROL_BONE_MULTIPLIER_MIN, CONTROL_BONE_MULTIPLIER_MAX);
            wobble.angularVelocity *= multiplier * (1.0f - depthReduction * CONTROL_BONE_DEPTH_IMPACT);
        }
    }
    // ================================
    // PLAYER BONE WOBBLE INITIALIZATION
    // ================================
    /**
     * Initialize bone wobbles for players with hierarchy-aware physics
     */
    private void initializePlayerHierarchicalBoneWobbles(MultiBodyRagdoll ragdoll, Skeleton skeleton, AbstractPlayer player) {
        float overkillDamage = OverkillTracker.getOverkillDamage(player);
        String playerName = player.getClass().getSimpleName();

        for (Bone bone : skeleton.getBones()) {
            BoneWobble wobble = new BoneWobble(bone.getRotation(), bone);

            // Apply depth-based reduction
            float depthReduction = Math.min(wobble.chainDepth * CHAIN_DEPTH_REDUCTION_FACTOR, MAX_DEPTH_REDUCTION);
            wobble.angularVelocity = MathUtils.random(-360f, 360f) * (1.0f - depthReduction);

            // Determine bone characteristics
            boolean hasVisualAttachment = hasVisualAttachment(bone);
            boolean willBeDetached = willPlayerBoneBeDetached(bone, skeleton, playerName, overkillDamage);
            boolean isVisualLimb = hasVisualAttachment && isLimbBone(bone);

            // Apply appropriate physics enhancement
            applyBoneEnhancement(wobble, willBeDetached, isVisualLimb, hasVisualAttachment, depthReduction);

            // Apply minimal random initial rotation offset
            wobble.rotation += MathUtils.random(-2f, 2f);

            // Store the wobble in the ragdoll
            ragdoll.boneWobbles.put(bone, wobble);
        }
    }

    /**
     * Check if a player bone will have its attachment detached for physics
     */
    private boolean willPlayerBoneBeDetached(Bone bone, Skeleton skeleton, String playerName, float overkillDamage) {
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getBone() == bone && slot.getAttachment() instanceof RegionAttachment) {
                RegionAttachment regionAttachment = (RegionAttachment) slot.getAttachment();
                String attachmentName = regionAttachment.getName();
                if (AttachmentConfig.shouldDetachAttachment(playerName, attachmentName, overkillDamage)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================
    // HELPER METHODS
    // ================================

    /**
     * Calculate custom ground level for specific monsters
     */
    private float calculateGroundLevel(AbstractMonster monster) {
        float baseGroundLevel = monster.drawY;

        switch (monster.id) {
            case TheGuardian.ID:
                return baseGroundLevel + (100f * Settings.scale);
            case Cultist.ID:
                return baseGroundLevel + (20f * Settings.scale);
            case Byrd.ID:
            case BronzeOrb.ID:
            case SnakeDagger.ID:
                return AbstractDungeon.player.drawY + (20f * Settings.scale);
            default:
                return AbstractDungeon.player.drawY + (20f * Settings.scale);
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
            return true; // Default to visual if determination fails
        }
        return false;
    }

    /**
     * Check if bone is a limb based on naming pattern
     */
    private boolean isLimbBone(Bone bone) {
        String boneName = bone.getData().getName().toLowerCase();
        return boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");
    }

    /**
     * Check if a bone will have its attachment detached for physics
     */
    private boolean willBoneBeDetached(Bone bone, Skeleton skeleton, String monsterName, float overkillDamage) {
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getBone() == bone && slot.getAttachment() instanceof RegionAttachment) {
                RegionAttachment regionAttachment = (RegionAttachment) slot.getAttachment();
                String attachmentName = regionAttachment.getName();
                if (AttachmentConfig.shouldDetachAttachment(monsterName, attachmentName, overkillDamage)) {
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
        float baseForceX = MathUtils.random(MIN_FORCE_X, MAX_FORCE_X);
        float forceY = MathUtils.random(MIN_FORCE_Y, MAX_FORCE_Y);

        // Determine direction based on monster position relative to player
        float playerX = AbstractDungeon.player.drawX;
        float monsterX = ragdoll.getAssociatedEntity().drawX;
        float forceX = (monsterX < playerX) ? -baseForceX : baseForceX;

        ragdoll.applyGlobalForce(forceX, forceY);

        // Initialize rotation tracking
        ragdoll.lastRotation = ragdoll.mainBody.rotation;
        ragdoll.totalRotationDegrees = 0f;
    }

    /**
     * Check if monster is image-based (has image but no skeleton)
     */
    private boolean isImageBasedMonster(AbstractMonster monster, ReflectionHelper reflectionHelper) {
        try {
            return reflectionHelper.getImage(monster) != null &&
                    reflectionHelper.getSkeleton(monster) == null;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================
    // PLAYER HELPER METHODS
    // ================================
    /**
     * Calculate ground level for players
     */
    private float calculatePlayerGroundLevel(AbstractPlayer player) {
        // Players typically stay at floor level
        return AbstractDungeon.floorY + (20f * Settings.scale);
    }

    /**
     * Apply initial physics force to the player ragdoll
     */
    private void applyInitialPlayerForce(MultiBodyRagdoll ragdoll) {
        float baseForceX = MathUtils.random(MIN_FORCE_X, MAX_FORCE_X);
        float forceY = MathUtils.random(MIN_FORCE_Y, MAX_FORCE_Y);

        float forceX = -baseForceX;

        ragdoll.applyGlobalForce(forceX, forceY);

        // Initialize rotation tracking
        ragdoll.lastRotation = ragdoll.mainBody.rotation;
        ragdoll.totalRotationDegrees = 0f;
    }

    /**
     * Check if player is image-based (has image but no skeleton)
     */
    private boolean isImageBasedPlayer(AbstractPlayer player, ReflectionHelper reflectionHelper) {
        try {
            return reflectionHelper.getImage(player) != null &&
                    reflectionHelper.getSkeleton(player) == null;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================
    // FACTORY STATISTICS
    // ================================

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