
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

public class RagdollPatchesNEW {
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
            // Prevent creating new ragdolls for monsters that are already fully dead.
            if (__instance.isDead) {
                return SpireReturn.Return();
            }

            if (!ragdollBodies.containsKey(__instance)) {
                initializeRagdoll(__instance);
            }

            MultiBodyRagdoll ragdoll = ragdollBodies.get(__instance);
            if (ragdoll != null) {
                ragdoll.update(Gdx.graphics.getDeltaTime());

                // The death timer only starts counting down once the ragdoll has settled.
                if (ragdoll.hasSettledOnGround()) {
                    __instance.deathTimer -= Gdx.graphics.getDeltaTime();
                }
            } else {
                __instance.deathTimer -= Gdx.graphics.getDeltaTime();
            }

            // The tint fade-out starts shortly after the monster has settled.
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
                float forceX = MathUtils.random(600f, 900f);
                float forceY = MathUtils.random(1000f, 1500f);
                ragdoll.applyGlobalForce(forceX, forceY);
                ragdollBodies.put(monster, ragdoll);
            }
            monster.deathTimer = 2.5f; // This timer won't countdown until the ragdoll settles.
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
                    float rotation = ragdoll.getAverageRotation();
                    float imgCenterX = img.getWidth() * Settings.scale / 2.0F;
                    float imgCenterY = img.getHeight() * Settings.scale / 2.0F;
                    sb.draw(img, centerX - imgCenterX, centerY - imgCenterY, imgCenterX, imgCenterY, img.getWidth() * Settings.scale, img.getHeight() * Settings.scale, 1f, 1f, rotation, 0, 0, img.getWidth(), img.getHeight(), monster.flipHorizontal, monster.flipVertical);
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

    public static class MultiBodyRagdoll {
        private final HashMap<Bone, BoneWobble> boneWobbles;
        private final HashMap<String, WeaponPhysics> weaponBodies;
        private final RagdollPhysics mainBody;
        private final float groundY;

        private static final float GRAVITY = -1200f;
        private static final float RIGHT_WALL_X = 1850f;
        private static final float LEFT_WALL_X = -200f;
        private static final float SETTLED_THRESHOLD = 0.5f; // Must be motionless for this duration.

        private float settledTimer = 0f;
        private float totalRotationDegrees = 0f;
        private float lastRotation = 0f;

        private static final float FIXED_TIMESTEP = 1.0f / 60.0f;
        private float accumulator = 0f;

        public MultiBodyRagdoll(Skeleton skeleton, float groundLevel, float startX, float startY) {
            this.boneWobbles = new HashMap<>();
            this.weaponBodies = new HashMap<>();
            this.groundY = groundLevel;
            this.mainBody = new RagdollPhysics(startX, startY, 0, 0, groundLevel);

            for (Bone bone : skeleton.getBones()) {
                boneWobbles.put(bone, new BoneWobble(bone.getRotation()));
                String boneName = bone.getData().getName().toLowerCase();
                if (boneName.contains("staff") || boneName.contains("weapon") || boneName.contains("sword") || boneName.contains("blade") || boneName.contains("wand") || boneName.contains("rod") || boneName.contains("dagger") || boneName.contains("spear") || boneName.contains("bow") || boneName.contains("axe")) {
                    BaseMod.logger.info("Found weapon bone: " + bone.getData().getName());
                    weaponBodies.put(bone.getData().getName(), new WeaponPhysics(startX + bone.getWorldX() * Settings.scale, startY + bone.getWorldY() * Settings.scale, groundLevel, bone));
                }
            }
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

            float preUpdateVelocityY = mainBody.velocityY;
            if (mainBody.y > groundY + 50f && preUpdateVelocityY > 200f) {
                float airborneIntensity = Math.min(preUpdateVelocityY / 600f, 1.0f);
                mainBody.angularVelocity *= (1.0f + airborneIntensity * 0.02f);
            }

            // Wall collisions
            if (mainBody.x > RIGHT_WALL_X && mainBody.velocityX > 0) {
                handleWallCollision(RIGHT_WALL_X, -0.4f);
            }
            if (mainBody.x < LEFT_WALL_X && mainBody.velocityX < 0) {
                handleWallCollision(LEFT_WALL_X, -0.4f);
            }

            for (WeaponPhysics weapon : weaponBodies.values()) weapon.update(deltaTime);
            for (BoneWobble wobble : boneWobbles.values()) wobble.update(deltaTime, mainBody.velocityX, mainBody.velocityY);
            applyRotationLimiting(deltaTime);
        }

        private void handleWallCollision(float wallX, float bounceMultiplier) {
            mainBody.x = wallX;
            mainBody.velocityX *= bounceMultiplier;
            mainBody.velocityY *= 0.7f;
            float wallImpactIntensity = Math.abs(mainBody.velocityX) / 400f;
            mainBody.angularVelocity += MathUtils.random(-360f, 360f) * (1.0f + wallImpactIntensity);
            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.angularVelocity += MathUtils.random(-270f, 270f) * (1.0f + wallImpactIntensity * 0.5f);
            }
        }

        private void applyRotationLimiting(float deltaTime) {
            float rotationDelta = mainBody.rotation - lastRotation;
            if (rotationDelta > 180f) rotationDelta -= 360f;
            else if (rotationDelta < -180f) rotationDelta += 360f;

            totalRotationDegrees += Math.abs(rotationDelta);
            lastRotation = mainBody.rotation;

            // Exponentially damp angular velocity after each full rotation.
            float flipsCompleted = totalRotationDegrees / 360f;
            float dampingFactor = (float) Math.pow(0.98, flipsCompleted);
            mainBody.angularVelocity *= dampingFactor;
        }

        public boolean hasSettledOnGround() {
            float totalMomentum = Math.abs(mainBody.velocityX) + Math.abs(mainBody.velocityY) + Math.abs(mainBody.angularVelocity) / 10f;
            boolean isLowMomentum = totalMomentum < 30f;
            if (isLowMomentum) {
                settledTimer += Gdx.graphics.getDeltaTime();
                return settledTimer >= SETTLED_THRESHOLD;
            } else {
                settledTimer = 0f;
                return false;
            }
        }

        public void applyGlobalForce(float forceX, float forceY) {
            mainBody.velocityX += forceX * 0.8f;
            mainBody.velocityY += forceY * 0.8f;
            lastRotation = mainBody.rotation;

            float upwardVelocity = Math.max(0, mainBody.velocityY);
            float flipIntensity = Math.min(upwardVelocity / 800f, 2.0f);
            float baseAngularVel = MathUtils.random(-360f, 360f);
            mainBody.angularVelocity += baseAngularVel * (1.0f + flipIntensity);

            if (upwardVelocity > 600f) {
                float flipChance = (upwardVelocity - 600f) / 400f;
                if (MathUtils.random() < flipChance) {
                    mainBody.angularVelocity += Math.signum(baseAngularVel) * 720f * ((int)(flipChance * 3) + 1);
                }
            }

            for (BoneWobble wobble : boneWobbles.values()) {
                wobble.angularVelocity += MathUtils.random(-180f, 180f) * (1.0f + flipIntensity * 0.5f);
            }

            for (WeaponPhysics weapon : weaponBodies.values()) {
                weapon.velocityX += forceX * MathUtils.random(0.8f, 1.0f);
                weapon.velocityY += forceY * MathUtils.random(0.8f, 1.0f);
                float weaponBaseAngular = MathUtils.random(-720f, 720f);
                weapon.angularVelocity += weaponBaseAngular * (1.0f + flipIntensity * 1.5f);
                if (upwardVelocity > 500f && MathUtils.random() < flipIntensity * 0.7f) {
                    weapon.angularVelocity += Math.signum(weaponBaseAngular) * 1440f;
                }
            }
        }

        public void applyToBones(Skeleton skeleton) {
            skeleton.setPosition(mainBody.x, mainBody.y);
            if (skeleton.getRootBone() != null) {
                skeleton.getRootBone().setRotation(mainBody.rotation);
            }

            boolean isDramaticallyFlipping = Math.abs(mainBody.angularVelocity) > 180f;

            for (Bone bone : skeleton.getBones()) {
                if (weaponBodies.containsKey(bone.getData().getName())) {
                    bone.setScaleX(0f);
                    bone.setScaleY(0f);
                } else if (!isDramaticallyFlipping && bone != skeleton.getRootBone()) {
                    BoneWobble wobble = boneWobbles.get(bone);
                    if (wobble != null) {
                        bone.setRotation(bone.getData().getRotation() + wobble.rotation * 0.3f);
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
                            float width = weapon.attachment.getWidth() * Settings.scale;
                            float height = weapon.attachment.getHeight() * Settings.scale;
                            float offsetX = weapon.attachment.getX() * Settings.scale;
                            float offsetY = weapon.attachment.getY() * Settings.scale;
                            float attachmentRotation = weapon.attachment.getRotation();
                            sb.draw(region, weapon.x - width / 2f + offsetX, weapon.y - height / 2f + offsetY, width / 2f, height / 2f, width, height, 1f, 1f, weapon.rotation + attachmentRotation);
                            continue;
                        }
                    } catch (Exception e) {
                        BaseMod.logger.error("Failed to render weapon attachment: " + e.getMessage());
                    }
                }

                // Fallback rendering if attachment fails
                TextureAtlas.AtlasRegion region = atlas.findRegion(weaponName);
                if (region != null) {
                    float width = region.getRegionWidth() * Settings.scale;
                    float height = region.getRegionHeight() * Settings.scale;
                    sb.draw(region, weapon.x - width/2f, weapon.y - height/2f, width/2f, height/2f, width, height, 1f, 1f, weapon.rotation);
                }
            }
        }

        public float getCenterX() { return mainBody.x; }
        public float getCenterY() { return mainBody.y; }
        public float getAverageRotation() { return mainBody.rotation; }
    }

    public static class WeaponPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;
        private final RegionAttachment attachment;

        private static final float WEAPON_BOUNCE_THRESHOLD = 150f;

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

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            rotation += angularVelocity * deltaTime;

            float velocityMagnitude = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float velocityRatio = Math.min(velocityMagnitude / 1000f, 1.0f);
            float rotationDamping = 0.9f + (0.998f - 0.9f) * velocityRatio;
            angularVelocity *= rotationDamping;

            if (y < groundY && velocityY < 0) {
                y = groundY;
                if (Math.abs(velocityY) > WEAPON_BOUNCE_THRESHOLD) {
                    velocityY *= -0.4f;
                    velocityX *= 0.85f;
                    angularVelocity *= 0.6f;
                } else {
                    velocityY = 0f;
                    velocityX *= 0.95f;
                    angularVelocity *= 0.7f;
                }
            }
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

        public BoneWobble(float initialRotation) {}

        public void update(float deltaTime, float parentVelocityX, float parentVelocityY) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            float velocityMagnitude = (float) Math.sqrt(parentVelocityX * parentVelocityX + parentVelocityY * parentVelocityY);
            float velocityRatio = Math.min(velocityMagnitude / 1000f, 1.0f);
            float velocityBasedDamping = 0.85f + (0.995f - 0.85f) * velocityRatio;

            rotation += angularVelocity * deltaTime;
            angularVelocity *= velocityBasedDamping;

            float restoreForce = 0.998f - (0.003f * (1.0f - velocityRatio));
            rotation *= restoreForce;
        }
    }

    public static class RagdollPhysics {
        public float x, y, velocityX, velocityY, rotation, angularVelocity;
        private final float groundY;
        private static final float SIMPLE_BOUNCE_THRESHOLD = 200f;

        public RagdollPhysics(float startX, float startY, float forceX, float forceY, float groundLevel) {
            this.x = startX; this.y = startY;
            this.velocityX = forceX; this.velocityY = forceY;
            this.groundY = groundLevel;
            this.rotation = 0f;
            this.angularVelocity = MathUtils.random(-720f, 720f);
        }

        public void update(float deltaTime) {
            deltaTime = Math.min(deltaTime, 1.0f / 30.0f);
            velocityY += MultiBodyRagdoll.GRAVITY * deltaTime;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;

            float velocityMagnitude = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float velocityRatio = Math.min(velocityMagnitude / 1000f, 1.0f);
            float rotationDamping = 0.85f + (0.998f - 0.85f) * velocityRatio;
            rotation += angularVelocity * deltaTime;
            angularVelocity *= rotationDamping;

            if (y < groundY && velocityY < 0) {
                y = groundY;
                if (Math.abs(velocityY) > SIMPLE_BOUNCE_THRESHOLD) {
                    velocityY *= -0.5f;
                    velocityX *= 0.8f;
                    angularVelocity *= 0.5f;
                } else {
                    velocityY = 0f;
                    velocityX *= 0.9f;
                    angularVelocity *= 0.6f;
                }
            }
            velocityX *= 0.998f;
        }
    }
}

 */