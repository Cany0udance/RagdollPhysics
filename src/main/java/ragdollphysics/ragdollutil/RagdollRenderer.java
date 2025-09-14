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
    }


    // ================================
    // MAIN RENDERING ENTRY POINT
    // ================================

    /**
     * Main render method - handles both skeleton and image-based ragdolls
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
                renderImageBased(monster, sb, ragdoll, reflectionHelper);
            } else {
                renderSkeletonBased(monster, sb, ragdoll, reflectionHelper, atlas);
            }

            // Render health and name if player is alive
            if (!AbstractDungeon.player.isDead) {
                monster.renderHealth(sb);
                reflectionHelper.renderName(monster, sb);
            }

        } catch (Exception e) {
            throw e;
        }
    }


    // ================================
    // SKELETON-BASED RENDERING
    // ================================

    /** Render monsters with Spine skeleton animations */
    private void renderSkeletonBased(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll,
                                     ReflectionHelper reflectionHelper, TextureAtlas atlas) throws Exception {
        Skeleton skeleton = reflectionHelper.getSkeleton(monster);
        SkeletonRenderer sr = reflectionHelper.getSkeletonRenderer(monster);
        if (skeleton == null || sr == null) {
            return;
        }

        // Apply ragdoll physics to skeleton bones and update transforms
        ragdoll.applyToBones(skeleton, monster);
        skeleton.updateWorldTransform();

        // Apply monster visual properties
        skeleton.setColor(monster.tint.color);
        skeleton.setFlip(monster.flipHorizontal, monster.flipVertical);

        // Switch to polygon sprite batch for skeleton rendering
        sb.end();
        CardCrawlGame.psb.begin();

        // Render skeleton and detached attachments
        sr.draw(CardCrawlGame.psb, skeleton);
        ragdoll.renderDetachedAttachments(CardCrawlGame.psb, atlas, monster);

        // Switch back to normal sprite batch
        CardCrawlGame.psb.end();
        sb.begin();
        sb.setBlendFunction(770, 771); // Reset blend function

        // Render debug visualization
        RagdollDebugRenderer.renderDebugSquares(sb, ragdoll);
    }


    // ================================
    // IMAGE-BASED RENDERING
    // ================================

    /** Render monsters using static images (like Hexaghost) */
    private void renderImageBased(AbstractMonster monster, SpriteBatch sb, MultiBodyRagdoll ragdoll,
                                  ReflectionHelper reflectionHelper) throws Exception {
        Texture img = reflectionHelper.getImage(monster);
        if (img == null) {
            return;
        }

        // Set monster tint and get physics state
        sb.setColor(monster.tint.color);
        float centerX = ragdoll.getCenterX();
        float centerY = ragdoll.getCenterY();
        float rotation = ragdoll.getAverageRotation();

        // Calculate image center for proper rotation
        float imgCenterX = img.getWidth() * Settings.scale / 2.0f;
        float imgCenterY = img.getHeight() * Settings.scale / 2.0f;

        // Render image with physics-based transformation
        sb.draw(img,
                centerX - imgCenterX,                  // x
                centerY - imgCenterY,                  // y
                imgCenterX,                            // origin X
                imgCenterY,                            // origin Y
                img.getWidth() * Settings.scale,       // width
                img.getHeight() * Settings.scale,      // height
                1f, 1f,                               // scale X, Y
                rotation,                             // rotation
                0, 0,                                 // source X, Y
                img.getWidth(), img.getHeight(),      // source width, height
                monster.flipHorizontal,               // flip X
                monster.flipVertical);                // flip Y

        // Render debug visualization
        RagdollDebugRenderer.renderDebugSquares(sb, ragdoll);
    }
}