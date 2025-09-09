package ragdollphysics.ragdollutil;
import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Slot;

import static ragdollphysics.ragdollutil.MultiBodyRagdoll.printInitializationLogs;

/**
 * Handles wobble physics for individual skeleton bones.
 * Provides hierarchy-aware bone movement with gravitational effects for limbs.
 */
public class BoneWobble {
    public float rotation = 0f;
    public float angularVelocity = 0f;
    private final String wobbleId;
    private int updateCount = 0;
    private float timeAlive = 0f;
    private final Bone bone;

    // PUBLIC: Hierarchical constraint properties (accessible from outside)
    public final boolean isRootBone;
    public final boolean isLeafBone;
    public final int chainDepth;
    public final float baseRotationConstraint;
    public final float parentInfluence;

    // Limb gravity properties
    private final boolean isLimb;
    private final boolean isLongLimb;
    private final float originalRotation;
    private boolean hasAppliedGravityCorrection = false;
    private float gravityTimer = 0f;
    private static final float GRAVITY_CORRECTION_DELAY = 1.0f;

    // Locking mechanism for settled bones
    private boolean isLocked = false;
    private float timeSettled = 0f;
    private boolean wasRecentlyUnlocked = false;
    private float lastSignificantMovement = 0f;
    private boolean hasLoggedLocking = false;

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

        // FIXED: More reasonable constraint values
        if (hasVisualAttachment) {
            // Visual bones get more freedom
            this.baseRotationConstraint = isRootBone ? 60f : Math.max(25f, 45f - chainDepth * 2f);
            this.parentInfluence = 0.2f; // Reduced parent influence
        } else {
            // Control bones still get constraints but not so restrictive
            this.baseRotationConstraint = isRootBone ? 45f : Math.max(12f, 25f - chainDepth * 1f);
            this.parentInfluence = 0.4f; // Reduced parent influence for control bones too
        }

        this.isLimb = hasVisualAttachment && isAnatomicalLimbName(boneName);
        this.isLongLimb = this.isLimb;
    }

    // Calculate how deep this bone is in its chain
    private int calculateChainDepth(Bone bone) {
        int depth = 0;
        Bone current = bone;
        while (current.getParent() != null) {
            current = current.getParent();
            depth++;
        }
        return depth;
    }

    // Get the parent bone's wobble rotation if it exists
    private float getParentWobbleRotation(MultiBodyRagdoll ragdoll) {
        if (bone.getParent() != null) {
            BoneWobble parentWobble = ragdoll.boneWobbles.get(bone.getParent());
            if (parentWobble != null) {
                return parentWobble.rotation;
            }
        }
        return 0f;
    }

    // Check if a bone actually has a visual sprite attachment
    private boolean boneHasVisualAttachment(Bone bone) {
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

    // Much simpler anatomical limb detection
    private boolean isAnatomicalLimbName(String boneName) {
        return boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");
    }

    public void update(float deltaTime, float parentVelocityX, float parentVelocityY,
                       boolean parentHasSettled, MultiBodyRagdoll ragdoll) {
        updateCount++;
        timeAlive += deltaTime;
        float oldAngularVelocity = angularVelocity;
        float oldRotation = rotation;

        // Track time since parent settled
        if (parentHasSettled) {
            timeSettled += deltaTime;
        } else {
            timeSettled = 0f;
            if (isLocked) {
                isLocked = false;
                wasRecentlyUnlocked = true;
            }
        }

        // Lock bones when parent has fully settled - be more aggressive about locking
        // Important comment: The following block makes it so limbs lock entirely when the ragdoll is settled.
        if (parentHasSettled && !isLocked) {
            isLocked = true;
            angularVelocity = 0f;
            hasLoggedLocking = true;
        }

        // Skip all updates for locked bones
        if (isLocked) {
            return; // Exit early for locked bones
        }

        // Track significant movement for unlocked bones
        if (Math.abs(oldAngularVelocity) > 10f || Math.abs(rotation - oldRotation) > 5f) {
            lastSignificantMovement = timeAlive;
        }

        // Calculate parent motion characteristics
        float velocityMagnitude = (float) Math.sqrt(parentVelocityX * parentVelocityX + parentVelocityY * parentVelocityY);
        boolean isAirborne = Math.abs(parentVelocityY) > 30f || velocityMagnitude > 150f;
        boolean hasContactedGround = ragdoll.mainBody.y <= ragdoll.getGroundY() + 5f; // Replace isEarlyFlight check

        // Apply rotation
        rotation += angularVelocity * deltaTime;

        // Apply hierarchical constraints - only during airborne and initial ground contact, not during settling
        if (chainDepth > 0 && hasContactedGround && isAirborne) {
            applyHierarchicalConstraints(ragdoll, parentHasSettled);
        }

        // Apply limb gravity - only after ground contact
        if (isLongLimb && parentHasSettled && hasContactedGround && timeSettled < 0.5f) {
            applyLimbGravity(deltaTime);
        }

        // Frame-rate independent damping calculation
        float rotationMagnitude = Math.abs(rotation);
        float constraintViolation = Math.max(0, rotationMagnitude - baseRotationConstraint) / baseRotationConstraint;

        // Calculate base damping factor - this preserves the original logic flow
        float baseDampingFactor;
        if (!hasContactedGround) {
            // Still airborne - use minimal damping (same as original isEarlyFlight)
            baseDampingFactor = 0.995f;
        } else if (isAirborne) {
            // Has contacted ground but still moving - use moderate damping
            baseDampingFactor = 0.98f;
        } else {
            // On ground and settling - use the original ground-based damping logic
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

        // Apply frame-rate independent damping
        // Add damping dead zone to prevent micro-oscillations
        if (parentHasSettled && Math.abs(angularVelocity) < 0.1f) {
            angularVelocity = 0f;
        } else {
            angularVelocity *= (float) Math.pow(baseDampingFactor, deltaTime * 60f);
        }

        // Enhanced velocity thresholding
        if (parentHasSettled && Math.abs(angularVelocity) < 2f) {
            angularVelocity = 0f;
        }

        // Constraint restoration force - only when airborne or moving after ground contact
        if (constraintViolation > 0.2f && (!hasContactedGround || isAirborne)) {
            float baseStrength = !hasContactedGround ? 3f : 5f;
            float restorationForce = -Math.signum(rotation) * constraintViolation * baseStrength * deltaTime * 60f;
            angularVelocity += restorationForce;
        }
    }

    private void applyHierarchicalConstraints(MultiBodyRagdoll ragdoll, boolean parentHasSettled) {
        float parentWobbleRotation = getParentWobbleRotation(ragdoll);
        // Calculate the relative rotation from parent
        float relativeRotation = rotation - parentWobbleRotation * parentInfluence;

        // Apply constraint based on chain depth and bone type - but more lenient
        float maxRelativeRotation = baseRotationConstraint * (1.0f - Math.min(chainDepth * 0.05f, 0.3f));
        if (Math.abs(relativeRotation) > maxRelativeRotation) {
            float constraintForce = (Math.abs(relativeRotation) - maxRelativeRotation) / maxRelativeRotation;
            // Gently pull back toward constraint boundary
            float targetRotation = parentWobbleRotation * parentInfluence
                    + Math.signum(relativeRotation) * maxRelativeRotation;
            float correctionStrength = parentHasSettled ? 0.03f : 0.01f;
            rotation = rotation * (1.0f - correctionStrength) + targetRotation * correctionStrength;
            // Also reduce angular velocity when violating constraints
            angularVelocity *= (1.0f - constraintForce * 0.1f);
        }
    }

    // Keep existing limb gravity method (unchanged)
    private void applyLimbGravity(float deltaTime) {
        gravityTimer += deltaTime;
        // Normalize rotation to 0-360 range for easier calculations
        float normalizedRotation = ((rotation % 360f) + 360f) % 360f;

        // Determine if limb is pointing "up" (roughly vertical, pointing skyward)
        boolean isPointingUp = (normalizedRotation > 45f && normalizedRotation < 135f)
                || (normalizedRotation > 225f && normalizedRotation < 315f);

        if (isPointingUp && !hasAppliedGravityCorrection) {
            // Calculate the closest "flat" position (horizontal)
            float targetRotation;
            if (normalizedRotation >= 45f && normalizedRotation <= 135f) {
                // Upper right quadrant - fall to right (0° or 360°)
                targetRotation = (normalizedRotation < 90f) ? 0f : 180f;
            } else {
                // Upper left quadrant - fall to left (180°)
                targetRotation = (normalizedRotation < 270f) ? 180f : 0f;
            }

            // Calculate shortest path to target
            float rotationDiff = targetRotation - normalizedRotation;
            if (rotationDiff > 180f) rotationDiff -= 360f;
            if (rotationDiff < -180f) rotationDiff += 360f;

            // Much gentler gravity torque for root limbs to prevent accordion effect
            float verticalness = 1.0f - Math.abs(Math.abs(normalizedRotation - 90f) - 90f) / 90f;
            float gravityTorque = rotationDiff * verticalness * 2.0f; // Reduced from 4.0f to 2.0f
            angularVelocity += gravityTorque;

            // Wider tolerance for completion to prevent micro-corrections
            if (Math.abs(rotationDiff) < 30f) { // Increased from 20f to 30f
                hasAppliedGravityCorrection = true;
            }

            if (gravityTimer > 0.5f) { // Reduced logging frequency from 0.3s to 0.5s
                gravityTimer = 0f;
            }
        }
    }
}