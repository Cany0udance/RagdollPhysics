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

    // Physics constants
    private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;
    private static final float GRAVITY = -1200f;
    private static final float RIGHT_WALL_X = 1850f;
    private static final float LEFT_WALL_X = 0f;
    private static final float CEILING_Y = 1100f;

    // Rotation tracking for limiting
    public float totalRotationDegrees = 0f;
    public float lastRotation = 0f;

    public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel) {
        this.x = startX;
        this.y = startY;
        this.velocityX = forceX;
        this.velocityY = forceY;
        this.groundY = groundLevel;
        this.rotation = 0f;
        this.angularVelocity = MathUtils.random(-144f, 144f);
        this.physicsId = "Physics_" + System.currentTimeMillis() % 10000;
        this.lastRotation = 0f;
        this.totalRotationDegrees = 0f;
    }

    public void update(float deltaTime, MultiBodyRagdoll parent) {
        updateCount++;
        deltaTime = Math.min(deltaTime, 1.0f / 30.0f);

        // Apply gravity and update position
        velocityY += GRAVITY * deltaTime;
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
        rotation += angularVelocity * deltaTime;

        // Enhanced airborne rotation
        float preUpdateVelocityY = velocityY;
        if (y > groundY + 50f && preUpdateVelocityY > 200f) {
            float airborneIntensity = Math.min(preUpdateVelocityY / 600f, 1.0f);
            angularVelocity *= (1.0f + airborneIntensity * 0.02f);
        }

        // Wall collision handling
        if (x > RIGHT_WALL_X && velocityX > 0) {
            handleWallCollision(RIGHT_WALL_X, -0.4f);
        }
        if (x < LEFT_WALL_X && velocityX < 0) {
            handleWallCollision(LEFT_WALL_X, -0.4f);
        }

        // Ceiling collision handling
        if (y > CEILING_Y && velocityY > 0) {
            handleCeilingCollision();
        }

        // Ground collision handling
        if (y <= groundY && velocityY <= 0) {
            y = groundY;
            if (Math.abs(velocityY) > SIMPLE_BOUNCE_THRESHOLD) {
                velocityY = Math.abs(velocityY) * 0.4f;
                velocityX *= 0.8f;
                angularVelocity *= 0.6f;
            } else {
                velocityY = 0f;
                velocityX *= 0.92f;
                angularVelocity *= 0.85f;
            }
        } else {
            angularVelocity *= 0.999f;
        }

        // Air resistance
        velocityX *= 0.999f;

        // Apply rotation limiting
        applyRotationLimiting(deltaTime);
    }

    private void handleWallCollision(float wallX, float bounceMultiplier) {
        x = wallX;
        velocityX *= bounceMultiplier;
        velocityY *= 0.7f;

        // Add rotational effect from wall impact
        float wallImpactIntensity = Math.abs(velocityX) / 800f;
        angularVelocity += MathUtils.random(-90f, 90f) * (1.0f + wallImpactIntensity * 0.3f);
    }

    private void handleCeilingCollision() {
        // Position at ceiling and reverse vertical velocity
        y = CEILING_Y;
        velocityY *= -0.6f; // Bounce downward with some energy loss

        // Add rotational effect from ceiling impact
        float ceilingImpactIntensity = Math.abs(velocityY) / 600f;
        angularVelocity += MathUtils.random(-120f, 120f) * (1.0f + ceilingImpactIntensity * 0.4f);
    }

    private void applyRotationLimiting(float deltaTime) {
        float rotationDelta = rotation - lastRotation;
        if (rotationDelta > 180f)
            rotationDelta -= 360f;
        else if (rotationDelta < -180f)
            rotationDelta += 360f;

        boolean isActuallyOnGround = y <= groundY + 1f;
        boolean hasVeryLowMomentum = Math.abs(velocityX) + Math.abs(velocityY) < 150f;
        boolean isSettling = isActuallyOnGround && hasVeryLowMomentum;

        if (isSettling) {
            totalRotationDegrees += Math.abs(rotationDelta);
            float flipsCompleted = totalRotationDegrees / 360f;
            float dampingFactor = (float) Math.pow(0.98, flipsCompleted);
            angularVelocity *= dampingFactor;
        } else {
            if (!isActuallyOnGround && Math.abs(velocityY) > 100f) {
                totalRotationDegrees = 0f;
            }
            angularVelocity *= 0.9995f;
        }

        lastRotation = rotation;
    }

    public boolean hasSettledOnGround() {
        float totalMomentum = Math.abs(velocityX) + Math.abs(velocityY) + Math.abs(angularVelocity) / 10f;
        boolean isLowMomentum = totalMomentum < 25f;
        boolean isNearGround = y <= groundY + 10f;
        return isLowMomentum && isNearGround;
    }
}