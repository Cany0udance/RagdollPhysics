
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

public class RagdollPatchesCHAOSMODE {
    public static final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies = new HashMap<>();
    private static Field atlasField, imgField, skeletonField, srField;
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
                initializeRagdoll(__instance);
            }

            MultiBodyRagdoll ragdoll = ragdollBodies.get(__instance);
            if (ragdoll != null) {
                ragdoll.update(Gdx.graphics.getDeltaTime());

                if (ragdoll.hasSettled()) {
                    __instance.deathTimer -= Gdx.graphics.getDeltaTime();
                }
            } else {
                __instance.deathTimer -= Gdx.graphics.getDeltaTime();
            }

            if (__instance.deathTimer < 2.2F && !__instance.tintFadeOutCalled) {
                __instance.tintFadeOutCalled = true;
                __instance.tint.fadeOut();
            }

            if (__instance.deathTimer < 0.0F) {
                __instance.isDead = true;
                if (AbstractDungeon.getMonsters().areMonstersDead() && !AbstractDungeon.getCurrRoom().isBattleOver && !AbstractDungeon.getCurrRoom().cannotLose) {
                    AbstractDungeon.getCurrRoom().endBattle();
                }
                ragdollBodies.remove(__instance);
                __instance.dispose();
            }
            return SpireReturn.Return();
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "render")
    public static class RenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            if (ragdollBodies.containsKey(__instance)) {
                try {
                    renderRagdoll(__instance, sb, ragdollBodies.get(__instance));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return SpireReturn.Return();
            }
            return SpireReturn.Continue();
        }
    }

    private static void initializeRagdoll(AbstractMonster monster) {
        try {
            Skeleton skeleton = (Skeleton) skeletonField.get(monster);
            if (skeleton != null) {
                MultiBodyRagdoll ragdoll = new MultiBodyRagdoll(skeleton, monster.drawY, monster.drawX, monster.drawY);

                // Apply initial death force with some variation
                float baseForceX = PhysicsConstants.BASE_DEATH_FORCE_X;
                float baseForceY = PhysicsConstants.BASE_DEATH_FORCE_Y;
                float forceVariation = MathUtils.random(0.8f, 1.2f);

                ragdoll.applyDeathForce(baseForceX * forceVariation, baseForceY * forceVariation);
                ragdollBodies.put(monster, ragdoll);
            }
            monster.deathTimer = 2.5f;
        } catch (Exception e) {
            e.printStackTrace();
            monster.deathTimer = 3.0f;
        }
    }

    private static void renderRagdoll(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll) throws Exception {
        if (monster.tint.color.a > 0) {
            TextureAtlas atlas = (TextureAtlas) atlasField.get(monster);
            if (atlas == null) {
                Texture img = (Texture) imgField.get(monster);
                sb.setColor(monster.tint.color);
                if (img != null) {
                    float centerX = ragdoll.getCenterX();
                    float centerY = ragdoll.getCenterY();
                    float rotation = ragdoll.getRotation();
                    float imgCenterX = img.getWidth() * Settings.scale / 2.0F;
                    float imgCenterY = img.getHeight() * Settings.scale / 2.0F;
                    sb.draw(img, centerX - imgCenterX, centerY - imgCenterY, imgCenterX, imgCenterY,
                            img.getWidth() * Settings.scale, img.getHeight() * Settings.scale,
                            1f, 1f, rotation, 0, 0, img.getWidth(), img.getHeight(),
                            monster.flipHorizontal, monster.flipVertical);
                }
            } else {
                Skeleton skeleton = (Skeleton) skeletonField.get(monster);
                SkeletonRenderer sr = (SkeletonRenderer) srField.get(monster);
                ragdoll.applyToBones(skeleton);
                skeleton.updateWorldTransform();
                skeleton.setColor(monster.tint.color);
                skeleton.setFlip(monster.flipHorizontal, monster.flipVertical);

                sb.end();
                CardCrawlGame.psb.begin();
                sr.draw(CardCrawlGame.psb, skeleton);
                ragdoll.renderDetachedWeapons(CardCrawlGame.psb, atlas);
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

    // Centralized physics constants for easy tuning
    private static class PhysicsConstants {
        // Death forces
        static final float BASE_DEATH_FORCE_X = 700f;
        static final float BASE_DEATH_FORCE_Y = 1200f;

        // Physics properties
        static final float GRAVITY = -1200f;
        static final float GROUND_FRICTION = 0.92f;
        static final float AIR_RESISTANCE = 0.999f;

        // Bounce behavior
        static final float BOUNCE_THRESHOLD = 180f;
        static final float BOUNCE_RETENTION = 0.5f;
        static final float BOUNCE_HORIZONTAL_DAMPING = 0.8f;
        static final float BOUNCE_ANGULAR_DAMPING = 0.7f;

        // Settling behavior
        static final float SETTLE_VELOCITY_THRESHOLD = 30f;
        static final float SETTLE_ANGULAR_THRESHOLD = 20f;
        static final float SETTLE_TIME_REQUIRED = 0.4f;
        static final float SETTLE_DAMPING = 0.88f;

        // Walls
        static final float RIGHT_WALL_X = 1850f;
        static final float LEFT_WALL_X = -200f;
        static final float WALL_BOUNCE_RETENTION = 0.6f;

        // Angular velocity limits and behavior
        static final float MAX_ANGULAR_VELOCITY = 1800f; // Doubled for more flips
        static final float COMEDIC_SPIN_MULTIPLIER = 2.8f; // Much higher for more spinning
        static final float SPIN_VELOCITY_THRESHOLD = 600f; // Lower threshold = more spinning
        static final float EXTRA_FLIP_CHANCE = 0.4f; // 40% chance for bonus flips
        static final float BONUS_FLIP_FORCE = 720f; // Full extra rotation

        // Weapon physics
        static final float WEAPON_FORCE_MULTIPLIER = 0.9f;
        static final float WEAPON_BOUNCE_RETENTION = 0.4f;
        static final float WEAPON_SPIN_MULTIPLIER = 1.8f;

        // Bone wobble - much more dramatic
        static final float WOBBLE_FORCE_MULTIPLIER = 2.2f; // Much higher for flailing
        static final float WOBBLE_DAMPING = 0.95f; // Less damping = more flailing
        static final float WOBBLE_MOMENTUM_TRANSFER = 0.3f; // Bones get momentum from body
        static final float WOBBLE_RANDOM_FORCE = 150f; // Random forces during movement
    }

    public static class MultiBodyRagdoll {
        private final HashMap<Bone, BoneWobble> boneWobbles;
        private final HashMap<String, WeaponPhysics> weaponBodies;
        private final RagdollPhysics mainBody;
        private final float groundY;

        private float settledTimer = 0f;
        private static final float FIXED_TIMESTEP = 1.0f / 60.0f;
        private float accumulator = 0f;

        public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY) {
            this.boneWobbles = new HashMap<>();
            this.weaponBodies = new HashMap<>();
            this.groundY = groundLevel;
            this.mainBody = new RagdollPhysics(startX, startY, groundLevel);

            initializeBones(skeleton, startX, startY);
        }

        private void initializeBones(Skeleton skeleton, float startX, float startY) {
            for (Bone bone : skeleton.getBones()) {
                boneWobbles.put(bone, new BoneWobble());

                String boneName = bone.getData().getName().toLowerCase();
                if (isWeaponBone(boneName)) {
                    float weaponX = startX + bone.getWorldX() * Settings.scale;
                    float weaponY = startY + bone.getWorldY() * Settings.scale;
                    weaponBodies.put(bone.getData().getName(), new WeaponPhysics(weaponX, weaponY, groundY, bone));
                }
            }
        }

        private boolean isWeaponBone(String boneName) {
            return boneName.contains("staff") || boneName.contains("weapon") ||
                    boneName.contains("sword") || boneName.contains("blade") ||
                    boneName.contains("wand") || boneName.contains("rod") ||
                    boneName.contains("dagger") || boneName.contains("spear") ||
                    boneName.contains("bow") || boneName.contains("axe");
        }

        public void update(float deltaTime) {
            accumulator += Math.min(deltaTime, 0.1f);
            while (accumulator >= FIXED_TIMESTEP) {
                updatePhysics(FIXED_TIMESTEP);
                accumulator -= FIXED_TIMESTEP;
            }
        }

        private void updatePhysics(float deltaTime) {
            mainBody.update(deltaTime);
            handleWallCollisions();

            for (WeaponPhysics weapon : weaponBodies.values()) {
                weapon.update(deltaTime);
            }

            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.update(deltaTime, mainBody.getTotalVelocity());
            }
        }

        private void handleWallCollisions() {
            if (mainBody.x > PhysicsConstants.RIGHT_WALL_X && mainBody.velocityX > 0) {
                mainBody.x = PhysicsConstants.RIGHT_WALL_X;
                mainBody.velocityX *= -PhysicsConstants.WALL_BOUNCE_RETENTION;
                mainBody.velocityY *= PhysicsConstants.BOUNCE_HORIZONTAL_DAMPING;
                addWallImpactSpin();
            }
            if (mainBody.x < PhysicsConstants.LEFT_WALL_X && mainBody.velocityX < 0) {
                mainBody.x = PhysicsConstants.LEFT_WALL_X;
                mainBody.velocityX *= -PhysicsConstants.WALL_BOUNCE_RETENTION;
                mainBody.velocityY *= PhysicsConstants.BOUNCE_HORIZONTAL_DAMPING;
                addWallImpactSpin();
            }
        }

        private void addWallImpactSpin() {
            float impactIntensity = Math.abs(mainBody.velocityX) / 400f;
            mainBody.angularVelocity += MathUtils.random(-450f, 450f) * (1.0f + impactIntensity);

            // Wall impacts should cause dramatic limb flailing
            for (BoneWobble wobble : boneWobbles.values()) {
                float wallImpact = MathUtils.random(-300f, 300f) * impactIntensity;
                wobble.addImpact(wallImpact);
            }
        }

        public boolean hasSettled() {
            float totalVelocity = mainBody.getTotalVelocity();
            boolean isLowVelocity = totalVelocity < PhysicsConstants.SETTLE_VELOCITY_THRESHOLD;
            boolean isLowAngular = Math.abs(mainBody.angularVelocity) < PhysicsConstants.SETTLE_ANGULAR_THRESHOLD;
            boolean isNearGround = mainBody.y <= groundY + 5f;

            if (isLowVelocity && isLowAngular && isNearGround) {
                settledTimer += Gdx.graphics.getDeltaTime();
                return settledTimer >= PhysicsConstants.SETTLE_TIME_REQUIRED;
            } else {
                settledTimer = 0f;
                return false;
            }
        }

        public void applyDeathForce(float forceX, float forceY) {
            mainBody.velocityX += forceX;
            mainBody.velocityY += forceY;

            // Add comedic spin based on upward force
            float spinIntensity = Math.min(forceY / PhysicsConstants.SPIN_VELOCITY_THRESHOLD, 2.5f);
            float baseAngularVel = MathUtils.random(-540f, 540f); // Wider range for more variety
            mainBody.angularVelocity = baseAngularVel * PhysicsConstants.COMEDIC_SPIN_MULTIPLIER * (1.0f + spinIntensity);

            // Chance for bonus flips to make spinning more dramatic
            if (MathUtils.random() < PhysicsConstants.EXTRA_FLIP_CHANCE) {
                float bonusFlips = MathUtils.random(1, 3) * PhysicsConstants.BONUS_FLIP_FORCE;
                mainBody.angularVelocity += Math.signum(mainBody.angularVelocity) * bonusFlips;
            }

            // Cap angular velocity to prevent excessive spinning but allow more drama
            mainBody.angularVelocity = MathUtils.clamp(mainBody.angularVelocity,
                    -PhysicsConstants.MAX_ANGULAR_VELOCITY, PhysicsConstants.MAX_ANGULAR_VELOCITY);

            // Apply much stronger forces to bones for flailing effect
            for (BoneWobble wobble : boneWobbles.values()) {
                float boneImpact = MathUtils.random(-200f, 200f) * PhysicsConstants.WOBBLE_FORCE_MULTIPLIER;
                // Add some momentum transfer from main body
                boneImpact += (forceX + forceY) * PhysicsConstants.WOBBLE_MOMENTUM_TRANSFER * MathUtils.random(-0.5f, 0.5f);
                wobble.addImpact(boneImpact);
            }

            for (WeaponPhysics weapon : weaponBodies.values()) {
                weapon.applyForce(forceX * PhysicsConstants.WEAPON_FORCE_MULTIPLIER,
                        forceY * PhysicsConstants.WEAPON_FORCE_MULTIPLIER,
                        spinIntensity);
            }
        }

        public void applyToBones(Skeleton skeleton) {
            skeleton.setPosition(mainBody.x, mainBody.y);
            if (skeleton.getRootBone() != null) {
                skeleton.getRootBone().setRotation(mainBody.rotation);
            }

            for (Bone bone : skeleton.getBones()) {
                String boneName = bone.getData().getName();
                WeaponPhysics weaponPhysics = weaponBodies.get(boneName);

                if (weaponPhysics != null) {
                    // Hide weapon bones since we render them separately
                    bone.setScaleX(0f);
                    bone.setScaleY(0f);
                } else {
                    BoneWobble wobble = boneWobbles.get(bone);
                    if (wobble != null) {
                        bone.setRotation(bone.getData().getRotation() + wobble.getRotation());
                    }
                }
            }

            skeleton.updateWorldTransform();
        }

        public void renderDetachedWeapons(PolygonSpriteBatch sb, TextureAtlas atlas) {
            for (Map.Entry<String, WeaponPhysics> entry : weaponBodies.entrySet()) {
                String weaponName = entry.getKey();
                WeaponPhysics weapon = entry.getValue();

                if (weapon.attachment != null) {
                    try {
                        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) weapon.attachment.getRegion();
                        if (region != null) {
                            weapon.renderAttachment(sb, region);
                            continue;
                        }
                    } catch (Exception e) {
                        BaseMod.logger.error("Failed to render weapon attachment: " + e.getMessage());
                    }
                }

                TextureAtlas.AtlasRegion region = atlas.findRegion(weaponName);
                if (region != null) {
                    weapon.renderRegion(sb, region);
                }
            }
        }

        public float getCenterX() { return mainBody.x; }
        public float getCenterY() { return mainBody.y; }
        public float getRotation() { return mainBody.rotation; }
    }

    public static class WeaponPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;
        public final RegionAttachment attachment;

        public WeaponPhysics(float startX, float startY, float groundLevel, Bone bone) {
            this.x = startX;
            this.y = startY;
            this.groundY = groundLevel;
            this.rotation = bone.getRotation();
            this.attachment = findAttachmentForBone(bone);
        }

        private static RegionAttachment findAttachmentForBone(Bone bone) {
            try {
                for (Slot slot : bone.getSkeleton().getSlots()) {
                    if (slot.getBone() == bone && slot.getAttachment() instanceof RegionAttachment) {
                        return (RegionAttachment) slot.getAttachment();
                    }
                }
            } catch (Exception e) {
                BaseMod.logger.error("Could not find attachment for bone: " + bone.getData().getName());
            }
            return null;
        }

        public void applyForce(float forceX, float forceY, float spinIntensity) {
            velocityX += forceX + MathUtils.random(-30f, 30f);
            velocityY += forceY + MathUtils.random(-15f, 45f);

            float baseAngular = MathUtils.random(-450f, 450f);
            angularVelocity += baseAngular * PhysicsConstants.WEAPON_SPIN_MULTIPLIER * (1.0f + spinIntensity);
        }

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, MultiBodyRagdoll.FIXED_TIMESTEP);

            velocityY += PhysicsConstants.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            rotation += angularVelocity * deltaTime;

            handleGroundCollision();
            handleWallCollisions();

            velocityX *= PhysicsConstants.AIR_RESISTANCE;
            angularVelocity *= 0.998f; // Slight angular damping
        }

        private void handleGroundCollision() {
            if (y < groundY && velocityY < 0) {
                y = groundY;
                if (Math.abs(velocityY) > PhysicsConstants.BOUNCE_THRESHOLD) {
                    velocityY *= -PhysicsConstants.WEAPON_BOUNCE_RETENTION;
                    velocityX *= PhysicsConstants.BOUNCE_HORIZONTAL_DAMPING;
                    angularVelocity *= PhysicsConstants.BOUNCE_ANGULAR_DAMPING;
                } else {
                    velocityY = 0f;
                    velocityX *= PhysicsConstants.GROUND_FRICTION;
                    angularVelocity *= PhysicsConstants.SETTLE_DAMPING;
                }
            }
        }

        private void handleWallCollisions() {
            if (x > PhysicsConstants.RIGHT_WALL_X && velocityX > 0) {
                x = PhysicsConstants.RIGHT_WALL_X;
                velocityX *= -PhysicsConstants.WALL_BOUNCE_RETENTION;
                angularVelocity = MathUtils.random(-270f, 270f);
            }
            if (x < PhysicsConstants.LEFT_WALL_X && velocityX < 0) {
                x = PhysicsConstants.LEFT_WALL_X;
                velocityX *= -PhysicsConstants.WALL_BOUNCE_RETENTION;
                angularVelocity = MathUtils.random(-270f, 270f);
            }
        }

        public void renderAttachment(PolygonSpriteBatch sb, TextureAtlas.AtlasRegion region) {
            float width = attachment.getWidth() * Settings.scale;
            float height = attachment.getHeight() * Settings.scale;
            float offsetX = attachment.getX() * Settings.scale;
            float offsetY = attachment.getY() * Settings.scale;
            float attachmentRotation = attachment.getRotation();

            sb.draw(region, x - width / 2f + offsetX, y - height / 2f + offsetY,
                    width / 2f, height / 2f, width, height, 1f, 1f,
                    rotation + attachmentRotation);
        }

        public void renderRegion(PolygonSpriteBatch sb, TextureAtlas.AtlasRegion region) {
            float width = region.getRegionWidth() * Settings.scale;
            float height = region.getRegionHeight() * Settings.scale;
            sb.draw(region, x - width / 2f, y - height / 2f, width / 2f, height / 2f,
                    width, height, 1f, 1f, rotation);
        }
    }

    public static class BoneWobble {
        private float rotation = 0f;
        private float angularVelocity = 0f;

        public void addImpact(float impact) {
            angularVelocity += impact;
        }

        public void update(float deltaTime, float parentVelocity) {
            deltaTime = Math.min(deltaTime, MultiBodyRagdoll.FIXED_TIMESTEP);

            rotation += angularVelocity * deltaTime;

            // Add random forces while the body is moving to create flailing
            if (parentVelocity > 50f) {
                float movementIntensity = Math.min(parentVelocity / 500f, 1.0f);
                float randomForce = MathUtils.random(-PhysicsConstants.WOBBLE_RANDOM_FORCE,
                        PhysicsConstants.WOBBLE_RANDOM_FORCE);
                angularVelocity += randomForce * movementIntensity * deltaTime * 60f; // Frame-rate independent
            }

            // Damping based on parent velocity - much less aggressive damping
            float velocityRatio = Math.min(parentVelocity / 800f, 1.0f);
            float damping = PhysicsConstants.WOBBLE_DAMPING + (0.985f - PhysicsConstants.WOBBLE_DAMPING) * velocityRatio;
            angularVelocity *= damping;
        }

        public float getRotation() {
            return rotation;
        }
    }

    public static class RagdollPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;

        public RagdollPhysics(float startX, float startY, float groundLevel) {
            this.x = startX;
            this.y = startY;
            this.groundY = groundLevel;
            this.rotation = 0f;
        }

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, MultiBodyRagdoll.FIXED_TIMESTEP);

            velocityY += PhysicsConstants.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            rotation += angularVelocity * deltaTime;

            handleGroundCollision();

            velocityX *= PhysicsConstants.AIR_RESISTANCE;
        }

        private void handleGroundCollision() {
            if (y <= groundY && velocityY <= 0) {
                y = groundY;

                if (Math.abs(velocityY) > PhysicsConstants.BOUNCE_THRESHOLD) {
                    velocityY = Math.abs(velocityY) * PhysicsConstants.BOUNCE_RETENTION;
                    velocityX *= PhysicsConstants.BOUNCE_HORIZONTAL_DAMPING;
                    angularVelocity *= PhysicsConstants.BOUNCE_ANGULAR_DAMPING;
                } else {
                    velocityY = 0f;
                    velocityX *= PhysicsConstants.GROUND_FRICTION;
                    angularVelocity *= PhysicsConstants.SETTLE_DAMPING;
                }
            }
        }

        public float getTotalVelocity() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        }
    }
}

 */