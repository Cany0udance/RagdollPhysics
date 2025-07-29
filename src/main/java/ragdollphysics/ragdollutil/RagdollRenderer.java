package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

/**
 * Handles all ragdoll rendering operations.
 * Renders both skeleton-based and image-based ragdolls with proper physics positioning.
 */
public class RagdollRenderer {
    private final String rendererId;

    public RagdollRenderer() {
        this.rendererId = "Renderer_" + System.currentTimeMillis() % 10000;
        BaseMod.logger.info("[" + rendererId + "] RagdollRenderer initialized");
    }

    /**
     * Main render method - handles both skeleton and image-based ragdolls
     *
     * @param monster The monster being rendered
     * @param sb The sprite batch for rendering
     * @param ragdoll The ragdoll physics instance
     * @param reflectionHelper Helper for accessing monster internals
     * @throws Exception if rendering fails
     */
    public void render(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll,
                       ReflectionHelper reflectionHelper) throws Exception {

        // Skip rendering if monster has completely faded out
        if (monster.tint.color.a <= 0) {
            return;
        }

        try {
            // Determine rendering path based on monster type
            TextureAtlas atlas = reflectionHelper.getAtlas(monster);
            if (atlas == null) {
                // Image-based rendering (like Hexaghost)
                renderImageBased(monster, sb, ragdoll, reflectionHelper);
            } else {
                // Skeleton-based rendering (normal monsters)
                renderSkeletonBased(monster, sb, ragdoll, reflectionHelper, atlas);
            }

            // Render health and name if player is alive
            if (!AbstractDungeon.player.isDead) {
                monster.renderHealth(sb);
                reflectionHelper.renderName(monster, sb);
            }

        } catch (Exception e) {
            BaseMod.logger.error("[" + rendererId + "] Rendering failed for "
                    + monster.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Render image-based monsters (like Hexaghost)
     */
    private void renderImageBased(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll,
                                  ReflectionHelper reflectionHelper) throws Exception {

        Texture img = reflectionHelper.getImage(monster);
        if (img == null) {
            return;
        }

        // Set monster's tint color
        sb.setColor(monster.tint.color);

        // Get ragdoll position and rotation
        float centerX = ragdoll.getCenterX();
        float centerY = ragdoll.getCenterY();
        float rotation = ragdoll.getAverageRotation();

        // Calculate image center for proper rotation
        float imgCenterX = img.getWidth() * Settings.scale / 2.0f;
        float imgCenterY = img.getHeight() * Settings.scale / 2.0f;

        // Render the image with physics-based position and rotation
        sb.draw(img,
                centerX - imgCenterX,
                centerY - imgCenterY,
                imgCenterX,  // origin X
                imgCenterY,  // origin Y
                img.getWidth() * Settings.scale,   // width
                img.getHeight() * Settings.scale,  // height
                1f,          // scale X
                1f,          // scale Y
                rotation,    // rotation
                0, 0,        // source X, Y
                img.getWidth(),
                img.getHeight(),
                monster.flipHorizontal,
                monster.flipVertical);
    }

    /**
     * Render skeleton-based monsters with physics applied to bones
     */
    private void renderSkeletonBased(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll,
                                     ReflectionHelper reflectionHelper, TextureAtlas atlas) throws Exception {

        Skeleton skeleton = reflectionHelper.getSkeleton(monster);
        SkeletonRenderer sr = reflectionHelper.getSkeletonRenderer(monster);

        if (skeleton == null || sr == null) {
            return;
        }

        // Apply ragdoll physics to the skeleton bones
        ragdoll.applyToBones(skeleton, monster);

        // Update skeleton world transform after applying physics
        skeleton.updateWorldTransform();

        // Apply monster's tint color and flip settings
        skeleton.setColor(monster.tint.color);
        skeleton.setFlip(monster.flipHorizontal, monster.flipVertical);

        // Switch to polygon sprite batch for skeleton rendering
        sb.end();
        CardCrawlGame.psb.begin();

        // Render the skeleton
        sr.draw(CardCrawlGame.psb, skeleton);

        // Render detached attachments (weapons, etc.) with their own physics
        ragdoll.renderDetachedAttachments(CardCrawlGame.psb, atlas, monster);

        // Switch back to normal sprite batch
        CardCrawlGame.psb.end();
        sb.begin();

        // Reset blend function to standard alpha blending
        sb.setBlendFunction(770, 771);
    }
}