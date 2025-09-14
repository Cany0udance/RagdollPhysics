package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.attachments.Attachment;

import java.util.ArrayList;
import java.util.List;

import static ragdollphysics.RagdollPhysics.enableZeroGravity;

/**
 * Physics simulation for detached monster attachments (weapons, armor pieces, etc.).
 * Handles independent physics movement for attachments that break away from bones.
 * Supports parent-child relationships for composite attachments where child parts
 * maintain their relative position to their parent during physics simulation.
 */
public class AttachmentPhysics {

    // ================================
    // PHYSICS CONSTANTS
    // ================================

    private static final float ATTACHMENT_BOUNCE_THRESHOLD = 150f;
    private static final float GRAVITY = -1200f;
    private static final float GROUND_BOUNCE_DAMPING = 0.4f;
    private static final float GROUND_FRICTION = 0.85f;
    private static final float GROUND_ANGULAR_DAMPING = 0.6f;
    private static final float CEILING_BOUNCE_DAMPING = 0.5f;
    private static final float WALL_BOUNCE_DAMPING = 0.7f;
    private static final float AIR_RESISTANCE = 0.999f;
    private static final float GROUND_AIR_RESISTANCE = 0.95f;
    private static final float GROUND_ANGULAR_RESISTANCE = 0.7f;
    private static final float CEILING_ANGULAR_RESISTANCE = 0.8f;
    private static final float CHILD_WOBBLE_INTENSITY = 0.05f;

    // ================================
    // WORLD BOUNDARIES
    // ================================

    private static final float RIGHT_WALL_X = 1850f;
    private static final float LEFT_WALL_X = 0f;
    private static final float CEILING_Y = 1100f;

    // ================================
    // PHYSICS STATE
    // ================================

    public float x, y;
    public float velocityX, velocityY;
    public float rotation, angularVelocity;
    private final float groundY;

    // ================================
    // ATTACHMENT PROPERTIES
    // ================================

    public final Attachment attachment;
    public final Bone originalBone;
    public final float originalScaleX;
    public final float originalScaleY;
    private final String attachmentId;
    private final String attachmentName;

    // ================================
    // PARENT-CHILD RELATIONSHIPS
    // ================================

    private final boolean isChild;
    private AttachmentPhysics parentAttachment;
    private final List<AttachmentPhysics> childAttachments = new ArrayList<>();

    // Relative positioning for child attachments
    private float relativeX, relativeY;
    private float relativeRotation;

    // ================================
    // CONSTRUCTORS
    // ================================

    /**
     * Create a parent (independent) attachment with full physics simulation
     */
    public AttachmentPhysics(float startX, float startY, float groundLevel, Bone bone,
                             Attachment attachment, String attachmentName) {
        this(startX, startY, groundLevel, bone, attachment, attachmentName, null);
    }

    /**
     * Create an attachment with optional parent relationship
     * @param parent If null, creates independent attachment. If provided, creates child attachment.
     */
    public AttachmentPhysics(float startX, float startY, float groundLevel, Bone bone,
                             Attachment attachment, String attachmentName, AttachmentPhysics parent) {
        // Initialize basic properties
        this.x = startX;
        this.y = startY;
        this.groundY = groundLevel;
        this.rotation = bone.getWorldRotationX();
        this.attachment = attachment;
        this.attachmentName = attachmentName;
        this.attachmentId = generateAttachmentId(attachmentName);
        this.originalBone = bone;
        this.originalScaleX = bone.getScaleX();
        this.originalScaleY = bone.getScaleY();

        // Setup parent-child relationship
        this.parentAttachment = parent;
        this.isChild = (parent != null);

        if (isChild) {
            setupAsChildAttachment(parent, startX, startY);
        }
    }

    /**
     * Generate unique identifier for attachment tracking
     */
    private String generateAttachmentId(String attachmentName) {
        return "Attachment_" + attachmentName + "_" + System.currentTimeMillis() % 1000;
    }

    /**
     * Configure this attachment as a child of the given parent
     */
    private void setupAsChildAttachment(AttachmentPhysics parent, float startX, float startY) {
        parent.addChild(this);

        // Calculate initial relative position and rotation
        this.relativeX = startX - parent.x;
        this.relativeY = startY - parent.y;
        this.relativeRotation = this.rotation - parent.rotation;
    }

    // ================================
    // PARENT-CHILD MANAGEMENT
    // ================================

    /**
     * Add a child attachment that will follow this parent's movement
     */
    public void addChild(AttachmentPhysics child) {
        if (!childAttachments.contains(child)) {
            childAttachments.add(child);
        }
    }

    // ================================
    // PHYSICS UPDATE SYSTEM
    // ================================

    /**
     * Main update method - handles both parent and child physics
     */
    public void update(float deltaTime) {
        if (isChild && parentAttachment != null) {
            updateAsChild(deltaTime);
        } else {
            updateAsParent(deltaTime);
            updateChildren(deltaTime);
        }
    }

    /**
     * Full physics simulation for parent attachments
     */
    private void updateAsParent(float deltaTime) {
        applyGravity(deltaTime);
        updatePosition(deltaTime);
        updateRotation(deltaTime);
        handleCollisions(deltaTime);
        applyAirResistance(deltaTime);
    }

    /**
     * Apply gravitational force to velocity
     */
    private void applyGravity(float deltaTime) {
        if (!enableZeroGravity) {  // Assuming this is defined elsewhere or should be removed
            velocityY += GRAVITY * deltaTime;
        }
    }

    /**
     * Update position based on current velocity
     */
    private void updatePosition(float deltaTime) {
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
    }

    /**
     * Update rotation based on angular velocity
     */
    private void updateRotation(float deltaTime) {
        rotation += angularVelocity * deltaTime;
    }

    /**
     * Handle collisions with world boundaries
     */
    private void handleCollisions(float deltaTime) {
        handleGroundCollision(deltaTime);
        handleCeilingCollision(deltaTime);
        handleWallCollisions();
    }

    /**
     * Handle collision with ground surface
     */
    private void handleGroundCollision(float deltaTime) {
        if (y < groundY && velocityY < 0) {
            y = groundY;

            if (Math.abs(velocityY) > ATTACHMENT_BOUNCE_THRESHOLD) {
                // High energy bounce
                velocityY *= -GROUND_BOUNCE_DAMPING;
                velocityX *= GROUND_FRICTION;
                angularVelocity *= GROUND_ANGULAR_DAMPING;
            } else {
                // Low energy settling
                velocityY = 0f;
                velocityX *= (float) Math.pow(GROUND_AIR_RESISTANCE, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(GROUND_ANGULAR_RESISTANCE, deltaTime * 60f);
            }
        }
    }

    /**
     * Handle collision with ceiling
     */
    private void handleCeilingCollision(float deltaTime) {
        if (y > CEILING_Y && velocityY > 0) {
            y = CEILING_Y;

            if (Math.abs(velocityY) > ATTACHMENT_BOUNCE_THRESHOLD) {
                velocityY *= -CEILING_BOUNCE_DAMPING;
                velocityX *= GROUND_FRICTION;
                angularVelocity = MathUtils.random(-450f, 450f);
            } else {
                velocityY = 0f;
                velocityX *= (float) Math.pow(GROUND_AIR_RESISTANCE, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(CEILING_ANGULAR_RESISTANCE, deltaTime * 60f);
            }
        }
    }

    /**
     * Handle collisions with left and right walls
     */
    private void handleWallCollisions() {
        if (x > RIGHT_WALL_X && velocityX > 0) {
            x = RIGHT_WALL_X;
            velocityX *= -WALL_BOUNCE_DAMPING;
            angularVelocity = MathUtils.random(-360f, 360f);
        }

        if (x < LEFT_WALL_X && velocityX < 0) {
            x = LEFT_WALL_X;
            velocityX *= -WALL_BOUNCE_DAMPING;
            angularVelocity = MathUtils.random(-360f, 360f);
        }
    }

    /**
     * Apply air resistance to slow down movement over time
     */
    private void applyAirResistance(float deltaTime) {
        if (y > groundY) {  // Only apply air resistance when not on ground
            velocityX *= (float) Math.pow(AIR_RESISTANCE, deltaTime * 60f);
            angularVelocity *= (float) Math.pow(AIR_RESISTANCE, deltaTime * 60f);
        }
    }

    /**
     * Update child attachment to maintain relative position to parent
     */
    private void updateAsChild(float deltaTime) {
        if (parentAttachment == null) return;

        updateRelativePosition();
        copyParentVelocity();
        addNaturalWobble();
    }

    /**
     * Calculate position based on parent's current state using rotation matrix
     */
    private void updateRelativePosition() {
        float parentRotRad = (float) Math.toRadians(parentAttachment.rotation);
        float cosRot = (float) Math.cos(parentRotRad);
        float sinRot = (float) Math.sin(parentRotRad);

        // Apply rotation matrix to relative position
        this.x = parentAttachment.x + (relativeX * cosRot - relativeY * sinRot);
        this.y = parentAttachment.y + (relativeX * sinRot + relativeY * cosRot);
        this.rotation = parentAttachment.rotation + relativeRotation;
    }

    /**
     * Copy parent's velocity for realistic movement tracking
     */
    private void copyParentVelocity() {
        this.velocityX = parentAttachment.velocityX;
        this.velocityY = parentAttachment.velocityY;
        this.angularVelocity = parentAttachment.angularVelocity;
    }

    /**
     * Add small random wobble for more organic child movement
     */
    private void addNaturalWobble() {
        this.rotation += MathUtils.random(-CHILD_WOBBLE_INTENSITY, CHILD_WOBBLE_INTENSITY);
    }

    /**
     * Update all child attachments based on current parent state
     */
    private void updateChildren(float deltaTime) {
        for (AttachmentPhysics child : childAttachments) {
            child.updateAsChild(deltaTime);
            child.updateChildren(deltaTime);  // Handle nested children recursively
        }
    }

    // ================================
    // PUBLIC ACCESSORS
    // ================================

    public boolean isChild() {
        return isChild;
    }

    public boolean hasChildren() {
        return !childAttachments.isEmpty();
    }

    public int getChildCount() {
        return childAttachments.size();
    }

    public AttachmentPhysics getParent() {
        return parentAttachment;
    }

    public List<AttachmentPhysics> getChildren() {
        return new ArrayList<>(childAttachments);
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public String getAttachmentId() {
        return attachmentId;
    }
}