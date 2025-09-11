package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.city.BronzeOrb;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.util.*;

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
    private static Texture debugSquareTexture = null;

    // Fields for shadow fading
    private static class FadeableSlot {
        final Slot slot;
        final float initialAlpha;

        FadeableSlot(Slot slot, float initialAlpha) {
            this.slot = slot;
            this.initialAlpha = initialAlpha;
        }
    }

    private final List<FadeableSlot> fadeableSlots;
    private float fadeTimer = 0f;
    private static final float FADE_DURATION = 0.5f;
    private static final float SHADOW_FADE_DURATION = 0.5f;
    // Add these fields to MultiBodyRagdoll class to track the relationship
    private final float physicsToVisualOffsetX;
    private final float physicsToVisualOffsetY;

    private final float initialOffsetX;
    private final float initialOffsetY;
    private static final float CEILING_Y = 1100f;

    private float settledTimer = 0f;
    public float totalRotationDegrees = 0f;
    public float lastRotation = 0f;
    private static final float MIN_PHYSICS_TIMESTEP = 1.0f / 60.0f; // Minimum 60Hz
    private static final float MAX_PHYSICS_TIMESTEP = 1.0f / 30.0f; // Don't go slower than 30Hz
    private static final float FIXED_TIMESTEP = 1.0f / 60.0f;
    private float accumulator = 0f;
    private final String monsterClassName;

    // Enhanced logging control
    private final long creationTime;
    public long lastLogTime = 0;
    private int updateCount = 0;
    private int physicsStepCount = 0;
    private final String ragdollId;
    private static boolean printFieldLogs = false; // Set to true to enable field discovery logs
    public static boolean printInitializationLogs = false; // Set to true to enable detailed initialization logs
    private static boolean printUpdateLogs = false; // Set to true to enable regular update logs
    private final boolean allowsFreeRotation;

    // Flag to indicate if this is an image-based ragdoll
    private final boolean isImageBased;

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

    // Helper class to store slot data for second pass
    class SlotAttachmentData {
        final Slot slot;
        final String attachmentName;
        final Bone bone;

        SlotAttachmentData(Slot slot, String attachmentName, Bone bone) {
            this.slot = slot;
            this.attachmentName = attachmentName;
            this.bone = bone;
        }
    }

    public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY,
                            String monsterClassName, AbstractMonster monster) {
        this.boneWobbles = new HashMap<>();
        this.monsterClassName = monsterClassName;
        this.associatedMonster = monster;
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(monsterClassName);

        // DYNAMIC CENTER OF MASS CALCULATION based on actual body bone
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(skeleton, monsterClassName);

        // Apply the correction to the main body physics center
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, monsterClassName);

        // CRITICAL: Calculate the FIXED relationship between physics center and visual center
        // This relationship should NEVER change during the simulation
        this.physicsToVisualOffsetX = (monster.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (monster.drawY - correctedStartY);

        // Store original offsets for reference (but we won't use these for positioning)
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "Ragdoll_" + System.currentTimeMillis() % 10000;

        BaseMod.logger.info("[" + ragdollId + "] DEBUG: About to calculate center offset");
        BaseMod.logger.info("[" + ragdollId + "] DEBUG: skeleton = " + (skeleton != null ? "NOT NULL" : "NULL"));
        BaseMod.logger.info("[" + ragdollId + "] DEBUG: monsterClassName = '" + monsterClassName + "'");
        BaseMod.logger.info("[" + ragdollId + "] DEBUG: SlaverBlue.ID = '" + SlaverBlue.ID + "'");
        BaseMod.logger.info("[" + ragdollId + "] DEBUG: Returned center offset: " + centerOffset);
        this.isImageBased = false;

        // Log the FIXED relationship
        if (printInitializationLogs) {
            BaseMod.logger.info("[" + ragdollId + "] DYNAMIC PHYSICS-VISUAL RELATIONSHIP established:");
            BaseMod.logger.info("[" + ragdollId + "] Physics center: (" + correctedStartX + ", " + correctedStartY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Visual center: (" + monster.drawX + ", " + monster.drawY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Fixed offset: (" + physicsToVisualOffsetX + ", " + physicsToVisualOffsetY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Dynamic center correction: " + centerOffset);
        }

        this.fadeableSlots = findFadeableSlots(skeleton);

        if (printInitializationLogs) {
            BaseMod.logger.info("[" + ragdollId + "] Found " + fadeableSlots.size() + " fadeable slots");
        }

        if (printInitializationLogs) {
            BaseMod.logger.info("[" + ragdollId + "] Creating dynamic ragdoll for "
                    + monsterClassName + " at corrected (" + correctedStartX + ", " + correctedStartY
                    + "), ground: " + groundLevel);
            BaseMod.logger.info("[" + ragdollId + "] Skeleton has "
                    + skeleton.getBones().size + " bones");
        }

        // In MultiBodyRagdoll constructor, around line 120, replace this section:

        int attachmentCount = 0;
// Get overkill damage for dismemberment calculations
        float overkillDamage = OverkillTracker.getOverkillDamage(monster);

// Track created attachments for parent-child linking
        HashMap<String, AttachmentPhysics> parentAttachments = new HashMap<>();
        List<SlotAttachmentData> potentialChildren = new ArrayList<>();


// FIRST PASS: Create parent attachments only
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();
                Bone bone = slot.getBone();

                boolean shouldDetach = AttachmentConfig.shouldDetachAttachment(monsterClassName, attachmentName, overkillDamage);

                if (shouldDetach) {
                    // Create parent attachment (no parent parameter)
                    AttachmentPhysics parentAttachment = new AttachmentPhysics(
                            startX + bone.getWorldX() * Settings.scale,
                            startY + bone.getWorldY() * Settings.scale,
                            groundLevel, bone, slot.getAttachment(), attachmentName);

                    parentAttachments.put(attachmentName.toLowerCase(), parentAttachment);
                    attachmentBodies.put(attachmentName, parentAttachment);
                    attachmentCount++;

                    if (printInitializationLogs) {
                        String attachmentType = slot.getAttachment().getClass().getSimpleName();
                        BaseMod.logger.info("[" + ragdollId + "] Created PARENT " + attachmentType
                                + ": '" + attachmentName + "' on bone: " + bone.getData().getName()
                                + " (overkill: " + String.format("%.1f", overkillDamage) + ")");
                    }
                } else {
                    // Store for potential child processing
                    potentialChildren.add(new SlotAttachmentData(slot, attachmentName, bone));
                }
            }
        }

// SECOND PASS: Process potential child attachments for this specific monster
        int childrenCreated = 0;
        for (SlotAttachmentData data : potentialChildren) {
            String attachmentName = data.attachmentName;

            // Find potential parent for this attachment using monster-specific rules
            AttachmentPhysics parentAttachment = findParentForChild(monsterClassName, attachmentName, parentAttachments);

            if (parentAttachment != null) {
                // Create child attachment linked to parent
                AttachmentPhysics childAttachment = new AttachmentPhysics(
                        startX + data.bone.getWorldX() * Settings.scale,
                        startY + data.bone.getWorldY() * Settings.scale,
                        groundLevel, data.bone, data.slot.getAttachment(), attachmentName,
                        parentAttachment); // Link to parent

                attachmentBodies.put(attachmentName, childAttachment);
                attachmentCount++;
                childrenCreated++;

                if (printInitializationLogs) {
                    String attachmentType = data.slot.getAttachment().getClass().getSimpleName();
                    BaseMod.logger.info("[" + ragdollId + "] Created CHILD " + attachmentType
                            + ": '" + attachmentName + "' linked to '" + parentAttachment.getAttachmentName()
                            + "' for monster " + monsterClassName);
                }
            }
        }

        if (printInitializationLogs && childrenCreated > 0) {
            BaseMod.logger.info("[" + ragdollId + "] Parent-child creation complete for " + monsterClassName
                    + " - Parents: " + parentAttachments.size() + ", Children: " + childrenCreated);
        }

        if (printInitializationLogs) {
            BaseMod.logger.info("[" + ragdollId + "] Created " + attachmentCount + " attachment physics bodies");
        }

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
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(monsterClassName);


        // GET CENTER OF MASS CORRECTION for image-based ragdolls too
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(null, monsterClassName);

        // Apply the correction to the main body physics center
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, monsterClassName);

        // CRITICAL: Calculate the FIXED relationship between physics center and visual center
        this.physicsToVisualOffsetX = (monster.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (monster.drawY - correctedStartY);

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "ImageRagdoll_" + System.currentTimeMillis() % 10000;
        this.isImageBased = true;

        // Store the offset between monster draw position and original physics body start position (for reference)
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

// No fadeable parts for image-based ragdolls
        this.fadeableSlots = new ArrayList<>();

        // Log the FIXED relationship for image ragdolls too
        if (printInitializationLogs) {
            BaseMod.logger.info("[" + ragdollId + "] FIXED PHYSICS-VISUAL RELATIONSHIP established (IMAGE-BASED):");
            BaseMod.logger.info("[" + ragdollId + "] Physics center: (" + correctedStartX + ", " + correctedStartY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Visual center: (" + monster.drawX + ", " + monster.drawY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Fixed offset: (" + physicsToVisualOffsetX + ", " + physicsToVisualOffsetY + ")");
            BaseMod.logger.info("[" + ragdollId + "] Center correction applied: " + centerOffset);
        }
    }

    // ADD these new helper methods to your MultiBodyRagdoll class:

    private List<FadeableSlot> findFadeableSlots(Skeleton skeleton) {
        List<FadeableSlot> fadeableSlots = new ArrayList<>();

        // Always check for shadow (existing behavior)
        Slot shadowSlot = findShadowSlot(skeleton);
        if (shadowSlot != null) {
            fadeableSlots.add(new FadeableSlot(shadowSlot, shadowSlot.getColor().a));
        }

        // Check for monster-specific fadeable parts
        String[] fadeableParts = FadeablePartsConfig.getFadeableParts(monsterClassName);
        if (fadeableParts != null) {
            for (String partName : fadeableParts) {
                Slot partSlot = findSlotByPartName(skeleton, partName);
                if (partSlot != null && partSlot != shadowSlot) { // Don't duplicate shadow
                    fadeableSlots.add(new FadeableSlot(partSlot, partSlot.getColor().a));

                    if (printInitializationLogs) {
                        BaseMod.logger.info("[" + ragdollId + "] Found fadeable part: "
                                + partName + " in slot: " + partSlot.getData().getName());
                    }
                }
            }
        }

        return fadeableSlots;
    }

    private Slot findShadowSlot(Skeleton skeleton) {
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

        return shadowSlot;
    }

    private Slot findSlotByPartName(Skeleton skeleton, String partName) {
        // First try to find slot by name
        Slot slot = skeleton.findSlot(partName);
        if (slot != null) return slot;

        // Then try to find by attachment name
        for (Slot s : skeleton.getSlots()) {
            if (s.getAttachment() != null) {
                String attachmentName = s.getAttachment().getName().toLowerCase();
                if (attachmentName.contains(partName.toLowerCase())) {
                    return s;
                }
            }
        }

        // Finally try slot name contains
        for (Slot s : skeleton.getSlots()) {
            if (s.getData().getName().toLowerCase().contains(partName.toLowerCase())) {
                return s;
            }
        }

        return null;
    }

    public boolean isProperlyInitialized() {
        return mainBody != null && (isImageBased || (!boneWobbles.isEmpty()));
    }

    public boolean canLog() {
        if (!printUpdateLogs) {
            return false; // Disable all update logging if flag is false
        }

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

        if (!fadeableSlots.isEmpty() && fadeTimer < FADE_DURATION) {
            fadeTimer += deltaTime;
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

        // Clamp deltaTime to prevent huge timesteps that break physics
        deltaTime = Math.min(deltaTime, MAX_PHYSICS_TIMESTEP);

        // Calculate adaptive timestep - smaller timesteps for higher framerates
        float physicsTimestep = Math.max(deltaTime, MIN_PHYSICS_TIMESTEP);

        accumulator += deltaTime;
        int steps = 0;

        while (accumulator >= physicsTimestep) {
            steps++;
            physicsStepCount++;
            updatePhysics(physicsTimestep);
            accumulator -= physicsTimestep;

            // Recalculate timestep each iteration in case frame rate changed
            physicsTimestep = Math.max(MIN_PHYSICS_TIMESTEP, accumulator);

            if (steps > 10) { // Prevent infinite loops
                BaseMod.logger.warn("[" + ragdollId + "] Breaking physics loop after "
                        + steps + " steps");
                accumulator = 0;
                break;
            }
        }

        if (steps > 0 && printUpdateLogs && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] Executed " + steps
                    + " physics steps with timestep " + String.format("%.4f", physicsTimestep)
                    + " (total: " + physicsStepCount + ")");
        }
    }

    private void updatePhysics(float deltaTime) {
        mainBody.update(deltaTime, this);

        // Update attachments
        int activeAttachments = 0;
        for (AttachmentPhysics attachment : attachmentBodies.values()) {
            attachment.update(deltaTime);
            if (Math.abs(attachment.velocityX) + Math.abs(attachment.velocityY) > 10f) {
                activeAttachments++;
            }
        }

        boolean parentHasSettled = hasSettledOnGround();
        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.update(deltaTime, mainBody.velocityX, mainBody.velocityY,
                    parentHasSettled, this);
        }
    }

    // Add a method to handle image-based positioning with fixed relationship
    public void applyToImage(AbstractMonster monster) {
        // For image-based ragdolls, we need to update the monster's drawX/drawY
        // to maintain the fixed relationship with the physics center
        monster.drawX = mainBody.x + physicsToVisualOffsetX;
        monster.drawY = mainBody.y + physicsToVisualOffsetY;

        // Apply rotation if the monster supports it
        // (You might need to store rotation in a field that the image renderer uses)

        if (updateCount % 180 == 0 && canLog()) {
            BaseMod.logger.info("[" + ragdollId + "] IMAGE SYNC CHECK - Physics: ("
                    + String.format("%.1f", mainBody.x) + ", " + String.format("%.1f", mainBody.y)
                    + ") -> Visual: (" + String.format("%.1f", monster.drawX) + ", " + String.format("%.1f", monster.drawY)
                    + "), offset: (" + String.format("%.1f", physicsToVisualOffsetX) + ", "
                    + String.format("%.1f", physicsToVisualOffsetY) + ")");
            lastLogTime = System.currentTimeMillis();
        }
    }

    // Helper method to find parent attachment for a potential child (monster-specific)
    private AttachmentPhysics findParentForChild(String monsterName, String childName,
                                                 HashMap<String, AttachmentPhysics> parentAttachments) {

        // Check each parent attachment to see if this child should belong to it
        for (Map.Entry<String, AttachmentPhysics> entry : parentAttachments.entrySet()) {
            String parentName = entry.getValue().getAttachmentName();

            // Use monster-specific child attachment checking
            if (AttachmentConfig.isChildAttachment(monsterName, parentName, childName)) {
                if (printInitializationLogs) {
                    BaseMod.logger.info("[" + ragdollId + "] Found parent '" + parentName
                            + "' for child '" + childName + "' in monster " + monsterName);
                }
                return entry.getValue();
            }
        }

        return null;
    }

    public boolean hasSettledOnGround() {
        return mainBody.hasSettledOnGround();
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
        float baseAngularVel = MathUtils.random(-72f, 72f); // Change this to give enemies more rotation
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

    // Update the applyToBones method to rotate around the body bone
    public void applyToBones(Skeleton skeleton, AbstractMonster monster) {
        // Find the body bone that we're using as the center of mass
        Bone bodyBone = findBodyBone(skeleton);

        if (bodyBone != null) {
            // BODY-CENTERED POSITIONING AND ROTATION

            // Calculate where the body bone should be in world space
            float targetBodyWorldX = mainBody.x;
            float targetBodyWorldY = mainBody.y;

            // Get current body bone world position relative to skeleton origin
            float currentBodyOffsetX = bodyBone.getWorldX() * Settings.scale;
            float currentBodyOffsetY = bodyBone.getWorldY() * Settings.scale;

            // Position skeleton so that body bone ends up at physics center
            skeleton.setPosition(
                    targetBodyWorldX - currentBodyOffsetX,
                    targetBodyWorldY - currentBodyOffsetY
            );

            // Apply rotation to the body bone instead of root bone
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;

            // Set the body bone's rotation to match physics rotation
            bodyBone.setRotation(bodyBone.getData().getRotation() + normalizedRotation);

            // Log positioning every so often
            if (updateCount % 120 == 0 && canLog()) {
                BaseMod.logger.info("[" + ragdollId + "] BODY-CENTERED positioning:");
                BaseMod.logger.info("[" + ragdollId + "] - Physics center: (" + String.format("%.1f", mainBody.x) + ", " + String.format("%.1f", mainBody.y) + ")");
                BaseMod.logger.info("[" + ragdollId + "] - Body bone '" + bodyBone.getData().getName() + "' offset: (" + String.format("%.1f", currentBodyOffsetX) + ", " + String.format("%.1f", currentBodyOffsetY) + ")");
                BaseMod.logger.info("[" + ragdollId + "] - Skeleton positioned at: (" + String.format("%.1f", skeleton.getX()) + ", " + String.format("%.1f", skeleton.getY()) + ")");
                BaseMod.logger.info("[" + ragdollId + "] - Applied rotation: " + String.format("%.1f", normalizedRotation) + "°");
                lastLogTime = System.currentTimeMillis();
            }

        } else {
            // FALLBACK: Use root bone method if no body bone found
            BaseMod.logger.warn("[" + ragdollId + "] No body bone found, falling back to root bone rotation");

            skeleton.setPosition(
                    mainBody.x + physicsToVisualOffsetX,
                    mainBody.y + physicsToVisualOffsetY
            );

            if (skeleton.getRootBone() != null) {
                float normalizedRotation = mainBody.rotation % 360f;
                if (normalizedRotation < 0) normalizedRotation += 360f;
                skeleton.getRootBone().setRotation(normalizedRotation);
            }
        }

        for (FadeableSlot fadeableSlot : fadeableSlots) {
            float fadeProgress = Math.min(1f, fadeTimer / FADE_DURATION);
            fadeableSlot.slot.getColor().a = fadeableSlot.initialAlpha * (1f - fadeProgress);
        }

        int hiddenAttachments = 0;
        int wobbledBones = 0;

        // Apply bone wobbles
        for (Bone bone : skeleton.getBones()) {
            BoneWobble wobble = boneWobbles.get(bone);
            if (wobble != null) {
                bone.setRotation(bone.getData().getRotation() + wobble.rotation);
                wobbledBones++;
            }
        }

        // Hide detached attachments
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();

                if (attachmentBodies.containsKey(attachmentName)) {
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
                    + ", wobbled bones: " + wobbledBones + ", body-centered positioning active");
            lastLogTime = System.currentTimeMillis();
        }

        skeleton.updateWorldTransform();

        // Re-check body bone position after world transform update
        if (bodyBone != null) {
            // Re-apply rotation to body bone after updateWorldTransform
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;
            bodyBone.setRotation(bodyBone.getData().getRotation() + normalizedRotation);
        } else if (skeleton.getRootBone() != null) {
            // Fallback: re-apply to root bone
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;
            skeleton.getRootBone().setRotation(normalizedRotation);
        }
    }

    // Updated helper method to find body bone (same logic as in CenterOfMassConfig)
    private Bone findBodyBone(Skeleton skeleton) {
        if (skeleton == null) return null;
        // Use the same logic as CenterOfMassConfig for consistency
        String[] customBodyAttachments = CenterOfMassConfig.customBodyAttachments.get(monsterClassName);
        if (customBodyAttachments != null) {
            for (String attachmentName : customBodyAttachments) {
                // First try to find bone by attachment name
                Bone boneFromAttachment = findBoneByAttachmentName(skeleton, attachmentName);
                if (boneFromAttachment != null) {
                    return boneFromAttachment;
                }
                // Fallback: try the attachment name as a direct bone name
                Bone directBone = skeleton.findBone(attachmentName);
                if (directBone != null) {
                    return directBone;
                }
            }
        }
        // Fallback to generic names
        String[] bodyBoneNames = {"body", "torso", "chest", "spine", "hip", "pelvis", "trunk"};
        for (String boneName : bodyBoneNames) {
            Bone bone = skeleton.findBone(boneName);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }

    // Helper method to find bone by attachment name (same as in CenterOfMassConfig)
    private Bone findBoneByAttachmentName(Skeleton skeleton, String attachmentName) {
        // Search through all slots to find one with the matching attachment
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String slotAttachmentName = slot.getAttachment().getName();
                if (attachmentName.equals(slotAttachmentName)) {
                    return slot.getBone();
                }
            }
        }
        return null;
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
    public float getPhysicsToVisualOffsetX() { return physicsToVisualOffsetX; }
    public float getPhysicsToVisualOffsetY() { return physicsToVisualOffsetY; }
    public AbstractMonster getAssociatedMonster() { return associatedMonster; }
    public HashMap<String, AttachmentPhysics> getAttachmentBodies() { return attachmentBodies; }
    public float getGroundY() { return groundY; }
    public boolean isImageBased() { return isImageBased; }
    public String getRagdollId() { return ragdollId; }
    public int getUpdateCount() { return updateCount; }
    public boolean getAllowsFreeRotation() {
        return allowsFreeRotation;
    }

    public String getMonsterClassName() {
        return monsterClassName;
    }

}