
/*

package ragdollphysics.patches;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RagdollPatchesEXPERIMENTAL {
    public static final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies =
            new HashMap<>();
    private static final Set<AbstractMonster> FAILED_RAGDOLLS = new HashSet<>();
    private static Field atlasField, imgField, skeletonField, srField;
    private static Method renderNameMethod;

    public static class AttachmentConfig {
        private static final HashMap<String, String[]> MONSTER_ATTACHMENTS =
                new HashMap<>();
        private static final String[] GLOBAL_ATTACHMENTS = {"weapon", "sword",
                "blade", "staff", "wand", "rod", "dagger", "spear", "axe", "club",
                "mace", "bow", "shield", "orb", "crystal", "gem", "whip"};

        static {
            // Configure which attachments should be physics bodies per monster
            MONSTER_ATTACHMENTS.put(TimeEater.ID, new String[] {"clock"});
            MONSTER_ATTACHMENTS.put(
                    Sentry.ID, new String[] {"top", "bottom", "jewel"});
            MONSTER_ATTACHMENTS.put(SlaverRed.ID, new String[] {"weponred", "net"});
            // Add more monsters as needed
        }

        public static String[] getAttachmentsForMonster(String monsterName) {
            return MONSTER_ATTACHMENTS.getOrDefault(monsterName, new String[0]);
        }

        public static boolean shouldDetachAttachment(
                String monsterName, String attachmentName) {
            String attachmentLower = attachmentName.toLowerCase();

            // Check global attachments first
            for (String globalAttachment : GLOBAL_ATTACHMENTS) {
                if (attachmentLower.contains(globalAttachment.toLowerCase())) {
                    return true;
                }
            }

            // Check monster-specific attachments
            String[] attachments = getAttachmentsForMonster(monsterName);
            for (String attachment : attachments) {
                // More flexible matching - check if the attachment name ends with our
                // target
                if (attachmentLower.endsWith(attachment.toLowerCase())
                        || attachmentLower.contains("/" + attachment.toLowerCase())) {
                    return true;
                }
            }

            return false;
        }
    }

    static {
        try {
            atlasField = AbstractCreature.class.getDeclaredField("atlas");
            atlasField.setAccessible(true);
            imgField = AbstractMonster.class.getDeclaredField("img");
            imgField.setAccessible(true);
            skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);
            srField = AbstractCreature.class.getDeclaredField("sr");
            srField.setAccessible(true);
            renderNameMethod = AbstractMonster.class.getDeclaredMethod(
                    "renderName", SpriteBatch.class);
            renderNameMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // General failsafe method - checks if ragdoll is viable
    private static boolean isRagdollViable(AbstractMonster monster) {
        try {
            // If this monster has failed before, don't try again (and don't log
            // repeatedly)
            if (FAILED_RAGDOLLS.contains(monster)) {
                return false;
            }

            // Special case for image-based monsters like Hexaghost
            if (isImageBasedMonster(monster)) {
                BaseMod.logger.info("Monster " + monster.getClass().getSimpleName()
                        + " is image-based, using special ragdoll");
                return true;
            }

            // Check if monster has valid skeleton with bones
            Skeleton skeleton;
            try {
                skeleton = (Skeleton) skeletonField.get(monster);
            } catch (IllegalAccessException e) {
                BaseMod.logger.info("Cannot access skeleton field for "
                        + monster.getClass().getSimpleName() + ": " + e.getMessage());
                // Mark as failed to prevent repeated logging
                FAILED_RAGDOLLS.add(monster);
                return false;
            }

            if (skeleton == null) {
                BaseMod.logger.info("Monster " + monster.getClass().getSimpleName()
                        + " has null skeleton");
                // Mark as failed to prevent repeated logging
                FAILED_RAGDOLLS.add(monster);
                return false;
            }

            if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
                BaseMod.logger.info("Monster " + monster.getClass().getSimpleName()
                        + " has no bones (bones: "
                        + (skeleton.getBones() == null ? "null" : skeleton.getBones().size)
                        + ")");
                // Mark as failed to prevent repeated logging
                FAILED_RAGDOLLS.add(monster);
                return false;
            }

            // Check if skeleton renderer exists
            SkeletonRenderer sr;
            try {
                sr = (SkeletonRenderer) srField.get(monster);
            } catch (IllegalAccessException e) {
                BaseMod.logger.info("Cannot access SkeletonRenderer field for "
                        + monster.getClass().getSimpleName() + ": " + e.getMessage());
                // Mark as failed to prevent repeated logging
                FAILED_RAGDOLLS.add(monster);
                return false;
            }

            if (sr == null) {
                BaseMod.logger.info("Monster " + monster.getClass().getSimpleName()
                        + " has null SkeletonRenderer");
                // Mark as failed to prevent repeated logging
                FAILED_RAGDOLLS.add(monster);
                return false;
            }

            BaseMod.logger.info("Monster " + monster.getClass().getSimpleName()
                    + " ragdoll viability check PASSED - skeleton has "
                    + skeleton.getBones().size + " bones, renderer exists");

            // Additional viability checks can go here
            return true;

        } catch (Exception e) {
            BaseMod.logger.info("Ragdoll viability check failed for "
                    + monster.getClass().getSimpleName()
                    + ", using default death: " + e.getMessage());
            // Mark as failed to prevent repeated checking
            FAILED_RAGDOLLS.add(monster);
            return false;
        }
    }

    // Check if this is an image-based monster (no skeleton)
    private static boolean isImageBasedMonster(AbstractMonster monster) {
        try {
            // Check if monster has an image but no skeleton
            Texture img = (Texture) imgField.get(monster);
            Skeleton skeleton = (Skeleton) skeletonField.get(monster);

            return img != null && skeleton == null;
        } catch (Exception e) {
            return false;
        }
    }

    // Special handling for monsters with custom components
    private static void handleSpecialMonsterComponents(AbstractMonster monster) {
        String monsterClass = monster.getClass().getSimpleName();
        BaseMod.logger.info(
                "Checking for special handling for monster: " + monsterClass);

        if (monsterClass.equals("Hexaghost")) {
            BaseMod.logger.info("Applying Hexaghost special handling");
            handleHexaghostSpecialComponents(monster);
        }
        // Add other special cases here as needed
    }

    private static void handleHexaghostSpecialComponents(
            AbstractMonster monster) {
        try {
            // Access Hexaghost's orbs and body through reflection
            Field orbsField = monster.getClass().getDeclaredField("orbs");
            orbsField.setAccessible(true);
            ArrayList<?> orbs = (ArrayList<?>) orbsField.get(monster);

            // Deactivate and hide the orbs
            if (orbs != null) {
                for (Object orb : orbs) {
                    try {
                        // Try deactivate first
                        Method deactivateMethod = orb.getClass().getMethod("deactivate");
                        deactivateMethod.invoke(orb);

                        // Then hide
                        Method hideMethod = orb.getClass().getMethod("hide");
                        hideMethod.invoke(orb);

                        BaseMod.logger.info("Deactivated and hidden Hexaghost orb");
                    } catch (Exception e) {
                        BaseMod.logger.info(
                                "Could not deactivate/hide Hexaghost orb: " + e.getMessage());
                    }
                }
            }

            // Let the body fade naturally - don't dispose it immediately
            BaseMod.logger.info(
                    "Hexaghost orbs deactivated, body will fade naturally");

        } catch (Exception e) {
            BaseMod.logger.info(
                    "Could not handle Hexaghost special components: " + e.getMessage());
            // Not critical - ragdoll can still proceed
        }
    }

    // Helper method for clean fallback to default death animation
    private static SpireReturn<Void> fallbackToDefaultDeath(
            AbstractMonster monster) {
        // Replicate the original updateDeathAnimation logic exactly
        if (monster.isDying) {
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
            if (monster.deathTimer < 1.8F && !monster.tintFadeOutCalled) {
                monster.tintFadeOutCalled = true;
                monster.tint.fadeOut();
            }
        }

        if (monster.deathTimer < 0.0F) {
            monster.isDead = true;
            if (AbstractDungeon.getMonsters().areMonstersDead()
                    && !AbstractDungeon.getCurrRoom().isBattleOver
                    && !AbstractDungeon.getCurrRoom().cannotLose) {
                AbstractDungeon.getCurrRoom().endBattle();
            }
            monster.dispose();
            monster.powers.clear();

            // Clean up our tracking
            ragdollBodies.remove(monster);
            FAILED_RAGDOLLS.remove(monster);
        }
        return SpireReturn.Return();
    }

    @SpirePatch(clz = AbstractMonster.class, method = "updateDeathAnimation")
    public static class DeathAnimationPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance) {
            if (!__instance.isDying) {
                return SpireReturn.Continue();
            }

            if (__instance.isDead) {
                return SpireReturn.Return();
            }

            if (!ragdollBodies.containsKey(__instance)) {
                // ONLY check viability when we need to initialize ragdoll
                if (!isRagdollViable(__instance)) {
                    return fallbackToDefaultDeath(__instance);
                }

                try {
                    // Handle special monster components before ragdoll initialization
                    handleSpecialMonsterComponents(__instance);

                    initializeRagdoll(__instance);

                    // Verify ragdoll was created successfully
                    if (!ragdollBodies.containsKey(__instance)) {
                        BaseMod.logger.info("Ragdoll initialization silent failure for "
                                + __instance.getClass().getSimpleName());
                        return fallbackToDefaultDeath(__instance);
                    }

                } catch (Exception e) {
                    BaseMod.logger.info("Ragdoll initialization failed for "
                            + __instance.getClass().getSimpleName() + ": " + e.getMessage());

                    // Mark this monster as failed and use default death
                    FAILED_RAGDOLLS.add(__instance);
                    return fallbackToDefaultDeath(__instance);
                }
            }

            MultiBodyRagdoll ragdoll = ragdollBodies.get(__instance);
            if (ragdoll != null) {
                try {
                    ragdoll.update(Gdx.graphics.getDeltaTime());

                    if (ragdoll.hasSettledOnGround()) {
                        __instance.deathTimer -= Gdx.graphics.getDeltaTime();
                    }
                } catch (Exception e) {
                    BaseMod.logger.error("Ragdoll update failed for "
                            + __instance.getClass().getSimpleName() + ": " + e.getMessage());

                    // Remove failed ragdoll and continue with default behavior
                    ragdollBodies.remove(__instance);
                    FAILED_RAGDOLLS.add(__instance);
                    return fallbackToDefaultDeath(__instance);
                }
            } else {
                __instance.deathTimer -= Gdx.graphics.getDeltaTime();
            }

            if (__instance.deathTimer < 1.2F && !__instance.tintFadeOutCalled) {
                __instance.tintFadeOutCalled = true;
                __instance.tint.fadeOut();
            }

            if (__instance.deathTimer < 0.0F) {
                __instance.isDead = true;
                if (AbstractDungeon.getMonsters().areMonstersDead()
                        && !AbstractDungeon.getCurrRoom().isBattleOver
                        && !AbstractDungeon.getCurrRoom().cannotLose) {
                    AbstractDungeon.getCurrRoom().endBattle();
                }
                ragdollBodies.remove(__instance);
                FAILED_RAGDOLLS.remove(__instance); // Clean up on death
                __instance.dispose();
            }
            return SpireReturn.Return();
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "render")
    public static class RenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(
                AbstractMonster __instance, SpriteBatch sb) {
            if (ragdollBodies.containsKey(__instance)) {
                try {
                    renderRagdoll(__instance, sb, ragdollBodies.get(__instance));
                } catch (Exception e) {
                    BaseMod.logger.error("Ragdoll rendering failed for "
                            + __instance.getClass().getSimpleName() + ": " + e.getMessage());

                    // Remove failed ragdoll and fall back to default rendering
                    ragdollBodies.remove(__instance);
                    FAILED_RAGDOLLS.add(__instance);
                    return SpireReturn.Continue();
                }
                return SpireReturn.Return();
            }
            return SpireReturn.Continue();
        }
    }

    private static void initializeRagdoll(AbstractMonster monster) {
        try {
            BaseMod.logger.info("=== RAGDOLL INITIALIZATION ===");
            BaseMod.logger.info("Monster: " + monster.getClass().getSimpleName()
                    + " at position (" + monster.drawX + ", " + monster.drawY + ")");

            // Handle image-based monsters (like Hexaghost)
            if (isImageBasedMonster(monster)) {
                BaseMod.logger.info("Initializing simple image ragdoll for "
                        + monster.getClass().getSimpleName());
                initializeImageRagdoll(monster);
                return;
            }

            // Handle skeleton-based monsters (normal case)
            Skeleton skeleton;
            try {
                skeleton = (Skeleton) skeletonField.get(monster);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Failed to access skeleton field: " + e.getMessage());
            }

            if (skeleton == null) {
                throw new RuntimeException("Skeleton is null");
            }

            if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
                throw new RuntimeException("Skeleton has no bones");
            }

            SkeletonRenderer sr;
            try {
                sr = (SkeletonRenderer) srField.get(monster);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Failed to access SkeletonRenderer field: " + e.getMessage());
            }

            if (sr == null) {
                throw new RuntimeException("SkeletonRenderer is null");
            }

            // CHANGED: Pass monster class name to constructor
            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    skeleton, monster.drawY, monster.drawX, monster.drawY,
                    monster.getClass().getSimpleName(), monster);

            // Verify ragdoll creation was successful
            if (!ragdoll.isProperlyInitialized()) {
                throw new RuntimeException(
                        "Ragdoll creation failed - not properly initialized");
            }

            float forceX = MathUtils.random(600f, 900f);
            float forceY = MathUtils.random(1000f, 1500f);

            BaseMod.logger.info(
                    "Applying initial force: (" + forceX + ", " + forceY + ")");
            ragdoll.applyGlobalForce(forceX, forceY);

            // Initialize lastRotation to prevent phantom rotation on first frame
            ragdoll.lastRotation = ragdoll.mainBody.rotation;
            ragdoll.totalRotationDegrees = 0f;

            // ENHANCED: Hierarchy-aware bone wobble initialization
            BaseMod.logger.info("=== HIERARCHY-AWARE BONE WOBBLE INITIALIZATION ===");
            int visualLimbs = 0;
            int visualBones = 0;
            int controlBones = 0;
            int attachmentBones = 0;

            for (Bone bone : skeleton.getBones()) {
                // CHANGED: Use new constructor with bone parameter
                BoneWobble wobble = new BoneWobble(bone.getRotation(), bone);

                // FIXED: More reasonable depth reduction
                float depthReduction = Math.min(wobble.chainDepth * 0.08f, 0.4f);
                wobble.angularVelocity =
                        MathUtils.random(-360f, 360f) * (1.0f - depthReduction);

                // Check if bone has visual attachment
                boolean hasVisualAttachment = hasVisualAttachment(bone);

                // Categorize bones
                String boneName = bone.getData().getName().toLowerCase();

                // Check if this bone will become a detached attachment
                boolean willBeDetached = false;
                for (Slot slot : skeleton.getSlots()) {
                    if (slot.getBone() == bone
                            && slot.getAttachment() instanceof RegionAttachment) {
                        RegionAttachment regionAttachment =
                                (RegionAttachment) slot.getAttachment();
                        String attachmentName = regionAttachment.getName();
                        if (AttachmentConfig.shouldDetachAttachment(
                                monster.getClass().getSimpleName(), attachmentName)) {
                            willBeDetached = true;
                            break;
                        }
                    }
                }

                boolean isVisualLimb = hasVisualAttachment
                        && boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");

                if (willBeDetached) {
                    // Bones with detached attachments get moderate enhancement
                    wobble.angularVelocity *= MathUtils.random(1.2f, 1.8f);
                    attachmentBones++;
                    BaseMod.logger.info("Enhanced attachment bone: "
                            + bone.getData().getName() + " with angVel: "
                            + String.format("%.1f", wobble.angularVelocity));
                } else if (isVisualLimb) {
                    // Visual limbs get enhanced motion but with reasonable depth
                    // constraints
                    wobble.angularVelocity *=
                            MathUtils.random(1.8f, 2.5f) * (1.0f - depthReduction * 0.3f);
                    visualLimbs++;
                    BaseMod.logger.info("Enhanced visual limb: "
                            + bone.getData().getName() + " with angVel: "
                            + String.format("%.1f", wobble.angularVelocity)
                            + ", depth: " + wobble.chainDepth);
                } else if (hasVisualAttachment) {
                    // Other visual bones get slight enhancement with reasonable depth
                    // reduction
                    wobble.angularVelocity *=
                            MathUtils.random(0.8f, 1.3f) * (1.0f - depthReduction * 0.2f);
                    visualBones++;
                } else {
                    // Animation control bones get reduced motion but not too much
                    wobble.angularVelocity *=
                            MathUtils.random(0.2f, 0.5f) * (1.0f - depthReduction * 0.5f);
                    controlBones++;
                }

                // Minimal random initial rotation offset (reduced)
                wobble.rotation += MathUtils.random(-2f, 2f);
                ragdoll.boneWobbles.put(bone, wobble);
            }

            BaseMod.logger.info("Hierarchy-aware bone initialization - Visual Limbs: "
                    + visualLimbs + ", Visual Parts: " + visualBones + ", Control Bones: "
                    + controlBones + ", Attachment Bones: " + attachmentBones
                    + ", Total: " + ragdoll.boneWobbles.size());

            ragdollBodies.put(monster, ragdoll);
            BaseMod.logger.info(
                    "Ragdoll initialized successfully with hierarchical bone dynamics");
            monster.deathTimer = 2.5f;

        } catch (Exception e) {
            BaseMod.logger.error("Failed to initialize ragdoll for "
                    + monster.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            monster.deathTimer = 3.0f;
            throw e; // Re-throw to be caught by calling method
        }
    }

    private static void initializeImageRagdoll(AbstractMonster monster) {
        try {
            MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(
                    monster.drawX, monster.drawY, monster.drawY,
                    monster.getClass().getSimpleName(), monster);

            // Use same force as skeleton-based monsters
            float forceX = MathUtils.random(600f, 900f);
            float forceY = MathUtils.random(1000f, 1500f);

            BaseMod.logger.info(
                    "Applying image ragdoll force: (" + forceX + ", " + forceY + ")");
            ragdoll.applyGlobalForce(forceX, forceY);

            ragdollBodies.put(monster, ragdoll);
            monster.deathTimer = 2.5f;

            BaseMod.logger.info("Simple image ragdoll initialized for "
                    + monster.getClass().getSimpleName());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize image ragdoll: " + e.getMessage());
        }
    }

    private static boolean hasVisualAttachment(Bone bone) {
        try {
            for (Slot slot : bone.getSkeleton().getSlots()) {
                if (slot.getBone() == bone && slot.getAttachment() != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true; // Default to visual if we can't determine
        }
        return false;
    }

    private static void renderRagdoll(AbstractMonster monster, SpriteBatch sb,
                                      MultiBodyRagdoll ragdoll) throws Exception {
        if (monster.tint.color.a > 0) {
            TextureAtlas atlas = (TextureAtlas) atlasField.get(monster);
            if (atlas == null) {
                Texture img = (Texture) imgField.get(monster);
                sb.setColor(monster.tint.color);
                if (img != null) {
                    float centerX = ragdoll.getCenterX();
                    float centerY = ragdoll.getCenterY();
                    float rotation = ragdoll.getAverageRotation();
                    float imgCenterX = img.getWidth() * Settings.scale / 2.0F;
                    float imgCenterY = img.getHeight() * Settings.scale / 2.0F;
                    sb.draw(img, centerX - imgCenterX, centerY - imgCenterY, imgCenterX,
                            imgCenterY, img.getWidth() * Settings.scale,
                            img.getHeight() * Settings.scale, 1f, 1f, rotation, 0, 0,
                            img.getWidth(), img.getHeight(), monster.flipHorizontal,
                            monster.flipVertical);
                }
            } else {
                Skeleton skeleton = (Skeleton) skeletonField.get(monster);
                SkeletonRenderer sr = (SkeletonRenderer) srField.get(monster);
                ragdoll.applyToBones(skeleton, monster);
                skeleton.updateWorldTransform();
                skeleton.setColor(monster.tint.color);
                skeleton.setFlip(monster.flipHorizontal, monster.flipVertical);

                sb.end();
                CardCrawlGame.psb.begin();
                sr.draw(CardCrawlGame.psb, skeleton);
                ragdoll.renderDetachedAttachments(CardCrawlGame.psb, atlas);
                CardCrawlGame.psb.end();
                sb.begin();
                sb.setBlendFunction(770, 771);
            }
        }

        if (!AbstractDungeon.player.isDead) {
            monster.renderHealth(sb);
            renderNameMethod.invoke(monster, sb);
        }
    }

    public static class MultiBodyRagdoll {
        private final HashMap<Bone, BoneWobble> boneWobbles;
        private final HashMap<String, AttachmentPhysics> attachmentBodies;
        private final RagdollPhysics mainBody;
        private final float groundY;

        // NEW: Fields for shadow fading
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
        private float totalRotationDegrees = 0f;
        private float lastRotation = 0f;

        private static final float FIXED_TIMESTEP = 1.0f / 60.0f;
        private float accumulator = 0f;
        private final String monsterClassName;

        // Enhanced logging control
        private final long creationTime;
        long lastLogTime = 0;
        private int updateCount = 0;
        private int physicsStepCount = 0;
        private final String ragdollId;

        // NEW: Flag to indicate if this is an image-based ragdoll
        private final boolean isImageBased;

        // Original constructor for skeleton-based ragdolls
        public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY, String monsterClassName, AbstractMonster monster) {
            this.boneWobbles = new HashMap<>();
            this.monsterClassName = monsterClassName;
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
                        String attachmentName =
                                slot.getAttachment().getName().toLowerCase();
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

                    // Check if this attachment should be detached for this monster
                    if (AttachmentConfig.shouldDetachAttachment(
                            monsterClassName, attachmentName)) {
                        // Create AttachmentPhysics for ANY attachment type
                        attachmentBodies.put(attachmentName,
                                new AttachmentPhysics(
                                        startX + bone.getWorldX() * Settings.scale,
                                        startY + bone.getWorldY() * Settings.scale, groundLevel,
                                        bone, slot.getAttachment(), attachmentName));

                        attachmentCount++;
                        String attachmentType =
                                slot.getAttachment().getClass().getSimpleName();
                        BaseMod.logger.info("[" + ragdollId + "] Found detachable "
                                + attachmentType + ": '" + attachmentName
                                + "' on bone: " + bone.getData().getName());
                    }
                }
            }

            BaseMod.logger.info("[" + ragdollId + "] Created " + attachmentCount
                    + " attachment physics bodies");

            // Initialize bone wobbles for remaining bones
            for (Bone bone : skeleton.getBones()) {
                boneWobbles.put(bone, new BoneWobble(bone.getRotation(), bone));
            }
        }

        // NEW: Constructor for image-based ragdolls (like Hexaghost)
        public MultiBodyRagdoll(float startX, float startY, float groundLevel, String monsterClassName, AbstractMonster monster) {
            this.boneWobbles = new HashMap<>(); // Empty for image ragdolls
            this.monsterClassName = monsterClassName;
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

            BaseMod.logger.info("[" + ragdollId + "] Created image ragdoll for " + monsterClassName
                    + " at (" + startX + ", " + startY + "), ground: " + groundLevel);
        }

        public boolean isProperlyInitialized() {
            return mainBody != null && (isImageBased || (!boneWobbles.isEmpty()));
        }

        boolean canLog() {
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

            // NEW: Update the shadow fade timer
            if (shadowSlot != null && shadowFadeTimer < SHADOW_FADE_DURATION) {
                shadowFadeTimer += deltaTime;
            }

            if (canLog()) {
                BaseMod.logger.info(
                        "[" + ragdollId + "] === UPDATE " + updateCount + " ===");
                BaseMod.logger.info("[" + ragdollId + "] DeltaTime: " + deltaTime
                        + ", Accumulator: " + accumulator);
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

            // Wall collision handling (same as before)
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

            // Update attachments instead of weapons
            int activeAttachments = 0;
            for (AttachmentPhysics attachment : attachmentBodies.values()) {
                attachment.update(deltaTime);
                if (Math.abs(attachment.velocityX) + Math.abs(attachment.velocityY)
                        > 10f) {
                    activeAttachments++;
                }
            }

            if (activeAttachments > 0 && physicsStepCount % 60 == 0 && canLog()) {
                BaseMod.logger.info("[" + ragdollId + "] " + activeAttachments
                        + " attachments still moving");
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
            mainBody.angularVelocity +=
                    MathUtils.random(-90f, 90f) * (1.0f + wallImpactIntensity * 0.3f);

            if (canLog()) {
                BaseMod.logger.info("[" + ragdollId
                        + "] Wall collision impact - intensity: "
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
                wobble.angularVelocity +=
                        MathUtils.random(-45f, 45f) * (1.0f + wallImpactIntensity * 0.2f);
            }
        }

        private void applyRotationLimiting(float deltaTime) {
            float rotationDelta = mainBody.rotation - lastRotation;
            if (rotationDelta > 180f)
                rotationDelta -= 360f;
            else if (rotationDelta < -180f)
                rotationDelta += 360f;

            boolean isActuallyOnGround = mainBody.y <= groundY + 1f;
            boolean hasVeryLowMomentum =
                    Math.abs(mainBody.velocityX) + Math.abs(mainBody.velocityY) < 150f;
            boolean isSettling = isActuallyOnGround && hasVeryLowMomentum;

            if (isSettling) {
                float oldTotalRotation = totalRotationDegrees;
                totalRotationDegrees += Math.abs(rotationDelta);

                float flipsCompleted = totalRotationDegrees / 360f;
                float oldAngVel = mainBody.angularVelocity;
                float dampingFactor = (float) Math.pow(0.98, flipsCompleted);
                mainBody.angularVelocity *= dampingFactor;

                if (canLog() || Math.abs(rotationDelta) > 10f) {
                    BaseMod.logger.info("[" + ragdollId
                            + "] Ground rotation limiting - delta: "
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
                    BaseMod.logger.info("[" + ragdollId
                            + "] Settlement interrupted - momentum: "
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

            mainBody.velocityX += forceX * 0.8f;
            mainBody.velocityY += forceY * 0.8f;
            lastRotation = mainBody.rotation;

            // Reduced angular velocity
            float upwardVelocity = Math.max(0, mainBody.velocityY);
            float flipIntensity = Math.min(upwardVelocity / 1200f, 0.5f);
            float baseAngularVel = MathUtils.random(-72f, 72f);
            mainBody.angularVelocity +=
                    baseAngularVel * (1.0f + flipIntensity * 0.3f);

            if (canLog()) {
                BaseMod.logger.info("[" + ragdollId + "] After initial force - vel: ("
                        + String.format("%.1f", mainBody.velocityX) + ", "
                        + String.format("%.1f", mainBody.velocityY)
                        + "), angVel: " + String.format("%.1f", mainBody.angularVelocity)
                        + ", flipIntensity: " + String.format("%.2f", flipIntensity));
                lastLogTime = System.currentTimeMillis();
            }

            // Attachment force application
            int attachmentsAffected = 0;
            for (AttachmentPhysics attachment : attachmentBodies.values()) {
                attachment.velocityX += forceX * MathUtils.random(0.8f, 1.0f);
                attachment.velocityY += forceY * MathUtils.random(0.8f, 1.0f);
                float attachmentBaseAngular = MathUtils.random(-180f, 180f);
                attachment.angularVelocity +=
                        attachmentBaseAngular * (1.0f + flipIntensity * 0.5f);

                // Add some variation
                attachment.velocityX += MathUtils.random(-25f, 25f);
                attachment.velocityY += MathUtils.random(-15f, 35f);

                attachmentsAffected++;
            }

            // Bone wobble application
            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.angularVelocity +=
                        MathUtils.random(-90f, 90f) * (1.0f + flipIntensity * 0.5f);
            }

            if (canLog()) {
                BaseMod.logger.info("[" + ragdollId
                        + "] Global force complete - affected " + attachmentsAffected
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
                // Don't include bones that have detached attachments in the bounds
                // calculation
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

            skeleton.setPosition(mainBody.x + initialOffsetX - centerX * Settings.scale,
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
                float fadeProgress =
                        Math.min(1f, shadowFadeTimer / SHADOW_FADE_DURATION);
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
                            BaseMod.logger.info("[" + ragdollId
                                    + "] Hiding original attachment: " + attachmentName);
                        }
                    }
                }
            }

            if (updateCount % 120 == 0 && canLog()) {
                BaseMod.logger.info("[" + ragdollId
                        + "] Bones applied - hidden attachments: " + hiddenAttachments
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

        public void renderDetachedAttachments(
                PolygonSpriteBatch sb, TextureAtlas atlas) {
            int attachmentsRendered = 0;
            int attachmentsFailed = 0;

            for (Map.Entry<String, AttachmentPhysics> entry :
                    attachmentBodies.entrySet()) {
                String attachmentName = entry.getKey();
                AttachmentPhysics attachmentPhysics = entry.getValue();
                boolean rendered = false;

                if (attachmentPhysics.attachment != null) {
                    try {
                        // Handle RegionAttachment (your existing code)
                        if (attachmentPhysics.attachment instanceof RegionAttachment) {
                            RegionAttachment regionAttachment =
                                    (RegionAttachment) attachmentPhysics.attachment;
                            TextureAtlas.AtlasRegion region =
                                    (TextureAtlas.AtlasRegion) regionAttachment.getRegion();

                            if (region != null) {
                                // Your existing RegionAttachment rendering code
                                float regionPixelWidth = region.getRegionWidth();
                                float regionPixelHeight = region.getRegionHeight();
                                float attachmentScaleX = regionAttachment.getScaleX();
                                float attachmentScaleY = regionAttachment.getScaleY();
                                float attachmentRotation = regionAttachment.getRotation();
                                float finalWidth = regionPixelWidth * Math.abs(attachmentScaleX)
                                        * Settings.scale;
                                float finalHeight = regionPixelHeight
                                        * Math.abs(attachmentScaleY) * Settings.scale;
                                float offsetX =
                                        regionAttachment.getX() * attachmentScaleX * Settings.scale;
                                float offsetY =
                                        regionAttachment.getY() * attachmentScaleY * Settings.scale;

                                sb.draw(region, attachmentPhysics.x - finalWidth / 2f + offsetX,
                                        attachmentPhysics.y - finalHeight / 2f + offsetY,
                                        finalWidth / 2f, finalHeight / 2f, finalWidth, finalHeight,
                                        1f, 1f, attachmentPhysics.rotation + attachmentRotation);

                                rendered = true;
                                attachmentsRendered++;
                            }
                        }
                        // Handle MeshAttachment
// Handle MeshAttachment
                        else if (attachmentPhysics.attachment instanceof MeshAttachment) {
                            MeshAttachment meshAttachment = (MeshAttachment) attachmentPhysics.attachment;
                            TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) meshAttachment.getRegion();

                            if (region != null) {
                                float width = region.getRegionWidth() * Settings.scale;
                                float height = region.getRegionHeight() * Settings.scale;

                                // Apply bone scale if available
                                if (attachmentPhysics.originalBone != null) {
                                    width *= Math.abs(attachmentPhysics.originalScaleX);
                                    height *= Math.abs(attachmentPhysics.originalScaleY);
                                }

                                float finalRotation = attachmentPhysics.rotation;

                                // CASE-BY-CASE: Handle known rotated attachments by monster and attachment
                                if (region.rotate) {
                                    if (monsterClassName.equals(Sentry.ID) &&
                                            (attachmentName.contains("top") || attachmentName.contains("bottom"))) {
                                        finalRotation -= 90f;
                                        if (updateCount % 300 == 0 && canLog()) {
                                            BaseMod.logger.info("[" + ragdollId + "] Applied Sentry rotation correction for: " + attachmentName);
                                        }
                                    }
                                    // Add other monster-specific cases here as needed
                                }

                                sb.draw(region, attachmentPhysics.x - width / 2f,
                                        attachmentPhysics.y - height / 2f, width / 2f, height / 2f,
                                        width, height, 1f, 1f, finalRotation);

                                rendered = true;
                                attachmentsRendered++;
                            }
                        }
                    } catch (Exception e) {
                        BaseMod.logger.error("[" + ragdollId
                                + "] Failed to render attachment '" + attachmentName
                                + "': " + e.getMessage());
                        attachmentsFailed++;
                    }
                }

                // Fallback rendering (same as before but with less logging)
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
            }

            // REDUCED LOGGING: Less frequent summary
            if (updateCount % 300 == 0 && canLog()
                    && (attachmentsRendered > 0 || attachmentsFailed > 0)) {
                BaseMod.logger.info("[" + ragdollId
                        + "] Attachment render summary - rendered: " + attachmentsRendered
                        + ", failed: " + attachmentsFailed);
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

    public static class AttachmentPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;
        public final Attachment attachment; // CHANGED: Use generic Attachment
        // instead of RegionAttachment
        private final String attachmentId;
        public final Bone originalBone;
        private final float originalScaleX;
        private final float originalScaleY;
        private final String attachmentName;

        private static final float ATTACHMENT_BOUNCE_THRESHOLD = 150f;

        // CHANGED: Accept generic Attachment instead of RegionAttachment
        public AttachmentPhysics(float startX, float startY, float groundLevel,
                                 Bone bone, Attachment attachment, String attachmentName) {
            this.x = startX;
            this.y = startY;
            this.groundY = groundLevel;
            this.rotation = bone.getRotation();
            this.attachment = attachment;
            this.attachmentName = attachmentName;
            this.attachmentId = "Attachment_" + attachmentName + "_"
                    + System.currentTimeMillis() % 1000;

            this.originalBone = bone;
            this.originalScaleX = bone.getScaleX();
            this.originalScaleY = bone.getScaleY();

            String attachmentType = attachment != null
                    ? attachment.getClass().getSimpleName()
                    : "Unknown";
            BaseMod.logger.info("[" + attachmentId + "] Created attachment '"
                    + attachmentName + "' (" + attachmentType + ") at ("
                    + String.format("%.1f", startX) + ", " + String.format("%.1f", startY)
                    + "), ground: " + groundLevel + ", bone scale: ("
                    + String.format("%.2f", originalScaleX) + ", "
                    + String.format("%.2f", originalScaleY) + ")");
        }

        // Update method stays exactly the same
        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);

            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
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
                    velocityX *= 0.95f;
                    angularVelocity *= 0.7f;
                }
            } else {
                angularVelocity *= 0.999f;
            }

            // Wall collisions (same as before)
            if (x > MultiBodyRagdoll.RIGHT_WALL_X && velocityX > 0) {
                x = MultiBodyRagdoll.RIGHT_WALL_X;
                velocityX *= -0.7f;
                angularVelocity = MathUtils.random(-360f, 360f);
            }
            if (x < MultiBodyRagdoll.LEFT_WALL_X && velocityX < 0) {
                x = MultiBodyRagdoll.LEFT_WALL_X;
                velocityX *= -0.7f;
                angularVelocity = MathUtils.random(-360f, 360f);
            }

            velocityX *= 0.999f;
        }
    }

    public static class BoneWobble {
        public float rotation = 0f;
        public float angularVelocity = 0f;
        private final String wobbleId;
        private int updateCount = 0;
        private float timeAlive = 0f;
        private static long globalLastLogTime = 0;

        // Store bone reference for attachment checking
        private final Bone bone;

        // PUBLIC: Hierarchical constraint properties (accessible from outside)
        public final boolean isRootBone;
        public final boolean isLeafBone;
        public final int chainDepth;
        public final float baseRotationConstraint;
        public final float parentInfluence;

        // Limb gravity properties
        private final boolean isLimb;
        private final boolean isLongLimb;
        private final float originalRotation;
        private boolean hasAppliedGravityCorrection = false;
        private float gravityTimer = 0f;
        private static final float GRAVITY_CORRECTION_DELAY = 1.0f;

        public BoneWobble(float initialRotation, Bone bone) {
            this.originalRotation = initialRotation;
            this.bone = bone;
            this.wobbleId = "Wobble_" + System.currentTimeMillis() % 1000;

            String boneName = bone.getData().getName().toLowerCase();
            boolean hasVisualAttachment = boneHasVisualAttachment(bone);

            // Analyze bone hierarchy
            this.isRootBone = bone.getParent() == null;
            this.isLeafBone = bone.getChildren().size == 0;
            this.chainDepth = calculateChainDepth(bone);

            // FIXED: More reasonable constraint values
            if (hasVisualAttachment) {
                // Visual bones get more freedom
                this.baseRotationConstraint =
                        isRootBone ? 60f : Math.max(25f, 45f - chainDepth * 2f);
                this.parentInfluence = 0.2f; // Reduced parent influence
            } else {
                // Control bones still get constraints but not so restrictive
                this.baseRotationConstraint =
                        isRootBone ? 45f : Math.max(12f, 25f - chainDepth * 1f);
                this.parentInfluence =
                        0.4f; // Reduced parent influence for control bones too
            }

            this.isLimb = hasVisualAttachment && isAnatomicalLimbName(boneName);
            this.isLongLimb = this.isLimb;

            if (isLongLimb) {
                BaseMod.logger.info("[" + wobbleId + "] Created gravity-aware limb: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint)
                        + "°)");
            } else if (hasVisualAttachment) {
                BaseMod.logger.info("[" + wobbleId + "] Created visual bone: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint)
                        + "°)");
            } else {
                BaseMod.logger.info("[" + wobbleId + "] Created control bone: "
                        + bone.getData().getName() + " (depth: " + chainDepth
                        + ", constraint: " + String.format("%.1f", baseRotationConstraint)
                        + "°)");
            }
        }

        // Calculate how deep this bone is in its chain
        private int calculateChainDepth(Bone bone) {
            int depth = 0;
            Bone current = bone;
            while (current.getParent() != null) {
                current = current.getParent();
                depth++;
            }
            return depth;
        }

        // Get the parent bone's wobble rotation if it exists
        private float getParentWobbleRotation(MultiBodyRagdoll ragdoll) {
            if (bone.getParent() != null) {
                BoneWobble parentWobble = ragdoll.boneWobbles.get(bone.getParent());
                if (parentWobble != null) {
                    return parentWobble.rotation;
                }
            }
            return 0f;
        }

        // Check if a bone actually has a visual sprite attachment
        private boolean boneHasVisualAttachment(Bone bone) {
            try {
                for (Slot slot : bone.getSkeleton().getSlots()) {
                    if (slot.getBone() == bone && slot.getAttachment() != null) {
                        return true;
                    }
                }
            } catch (Exception e) {
                return true; // Default to visual if we can't determine
            }
            return false;
        }

        // Much simpler anatomical limb detection
        private boolean isAnatomicalLimbName(String boneName) {
            return boneName.matches("^(arm|leg|wing)(_bg|_fg|l|r|left|right)?$");
        }

        // SIMPLIFIED: Update method with less aggressive constraints
        public void update(float deltaTime, float parentVelocityX,
                           float parentVelocityY, boolean parentHasSettled,
                           MultiBodyRagdoll ragdoll) {
            updateCount++;
            timeAlive += deltaTime;
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);

            float oldAngularVelocity = angularVelocity;
            float oldRotation = rotation;

            // Calculate parent motion characteristics
            float velocityMagnitude =
                    (float) Math.sqrt(parentVelocityX * parentVelocityX
                            + parentVelocityY * parentVelocityY);
            boolean isAirborne =
                    Math.abs(parentVelocityY) > 30f || velocityMagnitude > 150f;
            boolean isEarlyFlight = timeAlive < 3.0f;

            // Apply rotation
            rotation += angularVelocity * deltaTime;

            // SIMPLIFIED: Apply hierarchical constraints (less aggressive)
            if (chainDepth > 0 && !isEarlyFlight) {
                applyHierarchicalConstraints(ragdoll, parentHasSettled);
            }

            // Only actual visual limb bones get gravity correction
            if (isLongLimb && parentHasSettled
                    && timeAlive > GRAVITY_CORRECTION_DELAY) {
                applyLimbGravity(deltaTime);
            }

            // LESS AGGRESSIVE damping based on constraint violations
            float rotationMagnitude = Math.abs(rotation);
            float constraintViolation =
                    Math.max(0, rotationMagnitude - baseRotationConstraint)
                            / baseRotationConstraint;

            // Different damping for visual vs control bones
            float dampingFactor;
            if (isEarlyFlight) {
                dampingFactor = 0.995f; // Less damping during early flight
            } else if (isAirborne) {
                dampingFactor = 0.98f; // Less damping when airborne
            } else {
                boolean hasVisualAttachment = boneHasVisualAttachment(this.bone);

                if (!hasVisualAttachment && parentHasSettled) {
                    // Animation control bones should settle quickly but not instantly
                    dampingFactor =
                            0.92f - (constraintViolation * 0.1f); // Reduced penalty
                } else if (!isLimb && parentHasSettled) {
                    // Visual bones that aren't limbs get moderate damping
                    dampingFactor =
                            0.88f - (constraintViolation * 0.08f); // Reduced penalty
                } else {
                    float velocityRatio = Math.min(velocityMagnitude / 200f, 1.0f);
                    dampingFactor = 0.92f + (0.98f - 0.92f) * velocityRatio;
                    dampingFactor -= constraintViolation * 0.05f; // Much reduced penalty
                }
            }

            dampingFactor = Math.max(0.7f, dampingFactor); // Higher minimum damping
            angularVelocity *= dampingFactor;

            // GENTLER constraint restoration force
            if (constraintViolation > 0.2f) { // Higher threshold
                float restorationForce =
                        -Math.signum(rotation) * constraintViolation * 15f; // Reduced force
                angularVelocity += restorationForce;
            }

            // Simplified logging for significant changes
            long currentTime = System.currentTimeMillis();
            boolean canLogGlobally =
                    (currentTime - globalLastLogTime) >= 2000; // Less frequent
            boolean isSignificantChange =
                    Math.abs(oldRotation - rotation) > 30f || constraintViolation > 0.5f;

            if (canLogGlobally && isSignificantChange) {
                String phase = isEarlyFlight ? "EARLY_FLIGHT"
                        : (isAirborne ? "AIRBORNE" : "SETTLING");
                String boneType =
                        isLongLimb ? "VISUAL_LIMB" : (isLimb ? "VISUAL" : "CONTROL");
                BaseMod.logger.info("[" + wobbleId + "] " + phase + " (" + boneType
                        + ") - Update " + updateCount
                        + ", rot: " + String.format("%.1f", oldRotation) + " -> "
                        + String.format("%.1f", rotation) + "°, constraint: "
                        + String.format("%.1f", baseRotationConstraint)
                        + "°, violation: " + String.format("%.2f", constraintViolation));
                globalLastLogTime = currentTime;
            }
        }

        // SIMPLIFIED: Apply hierarchical constraints to prevent accordion effect
        private void applyHierarchicalConstraints(
                MultiBodyRagdoll ragdoll, boolean parentHasSettled) {
            float parentWobbleRotation = getParentWobbleRotation(ragdoll);

            // Calculate the relative rotation from parent
            float relativeRotation =
                    rotation - parentWobbleRotation * parentInfluence;

            // Apply constraint based on chain depth and bone type - but more lenient
            float maxRelativeRotation = baseRotationConstraint
                    * (1.0f - Math.min(chainDepth * 0.05f, 0.3f)); // Much less reduction

            if (Math.abs(relativeRotation) > maxRelativeRotation) {
                float constraintForce =
                        (Math.abs(relativeRotation) - maxRelativeRotation)
                                / maxRelativeRotation;

                // Gently pull back toward constraint boundary
                float targetRotation = parentWobbleRotation * parentInfluence
                        + Math.signum(relativeRotation) * maxRelativeRotation;

                float correctionStrength =
                        parentHasSettled ? 0.03f : 0.01f; // Much gentler correction
                rotation = rotation * (1.0f - correctionStrength)
                        + targetRotation * correctionStrength;

                // Also reduce angular velocity when violating constraints - but less
                angularVelocity *=
                        (1.0f - constraintForce * 0.1f); // Much less reduction
            }
        }

        // Keep existing limb gravity method (unchanged)
        private void applyLimbGravity(float deltaTime) {
            gravityTimer += deltaTime;

            // Normalize rotation to 0-360 range for easier calculations
            float normalizedRotation = ((rotation % 360f) + 360f) % 360f;

            // Determine if limb is pointing "up" (roughly vertical, pointing skyward)
            boolean isPointingUp =
                    (normalizedRotation > 45f && normalizedRotation < 135f)
                            || (normalizedRotation > 225f && normalizedRotation < 315f);

            if (isPointingUp && !hasAppliedGravityCorrection) {
                // Calculate the closest "flat" position (horizontal)
                float targetRotation;

                if (normalizedRotation >= 45f && normalizedRotation <= 135f) {
                    // Upper right quadrant - fall to right (0° or 360°)
                    targetRotation = (normalizedRotation < 90f) ? 0f : 180f;
                } else {
                    // Upper left quadrant - fall to left (180°)
                    targetRotation = (normalizedRotation < 270f) ? 180f : 0f;
                }

                // Calculate shortest path to target
                float rotationDiff = targetRotation - normalizedRotation;
                if (rotationDiff > 180f)
                    rotationDiff -= 360f;
                if (rotationDiff < -180f)
                    rotationDiff += 360f;

                // Much gentler gravity torque for root limbs to prevent accordion
                // effect
                float verticalness =
                        1.0f - Math.abs(Math.abs(normalizedRotation - 90f) - 90f) / 90f;
                float gravityTorque =
                        rotationDiff * verticalness * 2.0f; // Reduced from 4.0f to 2.0f

                angularVelocity += gravityTorque;

                // Wider tolerance for completion to prevent micro-corrections
                if (Math.abs(rotationDiff) < 30f) { // Increased from 20f to 30f
                    hasAppliedGravityCorrection = true;
                    BaseMod.logger.info("[" + wobbleId
                            + "] ROOT limb gravity correction complete - settled at "
                            + String.format("%.1f", normalizedRotation) + "°");
                }

                // Log the gravity application
                if (gravityTimer
                        > 0.5f) { // Reduced logging frequency from 0.3s to 0.5s
                    BaseMod.logger.info("[" + wobbleId
                            + "] Applying ROOT limb gravity - current: "
                            + String.format("%.1f", normalizedRotation)
                            + "°, target: " + targetRotation
                            + "°, torque: " + String.format("%.2f", gravityTorque)
                            + ", verticalness: " + String.format("%.2f", verticalness));
                    gravityTimer = 0f;
                }
            }
        }
    }

    public static class RagdollPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;
        private final String physicsId;
        private int updateCount = 0;

        private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;

        public RagdollPhysics(float startX, float startY, float forceX,
                              float forceY, float groundLevel) {
            this.x = startX;
            this.y = startY;
            this.velocityX = forceX;
            this.velocityY = forceY;
            this.groundY = groundLevel;
            this.rotation = 0f;
            this.angularVelocity =
                    MathUtils.random(-144f, 144f); // Reduced from -720 to -144
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

            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            rotation += angularVelocity * deltaTime;

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
}

 */