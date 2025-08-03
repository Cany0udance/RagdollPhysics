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
    private static long globalLastLogTime = 0;

    // Store bone reference for attachment checking
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

        if (printInitializationLogs) {
            if (isLongLimb) {
                BaseMod.logger.info("[" + wobbleId + "] Created gravity-aware limb: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint) + "°)");
            } else if (hasVisualAttachment) {
                BaseMod.logger.info("[" + wobbleId + "] Created visual bone: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint) + "°)");
            } else {
                BaseMod.logger.info("[" + wobbleId + "] Created control bone: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint) + "°)");
            }
        }
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

        // Calculate parent motion characteristics
        float velocityMagnitude = (float) Math.sqrt(parentVelocityX * parentVelocityX + parentVelocityY * parentVelocityY);
        boolean isAirborne = Math.abs(parentVelocityY) > 30f || velocityMagnitude > 150f;
        boolean isEarlyFlight = timeAlive < 3.0f;

        // Apply rotation
        rotation += angularVelocity * deltaTime;

        // Apply hierarchical constraints
        if (chainDepth > 0 && !isEarlyFlight) {
            applyHierarchicalConstraints(ragdoll, parentHasSettled);
        }

        // Apply limb gravity
        if (isLongLimb && parentHasSettled && timeAlive > GRAVITY_CORRECTION_DELAY) {
            applyLimbGravity(deltaTime);
        }

        // Frame-rate independent damping calculation
        float rotationMagnitude = Math.abs(rotation);
        float constraintViolation = Math.max(0, rotationMagnitude - baseRotationConstraint) / baseRotationConstraint;

        // Calculate base damping factor
        float baseDampingFactor;
        if (isEarlyFlight) {
            baseDampingFactor = 0.995f;
        } else if (isAirborne) {
            baseDampingFactor = 0.98f;
        } else {
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
        angularVelocity *= (float) Math.pow(baseDampingFactor, deltaTime * 60f);

        // Constraint restoration force (this is a force, not damping, so time-scale it)
        if (constraintViolation > 0.2f) {
            float restorationForce = -Math.signum(rotation) * constraintViolation * 15f * deltaTime * 60f;
            angularVelocity += restorationForce;
        }

        // Logging section (unchanged)
        long currentTime = System.currentTimeMillis();
        boolean canLogGlobally = (currentTime - globalLastLogTime) >= 2000;
        boolean isSignificantChange = Math.abs(oldRotation - rotation) > 30f || constraintViolation > 0.5f;

        if (canLogGlobally && isSignificantChange) {
            String phase = isEarlyFlight ? "EARLY_FLIGHT" : (isAirborne ? "AIRBORNE" : "SETTLING");
            String boneType = isLongLimb ? "VISUAL_LIMB" : (isLimb ? "VISUAL" : "CONTROL");
            BaseMod.logger.info("[" + wobbleId + "] " + phase + " (" + boneType
                    + ") - Update " + updateCount
                    + ", rot: " + String.format("%.1f", oldRotation) + " -> "
                    + String.format("%.1f", rotation) + "°, constraint: "
                    + String.format("%.1f", baseRotationConstraint)
                    + "°, violation: " + String.format("%.2f", constraintViolation));
            globalLastLogTime = currentTime;
        }
    }

    // SIMPLIFIED: Apply hierarchical constraints to prevent accordion effect
    private void applyHierarchicalConstraints(MultiBodyRagdoll ragdoll, boolean parentHasSettled) {
        float parentWobbleRotation = getParentWobbleRotation(ragdoll);

        // Calculate the relative rotation from parent
        float relativeRotation = rotation - parentWobbleRotation * parentInfluence;

        // Apply constraint based on chain depth and bone type - but more lenient
        float maxRelativeRotation = baseRotationConstraint * (1.0f - Math.min(chainDepth * 0.05f, 0.3f)); // Much less reduction

        if (Math.abs(relativeRotation) > maxRelativeRotation) {
            float constraintForce = (Math.abs(relativeRotation) - maxRelativeRotation) / maxRelativeRotation;

            // Gently pull back toward constraint boundary
            float targetRotation = parentWobbleRotation * parentInfluence
                    + Math.signum(relativeRotation) * maxRelativeRotation;

            float correctionStrength = parentHasSettled ? 0.03f : 0.01f; // Much gentler correction
            rotation = rotation * (1.0f - correctionStrength) + targetRotation * correctionStrength;

            // Also reduce angular velocity when violating constraints - but less
            angularVelocity *= (1.0f - constraintForce * 0.1f); // Much less reduction
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
           //     BaseMod.logger.info("[" + wobbleId + "] ROOT limb gravity correction complete - settled at "
           //             + String.format("%.1f", normalizedRotation) + "°");
            }

            // Log the gravity application
            if (gravityTimer > 0.5f) { // Reduced logging frequency from 0.3s to 0.5s
               // BaseMod.logger.info("[" + wobbleId + "] Applying ROOT limb gravity - current: "
              //          + String.format("%.1f", normalizedRotation)
              //          + "°, target: " + targetRotation
              //          + "°, torque: " + String.format("%.2f", gravityTorque)
               //         + ", verticalness: " + String.format("%.2f", verticalness));
                gravityTimer = 0f;
            }
        }
    }
}