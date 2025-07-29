
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

public class RagdollPatchesOLD {
    // Physics body data for each monster
    public static final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies = new HashMap<>();
    // Reflection fields for accessing protected/private members
    private static Field atlasField;
    private static Field imgField;
    private static Field skeletonField;
    private static Field srField;
    private static Method renderNameMethod;

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
            renderNameMethod = AbstractMonster.class.getDeclaredMethod("renderName", SpriteBatch.class);
            renderNameMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SpirePatch(
            clz = AbstractMonster.class,
            method = "updateDeathAnimation"
    )
    public static class DeathAnimationPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance) {
            if (__instance.isDying) {
                // Initialize ragdoll physics on first death frame
                if (!ragdollBodies.containsKey(__instance)) {
                    initializeRagdoll(__instance);
                }
                MultiBodyRagdoll ragdoll = ragdollBodies.get(__instance);
                if (ragdoll != null) {
                    ragdoll.update(Gdx.graphics.getDeltaTime());
                }
                // Handle tint fade (keeping original timing)
                __instance.deathTimer -= Gdx.graphics.getDeltaTime();
                if (__instance.deathTimer < 1.8F && !__instance.tintFadeOutCalled) {
                    __instance.tintFadeOutCalled = true;
                    __instance.tint.fadeOut();
                }
            }
            // Death cleanup (same as original)
            if (__instance.deathTimer < 0.0F) {
                __instance.isDead = true;
                if (AbstractDungeon.getMonsters().areMonstersDead() &&
                        !AbstractDungeon.getCurrRoom().isBattleOver &&
                        !AbstractDungeon.getCurrRoom().cannotLose) {
                    AbstractDungeon.getCurrRoom().endBattle();
                }
                // Clean up our physics
                ragdollBodies.remove(__instance);
                __instance.dispose();
                __instance.powers.clear();
            }
            // Prevent original method from running
            return SpireReturn.Return();
        }
    }

    @SpirePatch(
            clz = AbstractMonster.class,
            method = "render"
    )
    public static class RenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            // Only intercept rendering if this monster has active ragdoll physics
            if (ragdollBodies.containsKey(__instance)) {
                MultiBodyRagdoll ragdoll = ragdollBodies.get(__instance);
                try {
                    renderRagdoll(__instance, sb, ragdoll);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return SpireReturn.Return(); // Skip original render
            }
            // Let original render method run for non-ragdoll monsters
            return SpireReturn.Continue();
        }
    }

    private static void initializeRagdoll(AbstractMonster monster) {
        try {
            Field skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);
            Skeleton skeleton = (Skeleton) skeletonField.get(monster);
            if (skeleton != null) {
                MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(skeleton, monster.drawY, monster.drawX, monster.drawY);
                // Reduced initial forces - less upward velocity, always go right
                float forceX = MathUtils.random(800f, 1200f);
                float forceY = MathUtils.random(800f, 1300f);

                ragdoll.applyGlobalForce(forceX, forceY);
                ragdollBodies.put(monster, ragdoll);
            }
            monster.deathTimer = monster.deathTimer * 4f;
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback with reduced forces too
            RagdollPhysics simple = new RagdollPhysics(monster.drawX, monster.drawY,
                    MathUtils.random(600f, 900f), MathUtils.random(400f, 700f), monster.drawY);
        }
    }

    private static void renderRagdoll(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll) throws Exception {
        TextureAtlas atlas = (TextureAtlas) atlasField.get(monster);
        if (atlas == null) {
            // Static image monsters - fallback to simple center position
            Texture img = (Texture) imgField.get(monster);
            sb.setColor(monster.tint.color);
            if (img != null) {
                // Get approximate center from ragdoll
                float centerX = ragdoll.getCenterX();
                float centerY = ragdoll.getCenterY();
                float rotation = ragdoll.getAverageRotation();
                float imgCenterX = img.getWidth() * Settings.scale / 2.0F;
                float imgCenterY = img.getHeight() * Settings.scale / 2.0F;
                sb.draw(img,
                        centerX - imgCenterX, centerY - imgCenterY,
                        imgCenterX, imgCenterY, // origin for rotation
                        img.getWidth() * Settings.scale,
                        img.getHeight() * Settings.scale,
                        1f, 1f, // scale
                        rotation, // rotation
                        0, 0, img.getWidth(), img.getHeight(),
                        monster.flipHorizontal, monster.flipVertical);
            }
        } else {
            // Spine animated monsters
            Skeleton skeleton = (Skeleton) skeletonField.get(monster);
            SkeletonRenderer sr = (SkeletonRenderer) srField.get(monster);
            monster.state.update(Gdx.graphics.getDeltaTime());
            monster.state.apply(skeleton);
            // Apply the ragdoll physics to the skeleton bones
            ragdoll.applyToBones(skeleton);
            skeleton.updateWorldTransform();
            skeleton.setColor(monster.tint.color);
            skeleton.setFlip(monster.flipHorizontal, monster.flipVertical);
            sb.end();
            CardCrawlGame.psb.begin();
            sr.draw(CardCrawlGame.psb, skeleton);
            // Render detached weapons separately
            ragdoll.renderDetachedWeapons(CardCrawlGame.psb, atlas);
            CardCrawlGame.psb.end();
            sb.begin();
            sb.setBlendFunction(770, 771);
        }
        // Still render health/name for dying monsters
        if (!AbstractDungeon.player.isDead) {
            monster.renderHealth(sb);
            renderNameMethod.invoke(monster, sb);
        }
    }

    // Complete MultiBodyRagdoll class with fixes
    public static class MultiBodyRagdoll {
        private final HashMap<Bone, BoneWobble> boneWobbles;
        private final HashMap<String, WeaponPhysics> weaponBodies; // Detached weapons
        private final RagdollPhysics mainBody;
        private final float groundY;
        private float skeletonMinY = Float.MAX_VALUE;
        private float groundCollisionCooldown = 0f;
        private static final float GRAVITY = -1200f;
        private static final float RIGHT_WALL_X = 1850f;
        private static final float LEFT_WALL_X = -200f;
        private static final float BOUNCE_VELOCITY_THRESHOLD = 200f; // Only bounce if hitting ground faster than this

        public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY) {
            this.boneWobbles = new HashMap<>();
            this.weaponBodies = new HashMap<>();
            this.groundY = groundLevel;
            this.mainBody = new RagdollPhysics(startX, startY, 0, 0, groundLevel);
            for (Bone bone : skeleton.getBones()) {
                BoneWobble wobble = new BoneWobble(bone.getRotation());
                boneWobbles.put(bone, wobble);
                // Enhanced weapon detection - check for staff, weapon, sword, etc.
                String boneName = bone.getData().getName().toLowerCase();
                if (boneName.contains("staff") || boneName.contains("weapon") ||
                        boneName.contains("sword") || boneName.contains("blade") ||
                        boneName.contains("wand") || boneName.contains("rod") ||
                        boneName.contains("dagger") || boneName.contains("spear") ||
                        boneName.contains("bow") || boneName.contains("axe")) {
                    BaseMod.logger.info("Found weapon bone: " + bone.getData().getName());
                    // Create separate physics for this weapon with initial world position
                    WeaponPhysics weapon = new WeaponPhysics(
                            startX + bone.getWorldX() * Settings.scale,
                            startY + bone.getWorldY() * Settings.scale,
                            groundLevel,
                            bone
                    );
                    weaponBodies.put(bone.getData().getName(), weapon);
                }
            }
        }

        public void applyGlobalForce(float forceX, float forceY) {
            // Main body force
            mainBody.velocityX += forceX * 0.8f;
            mainBody.velocityY += forceY * 0.8f;
            mainBody.angularVelocity += MathUtils.random(-360f, 360f);
            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.angularVelocity += MathUtils.random(-180f, 180f);
            }
            // Apply independent forces to detached weapons - reduced variation
            for (WeaponPhysics weapon : weaponBodies.values()) {
                // Each weapon gets its own random force multiplier for variety
                float weaponForceMultiplierX = MathUtils.random(0.8f, 1.0f); // Reduced upper bound
                float weaponForceMultiplierY = MathUtils.random(0.8f, 1.0f); // Reduced upper bound
                weapon.velocityX += forceX * weaponForceMultiplierX;
                weapon.velocityY += forceY * weaponForceMultiplierY;
                weapon.angularVelocity += MathUtils.random(-720f, 720f);
                // Reduced random variation
                weapon.velocityX += MathUtils.random(-50f, 50f); // Reduced from -100 to 100
                weapon.velocityY += MathUtils.random(-25f, 75f); // Reduced from -50 to 150
            }
        }

        public void update(float deltaTime) {
            // Cap deltaTime to prevent physics explosions
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            // Update cooldown
            if (groundCollisionCooldown > 0) {
                groundCollisionCooldown -= deltaTime;
            }
            mainBody.update(deltaTime);
            // Wall collisions with stronger dampening
            if (mainBody.x > RIGHT_WALL_X && mainBody.velocityX > 0) {
                mainBody.x = RIGHT_WALL_X;
                mainBody.velocityX = -mainBody.velocityX * 0.4f;
                mainBody.velocityY *= 0.7f;
                mainBody.angularVelocity = MathUtils.random(-360f, 360f);
                for (BoneWobble wobble : boneWobbles.values()) {
                    wobble.angularVelocity += MathUtils.random(-270f, 270f);
                }
            }
            if (mainBody.x < LEFT_WALL_X && mainBody.velocityX < 0) {
                mainBody.x = LEFT_WALL_X;
                mainBody.velocityX = -mainBody.velocityX * 0.4f;
                mainBody.velocityY *= 0.7f;
                mainBody.angularVelocity = MathUtils.random(-360f, 360f);
                for (BoneWobble wobble : boneWobbles.values()) {
                    wobble.angularVelocity += MathUtils.random(-270f, 270f);
                }
            }
            // Update detached weapons independently
            for (WeaponPhysics weapon : weaponBodies.values()) {
                weapon.update(deltaTime);
            }
            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.update(deltaTime, mainBody.velocityX, mainBody.velocityY);
            }
        }

        public void applyToBones(Skeleton skeleton) {
            skeleton.setPosition(mainBody.x, mainBody.y);
            if (skeleton.getRootBone() != null) {
                skeleton.getRootBone().setRotation(mainBody.rotation);
            }
            // Apply wobbles and handle weapon detachment
            for (Bone bone : skeleton.getBones()) {
                String boneName = bone.getData().getName();
                // Check if this bone is a detached weapon
                WeaponPhysics weaponPhysics = weaponBodies.get(boneName);
                if (weaponPhysics != null) {
                    // FIXED: Hide the weapon bone by making it invisible
                    // Set scale to 0 to make it invisible (it will be rendered separately)
                    bone.setScaleX(0f);
                    bone.setScaleY(0f);
                } else {
                    // Normal bone wobble for non-weapon bones
                    BoneWobble wobble = boneWobbles.get(bone);
                    if (wobble != null) {
                        bone.setRotation(bone.getData().getRotation() + wobble.rotation);
                    }
                }
            }
            // Update transforms once all rotations are set
            skeleton.updateWorldTransform();
            // Calculate bounds for ground collision - exclude hidden weapon bones
            float currentMinY = Float.MAX_VALUE;
            for (Bone bone : skeleton.getBones()) {
                // Skip bones that are scaled to 0 (hidden weapons)
                if (bone.getScaleX() > 0f && bone.getScaleY() > 0f) {
                    float boneWorldY = bone.getWorldY() + mainBody.y; // Add skeleton position
                    if (boneWorldY < currentMinY) {
                        currentMinY = boneWorldY;
                    }
                }
            }
            // FIXED: Better ground collision with velocity-based bouncing
            if (currentMinY < groundY && groundCollisionCooldown <= 0) {
                float penetration = groundY - currentMinY;
                BaseMod.logger.info("Ground collision - penetration: " + penetration + ", currentMinY: " + currentMinY + ", groundY: " + groundY);
                // Always push the body up to prevent dipping below ground
                mainBody.y += penetration + 10f * Settings.scale; // Add small buffer

                // Only bounce if moving downward fast enough, otherwise just "plop"
                if (mainBody.velocityY < -BOUNCE_VELOCITY_THRESHOLD) {
                    // Fast impact - bounce with reduced force
                    mainBody.velocityY = Math.abs(mainBody.velocityY) * 0.3f; // Reduced from 0.4f
                    mainBody.velocityX *= 0.7f; // Increased damping
                    mainBody.angularVelocity *= 0.7f; // Increased damping
                    // Set cooldown to prevent rapid re-triggering
                    groundCollisionCooldown = 0.1f;
                    // Add wobble randomness
                    for (BoneWobble wobble : boneWobbles.values()) {
                        wobble.angularVelocity += MathUtils.random(-180f, 180f);
                    }
                } else {
                    // Slow impact - just stop bouncing and settle
                    mainBody.velocityY = 0f;
                    mainBody.velocityX *= 0.9f; // Heavy damping
                    mainBody.angularVelocity *= 0.8f; // Slow down rotation
                }

                // Update skeleton to new position
                skeleton.setPosition(mainBody.x, mainBody.y);
                skeleton.updateWorldTransform();
            }
            skeletonMinY = currentMinY;
        }

        // NEW: Method to render detached weapons separately
        public void renderDetachedWeapons(PolygonSpriteBatch sb, TextureAtlas atlas) {
            for (Map.Entry<String, WeaponPhysics> entry : weaponBodies.entrySet()) {
                String weaponName = entry.getKey();
                WeaponPhysics weapon = entry.getValue();
                // Use the stored attachment if available
                if (weapon.attachment != null) {
                    try {
                        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) weapon.attachment.getRegion();
                        if (region != null) {
                            float width = weapon.attachment.getWidth() * Settings.scale;
                            float height = weapon.attachment.getHeight() * Settings.scale;
                            // Apply the attachment's offset and rotation
                            float offsetX = weapon.attachment.getX() * Settings.scale;
                            float offsetY = weapon.attachment.getY() * Settings.scale;
                            float attachmentRotation = weapon.attachment.getRotation();
                            sb.draw(region,
                                    weapon.x - width/2f + offsetX,
                                    weapon.y - height/2f + offsetY,
                                    width/2f, height/2f, // origin for rotation
                                    width, height, // size
                                    1f, 1f, // Use fixed scale instead of attachment scale
                                    weapon.rotation + attachmentRotation // combined rotation
                            );
                            continue; // Successfully rendered, skip fallback
                        }
                    } catch (Exception e) {
                        BaseMod.logger.info("Failed to render weapon attachment: " + e.getMessage());
                    }
                }
                // Fallback: try to find region by bone name or common weapon patterns
                TextureAtlas.AtlasRegion region = atlas.findRegion(weaponName);
                if (region == null) {
                    // Try some common weapon texture name patterns
                    String[] patterns = {
                            weaponName.replace("bone", "").replace("_", ""),
                            weaponName + "_texture",
                            weaponName.toLowerCase(),
                            "weapon",
                            "staff",
                            "sword"
                    };
                    for (String pattern : patterns) {
                        region = atlas.findRegion(pattern);
                        if (region != null) {
                            BaseMod.logger.info("Found weapon texture with pattern: " + pattern);
                            break;
                        }
                    }
                }
                if (region != null) {
                    float width = region.getRegionWidth() * Settings.scale;
                    float height = region.getRegionHeight() * Settings.scale;
                    sb.draw(region,
                            weapon.x - width/2f, weapon.y - height/2f, // position
                            width/2f, height/2f, // origin for rotation
                            width, height, // size
                            1f, 1f, // scale (keep this as 1f, 1f)
                            weapon.rotation // rotation
                    );
                } else {
                    BaseMod.logger.info("Could not find texture for weapon: " + weaponName);
                }
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

    // Physics for detached weapons
    public static class WeaponPhysics {
        public float x, y;
        public float velocityX, velocityY;
        public float rotation, angularVelocity;
        private final float groundY;
        private final Bone originalBone;
        private final RegionAttachment attachment;
        private static final float WEAPON_BOUNCE_THRESHOLD = 150f; // Lower threshold for weapons

        public WeaponPhysics(float startX, float startY, float groundLevel, Bone bone) {
            this.x = startX;
            this.y = startY;
            this.velocityX = 0;
            this.velocityY = 0;
            this.rotation = bone.getRotation();
            this.angularVelocity = MathUtils.random(-720f, 720f);
            this.groundY = groundLevel;
            this.originalBone = bone;
            // Find the attachment for this bone (existing code)
            RegionAttachment foundAttachment = null;
            try {
                Skeleton skeleton = bone.getSkeleton();
                for (Slot slot : skeleton.getSlots()) {
                    if (slot.getBone() == bone && slot.getAttachment() instanceof RegionAttachment) {
                        foundAttachment = (RegionAttachment) slot.getAttachment();
                        BaseMod.logger.info("Found attachment for weapon bone " + bone.getData().getName() + ": " + foundAttachment.getName());
                        break;
                    }
                }
            } catch (Exception e) {
                BaseMod.logger.info("Could not find attachment for bone: " + bone.getData().getName());
                e.printStackTrace();
            }
            this.attachment = foundAttachment;
        }

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            // Calculate velocity-based rotation damping
            float velocityMagnitude = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float maxVelocity = 1000f;
            float velocityRatio = Math.min(velocityMagnitude / maxVelocity, 1.0f);
            // Rotation damping based on velocity
            float rotationDamping = 0.9f + (0.998f - 0.9f) * velocityRatio;
            rotation += angularVelocity * deltaTime;
            angularVelocity *= rotationDamping;

            // Ground collision with velocity-based bouncing
            if (y < groundY && velocityY < 0) {
                y = groundY;
                // Only bounce if moving fast enough
                if (Math.abs(velocityY) > WEAPON_BOUNCE_THRESHOLD) {
                    velocityY = -velocityY * 0.4f; // Reduced bounce
                    velocityX *= 0.85f; // More damping
                    angularVelocity *= 0.6f; // Reduce spin
                } else {
                    // Slow impact - just stop
                    velocityY = 0f;
                    velocityX *= 0.95f; // Heavy damping
                    angularVelocity *= 0.7f; // Slow down spin
                }
            }

            // Wall collisions
            if (x > MultiBodyRagdoll.RIGHT_WALL_X && velocityX > 0) {
                x = MultiBodyRagdoll.RIGHT_WALL_X;
                velocityX = -velocityX * 0.7f;
                angularVelocity = MathUtils.random(-360f, 360f);
            }
            if (x < MultiBodyRagdoll.LEFT_WALL_X && velocityX < 0) {
                x = MultiBodyRagdoll.LEFT_WALL_X;
                velocityX = -velocityX * 0.7f;
                angularVelocity = MathUtils.random(-360f, 360f);
            }
            // Air resistance (existing)
            velocityX *= 0.999f;
        }
    }

    // Simple bone wobble - adds local rotation animation without changing position
    public static class BoneWobble {
        public float rotation;
        public float angularVelocity;
        private final float baseRotation;

        public BoneWobble(float initialRotation) {
            this.baseRotation = initialRotation;
            this.rotation = 0; // wobble offset from base
            this.angularVelocity = 0;
        }

        public void update(float deltaTime, float parentVelocityX, float parentVelocityY) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            // Calculate total velocity magnitude
            float velocityMagnitude = (float) Math.sqrt(parentVelocityX * parentVelocityX + parentVelocityY * parentVelocityY);
            // Calculate velocity-based damping factor
            // At high velocity (1000+): minimal damping (0.995)
            // At low velocity (0-100): strong damping (0.85)
            float maxVelocity = 1000f;
            float velocityRatio = Math.min(velocityMagnitude / maxVelocity, 1.0f);
            float velocityBasedDamping = 0.85f + (0.995f - 0.85f) * velocityRatio;
            // Update wobble rotation
            rotation += angularVelocity * deltaTime;
            // Apply velocity-based damping
            angularVelocity *= velocityBasedDamping;
            // Gentle spring back toward base rotation (stronger when moving slowly)
            float restoreForce = 0.998f - (0.003f * (1.0f - velocityRatio));
            rotation *= restoreForce;
        }
    }

    // Keep the simple physics as fallback
    public static class RagdollPhysics {
        public float x, y;
        public float velocityX, velocityY;
        public float rotation, angularVelocity;
        private final float groundY;
        private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;

        public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel) {
            this.x = startX;
            this.y = startY;
            this.velocityX = forceX;
            this.velocityY = forceY;
            this.rotation = 0f;
            this.angularVelocity = MathUtils.random(-720f, 720f);
            this.groundY = groundLevel;
        }

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            // Calculate velocity-based rotation damping
            float velocityMagnitude = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float maxVelocity = 1000f;
            float velocityRatio = Math.min(velocityMagnitude / maxVelocity, 1.0f);
            // Rotation damping based on velocity
            float rotationDamping = 0.85f + (0.998f - 0.85f) * velocityRatio;
            rotation += angularVelocity * deltaTime;
            angularVelocity *= rotationDamping;

            // Ground collision with velocity-based bouncing
            if (y < groundY && velocityY < 0) {
                y = groundY;
                // Only bounce if moving fast enough
                if (Math.abs(velocityY) > SIMPLE_BOUNCE_THRESHOLD) {
                    velocityY = -velocityY * 0.5f; // Reduced bounce
                    velocityX *= 0.8f; // More damping
                    angularVelocity *= 0.5f; // Stronger reduction
                } else {
                    // Slow impact - just stop
                    velocityY = 0f;
                    velocityX *= 0.9f; // Heavy damping
                    angularVelocity *= 0.6f; // Slow down spin
                }
            }
            velocityX *= 0.998f;
        }
    }
}



*/