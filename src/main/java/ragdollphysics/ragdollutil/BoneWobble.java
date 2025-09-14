package ragdollphysics.ragdollutil;
import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Slot;
import com.megacrit.cardcrawl.monsters.city.BronzeOrb;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.util.HashSet;
import java.util.Set;

import static ragdollphysics.ragdollutil.MultiBodyRagdoll.printInitializationLogs;

/**
 * Handles wobble physics for individual skeleton bones.
 * Provides hierarchy-aware bone movement with gravitational effects for limbs.
 */
public class BoneWobble {

    // ================================
    // CORE PHYSICS CONSTANTS
    // ================================

    private static final float GRAVITY_CORRECTION_DELAY = 1.0f;
    private static final float AIRBORNE_VELOCITY_THRESHOLD = 30f;
    private static final float AIRBORNE_MAGNITUDE_THRESHOLD = 150f;
    private static final float GROUND_CONTACT_TOLERANCE = 5f;
    private static final float LIMB_SETTLING_TIME = 0.5f;
    private static final float SIGNIFICANT_VELOCITY = 10f;
    private static final float SIGNIFICANT_ROTATION = 5f;
    private static final float VELOCITY_DEADZONE = 0.1f;
    private static final float SETTLED_VELOCITY_THRESHOLD = 2f;
    private static final float CONSTRAINT_VIOLATION_THRESHOLD = 0.2f;

    // ================================
    // ENEMY CONFIGURATION
    // ================================

    private static final Set<String> FREE_ROTATION_ENEMIES = new HashSet<>();
    static {
        FREE_ROTATION_ENEMIES.add(LouseNormal.ID);
        FREE_ROTATION_ENEMIES.add(LouseDefensive.ID);
        FREE_ROTATION_ENEMIES.add(AcidSlime_S.ID);
        FREE_ROTATION_ENEMIES.add(SpikeSlime_S.ID);
        FREE_ROTATION_ENEMIES.add(BronzeOrb.ID);
        FREE_ROTATION_ENEMIES.add(AcidSlime_M.ID);
        FREE_ROTATION_ENEMIES.add(SpikeSlime_M.ID);
    }

    // ================================
    // PHYSICS STATE
    // ================================

    public float rotation = 0f;
    public float angularVelocity = 0f;
    private final float originalRotation;

    // ================================
    // BONE PROPERTIES
    // ================================

    private final Bone bone;
    private final String wobbleId;
    private int updateCount = 0;
    private float timeAlive = 0f;

    // Hierarchy properties
    public final boolean isRootBone;
    public final boolean isLeafBone;
    public final int chainDepth;
    public final float baseRotationConstraint;
    public final float parentInfluence;

    // Limb properties
    private final boolean isLimb;
    private final boolean isLongLimb;

    // Locking mechanism
    public boolean isLocked = false;
    private float timeSettled = 0f;
    private boolean wasRecentlyUnlocked = false;
    private float lastSignificantMovement = 0f;
    private boolean hasLoggedLocking = false;

    // Limb gravity state
    private boolean hasAppliedGravityCorrection = false;
    private float gravityTimer = 0f;

    // ================================
    // CONSTRUCTOR
    // ================================

    public BoneWobble(float initialRotation, Bone bone) {
        this.originalRotation = initialRotation;
        this.bone = bone;
        this.wobbleId = "Wobble_" + System.currentTimeMillis() % 1000;

        String boneName = bone.getData().getName().toLowerCase();
        boolean hasVisualAttachment = boneHasVisualAttachment(bone);

        // Analyze bone hierarchy
        this.isRootBone = bone.getParent() == null;
        this.isLeafBone = bone.getChildren().size == 0;
        this.chainDepth = calculateChainDepth(bone);

        // Configure constraints - more reasonable values
        if (hasVisualAttachment) {
            this.baseRotationConstraint = isRootBone ? 60f : Math.max(25f, 45f - chainDepth * 2f);
            this.parentInfluence = 0.2f;
        } else {
            this.baseRotationConstraint = isRootBone ? 45f : Math.max(12f, 25f - chainDepth * 1f);
            this.parentInfluence = 0.4f;
        }

        this.isLimb = hasVisualAttachment && isAnatomicalLimbName(boneName);
        this.isLongLimb = this.isLimb;
    }

    // ================================
    // MAIN UPDATE METHOD
    // ================================

    public void update(float deltaTime, float parentVelocityX, float parentVelocityY,
                       boolean parentHasSettled, MultiBodyRagdoll ragdoll) {

        updateCount++;
        timeAlive += deltaTime;
        float oldAngularVelocity = angularVelocity;
        float oldRotation = rotation;

        // === SETTLING AND LOCKING LOGIC ===
        if (parentHasSettled) {
            timeSettled += deltaTime;
        } else {
            timeSettled = 0f;
            if (isLocked) {
                isLocked = false;
                wasRecentlyUnlocked = true;
            }
        }

        // Lock bones when parent has fully settled
        if (parentHasSettled && !isLocked) {
            isLocked = true;
            angularVelocity = 0f;
            hasLoggedLocking = true;
        }

        // Skip all updates for locked bones
        if (isLocked) {
            return;
        }

        // Track significant movement
        if (Math.abs(oldAngularVelocity) > SIGNIFICANT_VELOCITY ||
                Math.abs(rotation - oldRotation) > SIGNIFICANT_ROTATION) {
            lastSignificantMovement = timeAlive;
        }

        // === MOTION ANALYSIS ===
        float velocityMagnitude = (float) Math.sqrt(parentVelocityX * parentVelocityX + parentVelocityY * parentVelocityY);
        boolean isAirborne = Math.abs(parentVelocityY) > AIRBORNE_VELOCITY_THRESHOLD || velocityMagnitude > AIRBORNE_MAGNITUDE_THRESHOLD;
        boolean hasContactedGround = ragdoll.mainBody.y <= ragdoll.getGroundY() + GROUND_CONTACT_TOLERANCE;

        // === CORE PHYSICS ===
        rotation += angularVelocity * deltaTime;

        // Apply hierarchical constraints - only during airborne and initial ground contact
        if (chainDepth > 0 && hasContactedGround && isAirborne && !shouldAllowFreeRotation(ragdoll)) {
            applyHierarchicalConstraints(ragdoll, parentHasSettled);
        }

        // Apply limb gravity - only after ground contact during settling
        if (isLongLimb && parentHasSettled && hasContactedGround && timeSettled < LIMB_SETTLING_TIME) {
            applyLimbGravity(deltaTime);
        }

        // === DAMPING CALCULATION ===
        float rotationMagnitude = Math.abs(rotation);
        float constraintViolation = Math.max(0, rotationMagnitude - baseRotationConstraint) / baseRotationConstraint;

        float baseDampingFactor;
        if (!hasContactedGround) {
            baseDampingFactor = 0.995f;  // Still airborne
        } else if (isAirborne) {
            baseDampingFactor = 0.98f;   // Moving on ground
        } else {
            // On ground and settling - original ground logic
            boolean hasVisualAttachment = boneHasVisualAttachment(this.bone);
            if (!hasVisualAttachment && parentHasSettled) {
                baseDampingFactor = 0.92f - (constraintViolation * 0.1f);
            } else if (!isLimb && parentHasSettled) {
                baseDampingFactor = 0.88f - (constraintViolation * 0.08f);
            } else {
                float velocityRatio = Math.min(velocityMagnitude / 200f, 1.0f);
                baseDampingFactor = 0.92f + (0.98f - 0.92f) * velocityRatio;
                baseDampingFactor -= constraintViolation * 0.05f;
            }
        }
        baseDampingFactor = Math.max(0.7f, baseDampingFactor);

        // Apply damping with deadzone
        if (parentHasSettled && Math.abs(angularVelocity) < VELOCITY_DEADZONE) {
            angularVelocity = 0f;
        } else {
            angularVelocity *= (float) Math.pow(baseDampingFactor, deltaTime * 60f);
        }

        // Final velocity thresholding
        if (parentHasSettled && Math.abs(angularVelocity) < SETTLED_VELOCITY_THRESHOLD) {
            angularVelocity = 0f;
        }

        // Constraint restoration force
        if (constraintViolation > CONSTRAINT_VIOLATION_THRESHOLD &&
                (!hasContactedGround || isAirborne) && !shouldAllowFreeRotation(ragdoll)) {
            float baseStrength = !hasContactedGround ? 3f : 5f;
            float restorationForce = -Math.signum(rotation) * constraintViolation * baseStrength * deltaTime * 60f;
            angularVelocity += restorationForce;
        }
    }

    // ================================
    // HELPER METHODS
    // ================================

    private void applyHierarchicalConstraints(MultiBodyRagdoll ragdoll, boolean parentHasSettled) {
        float parentWobbleRotation = getParentWobbleRotation(ragdoll);
        float relativeRotation = rotation - parentWobbleRotation * parentInfluence;
        float maxRelativeRotation = baseRotationConstraint * (1.0f - Math.min(chainDepth * 0.05f, 0.3f));

        if (Math.abs(relativeRotation) > maxRelativeRotation) {
            float constraintForce = (Math.abs(relativeRotation) - maxRelativeRotation) / maxRelativeRotation;
            float targetRotation = parentWobbleRotation * parentInfluence + Math.signum(relativeRotation) * maxRelativeRotation;
            float correctionStrength = parentHasSettled ? 0.03f : 0.01f;

            rotation = rotation * (1.0f - correctionStrength) + targetRotation * correctionStrength;
            angularVelocity *= (1.0f - constraintForce * 0.1f);
        }
    }

    private void applyLimbGravity(float deltaTime) {
        gravityTimer += deltaTime;
        float normalizedRotation = ((rotation % 360f) + 360f) % 360f;
        boolean isPointingUp = (normalizedRotation > 45f && normalizedRotation < 135f) ||
                (normalizedRotation > 225f && normalizedRotation < 315f);

        if (isPointingUp && !hasAppliedGravityCorrection) {
            float targetRotation;
            if (normalizedRotation >= 45f && normalizedRotation <= 135f) {
                targetRotation = (normalizedRotation < 90f) ? 0f : 180f;
            } else {
                targetRotation = (normalizedRotation < 270f) ? 180f : 0f;
            }

            float rotationDiff = targetRotation - normalizedRotation;
            if (rotationDiff > 180f) rotationDiff -= 360f;
            if (rotationDiff < -180f) rotationDiff += 360f;

            float verticalness = 1.0f - Math.abs(Math.abs(normalizedRotation - 90f) - 90f) / 90f;
            float gravityTorque = rotationDiff * verticalness * 2.0f;
            angularVelocity += gravityTorque;

            if (Math.abs(rotationDiff) < 30f) {
                hasAppliedGravityCorrection = true;
            }

            if (gravityTimer > 0.5f) {
                gravityTimer = 0f;
            }
        }
    }

    private boolean shouldAllowFreeRotation(MultiBodyRagdoll ragdoll) {
        String monsterClassName = ragdoll.getEntityClassName();
        String boneName = bone.getData().getName().toLowerCase();

        if (!FREE_ROTATION_ENEMIES.contains(monsterClassName)) {
            return false;
        }

        // Constrain antler bones on Louse enemies
        if ((monsterClassName.equals(LouseNormal.ID) || monsterClassName.equals(LouseDefensive.ID)) &&
                boneName.contains("ant")) {
            return false;
        }

        return true;
    }

    private float getParentWobbleRotation(MultiBodyRagdoll ragdoll) {
        if (bone.getParent() != null) {
            BoneWobble parentWobble = ragdoll.boneWobbles.get(bone.getParent());
            if (parentWobble != null) {
                return parentWobble.rotation;
            }
        }
        return 0f;
    }

    private int calculateChainDepth(Bone bone) {
        int depth = 0;
        Bone current = bone;
        while (current.getParent() != null) {
            current = current.getParent();
            depth++;
        }
        return depth;
    }

    private boolean boneHasVisualAttachment(Bone bone) {
        try {
            for (Slot slot : bone.getSkeleton().getSlots()) {
                if (slot.getBone() == bone && slot.getAttachment() != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    private boolean isAnatomicalLimbName(String boneName) {
        return boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");
    }
}