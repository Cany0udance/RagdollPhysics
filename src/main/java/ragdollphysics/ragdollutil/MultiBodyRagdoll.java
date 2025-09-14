package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.core.AbstractCreature;
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

    // ================================
    // CORE PHYSICS COMPONENTS
    // ================================

    public final HashMap<Bone, BoneWobble> boneWobbles;
    private final HashMap<String, AttachmentPhysics> attachmentBodies;
    public final RagdollPhysics mainBody;
    private final List<String> attachmentDrawOrder = new ArrayList<>();


    // ================================
    // MONSTER AND POSITIONING DATA
    // ================================

    private final float groundY;
    private final AbstractCreature associatedEntity; // Changed from AbstractMonster
    private final String entityClassName;
    private final String ragdollId;

    // Fixed relationship between physics center and visual center
    private final float physicsToVisualOffsetX;
    private final float physicsToVisualOffsetY;
    private final float initialOffsetX;
    private final float initialOffsetY;


    // ================================
    // VISUAL EFFECTS AND FADING
    // ================================

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


    // ================================
    // PHYSICS CONFIGURATION
    // ================================

    private static final float CEILING_Y = 1100f;
    private static final float MIN_PHYSICS_TIMESTEP = 1.0f / 60.0f;
    private static final float MAX_PHYSICS_TIMESTEP = 1.0f / 30.0f;
    private static final float FIXED_TIMESTEP = 1.0f / 60.0f;

    private float accumulator = 0f;
    private float settledTimer = 0f;
    public float totalRotationDegrees = 0f;
    public float lastRotation = 0f;


    // ================================
    // MONSTER-SPECIFIC CONFIGURATIONS
    // ================================

    private final boolean allowsFreeRotation;
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


    // ================================
    // DEBUG AND MONITORING
    // ================================

    private static Texture debugSquareTexture = null;
    private final long creationTime;
    public long lastLogTime = 0;
    private int updateCount = 0;
    private int physicsStepCount = 0;

    private static boolean printFieldLogs = false;
    public static boolean printInitializationLogs = false;
    private static boolean printUpdateLogs = false;


    // ================================
    // CONSTRUCTORS
    // ================================

    /** Constructor for skeleton-based ragdolls - now supports both monsters and players */
    public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY,
                            String entityClassName, AbstractCreature entity) {
        this.boneWobbles = new HashMap<>();
        this.entityClassName = entityClassName;
        this.associatedEntity = entity; // Changed from associatedMonster
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(entityClassName);
        this.isImageBased = false;

        // Calculate dynamic center of mass correction
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(skeleton, entityClassName);
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, entityClassName);

        // Establish fixed physics-visual relationship
        this.physicsToVisualOffsetX = (entity.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (entity.drawY - correctedStartY);
        this.initialOffsetX = entity.drawX - startX;
        this.initialOffsetY = entity.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "Ragdoll_" + System.currentTimeMillis() % 10000;

        this.fadeableSlots = findFadeableSlots(skeleton);

        // Initialize attachments and bone wobbles
        initializeAttachments(skeleton, entity, startX, startY);
        initializeBoneWobbles(skeleton);
    }

    /** Constructor for image-based ragdolls - now supports both monsters and players */
    public MultiBodyRagdoll(float startX, float startY, float groundLevel,
                            String entityClassName, AbstractCreature entity) {
        this.boneWobbles = new HashMap<>();
        this.entityClassName = entityClassName;
        this.associatedEntity = entity; // Changed from associatedMonster
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(entityClassName);
        this.isImageBased = true;

        // Apply center of mass correction for image-based ragdolls too
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(null, entityClassName);
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, entityClassName);

        // Establish fixed physics-visual relationship
        this.physicsToVisualOffsetX = (entity.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (entity.drawY - correctedStartY);
        this.initialOffsetX = entity.drawX - startX;
        this.initialOffsetY = entity.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "ImageRagdoll_" + System.currentTimeMillis() % 10000;
        this.fadeableSlots = new ArrayList<>();
    }


    // ================================
    // INITIALIZATION METHODS
    // ================================

    /** Initialize attachment physics bodies - now works with any AbstractCreature */
    private void initializeAttachments(Skeleton skeleton, AbstractCreature entity, float startX, float startY) {
        BaseMod.logger.info("=== ATTACHMENT INITIALIZATION DEBUG ===");
        BaseMod.logger.info("Entity: " + entityClassName);
        BaseMod.logger.info("Entity type: " + entity.getClass().getSimpleName());

        float overkillDamage = OverkillTracker.getOverkillDamage(entity);
        HashMap<String, AttachmentPhysics> parentAttachments = new HashMap<>();
        List<SlotAttachmentData> potentialChildren = new ArrayList<>();

        // Log all slots and their attachments
        BaseMod.logger.info("Total slots found: " + skeleton.getSlots().size);
        for (Slot slot : skeleton.getSlots()) {
            String slotName = slot.getData().getName();
            String boneName = slot.getBone().getData().getName();
            String attachmentName = slot.getAttachment() != null ? slot.getAttachment().getName() : "null";

            BaseMod.logger.info("Slot: " + slotName + " | Bone: " + boneName + " | Attachment: " + attachmentName);

            if (slot.getAttachment() != null) {
                String attachmentNameFull = slot.getAttachment().getName();
                boolean shouldDetach = AttachmentConfig.shouldDetachAttachment(entityClassName, attachmentNameFull, overkillDamage);

                BaseMod.logger.info("  -> Should detach: " + shouldDetach);

                if (shouldDetach) {
                    float[] position = calculateAttachmentPosition(slot, entity, startX, startY);
                    AttachmentPhysics parentAttachment = new AttachmentPhysics(
                            position[0], position[1], groundY, slot.getBone(),
                            slot.getAttachment(), attachmentName);

                    parentAttachments.put(attachmentName.toLowerCase(), parentAttachment);
                    attachmentBodies.put(attachmentName, parentAttachment);
                    attachmentDrawOrder.add(attachmentName);
                } else {
                    potentialChildren.add(new SlotAttachmentData(slot, attachmentName, slot.getBone()));
                }
            }
        }

        // Second pass: Create child attachments linked to parents
        for (SlotAttachmentData data : potentialChildren) {
            AttachmentPhysics parentAttachment = findParentForChild(entityClassName, data.attachmentName, parentAttachments);
            if (parentAttachment != null) {
                float[] position = calculateAttachmentPosition(data.slot, entity, startX, startY);
                AttachmentPhysics childAttachment = new AttachmentPhysics(
                        position[0], position[1], groundY, data.bone,
                        data.slot.getAttachment(), data.attachmentName, parentAttachment);

                attachmentBodies.put(data.attachmentName, childAttachment);
                attachmentDrawOrder.add(data.attachmentName);
            }
        }

        BaseMod.logger.info("Created " + attachmentBodies.size() + " attachment physics bodies");
        BaseMod.logger.info("=== END ATTACHMENT DEBUG ===");
    }

    /** Calculate attachment position - now works with any AbstractCreature */
    private float[] calculateAttachmentPosition(Slot slot, AbstractCreature entity, float startX, float startY) {
        Bone bone = slot.getBone();

        if (slot.getAttachment() instanceof RegionAttachment) {
            RegionAttachment regionAttachment = (RegionAttachment) slot.getAttachment();
            float localX = regionAttachment.getX();
            float localY = regionAttachment.getY();
            float transformedX = bone.getA() * localX + bone.getB() * localY;
            float transformedY = bone.getC() * localX + bone.getD() * localY;
            return new float[]{
                    entity.drawX + (bone.getWorldX() + transformedX) * Settings.scale,
                    entity.drawY + (bone.getWorldY() + transformedY) * Settings.scale
            };
        } else if (slot.getAttachment() instanceof MeshAttachment) {
            MeshAttachment meshAttachment = (MeshAttachment) slot.getAttachment();
            float[] worldVertices = meshAttachment.updateWorldVertices(slot, false);
            float centerX = 0f, centerY = 0f;
            int vertexCount = 0;
            for (int i = 0; i < worldVertices.length; i += 5) {
                centerX += worldVertices[i];
                centerY += worldVertices[i + 1];
                vertexCount++;
            }
            if (vertexCount > 0) {
                centerX /= vertexCount;
                centerY /= vertexCount;
            }
            return new float[]{centerX, centerY};
        } else {
            // Fallback for other attachment types
            return new float[]{
                    startX + bone.getWorldX() * Settings.scale,
                    startY + bone.getWorldY() * Settings.scale
            };
        }
    }

    /** Initialize bone wobbles for all skeleton bones */
    private void initializeBoneWobbles(Skeleton skeleton) {
        for (Bone bone : skeleton.getBones()) {
            boneWobbles.put(bone, new BoneWobble(bone.getRotation(), bone));
        }
    }


    // ================================
    // FADEABLE SLOTS MANAGEMENT
    // ================================

    /** Find slots that should fade out over time (shadows, etc.) */
    private List<FadeableSlot> findFadeableSlots(Skeleton skeleton) {
        List<FadeableSlot> fadeableSlots = new ArrayList<>();

        // Always check for shadow
        Slot shadowSlot = findShadowSlot(skeleton);
        if (shadowSlot != null) {
            fadeableSlots.add(new FadeableSlot(shadowSlot, shadowSlot.getColor().a));
        }

        // Check for monster-specific fadeable parts
        String[] fadeableParts = FadeablePartsConfig.getFadeableParts(entityClassName);
        if (fadeableParts != null) {
            for (String partName : fadeableParts) {
                Slot partSlot = findSlotByPartName(skeleton, partName);
                if (partSlot != null && partSlot != shadowSlot) {
                    fadeableSlots.add(new FadeableSlot(partSlot, partSlot.getColor().a));
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
        Slot slot = skeleton.findSlot(partName);
        if (slot != null) return slot;

        for (Slot s : skeleton.getSlots()) {
            if (s.getAttachment() != null) {
                String attachmentName = s.getAttachment().getName().toLowerCase();
                if (attachmentName.contains(partName.toLowerCase())) {
                    return s;
                }
            }
        }

        for (Slot s : skeleton.getSlots()) {
            if (s.getData().getName().toLowerCase().contains(partName.toLowerCase())) {
                return s;
            }
        }

        return null;
    }


    // ================================
    // MAIN UPDATE LOOP
    // ================================

    /** Main update method called each frame */
    public void update(float deltaTime) {
        updateCount++;

        // Update fade timer
        if (!fadeableSlots.isEmpty() && fadeTimer < FADE_DURATION) {
            fadeTimer += deltaTime;
        }

        // Clamp deltaTime to prevent physics instability
        deltaTime = Math.min(deltaTime, MAX_PHYSICS_TIMESTEP);
        float physicsTimestep = Math.max(deltaTime, MIN_PHYSICS_TIMESTEP);
        accumulator += deltaTime;

        // Run physics steps
        int steps = 0;
        while (accumulator >= physicsTimestep) {
            steps++;
            physicsStepCount++;
            updatePhysics(physicsTimestep);
            accumulator -= physicsTimestep;
            physicsTimestep = Math.max(MIN_PHYSICS_TIMESTEP, accumulator);

            if (steps > 10) { // Prevent infinite loops
                accumulator = 0;
                break;
            }
        }
    }

    /** Update all physics components */
    private void updatePhysics(float deltaTime) {
        mainBody.update(deltaTime, this);

        // Update attachments
        for (AttachmentPhysics attachment : attachmentBodies.values()) {
            attachment.update(deltaTime);
        }

        // Update bone wobbles
        boolean parentHasSettled = hasSettledOnGround();
        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.update(deltaTime, mainBody.velocityX, mainBody.velocityY, parentHasSettled, this);
        }
    }


    // ================================
    // SKELETON POSITIONING AND ROTATION
    // ================================

    /** Apply physics state to skeleton bones - now works with any AbstractCreature */
    public void applyToBones(Skeleton skeleton, AbstractCreature entity) {
        Bone bodyBone = findBodyBone(skeleton);

        if (bodyBone != null) {
            // Body-centered positioning and rotation
            float targetBodyWorldX = mainBody.x;
            float targetBodyWorldY = mainBody.y;
            float currentBodyOffsetX = bodyBone.getWorldX() * Settings.scale;
            float currentBodyOffsetY = bodyBone.getWorldY() * Settings.scale;

            skeleton.setPosition(
                    targetBodyWorldX - currentBodyOffsetX,
                    targetBodyWorldY - currentBodyOffsetY
            );

            // Apply rotation to body bone
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;
            bodyBone.setRotation(bodyBone.getData().getRotation() + normalizedRotation);
        } else {
            // Fallback: use root bone method
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

        // Apply fading to fadeable slots
        for (FadeableSlot fadeableSlot : fadeableSlots) {
            float fadeProgress = Math.min(1f, fadeTimer / FADE_DURATION);
            fadeableSlot.slot.getColor().a = fadeableSlot.initialAlpha * (1f - fadeProgress);
        }

        // Apply bone wobbles and hide detached attachments
        for (Bone bone : skeleton.getBones()) {
            BoneWobble wobble = boneWobbles.get(bone);
            if (wobble != null) {
                bone.setRotation(bone.getData().getRotation() + wobble.rotation);
            }
        }

        // Hide original attachments that are now physics bodies
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();
                if (attachmentBodies.containsKey(attachmentName)) {
                    slot.setAttachment(null);
                }
            }
        }

        skeleton.updateWorldTransform();

        // Re-apply rotation after world transform update
        if (bodyBone != null) {
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;
            bodyBone.setRotation(bodyBone.getData().getRotation() + normalizedRotation);
        } else if (skeleton.getRootBone() != null) {
            float normalizedRotation = mainBody.rotation % 360f;
            if (normalizedRotation < 0) normalizedRotation += 360f;
            skeleton.getRootBone().setRotation(normalizedRotation);
        }
    }

    /** Apply physics positioning to image-based ragdolls - now works with any AbstractCreature */
    public void applyToImage(AbstractCreature entity) {
        entity.drawX = mainBody.x + physicsToVisualOffsetX;
        entity.drawY = mainBody.y + physicsToVisualOffsetY;
    }


    // ================================
    // FORCE APPLICATION
    // ================================

    /** Apply forces with entity-specific modifiers */
    public void applyGlobalForce(float forceX, float forceY) {
        PhysicsModifier.VelocityModifiers modifiers = PhysicsModifier.calculateModifiers(associatedEntity);

        // Apply modified forces to main body
        mainBody.velocityX += forceX * 0.8f * modifiers.horizontalMultiplier;
        mainBody.velocityY += forceY * 0.8f * modifiers.verticalMultiplier;

        lastRotation = mainBody.rotation;

        // Calculate angular velocity with modifiers
        float upwardVelocity = Math.max(0, mainBody.velocityY);
        float flipIntensity = Math.min(upwardVelocity / 1200f, 0.5f);
        float baseAngularVel = MathUtils.random(-72f, 72f);
        mainBody.angularVelocity += baseAngularVel * (1.0f + flipIntensity * 0.3f) * modifiers.angularMultiplier;

        // Apply modifiers to attachments
        for (AttachmentPhysics attachment : attachmentBodies.values()) {
            attachment.velocityX += forceX * MathUtils.random(0.5f, 1.2f) * modifiers.horizontalMultiplier;
            attachment.velocityY += forceY * MathUtils.random(0.4f, 1.0f) * modifiers.verticalMultiplier;

            float attachmentBaseAngular = MathUtils.random(-360f, 360f);
            attachment.angularVelocity += attachmentBaseAngular * (1.0f + flipIntensity * 0.5f) * modifiers.angularMultiplier;

            attachment.velocityX += MathUtils.random(-75f, 75f) * modifiers.horizontalMultiplier;
            attachment.velocityY += MathUtils.random(-50f, 100f) * modifiers.verticalMultiplier;
        }

        // Apply modifiers to bone wobbles
        for (BoneWobble wobble : boneWobbles.values()) {
            wobble.angularVelocity += MathUtils.random(-90f, 90f) * (1.0f + flipIntensity * 0.5f) * modifiers.angularMultiplier;
        }
    }


    // ================================
    // ATTACHMENT RENDERING
    // ================================

    /** Render detached attachments - now works with any AbstractCreature */
    public void renderDetachedAttachments(PolygonSpriteBatch sb, TextureAtlas atlas, AbstractCreature entity) {
        BaseMod.logger.info("=== RENDER ATTACHMENTS DEBUG ===");
        BaseMod.logger.info("Entity: " + entityClassName + " | Attachment count: " + attachmentBodies.size());
        BaseMod.logger.info("Entity tint alpha: " + entity.tint.color.a);

        // Store and set proper blend function
        int srcFunc = sb.getBlendSrcFunc();
        int dstFunc = sb.getBlendDstFunc();
        if (srcFunc != GL20.GL_SRC_ALPHA || dstFunc != GL20.GL_ONE_MINUS_SRC_ALPHA) {
            sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Skip if entity has completely faded
        Color entityColor = entity.tint.color;
        if (entityColor.a <= 0) {
            BaseMod.logger.info("Skipping render - entity alpha is 0");
            return;
        }

        // Render attachments in draw order
        for (String attachmentName : attachmentDrawOrder) {
            AttachmentPhysics attachmentPhysics = attachmentBodies.get(attachmentName);
            if (attachmentPhysics == null) {
                BaseMod.logger.info("Attachment physics null for: " + attachmentName);
                continue;
            }

            BaseMod.logger.info("Rendering attachment: " + attachmentName +
                    " at (" + attachmentPhysics.x + ", " + attachmentPhysics.y + ")");

            Color currentColor = sb.getColor();
            sb.setColor(entityColor);

            renderSingleAttachment(sb, atlas, attachmentPhysics, attachmentName);
            sb.setColor(currentColor);
        }

        BaseMod.logger.info("=== END RENDER DEBUG ===");

        // Restore original blend function
        if (srcFunc != GL20.GL_SRC_ALPHA || dstFunc != GL20.GL_ONE_MINUS_SRC_ALPHA) {
            sb.setBlendFunction(srcFunc, dstFunc);
        }
    }

    /** Render a single attachment with proper scaling */
    private void renderSingleAttachment(PolygonSpriteBatch sb, TextureAtlas atlas,
                                        AttachmentPhysics attachmentPhysics, String attachmentName) {
        boolean rendered = false;

        if (attachmentPhysics.attachment != null) {
            try {
                if (attachmentPhysics.attachment instanceof RegionAttachment) {
                    rendered = renderRegionAttachment(sb, (RegionAttachment) attachmentPhysics.attachment, attachmentPhysics);
                } else if (attachmentPhysics.attachment instanceof MeshAttachment) {
                    rendered = renderMeshAttachment(sb, (MeshAttachment) attachmentPhysics.attachment, attachmentPhysics, attachmentName);
                } else {
                    // Handle custom Haberdashery attachment types
                    rendered = renderCustomAttachment(sb, attachmentPhysics, attachmentName);
                }
            } catch (Exception e) {
                BaseMod.logger.info("Exception during attachment render: " + e.getMessage());
            }
        }

        // Fallback: render from atlas (won't work for Haberdashery, but keep for other mods)
        if (!rendered) {
            TextureAtlas.AtlasRegion region = atlas.findRegion(attachmentName);
            if (region != null) {
                float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                        entityClassName, region.getRegionWidth(), region.getRegionHeight(),
                        attachmentPhysics.originalScaleX, attachmentPhysics.originalScaleY);

                sb.draw(region,
                        attachmentPhysics.x - dimensions[0] / 2f,
                        attachmentPhysics.y - dimensions[1] / 2f,
                        dimensions[0] / 2f, dimensions[1] / 2f,
                        dimensions[0], dimensions[1],
                        1f, 1f, attachmentPhysics.rotation);
            }
        }
    }

    private boolean renderCustomAttachment(PolygonSpriteBatch sb, AttachmentPhysics physics, String attachmentName) {
        try {
            // Use reflection to get the texture region from custom attachment types
            Object attachment = physics.attachment;

            // Try to get texture region via common methods
            java.lang.reflect.Method getRegionMethod = null;
            try {
                getRegionMethod = attachment.getClass().getMethod("getRegion");
            } catch (NoSuchMethodException e) {
                try {
                    getRegionMethod = attachment.getClass().getMethod("getTextureRegion");
                } catch (NoSuchMethodException e2) {
                    // Try accessing fields directly
                    java.lang.reflect.Field regionField = null;
                    try {
                        regionField = attachment.getClass().getField("region");
                    } catch (NoSuchFieldException e3) {
                        try {
                            regionField = attachment.getClass().getDeclaredField("region");
                            regionField.setAccessible(true);
                        } catch (NoSuchFieldException e4) {
                            return false;
                        }
                    }
                    if (regionField != null) {
                        Object region = regionField.get(attachment);
                        if (region instanceof TextureRegion) {
                            return renderTextureRegion(sb, (TextureRegion) region, physics);
                        }
                    }
                    return false;
                }
            }

            if (getRegionMethod != null) {
                Object region = getRegionMethod.invoke(attachment);
                if (region instanceof TextureRegion) {
                    return renderTextureRegion(sb, (TextureRegion) region, physics);
                }
            }

        } catch (Exception e) {
            BaseMod.logger.info("Failed to render custom attachment " + attachmentName + ": " + e.getMessage());
        }

        return false;
    }

    private boolean renderTextureRegion(PolygonSpriteBatch sb, TextureRegion region, AttachmentPhysics physics) {
        float width = region.getRegionWidth() * Settings.scale;
        float height = region.getRegionHeight() * Settings.scale;

        sb.draw(region,
                physics.x - width / 2f,
                physics.y - height / 2f,
                width / 2f, height / 2f,
                width, height,
                1f, 1f, physics.rotation);

        return true;
    }

    private boolean renderRegionAttachment(PolygonSpriteBatch sb, RegionAttachment regionAttachment, AttachmentPhysics physics) {
        Object region = regionAttachment.getRegion();
        if (region == null) return false;

        // Handle both AtlasRegion and regular TextureRegion
        TextureRegion textureRegion;
        if (region instanceof TextureAtlas.AtlasRegion) {
            textureRegion = (TextureAtlas.AtlasRegion) region;
        } else if (region instanceof TextureRegion) {
            textureRegion = (TextureRegion) region;
        } else {
            return false;
        }

        float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                entityClassName, textureRegion.getRegionWidth(), textureRegion.getRegionHeight(),
                physics.originalScaleX, physics.originalScaleY);

        float finalWidth = dimensions[0] * Math.abs(regionAttachment.getScaleX());
        float finalHeight = dimensions[1] * Math.abs(regionAttachment.getScaleY());

        float scaleMultiplier = AttachmentScaleConfig.getAttachmentScaleMultiplier(entityClassName);
        float offsetX = regionAttachment.getX() * regionAttachment.getScaleX() * scaleMultiplier * Settings.scale;
        float offsetY = regionAttachment.getY() * regionAttachment.getScaleY() * scaleMultiplier * Settings.scale;

        sb.draw(textureRegion,
                physics.x - finalWidth / 2f + offsetX,
                physics.y - finalHeight / 2f + offsetY,
                finalWidth / 2f, finalHeight / 2f,
                finalWidth, finalHeight,
                1f, 1f, physics.rotation + regionAttachment.getRotation());

        return true;
    }

    private boolean renderMeshAttachment(PolygonSpriteBatch sb, MeshAttachment meshAttachment,
                                         AttachmentPhysics physics, String attachmentName) {
        Object region = meshAttachment.getRegion();
        if (region == null) return false;

        // Handle both AtlasRegion and regular TextureRegion
        TextureRegion textureRegion;
        boolean isAtlasRegion = false;
        if (region instanceof TextureAtlas.AtlasRegion) {
            textureRegion = (TextureAtlas.AtlasRegion) region;
            isAtlasRegion = true;
        } else if (region instanceof TextureRegion) {
            textureRegion = (TextureRegion) region;
        } else {
            return false;
        }

        float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                entityClassName, textureRegion.getRegionWidth(), textureRegion.getRegionHeight(),
                physics.originalScaleX, physics.originalScaleY);

        float finalRotation = physics.rotation;

        // Only apply special rotation logic for AtlasRegions
        if (isAtlasRegion && ((TextureAtlas.AtlasRegion) textureRegion).rotate &&
                entityClassName.equals(Sentry.ID) &&
                (attachmentName.contains("top") || attachmentName.contains("bottom"))) {
            finalRotation -= 90f;
        }

        sb.draw(textureRegion,
                physics.x - dimensions[0] / 2f,
                physics.y - dimensions[1] / 2f,
                dimensions[0] / 2f, dimensions[1] / 2f,
                dimensions[0], dimensions[1],
                1f, 1f, finalRotation);

        return true;
    }


    // ================================
    // HELPER METHODS
    // ================================

    private Bone findBodyBone(Skeleton skeleton) {
        if (skeleton == null) return null;

        String[] customBodyAttachments = CenterOfMassConfig.customBodyAttachments.get(entityClassName);
        if (customBodyAttachments != null) {
            for (String attachmentName : customBodyAttachments) {
                Bone boneFromAttachment = findBoneByAttachmentName(skeleton, attachmentName);
                if (boneFromAttachment != null) {
                    return boneFromAttachment;
                }
                Bone directBone = skeleton.findBone(attachmentName);
                if (directBone != null) {
                    return directBone;
                }
            }
        }

        String[] bodyBoneNames = {"body", "torso", "chest", "spine", "hip", "pelvis", "trunk"};
        for (String boneName : bodyBoneNames) {
            Bone bone = skeleton.findBone(boneName);
            if (bone != null) {
                return bone;
            }
        }

        return null;
    }

    private Bone findBoneByAttachmentName(Skeleton skeleton, String attachmentName) {
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

    private AttachmentPhysics findParentForChild(String monsterName, String childName,
                                                 HashMap<String, AttachmentPhysics> parentAttachments) {
        for (Map.Entry<String, AttachmentPhysics> entry : parentAttachments.entrySet()) {
            String parentName = entry.getValue().getAttachmentName();
            if (AttachmentConfig.isChildAttachment(monsterName, parentName, childName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean canLog() {
        if (!printUpdateLogs) return false;
        long currentTime = System.currentTimeMillis();
        long timeSinceCreation = currentTime - creationTime;
        return timeSinceCreation < 200 || updateCount % 60 == 0 ||
                (currentTime - lastLogTime) >= 100 || MathUtils.random() < 0.03f;
    }

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


    // ================================
    // PUBLIC UTILITY METHODS
    // ================================

    public boolean isProperlyInitialized() {
        return mainBody != null && (isImageBased || (!boneWobbles.isEmpty()));
    }

    public boolean hasSettledOnGround() {
        return mainBody.hasSettledOnGround();
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

    // Getters for various properties
    public float getPhysicsToVisualOffsetX() { return physicsToVisualOffsetX; }
    public float getPhysicsToVisualOffsetY() { return physicsToVisualOffsetY; }
    public AbstractCreature getAssociatedEntity() { return associatedEntity; } // Changed from getAssociatedMonster
    public HashMap<String, AttachmentPhysics> getAttachmentBodies() { return attachmentBodies; }
    public float getGroundY() { return groundY; }
    public boolean isImageBased() { return isImageBased; }
    public String getRagdollId() { return ragdollId; }
    public int getUpdateCount() { return updateCount; }
    public boolean getAllowsFreeRotation() { return allowsFreeRotation; }
    public String getEntityClassName() { return entityClassName; } // Changed from getMonsterClassName
}