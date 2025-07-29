package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.exordium.Sentry;
import ragdollphysics.ragdollutil.AttachmentPhysics;
import ragdollphysics.ragdollutil.BoneWobble;

import java.util.HashMap;
import java.util.Map;

/**
 * Main ragdoll physics system that coordinates multiple physics bodies.
 * Handles skeleton positioning, bone wobbles, and detached attachments.
 */
public class MultiBodyRagdoll {
    public final HashMap<Bone, BoneWobble> boneWobbles;
    private final HashMap<String, AttachmentPhysics> attachmentBodies;
    public final RagdollPhysics mainBody;
    private final float groundY;
    private final AbstractMonster associatedMonster;

    // Fields for shadow fading
    private final Slot shadowSlot;
    private final float initialShadowAlpha;
    private float shadowFadeTimer = 0f;
    private static final float SHADOW_FADE_DURATION = 0.5f;

    private final float initialOffsetX;
    private final float initialOffsetY;

    private static final float GRAVITY = -1200f;
    private static final float RIGHT_WALL_X = 1850f;
    private static final float LEFT_WALL_X = -200f;
    private static final float SETTLED_THRESHOLD = 0.5f;

    private float settledTimer = 0f;
    public float totalRotationDegrees = 0f;
    public float lastRotation = 0f;

    private static final float FIXED_TIMESTEP = 1.0f / 60.0f;
    private float accumulator = 0f;
    private final String monsterClassName;

    // Enhanced logging control
    private final long creationTime;
    public long lastLogTime = 0;
    private int updateCount = 0;
    private int physicsStepCount = 0;
    private final String ragdollId;

    // Flag to indicate if this is an image-based ragdoll
    private final boolean isImageBased;

    // Original constructor for skeleton-based ragdolls
    public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY,
                            String monsterClassName, AbstractMonster monster) {
        this.boneWobbles = new HashMap<>();
        this.monsterClassName = monsterClassName;
        this.associatedMonster = monster;
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.mainBody = new RagdollPhysics(startX, startY, 0, 0, groundLevel);
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "Ragdoll_" + System.currentTimeMillis() % 10000;
        this.isImageBased = false;

        Slot shadowSlot = skeleton.findSlot("shadow");
        Bone shadowBone = skeleton.findBone("shadow");

        if (shadowSlot == null && shadowBone != null) {
            for (Slot slot : skeleton.getSlots()) {
                if (slot.getBone() == shadowBone) {
                    shadowSlot = slot;
                    break;
                }
            }
        }

        if (shadowSlot == null) {
            for (Slot slot : skeleton.getSlots()) {
                if (slot.getAttachment() != null) {
                    String attachmentName = slot.getAttachment().getName().toLowerCase();
                    if (attachmentName.contains("shadow")) {
                        shadowSlot = slot;
                        break;
                    }
                }
            }
        }

        this.shadowSlot = shadowSlot;
        if (this.shadowSlot != null) {
            this.initialShadowAlpha = this.shadowSlot.getColor().a;
        } else {
            this.initialShadowAlpha = 0f;
        }

        BaseMod.logger.info("[" + ragdollId + "] Creating ragdoll for "
                + monsterClassName + " at (" + startX + ", " + startY
                + "), ground: " + groundLevel);
        BaseMod.logger.info("[" + ragdollId + "] Skeleton has "
                + skeleton.getBones().size + " bones");

        int attachmentCount = 0;

        // Handle any attachment type
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();
                Bone bone = slot.getBone();

                BaseMod.logger.info("DEBUG: Found attachment '" + attachmentName
                        + "' on bone '" + bone.getData().getName() + "'");

                // Check if this attachment should be detached for this monster
                boolean shouldDetach = AttachmentConfig.shouldDetachAttachment(monsterClassName, attachmentName);
                BaseMod.logger.info("DEBUG: Attachment '" + attachmentName + "' should detach: " + shouldDetach);

                if (AttachmentConfig.shouldDetachAttachment(monsterClassName, attachmentName)) {
                    // Create AttachmentPhysics for ANY attachment type
                    attachmentBodies.put(attachmentName,
                            new AttachmentPhysics(
                                    startX + bone.getWorldX() * Settings.scale,
                                    startY + bone.getWorldY() * Settings.scale, groundLevel,
                                    bone, slot.getAttachment(), attachmentName));

                    attachmentCount++;
                    String attachmentType = slot.getAttachment().getClass().getSimpleName();
                    BaseMod.logger.info("[" + ragdollId + "] Found detachable "
                            + attachmentType + ": '" + attachmentName
                            + "' on bone: " + bone.getData().getName());
                }
            }
        }

        BaseMod.logger.info("[" + ragdollId + "] Created " + attachmentCount + " attachment physics bodies");

        // Initialize bone wobbles for remaining bones
        for (Bone bone : skeleton.getBones()) {
            boneWobbles.put(bone, new BoneWobble(bone.getRotation(), bone));
        }
    }

    // Constructor for image-based ragdolls (like Hexaghost)
    public MultiBodyRagdoll(float startX, float startY, float groundLevel,
                            String monsterClassName, AbstractMonster monster) {
        this.boneWobbles = new HashMap<>(); // Empty for image ragdolls
        this.monsterClassName = monsterClassName;
        this.associatedMonster = monster;
        this.attachmentBodies = new HashMap<>(); // Empty for image ragdolls
        this.groundY = groundLevel;
        this.mainBody = new RagdollPhysics(startX, startY, 0, 0, groundLevel);
        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "ImageRagdoll_" + System.currentTimeMillis() % 10000;
        this.isImageBased = true;

        // Store the offset between monster draw position and physics body start position
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

        // No shadow handling for image-based ragdolls
        this.shadowSlot = null;
        this.initialShadowAlpha = 0f;

        BaseMod.logger.info("[" + ragdollId + "] Created image ragdoll for "
                + monsterClassName + " at (" + startX + ", " + startY
                + "), ground: " + groundLevel);
    }

    public boolean isProperlyInitialized() {
        return mainBody != null && (isImageBased || (!boneWobbles.isEmpty()));
    }

    public boolean canLog() {
        long currentTime = System.currentTimeMillis();
        long timeSinceCreation = currentTime - creationTime;

        // Enhanced logging during critical periods
        if (timeSinceCreation < 200) { // Extended initial logging
            return true;
        }

        // Log major state changes
        if (updateCount % 60 == 0) { // Every second at 60fps
            return true;
        }

        // Log when physics events occur
        return (currentTime - lastLogTime) >= 100 || MathUtils.random() < 0.03f;
    }

    public void update(float deltaTime) {
        updateCount++;

        // Update the shadow fade timer
        if (shadowSlot != null && shadowFadeTimer < SHADOW_FADE_DURATION) {
            shadowFadeTimer += deltaTime;
        }

        if (canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] === UPDATE " + updateCount + " ===");
            BaseMod.logger.info("[" + ragdollId + "] DeltaTime: " + deltaTime + ", Accumulator: " + accumulator);
            BaseMod.logger.info("[" + ragdollId + "] MainBody: pos("
                    + String.format("%.1f", mainBody.x) + ", "
                    + String.format("%.1f", mainBody.y) + "), vel("
                    + String.format("%.1f", mainBody.velocityX) + ", "
                    + String.format("%.1f", mainBody.velocityY)
                    + "), rot: " + String.format("%.1f", mainBody.rotation)
                    + ", angVel: " + String.format("%.1f", mainBody.angularVelocity));
            lastLogTime = System.currentTimeMillis();
        }

        accumulator += Math.min(deltaTime, 0.1f);
        int steps = 0;
        while (accumulator >= FIXED_TIMESTEP) {
            steps++;
            physicsStepCount++;
            updatePhysics(FIXED_TIMESTEP);
            accumulator -= FIXED_TIMESTEP;
            if (steps > 10) { // Prevent infinite loops
                BaseMod.logger.warn("[" + ragdollId + "] Breaking physics loop after "
                        + steps + " steps");
                accumulator = 0;
                break;
            }
        }

        if (steps > 0 && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Executed " + steps
                    + " physics steps (total: " + physicsStepCount + ")");
        }
    }

    private void updatePhysics(float deltaTime) {
        if (physicsStepCount % 120 == 0 && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Physics step "
                    + physicsStepCount + " - Ground distance: "
                    + String.format("%.2f", mainBody.y - groundY));
        }

        mainBody.update(deltaTime, this);

        // Enhanced airborne rotation logging
        float preUpdateVelocityY = mainBody.velocityY;
        if (mainBody.y > groundY + 50f && preUpdateVelocityY > 200f) {
            float airborneIntensity = Math.min(preUpdateVelocityY / 600f, 1.0f);
            float oldAngularVel = mainBody.angularVelocity;
            mainBody.angularVelocity *= (1.0f + airborneIntensity * 0.02f);

            if (canLog()) {
                BaseMod.logger.info("[" + ragdollId
                        + "] Airborne spin boost - intensity: "
                        + String.format("%.2f", airborneIntensity)
                        + ", angVel: " + String.format("%.1f", oldAngularVel) + " -> "
                        + String.format("%.1f", mainBody.angularVelocity));
                lastLogTime = System.currentTimeMillis();
            }
        }

        // Wall collision handling
        if (mainBody.x > RIGHT_WALL_X && mainBody.velocityX > 0) {
            BaseMod.logger.info("[" + ragdollId + "] RIGHT WALL COLLISION at x="
                    + mainBody.x + ", vel=" + mainBody.velocityX);
            handleWallCollision(RIGHT_WALL_X, -0.4f);
        }
        if (mainBody.x < LEFT_WALL_X && mainBody.velocityX < 0) {
            BaseMod.logger.info("[" + ragdollId + "] LEFT WALL COLLISION at x="
                    + mainBody.x + ", vel=" + mainBody.velocityX);
            handleWallCollision(LEFT_WALL_X, -0.4f);
        }

        // Update attachments
        int activeAttachments = 0;
        for (AttachmentPhysics attachment : attachmentBodies.values()) {
            attachment.update(deltaTime);
            if (Math.abs(attachment.velocityX) + Math.abs(attachment.velocityY) > 10f) {
                activeAttachments++;
            }
        }

        if (activeAttachments > 0 && physicsStepCount % 60 == 0 && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] " + activeAttachments + " attachments still moving");
            lastLogTime = System.currentTimeMillis();
        }

        boolean parentHasSettled = hasSettledOnGround();

        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.update(deltaTime, mainBody.velocityX, mainBody.velocityY,
                    parentHasSettled, this);
        }

        applyRotationLimiting(deltaTime);
    }

    private void handleWallCollision(float wallX, float bounceMultiplier) {
        BaseMod.logger.info("[" + ragdollId + "] WALL COLLISION at x=" + wallX
                + ", incoming vel=" + String.format("%.1f", mainBody.velocityX));

        float oldVelX = mainBody.velocityX;
        float oldVelY = mainBody.velocityY;
        float oldAngVel = mainBody.angularVelocity;

        mainBody.x = wallX;
        mainBody.velocityX *= bounceMultiplier;
        mainBody.velocityY *= 0.7f;

        // Greatly reduce wall impact angular velocity
        float wallImpactIntensity = Math.abs(mainBody.velocityX) / 800f;
        mainBody.angularVelocity += MathUtils.random(-90f, 90f) * (1.0f + wallImpactIntensity * 0.3f);

        if (canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Wall collision impact - intensity: "
                    + String.format("%.2f", wallImpactIntensity) + ", vel: ("
                    + String.format("%.1f", oldVelX) + ", "
                    + String.format("%.1f", oldVelY) + ") -> ("
                    + String.format("%.1f", mainBody.velocityX) + ", "
                    + String.format("%.1f", mainBody.velocityY)
                    + "), angVel: " + String.format("%.1f", oldAngVel) + " -> "
                    + String.format("%.1f", mainBody.angularVelocity));
            lastLogTime = System.currentTimeMillis();
        }

        // Reduce attachment wobble impact from wall collisions
        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.angularVelocity += MathUtils.random(-45f, 45f) * (1.0f + wallImpactIntensity * 0.2f);
        }
    }

    private void applyRotationLimiting(float deltaTime) {
        float rotationDelta = mainBody.rotation - lastRotation;
        if (rotationDelta > 180f)
            rotationDelta -= 360f;
        else if (rotationDelta < -180f)
            rotationDelta += 360f;

        boolean isActuallyOnGround = mainBody.y <= groundY + 1f;
        boolean hasVeryLowMomentum = Math.abs(mainBody.velocityX) + Math.abs(mainBody.velocityY) < 150f;
        boolean isSettling = isActuallyOnGround && hasVeryLowMomentum;

        if (isSettling) {
            float oldTotalRotation = totalRotationDegrees;
            totalRotationDegrees += Math.abs(rotationDelta);

            float flipsCompleted = totalRotationDegrees / 360f;
            float oldAngVel = mainBody.angularVelocity;
            float dampingFactor = (float) Math.pow(0.98, flipsCompleted);
            mainBody.angularVelocity *= dampingFactor;

            if (canLog() || Math.abs(rotationDelta) > 10f) {
                BaseMod.logger.info("[" + ragdollId + "] Ground rotation limiting - delta: "
                        + String.format("%.1f", rotationDelta)
                        + "°, total: " + String.format("%.1f", oldTotalRotation)
                        + " -> " + String.format("%.1f", totalRotationDegrees)
                        + "°, flips: " + String.format("%.2f", flipsCompleted)
                        + ", damping: " + String.format("%.3f", dampingFactor)
                        + ", angVel: " + String.format("%.1f", oldAngVel) + " -> "
                        + String.format("%.1f", mainBody.angularVelocity) + ", momentum: "
                        + String.format("%.1f",
                        Math.abs(mainBody.velocityX) + Math.abs(mainBody.velocityY)));
                lastLogTime = System.currentTimeMillis();
            }
        } else {
            if (!isActuallyOnGround && Math.abs(mainBody.velocityY) > 100f) {
                if (totalRotationDegrees > 0) {
                    BaseMod.logger.info("[" + ragdollId
                            + "] Resetting flip counter - airborne with velocityY: "
                            + String.format("%.1f", mainBody.velocityY) + ", was at "
                            + String.format("%.1f", totalRotationDegrees)
                            + "° total rotation");
                }
                totalRotationDegrees = 0f;
            }
            mainBody.angularVelocity *= 0.9995f;
        }

        lastRotation = mainBody.rotation;
    }

    public boolean hasSettledOnGround() {
        float totalMomentum = Math.abs(mainBody.velocityX)
                + Math.abs(mainBody.velocityY)
                + Math.abs(mainBody.angularVelocity) / 10f;
        boolean isLowMomentum = totalMomentum < 25f;
        boolean isNearGround = mainBody.y <= groundY + 10f;

        if (isLowMomentum && isNearGround) {
            float oldTimer = settledTimer;
            settledTimer += Gdx.graphics.getDeltaTime();
            boolean isSettled = settledTimer >= SETTLED_THRESHOLD;

            if (oldTimer < SETTLED_THRESHOLD && settledTimer >= SETTLED_THRESHOLD) {
                BaseMod.logger.info("[" + ragdollId + "] RAGDOLL SETTLED - momentum: "
                        + String.format("%.2f", totalMomentum) + ", ground dist: "
                        + String.format("%.2f", mainBody.y - groundY) + ", velocityY: "
                        + String.format("%.2f", mainBody.velocityY) + ", angularVel: "
                        + String.format("%.2f", mainBody.angularVelocity));
            }

            if (canLog() && settledTimer > 0.1f) {
                BaseMod.logger.info("[" + ragdollId + "] Settling progress: "
                        + String.format("%.2f", settledTimer) + "s/" + SETTLED_THRESHOLD
                        + "s, momentum: " + String.format("%.2f", totalMomentum));
                lastLogTime = System.currentTimeMillis();
            }

            return isSettled;
        } else {
            if (settledTimer > 0) {
                BaseMod.logger.info("[" + ragdollId + "] Settlement interrupted - momentum: "
                        + String.format("%.2f", totalMomentum)
                        + ", nearGround: " + isNearGround + ", groundDist: "
                        + String.format("%.2f", mainBody.y - groundY));
            }
            settledTimer = 0f;
            return false;
        }
    }

    public void applyGlobalForce(float forceX, float forceY) {
        BaseMod.logger.info("[" + ragdollId + "] APPLYING GLOBAL FORCE: ("
                + forceX + ", " + forceY + ")");

        if (canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Before force - vel: ("
                    + String.format("%.1f", mainBody.velocityX) + ", "
                    + String.format("%.1f", mainBody.velocityY) + "), angVel: "
                    + String.format("%.1f", mainBody.angularVelocity));
            lastLogTime = System.currentTimeMillis();
        }

        // GET PHYSICS MODIFIERS - This is the key addition
        PhysicsModifier.VelocityModifiers modifiers = PhysicsModifier.calculateModifiers(associatedMonster);

        // Apply modified forces to main body
        mainBody.velocityX += forceX * 0.8f * modifiers.horizontalMultiplier;
        mainBody.velocityY += forceY * 0.8f * modifiers.verticalMultiplier;
        lastRotation = mainBody.rotation;

        // Calculate angular velocity with modifiers
        float upwardVelocity = Math.max(0, mainBody.velocityY);
        float flipIntensity = Math.min(upwardVelocity / 1200f, 0.5f);
        float baseAngularVel = MathUtils.random(-72f, 72f);
        mainBody.angularVelocity += baseAngularVel * (1.0f + flipIntensity * 0.3f) * modifiers.angularMultiplier;

        if (canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] After modified force - vel: ("
                    + String.format("%.1f", mainBody.velocityX) + ", "
                    + String.format("%.1f", mainBody.velocityY)
                    + "), angVel: " + String.format("%.1f", mainBody.angularVelocity)
                    + ", modifiers: " + modifiers);
            lastLogTime = System.currentTimeMillis();
        }

        // Apply modifiers to attachments
        int attachmentsAffected = 0;
        for (AttachmentPhysics attachment : attachmentBodies.values()) {
            // Apply horizontal force with modifiers
            attachment.velocityX += forceX * MathUtils.random(0.5f, 1.2f) * modifiers.horizontalMultiplier;
            attachment.velocityY += forceY * MathUtils.random(0.4f, 1.0f) * modifiers.verticalMultiplier;

            // Apply angular velocity with modifiers
            float attachmentBaseAngular = MathUtils.random(-360f, 360f);
            attachment.angularVelocity += attachmentBaseAngular * (1.0f + flipIntensity * 0.5f) * modifiers.angularMultiplier;

            // Random variation (also affected by horizontal modifier for X component)
            attachment.velocityX += MathUtils.random(-75f, 75f) * modifiers.horizontalMultiplier;
            attachment.velocityY += MathUtils.random(-50f, 100f) * modifiers.verticalMultiplier;

            attachmentsAffected++;
        }

        // Apply modifiers to bone wobbles
        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.angularVelocity += MathUtils.random(-90f, 90f) * (1.0f + flipIntensity * 0.5f) * modifiers.angularMultiplier;
        }

        if (canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Modified global force complete - affected " + attachmentsAffected
                    + " attachments and " + boneWobbles.size() + " bone wobbles");
            lastLogTime = System.currentTimeMillis();
        }
    }

    // UPDATED applyToBones METHOD - Hide attachments that have physics bodies
    public void applyToBones(Skeleton skeleton, AbstractMonster monster) {
        // Calculate visual center of the skeleton instead of using root position
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

        // Find bounds of all visible bones (excluding detached attachments)
        for (Bone bone : skeleton.getBones()) {
            // Don't include bones that have detached attachments in the bounds calculation
            boolean hasDetachedAttachment = false;
            for (Slot slot : skeleton.getSlots()) {
                if (slot.getBone() == bone && slot.getAttachment() != null) {
                    String attachmentName = slot.getAttachment().getName();
                    if (attachmentBodies.containsKey(attachmentName)) {
                        hasDetachedAttachment = true;
                        break;
                    }
                }
            }

            if (!hasDetachedAttachment) {
                float boneX = bone.getWorldX();
                float boneY = bone.getWorldY();
                minX = Math.min(minX, boneX);
                maxX = Math.max(maxX, boneX);
                minY = Math.min(minY, boneY);
                maxY = Math.max(maxY, boneY);
            }
        }

        // Calculate center point
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;

        skeleton.setPosition(
                mainBody.x + initialOffsetX - centerX * Settings.scale,
                mainBody.y + initialOffsetY - centerY * Settings.scale);

        if (skeleton.getRootBone() != null) {
            // Apply rotation around the visual center, not the root
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0)
                normalizedRotation += 360f;

            skeleton.getRootBone().setRotation(normalizedRotation);

            boolean shouldLog = (System.currentTimeMillis() - lastLogTime) >= 100;
            if (shouldLog) {
                BaseMod.logger.info("[" + ragdollId + "] Applied rotation: "
                        + String.format("%.1f", normalizedRotation) + "°, center: ("
                        + String.format("%.1f", centerX) + ", "
                        + String.format("%.1f", centerY) + ")");
                lastLogTime = System.currentTimeMillis();
            }
        }

        // Apply fade out to shadow
        if (shadowSlot != null) {
            float fadeProgress = Math.min(1f, shadowFadeTimer / SHADOW_FADE_DURATION);
            shadowSlot.getColor().a = initialShadowAlpha * (1f - fadeProgress);
        }

        int hiddenAttachments = 0;
        int wobbledBones = 0;

        for (Bone bone : skeleton.getBones()) {
            BoneWobble wobble = boneWobbles.get(bone);
            if (wobble != null) {
                bone.setRotation(bone.getData().getRotation() + wobble.rotation);
                wobbledBones++;
            }
        }

        // CRITICAL FIX: Hide slots that have detached attachments
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();

                if (attachmentBodies.containsKey(attachmentName)) {
                    // Hide this attachment by setting the slot's attachment to null
                    slot.setAttachment(null);
                    hiddenAttachments++;

                    if (updateCount % 60 == 0 && canLog()) {
                        BaseMod.logger.info("[" + ragdollId + "] Hiding original attachment: " + attachmentName);
                    }
                }
            }
        }

        if (updateCount % 120 == 0 && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Bones applied - hidden attachments: " + hiddenAttachments
                    + ", wobbled bones: " + wobbledBones + ", skeleton pos: ("
                    + String.format("%.1f", mainBody.x) + ", "
                    + String.format("%.1f", mainBody.y) + ")");
            lastLogTime = System.currentTimeMillis();
        }

        skeleton.updateWorldTransform();

        // Re-apply rotation after updateWorldTransform
        if (skeleton.getRootBone() != null) {
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0)
                normalizedRotation += 360f;
            skeleton.getRootBone().setRotation(normalizedRotation);
        }
    }

    public void renderDetachedAttachments(PolygonSpriteBatch sb, TextureAtlas atlas, AbstractMonster monster) {
        int attachmentsRendered = 0;
        int attachmentsFailed = 0;

        // Store the current blend function
        int srcFunc = sb.getBlendSrcFunc();
        int dstFunc = sb.getBlendDstFunc();

        // Set proper blend function for alpha blending (if not already set)
        if (srcFunc != GL20.GL_SRC_ALPHA || dstFunc != GL20.GL_ONE_MINUS_SRC_ALPHA) {
            sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            if (updateCount % 300 == 0 && canLog()) {
                BaseMod.logger.info("[" + ragdollId + "] Set blend function from ("
                        + srcFunc + ", " + dstFunc
                        + ") to (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)");
            }
        }

        // Get monster's current tint color for synchronized fading
        Color monsterColor = monster.tint.color;

        // Skip all attachment rendering if monster has completely faded
        if (monsterColor.a <= 0) {
            return;
        }

        for (Map.Entry<String, AttachmentPhysics> entry : attachmentBodies.entrySet()) {
            String attachmentName = entry.getKey();
            AttachmentPhysics attachmentPhysics = entry.getValue();

            boolean rendered = false;

            // Store current color to restore later
            Color currentColor = sb.getColor();

            // Apply monster's tint color directly to attachments for synchronized fading
            sb.setColor(monsterColor);

            // DEBUG: Log rendering attempts for fading attachments
            if (monsterColor.a < 0.8f && updateCount % 120 == 0) {
                BaseMod.logger.info("[" + ragdollId + "] RENDERING attachment '"
                        + attachmentName + "' with monster alpha: "
                        + String.format("%.2f", monsterColor.a) + ", blend: ("
                        + sb.getBlendSrcFunc() + ", " + sb.getBlendDstFunc() + ")");
            }

            if (attachmentPhysics.attachment != null) {
                try {
                    // Handle RegionAttachment
                    if (attachmentPhysics.attachment instanceof RegionAttachment) {
                        RegionAttachment regionAttachment = (RegionAttachment) attachmentPhysics.attachment;
                        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) regionAttachment.getRegion();

                        if (region != null) {
                            float regionPixelWidth = region.getRegionWidth();
                            float regionPixelHeight = region.getRegionHeight();
                            float attachmentScaleX = regionAttachment.getScaleX();
                            float attachmentScaleY = regionAttachment.getScaleY();
                            float attachmentRotation = regionAttachment.getRotation();
                            float finalWidth = regionPixelWidth * Math.abs(attachmentScaleX) * Settings.scale;
                            float finalHeight = regionPixelHeight * Math.abs(attachmentScaleY) * Settings.scale;
                            float offsetX = regionAttachment.getX() * attachmentScaleX * Settings.scale;
                            float offsetY = regionAttachment.getY() * attachmentScaleY * Settings.scale;

                            sb.draw(region, attachmentPhysics.x - finalWidth / 2f + offsetX,
                                    attachmentPhysics.y - finalHeight / 2f + offsetY,
                                    finalWidth / 2f, finalHeight / 2f, finalWidth, finalHeight,
                                    1f, 1f, attachmentPhysics.rotation + attachmentRotation);

                            rendered = true;
                            attachmentsRendered++;
                        }
                    }
                    // Handle MeshAttachment
                    else if (attachmentPhysics.attachment instanceof MeshAttachment) {
                        MeshAttachment meshAttachment = (MeshAttachment) attachmentPhysics.attachment;
                        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) meshAttachment.getRegion();

                        if (region != null) {
                            float width = region.getRegionWidth() * Settings.scale;
                            float height = region.getRegionHeight() * Settings.scale;

                            if (attachmentPhysics.originalBone != null) {
                                width *= Math.abs(attachmentPhysics.originalScaleX);
                                height *= Math.abs(attachmentPhysics.originalScaleY);
                            }

                            float finalRotation = attachmentPhysics.rotation;

                            if (region.rotate) {
                                if (monsterClassName.equals(Sentry.ID)
                                        && (attachmentName.contains("top")
                                        || attachmentName.contains("bottom"))) {
                                    finalRotation -= 90f;
                                }
                            }

                            sb.draw(region, attachmentPhysics.x - width / 2f,
                                    attachmentPhysics.y - height / 2f, width / 2f, height / 2f,
                                    width, height, 1f, 1f, finalRotation);

                            rendered = true;
                            attachmentsRendered++;
                        }
                    }
                } catch (Exception e) {
                    BaseMod.logger.error("[" + ragdollId + "] Failed to render attachment '"
                            + attachmentName + "': " + e.getMessage());
                    attachmentsFailed++;
                }
            }

            // Fallback rendering
            if (!rendered) {
                TextureAtlas.AtlasRegion region = atlas.findRegion(attachmentName);
                if (region != null) {
                    float width = region.getRegionWidth() * Settings.scale;
                    float height = region.getRegionHeight() * Settings.scale;

                    if (attachmentPhysics.originalBone != null) {
                        width *= Math.abs(attachmentPhysics.originalScaleX);
                        height *= Math.abs(attachmentPhysics.originalScaleY);
                    }

                    sb.draw(region, attachmentPhysics.x - width / 2f,
                            attachmentPhysics.y - height / 2f, width / 2f, height / 2f,
                            width, height, 1f, 1f, attachmentPhysics.rotation);
                    attachmentsRendered++;
                } else {
                    attachmentsFailed++;
                }
            }

            // Restore original color
            sb.setColor(currentColor);
        }

        // Restore original blend function if we changed it
        if (srcFunc != GL20.GL_SRC_ALPHA || dstFunc != GL20.GL_ONE_MINUS_SRC_ALPHA) {
            sb.setBlendFunction(srcFunc, dstFunc);
        }

        // Updated logging without fade references
        if (updateCount % 300 == 0 && canLog()
                && (attachmentsRendered > 0 || attachmentsFailed > 0)) {
            BaseMod.logger.info("[" + ragdollId + "] Attachment render summary - rendered: " + attachmentsRendered
                    + ", failed: " + attachmentsFailed
                    + ", monster alpha: " + String.format("%.2f", monsterColor.a));
            lastLogTime = System.currentTimeMillis();
        }
    }

    public float getCenterX() {
        return mainBody.x;
    }

    public float getCenterY() {
        return mainBody.y;
    }

    public float getAverageRotation() {
        return mainBody.rotation;
    }
}