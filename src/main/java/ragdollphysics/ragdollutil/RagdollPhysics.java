package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.city.BronzeOrb;

import java.util.HashSet;
import java.util.Set;

import static ragdollphysics.RagdollPhysics.enableZeroGravity;

/**
 * Core physics simulation for ragdoll bodies.
 * Handles position, velocity, rotation, and ground/wall collisions.
 */
public class RagdollPhysics {

    // ================================
    // PHYSICS STATE VARIABLES
    // ================================

    public float x, y;
    public float velocityX, velocityY;
    public float rotation, angularVelocity;

    // Rotation tracking for limiting system
    public float totalRotationDegrees = 0f;
    public float lastRotation = 0f;

    private final float groundY;
    private final String physicsId;
    private final boolean hasZeroGravity;
    private int updateCount = 0;


    // ================================
    // PHYSICS CONSTANTS
    // ================================

    private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;
    private static final float GRAVITY = -1200f * Settings.scale;
    private static final float RIGHT_WALL_X = 1850f * Settings.scale;
    private static final float LEFT_WALL_X = 50f * Settings.scale;
    private static final float CEILING_Y = 1100f * Settings.scale;


    // ================================
    // ZERO GRAVITY CONFIGURATION
    // ================================

    /** Enemies that ignore gravity regardless of global settings */
    private static final Set<String> ZERO_GRAVITY_ENEMIES = new HashSet<>();

    static {
        ZERO_GRAVITY_ENEMIES.add(BronzeOrb.ID);
    }


    // ================================
    // CONSTRUCTORS
    // ================================

    /** Basic constructor without monster-specific gravity handling */
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
        this.hasZeroGravity = false; // Default constructor doesn't know about monster type
    }

    /** Constructor with monster instance for gravity determination */
    public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel, AbstractMonster monster) {
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
        this.hasZeroGravity = ZERO_GRAVITY_ENEMIES.contains(monster.id);
    }

    /** Constructor with monster class name for gravity determination */
    public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel, String monsterClassName) {
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
        this.hasZeroGravity = ZERO_GRAVITY_ENEMIES.contains(monsterClassName);
    }


    // ================================
    // MAIN PHYSICS UPDATE
    // ================================

    /** Main physics update - called each frame to simulate movement and collisions */
    public void update(float deltaTime, MultiBodyRagdoll parent) {
        updateCount++;

        // Apply gravity (unless disabled globally or for this specific enemy)
        applyGravity(deltaTime);

        // Update position based on velocity
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
        rotation += angularVelocity * deltaTime;

        // Enhanced rotation while airborne
        applyAirborneRotationBoost(deltaTime);

        // Handle boundary collisions
        handleBoundaryCollisions();

        // Handle ground interaction
        handleGroundCollision(deltaTime);

        // Apply air resistance and damping
        applyDamping(deltaTime);

        // Limit excessive rotation when settling
        applyRotationLimiting(deltaTime);
    }


    // ================================
    // GRAVITY AND MOVEMENT
    // ================================

    /** Apply gravity based on global settings and enemy-specific configuration */
    private void applyGravity(float deltaTime) {
        if (enableZeroGravity || hasZeroGravity) {
            velocityY += 0f * deltaTime;
        } else {
            velocityY += GRAVITY * deltaTime;
        }
    }

    /** Enhance rotation while airborne for more dynamic movement */
    private void applyAirborneRotationBoost(float deltaTime) {
        float preUpdateVelocityY = velocityY;
        boolean hasContactedGround = y <= groundY + 5f;

        // Only apply airborne rotation enhancement while actually airborne
        if (!hasContactedGround && preUpdateVelocityY > 200f) {
            float airborneIntensity = Math.min(preUpdateVelocityY / 600f, 1.0f);
            float airborneBoost = 1.0f + (airborneIntensity * 0.02f * deltaTime * 60f);
            angularVelocity *= airborneBoost;
        }
    }


    // ================================
    // COLLISION HANDLING
    // ================================

    /** Handle collisions with walls and ceiling */
    private void handleBoundaryCollisions() {
        // Wall collisions
        if (x > RIGHT_WALL_X && velocityX > 0) {
            handleWallCollision(RIGHT_WALL_X, -0.4f);
        }
        if (x < LEFT_WALL_X && velocityX < 0) {
            handleWallCollision(LEFT_WALL_X, -0.4f);
        }

        // Ceiling collision
        if (y > CEILING_Y && velocityY > 0) {
            handleCeilingCollision();
        }
    }

    /** Handle collision with a wall */
    private void handleWallCollision(float wallX, float bounceMultiplier) {
        x = wallX;
        velocityX *= bounceMultiplier;
        velocityY *= 0.7f;

        // Add rotational effect from wall impact
        float wallImpactIntensity = Math.abs(velocityX) / 800f;
        angularVelocity += MathUtils.random(-90f, 90f) * (1.0f + wallImpactIntensity * 0.3f);
    }

    /** Handle collision with ceiling */
    private void handleCeilingCollision() {
        y = CEILING_Y;
        velocityY *= -0.6f; // Bounce downward with energy loss

        // Add rotational effect from ceiling impact
        float ceilingImpactIntensity = Math.abs(velocityY) / 600f;
        angularVelocity += MathUtils.random(-120f, 120f) * (1.0f + ceilingImpactIntensity * 0.4f);
    }

    /** Handle ground collision with bouncing and settling behavior */
    private void handleGroundCollision(float deltaTime) {
        if (y <= groundY && velocityY <= 0) {
            y = groundY;

            if (Math.abs(velocityY) > SIMPLE_BOUNCE_THRESHOLD) {
                // High-energy bounce
                velocityY = Math.abs(velocityY) * 0.4f;
                velocityX *= 0.8f;
                angularVelocity *= 0.6f;
            } else {
                // Low-energy settle with ground friction
                velocityY = 0f;
                velocityX *= (float) Math.pow(0.92, deltaTime * 60f);
                angularVelocity *= (float) Math.pow(0.85, deltaTime * 60f);
            }
        }
    }


    // ================================
    // DAMPING AND ROTATION LIMITING
    // ================================

    /** Apply air resistance and general damping effects */
    private void applyDamping(float deltaTime) {
        // Frame-rate independent air resistance
        velocityX *= (float) Math.pow(0.999, deltaTime * 60f);

        // Air damping for angular velocity (only when airborne)
        if (y > groundY) {
            angularVelocity *= (float) Math.pow(0.999, deltaTime * 60f);
        }
    }

    /** Limit excessive rotation when ragdoll is settling on ground */
    private void applyRotationLimiting(float deltaTime) {
        float rotationDelta = rotation - lastRotation;

        // Normalize rotation delta to [-180, 180] range
        if (rotationDelta > 180f)
            rotationDelta -= 360f;
        else if (rotationDelta < -180f)
            rotationDelta += 360f;

        boolean isActuallyOnGround = y <= groundY + 1f;
        boolean hasVeryLowMomentum = Math.abs(velocityX) + Math.abs(velocityY) < 150f;
        boolean isSettling = isActuallyOnGround && hasVeryLowMomentum;

        if (isSettling) {
            // Apply rotation limiting when settling
            totalRotationDegrees += Math.abs(rotationDelta);
            float flipsCompleted = totalRotationDegrees / 360f;

            // Time-based damping based on completed flips
            float dampingStrength = 0.02f * flipsCompleted * deltaTime * 60f;
            angularVelocity *= (1.0f - Math.min(dampingStrength, 0.3f));
        } else {
            // Reset rotation tracking when airborne or high velocity
            if (!isActuallyOnGround || Math.abs(velocityY) > 100f) {
                totalRotationDegrees = 0f;
            }

            // Frame-rate independent general angular damping
            angularVelocity *= (float) Math.pow(0.9995, deltaTime * 60f);
        }

        lastRotation = rotation;
    }


    // ================================
    // PUBLIC UTILITY METHODS
    // ================================

    /** Check if the ragdoll has settled and stopped moving */
    public boolean hasSettledOnGround() {
        float totalMomentum = Math.abs(velocityX) + Math.abs(velocityY) + Math.abs(angularVelocity) / 10f;
        boolean isLowMomentum = totalMomentum < 25f;
        boolean isNearGround = y <= groundY + 10f;
        return isLowMomentum && isNearGround;
    }
}