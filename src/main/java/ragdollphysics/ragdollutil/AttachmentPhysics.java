package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.attachments.Attachment;

import static ragdollphysics.RagdollPhysics.enableZeroGravity;

/**
 * Physics simulation for detached monster attachments (weapons, armor pieces, etc.).
 * Handles independent physics movement for attachments that break away from bones.
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
    private static final float ATTACHMENT_BOUNCE_THRESHOLD = 150f;
    private static final float GRAVITY = -1200f; // default is -1200f
    private static final float RIGHT_WALL_X = 1850f;
    private static final float LEFT_WALL_X = 0f;
    private static final float CEILING_Y = 1100f;

    public AttachmentPhysics(float startX, float startY, float groundLevel, Bone bone,
                             Attachment attachment, String attachmentName) {
        this.x = startX;
        this.y = startY;
        this.groundY = groundLevel;
        this.rotation = bone.getRotation();
        this.attachment = attachment;
        this.attachmentName = attachmentName;
        this.attachmentId = "Attachment_" + attachmentName + "_" + System.currentTimeMillis() % 1000;
        this.originalBone = bone;
        this.originalScaleX = bone.getScaleX();
        this.originalScaleY = bone.getScaleY();
        String attachmentType = attachment != null ? attachment.getClass().getSimpleName() : "Unknown";
        BaseMod.logger.info("[" + attachmentId + "] Created attachment '"
                + attachmentName + "' (" + attachmentType + ") at ("
                + String.format("%.1f", startX) + ", " + String.format("%.1f", startY)
                + "), ground: " + groundLevel + ", bone scale: ("
                + String.format("%.2f", originalScaleX) + ", "
                + String.format("%.2f", originalScaleY) + ")");
    }

    public void update(float deltaTime) {
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
                // Frame-rate independent ground damping
                velocityX *= (float) Math.pow(0.95, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(0.7, deltaTime * 60f);
            }
        } else {
            // Frame-rate independent air angular damping
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
                // Frame-rate independent ceiling damping
                velocityX *= (float) Math.pow(0.95, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(0.8, deltaTime * 60f);
            }
        }

        // Wall collisions (instant response - no damping needed)
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

        // Frame-rate independent air resistance
        velocityX *= (float) Math.pow(0.999, deltaTime * 60f);
    }
}