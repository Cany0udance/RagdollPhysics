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
 * Now supports parent-child relationships for composite attachments.
 */
public class AttachmentPhysics {
    public float x, y, velocityX, velocityY, rotation, angularVelocity;
    private final float groundY;
    public final Attachment attachment;
    private final String attachmentId;
    public final Bone originalBone;
    public final float originalScaleX;
    public final float originalScaleY;
    private final String attachmentName;

    // Parent-child relationship fields
    private AttachmentPhysics parentAttachment;
    private final List<AttachmentPhysics> childAttachments = new ArrayList<>();
    private float relativeX, relativeY; // Position relative to parent
    private float relativeRotation; // Rotation relative to parent
    private final boolean isChild;

    private static final float ATTACHMENT_BOUNCE_THRESHOLD = 150f;
    private static final float GRAVITY = -1200f;
    private static final float RIGHT_WALL_X = 1850f;
    private static final float LEFT_WALL_X = 0f;
    private static final float CEILING_Y = 1100f;

    // Original constructor for parent attachments
    public AttachmentPhysics(float startX, float startY, float groundLevel, Bone bone,
                             Attachment attachment, String attachmentName) {
        this(startX, startY, groundLevel, bone, attachment, attachmentName, null);
    }

    // Enhanced constructor with parent support
    public AttachmentPhysics(float startX, float startY, float groundLevel, Bone bone,
                             Attachment attachment, String attachmentName, AttachmentPhysics parent) {
        this.x = startX;
        this.y = startY;
        this.groundY = groundLevel;
        this.rotation = bone.getWorldRotationX();
        this.attachment = attachment;
        this.attachmentName = attachmentName;
        this.attachmentId = "Attachment_" + attachmentName + "_" + System.currentTimeMillis() % 1000;
        this.originalBone = bone;
        this.originalScaleX = bone.getScaleX();
        this.originalScaleY = bone.getScaleY();
        this.parentAttachment = parent;
        this.isChild = (parent != null);

        if (parent != null) {
            parent.addChild(this);
            // Calculate initial relative position and rotation
            this.relativeX = startX - parent.x;
            this.relativeY = startY - parent.y;
            this.relativeRotation = this.rotation - parent.rotation;

            BaseMod.logger.info("[" + attachmentId + "] Created CHILD attachment '"
                    + attachmentName + "' linked to parent '" + parent.attachmentName
                    + "' with relative pos (" + String.format("%.1f", relativeX)
                    + ", " + String.format("%.1f", relativeY) + ") and rotation offset "
                    + String.format("%.1f", relativeRotation) + "°");
        } else {
            String attachmentType = attachment != null ? attachment.getClass().getSimpleName() : "Unknown";
            BaseMod.logger.info("[" + attachmentId + "] Created PARENT attachment '"
                    + attachmentName + "' (" + attachmentType + ") at ("
                    + String.format("%.1f", startX) + ", " + String.format("%.1f", startY)
                    + "), ground: " + groundLevel + ", bone scale: ("
                    + String.format("%.2f", originalScaleX) + ", "
                    + String.format("%.2f", originalScaleY) + ")");
        }
    }

    public void addChild(AttachmentPhysics child) {
        if (!childAttachments.contains(child)) {
            childAttachments.add(child);
            BaseMod.logger.info("[" + attachmentId + "] Added child '" + child.attachmentName
                    + "' (total children: " + childAttachments.size() + ")");
        }
    }

    public void update(float deltaTime) {
        if (isChild && parentAttachment != null) {
            // Child attachments follow their parent
            updateAsChild(deltaTime);
        } else {
            // Parent attachments have full physics
            updateAsParent(deltaTime);
            // Then update all children based on new parent position
            updateChildren(deltaTime);
        }
    }

    private void updateAsParent(float deltaTime) {
        // Apply gravity and update position
        if (enableZeroGravity) {
            velocityY += 0f * deltaTime;
        } else {
            velocityY += GRAVITY * deltaTime;
        }

        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
        rotation += angularVelocity * deltaTime;

        // Ground collision
        if (y < groundY && velocityY < 0) {
            y = groundY;
            if (Math.abs(velocityY) > ATTACHMENT_BOUNCE_THRESHOLD) {
                velocityY *= -0.4f;
                velocityX *= 0.85f;
                angularVelocity *= 0.6f;
            } else {
                velocityY = 0f;
                velocityX *= (float) Math.pow(0.95, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(0.7, deltaTime * 60f);
            }
        } else {
            angularVelocity *= (float) Math.pow(0.999, deltaTime * 60f);
        }

        // Ceiling collision
        if (y > CEILING_Y && velocityY > 0) {
            y = CEILING_Y;
            if (Math.abs(velocityY) > ATTACHMENT_BOUNCE_THRESHOLD) {
                velocityY *= -0.5f;
                velocityX *= 0.9f;
                angularVelocity = MathUtils.random(-450f, 450f);
            } else {
                velocityY = 0f;
                velocityX *= (float) Math.pow(0.95, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(0.8, deltaTime * 60f);
            }
        }

        // Wall collisions
        if (x > RIGHT_WALL_X && velocityX > 0) {
            x = RIGHT_WALL_X;
            velocityX *= -0.7f;
            angularVelocity = MathUtils.random(-360f, 360f);
        }
        if (x < LEFT_WALL_X && velocityX < 0) {
            x = LEFT_WALL_X;
            velocityX *= -0.7f;
            angularVelocity = MathUtils.random(-360f, 360f);
        }

        // Air resistance
        velocityX *= (float) Math.pow(0.999, deltaTime * 60f);
    }

    private void updateAsChild(float deltaTime) {
        // Child attachments maintain their relative position to parent
        if (parentAttachment == null) return;

        // Calculate position based on parent's current state using rotation matrix
        float parentRotRad = (float) Math.toRadians(parentAttachment.rotation);
        float cosRot = (float) Math.cos(parentRotRad);
        float sinRot = (float) Math.sin(parentRotRad);

        // Apply rotation matrix to relative position
        this.x = parentAttachment.x + (relativeX * cosRot - relativeY * sinRot);
        this.y = parentAttachment.y + (relativeX * sinRot + relativeY * cosRot);
        this.rotation = parentAttachment.rotation + relativeRotation;

        // Copy parent's velocity for realistic movement tracking
        this.velocityX = parentAttachment.velocityX;
        this.velocityY = parentAttachment.velocityY;
        this.angularVelocity = parentAttachment.angularVelocity;

        // Optional: Add small random wobble for more organic movement
        float wobbleIntensity = 0.05f; // Adjust as needed
        this.rotation += MathUtils.random(-wobbleIntensity, wobbleIntensity);
    }

    private void updateChildren(float deltaTime) {
        for (AttachmentPhysics child : childAttachments) {
            child.updateAsChild(deltaTime);
            // Recursively update grandchildren if any
            child.updateChildren(deltaTime);
        }
    }

    // Getter methods for debugging and external access
    public boolean isChild() { return isChild; }
    public boolean hasChildren() { return !childAttachments.isEmpty(); }
    public int getChildCount() { return childAttachments.size(); }
    public AttachmentPhysics getParent() { return parentAttachment; }
    public List<AttachmentPhysics> getChildren() { return new ArrayList<>(childAttachments); }
    public String getAttachmentName() { return attachmentName; }
}