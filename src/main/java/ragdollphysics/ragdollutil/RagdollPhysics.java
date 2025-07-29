package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;

/**
 * Core physics simulation for ragdoll bodies.
 * Handles position, velocity, rotation, and ground/wall collisions.
 */
public class RagdollPhysics {
    public float x, y, velocityX, velocityY, rotation, angularVelocity;
    private final float groundY;
    private final String physicsId;
    private int updateCount = 0;

    private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;
    private static final float GRAVITY = -1200f;

    public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel) {
        this.x = startX;
        this.y = startY;
        this.velocityX = forceX;
        this.velocityY = forceY;
        this.groundY = groundLevel;
        this.rotation = 0f;
        this.angularVelocity = MathUtils.random(-144f, 144f); // Reduced from -720 to -144
        this.physicsId = "Physics_" + System.currentTimeMillis() % 10000;

        BaseMod.logger.info("[" + physicsId + "] Created at ("
                + String.format("%.1f", startX) + ", " + String.format("%.1f", startY)
                + "), ground: " + groundLevel + ", initial force: ("
                + String.format("%.1f", forceX) + ", " + String.format("%.1f", forceY)
                + "), angVel: " + String.format("%.1f", angularVelocity));
    }

    public void update(float deltaTime, MultiBodyRagdoll parent) {
        updateCount++;
        deltaTime = Math.min(deltaTime, 1.0f / 30.0f);

        // Apply gravity and update position
        velocityY += GRAVITY * deltaTime;
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
        rotation += angularVelocity * deltaTime;

        // Ground collision handling
        boolean groundEvent = false;
        if (y <= groundY && velocityY <= 0) {
            y = groundY;
            groundEvent = true;

            if (Math.abs(velocityY) > SIMPLE_BOUNCE_THRESHOLD) {
                float oldVelY = velocityY;
                velocityY = Math.abs(velocityY) * 0.4f;
                velocityX *= 0.8f;
                angularVelocity *= 0.6f;

                // Ground bounce is significant, always log
                BaseMod.logger.info("[" + physicsId + "] MAIN BODY BOUNCE - impact: "
                        + String.format("%.1f", oldVelY)
                        + " -> bounce: " + String.format("%.1f", velocityY));
            } else {
                float oldVelY = velocityY;
                velocityY = 0f;
                velocityX *= 0.92f;
                angularVelocity *= 0.85f;

                // Only log significant settling
                if (Math.abs(oldVelY) > 30f || updateCount % 120 == 0) {
                    if (parent.canLog()) {
                        BaseMod.logger.info("[" + physicsId + "] Ground settle - impact: "
                                + String.format("%.1f", oldVelY) + " -> 0, groundDist: "
                                + String.format("%.2f", y - groundY));
                        parent.lastLogTime = System.currentTimeMillis();
                    }
                }
            }
        } else {
            angularVelocity *= 0.999f;
        }

        // Air resistance
        velocityX *= 0.999f;

        // Only log regular updates occasionally
        if (updateCount % 180 == 0 && parent.canLog()) {
            BaseMod.logger.info("[" + physicsId + "] Regular update " + updateCount
                    + " - pos: (" + String.format("%.1f", x) + ", "
                    + String.format("%.1f", y) + "), vel: ("
                    + String.format("%.1f", velocityX) + ", "
                    + String.format("%.1f", velocityY)
                    + "), rot: " + String.format("%.1f", rotation)
                    + ", angVel: " + String.format("%.1f", angularVelocity)
                    + ", groundDist: " + String.format("%.2f", y - groundY)
                    + ", airborne: " + (y > groundY + 1f));
            parent.lastLogTime = System.currentTimeMillis();
        }
    }
}