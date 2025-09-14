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
    private final AbstractMonster associatedMonster;
    private final String monsterClassName;
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

    /** Constructor for skeleton-based ragdolls */
    public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY,
                            String monsterClassName, AbstractMonster monster) {
        this.boneWobbles = new HashMap<>();
        this.monsterClassName = monsterClassName;
        this.associatedMonster = monster;
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(monsterClassName);
        this.isImageBased = false;

        // Calculate dynamic center of mass correction
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(skeleton, monsterClassName);
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, monsterClassName);

        // Establish fixed physics-visual relationship
        this.physicsToVisualOffsetX = (monster.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (monster.drawY - correctedStartY);
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "Ragdoll_" + System.currentTimeMillis() % 10000;

        this.fadeableSlots = findFadeableSlots(skeleton);

        // Initialize attachments and bone wobbles
        initializeAttachments(skeleton, monster, startX, startY);
        initializeBoneWobbles(skeleton);
    }

    /** Constructor for image-based ragdolls (like Hexaghost) */
    public MultiBodyRagdoll(float startX, float startY, float groundLevel,
                            String monsterClassName, AbstractMonster monster) {
        this.boneWobbles = new HashMap<>();
        this.monsterClassName = monsterClassName;
        this.associatedMonster = monster;
        this.attachmentBodies = new HashMap<>();
        this.groundY = groundLevel;
        this.allowsFreeRotation = FREE_ROTATION_ENEMIES.contains(monsterClassName);
        this.isImageBased = true;

        // Apply center of mass correction for image-based ragdolls too
        CenterOfMassConfig.CenterOffset centerOffset = CenterOfMassConfig.calculateCenterOffset(null, monsterClassName);
        float correctedStartX = startX + centerOffset.x;
        float correctedStartY = startY + centerOffset.y;

        this.mainBody = new RagdollPhysics(correctedStartX, correctedStartY, 0, 0, groundLevel, monsterClassName);

        // Establish fixed physics-visual relationship
        this.physicsToVisualOffsetX = (monster.drawX - correctedStartX);
        this.physicsToVisualOffsetY = (monster.drawY - correctedStartY);
        this.initialOffsetX = monster.drawX - startX;
        this.initialOffsetY = monster.drawY - startY;

        this.creationTime = System.currentTimeMillis();
        this.ragdollId = "ImageRagdoll_" + System.currentTimeMillis() % 10000;
        this.fadeableSlots = new ArrayList<>();
    }


    // ================================
    // INITIALIZATION METHODS
    // ================================

    /** Initialize attachment physics bodies with parent-child relationships */
    private void initializeAttachments(Skeleton skeleton, AbstractMonster monster, float startX, float startY) {
        float overkillDamage = OverkillTracker.getOverkillDamage(monster);
        HashMap<String, AttachmentPhysics> parentAttachments = new HashMap<>();
        List<SlotAttachmentData> potentialChildren = new ArrayList<>();

        // First pass: Create parent attachments
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();
                boolean shouldDetach = AttachmentConfig.shouldDetachAttachment(monsterClassName, attachmentName, overkillDamage);

                if (shouldDetach) {
                    float[] position = calculateAttachmentPosition(slot, monster, startX, startY);
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
            AttachmentPhysics parentAttachment = findParentForChild(monsterClassName, data.attachmentName, parentAttachments);
            if (parentAttachment != null) {
                float[] position = calculateAttachmentPosition(data.slot, monster, startX, startY);
                AttachmentPhysics childAttachment = new AttachmentPhysics(
                        position[0], position[1], groundY, data.bone,
                        data.slot.getAttachment(), data.attachmentName, parentAttachment);

                attachmentBodies.put(data.attachmentName, childAttachment);
                attachmentDrawOrder.add(data.attachmentName);
            }
        }
    }

    /** Calculate proper position for an attachment based on its type */
    private float[] calculateAttachmentPosition(Slot slot, AbstractMonster monster, float startX, float startY) {
        Bone bone = slot.getBone();

        if (slot.getAttachment() instanceof RegionAttachment) {
            RegionAttachment regionAttachment = (RegionAttachment) slot.getAttachment();
            float localX = regionAttachment.getX();
            float localY = regionAttachment.getY();
            float transformedX = bone.getA() * localX + bone.getB() * localY;
            float transformedY = bone.getC() * localX + bone.getD() * localY;
            return new float[]{
                    monster.drawX + (bone.getWorldX() + transformedX) * Settings.scale,
                    monster.drawY + (bone.getWorldY() + transformedY) * Settings.scale
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
        String[] fadeableParts = FadeablePartsConfig.getFadeableParts(monsterClassName);
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

    /** Apply physics state to skeleton bones */
    public void applyToBones(Skeleton skeleton, AbstractMonster monster) {
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

    /** Apply physics positioning to image-based ragdolls */
    public void applyToImage(AbstractMonster monster) {
        monster.drawX = mainBody.x + physicsToVisualOffsetX;
        monster.drawY = mainBody.y + physicsToVisualOffsetY;
    }


    // ================================
    // FORCE APPLICATION
    // ================================

    /** Apply forces to all physics bodies with monster-specific modifiers */
    public void applyGlobalForce(float forceX, float forceY) {
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

    /** Render detached attachments with proper scaling and positioning */
    public void renderDetachedAttachments(PolygonSpriteBatch sb, TextureAtlas atlas, AbstractMonster monster) {
        // Store and set proper blend function
        int srcFunc = sb.getBlendSrcFunc();
        int dstFunc = sb.getBlendDstFunc();
        if (srcFunc != GL20.GL_SRC_ALPHA || dstFunc != GL20.GL_ONE_MINUS_SRC_ALPHA) {
            sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Skip if monster has completely faded
        Color monsterColor = monster.tint.color;
        if (monsterColor.a <= 0) {
            return;
        }

        // Render attachments in draw order
        for (String attachmentName : attachmentDrawOrder) {
            AttachmentPhysics attachmentPhysics = attachmentBodies.get(attachmentName);
            if (attachmentPhysics == null) continue;

            Color currentColor = sb.getColor();
            sb.setColor(monsterColor);

            renderSingleAttachment(sb, atlas, attachmentPhysics, attachmentName);
            sb.setColor(currentColor);
        }

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
                }
            } catch (Exception e) {
                // Silent fallback to atlas rendering
            }
        }

        // Fallback: render from atlas
        if (!rendered) {
            TextureAtlas.AtlasRegion region = atlas.findRegion(attachmentName);
            if (region != null) {
                float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                        monsterClassName, region.getRegionWidth(), region.getRegionHeight(),
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

    private boolean renderRegionAttachment(PolygonSpriteBatch sb, RegionAttachment regionAttachment, AttachmentPhysics physics) {
        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) regionAttachment.getRegion();
        if (region == null) return false;

        float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                monsterClassName, region.getRegionWidth(), region.getRegionHeight(),
                physics.originalScaleX, physics.originalScaleY);

        float finalWidth = dimensions[0] * Math.abs(regionAttachment.getScaleX());
        float finalHeight = dimensions[1] * Math.abs(regionAttachment.getScaleY());

        float scaleMultiplier = AttachmentScaleConfig.getAttachmentScaleMultiplier(monsterClassName);
        float offsetX = regionAttachment.getX() * regionAttachment.getScaleX() * scaleMultiplier * Settings.scale;
        float offsetY = regionAttachment.getY() * regionAttachment.getScaleY() * scaleMultiplier * Settings.scale;

        sb.draw(region,
                physics.x - finalWidth / 2f + offsetX,
                physics.y - finalHeight / 2f + offsetY,
                finalWidth / 2f, finalHeight / 2f,
                finalWidth, finalHeight,
                1f, 1f, physics.rotation + regionAttachment.getRotation());

        return true;
    }

    private boolean renderMeshAttachment(PolygonSpriteBatch sb, MeshAttachment meshAttachment,
                                         AttachmentPhysics physics, String attachmentName) {
        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) meshAttachment.getRegion();
        if (region == null) return false;

        float[] dimensions = AttachmentScaleConfig.calculateRenderDimensions(
                monsterClassName, region.getRegionWidth(), region.getRegionHeight(),
                physics.originalScaleX, physics.originalScaleY);

        float finalRotation = physics.rotation;
        if (region.rotate && monsterClassName.equals(Sentry.ID) &&
                (attachmentName.contains("top") || attachmentName.contains("bottom"))) {
            finalRotation -= 90f;
        }

        sb.draw(region,
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

        String[] customBodyAttachments = CenterOfMassConfig.customBodyAttachments.get(monsterClassName);
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
    public AbstractMonster getAssociatedMonster() { return associatedMonster; }
    public HashMap<String, AttachmentPhysics> getAttachmentBodies() { return attachmentBodies; }
    public float getGroundY() { return groundY; }
    public boolean isImageBased() { return isImageBased; }
    public String getRagdollId() { return ragdollId; }
    public int getUpdateCount() { return updateCount; }
    public boolean getAllowsFreeRotation() { return allowsFreeRotation; }
    public String getMonsterClassName() { return monsterClassName; }
}