package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Encapsulates all reflection operations for accessing creature internals.
 * Centralizes field access and provides clean interface for monster and player data.
 */
public class ReflectionHelper {
    // Reflection fields for accessing creature internals
    private static Field atlasField;
    private static Field imgField;
    private static Field skeletonField;
    private static Field srField;
    private static Method renderNameMethod;
    // Initialization status
    private static boolean fieldsInitialized = false;

    static {
        initializeFields();
    }

    /**
     * Initialize all reflection fields and methods
     */
    private static void initializeFields() {
        try {
            atlasField = AbstractCreature.class.getDeclaredField("atlas");
            atlasField.setAccessible(true);

            // img field is in AbstractMonster, not AbstractCreature
            imgField = AbstractMonster.class.getDeclaredField("img");
            imgField.setAccessible(true);

            skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);

            srField = AbstractCreature.class.getDeclaredField("sr");
            srField.setAccessible(true);

            renderNameMethod = AbstractMonster.class.getDeclaredMethod("renderName", SpriteBatch.class);
            renderNameMethod.setAccessible(true);

            fieldsInitialized = true;
            BaseMod.logger.info("ReflectionHelper fields initialized successfully");
        } catch (Exception e) {
            fieldsInitialized = false;
            BaseMod.logger.error("Failed to initialize ReflectionHelper fields: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if reflection fields were properly initialized
     */
    public boolean isInitialized() {
        return fieldsInitialized;
    }

    // ================================
    // GENERIC CREATURE METHODS
    // ================================
    /**
     * Get creature's texture atlas
     */
    public TextureAtlas getAtlas(AbstractCreature creature) throws IllegalAccessException {
        if (atlasField == null) {
            throw new IllegalAccessException("atlasField not initialized");
        }
        return (TextureAtlas) atlasField.get(creature);
    }

    /**
     * Get creature's image texture
     * Note: This only works for AbstractMonster instances since img field is in AbstractMonster
     */
    public Texture getImage(AbstractCreature creature) throws IllegalAccessException {
        if (imgField == null) {
            throw new IllegalAccessException("imgField not initialized");
        }
        // Only works for monsters since img field is in AbstractMonster
        if (!(creature instanceof AbstractMonster)) {
            return null; // or throw exception if you prefer
        }
        return (Texture) imgField.get(creature);
    }

    /**
     * Get creature's skeleton
     */
    public Skeleton getSkeleton(AbstractCreature creature) throws IllegalAccessException {
        BaseMod.logger.info("[ReflectionHelper] getSkeleton called for " + creature.getClass().getSimpleName());
        if (skeletonField == null) {
            BaseMod.logger.info("[ReflectionHelper] ERROR: skeletonField not initialized");
            throw new IllegalAccessException("skeletonField not initialized");
        }
        Skeleton result = (Skeleton) skeletonField.get(creature);
        BaseMod.logger.info("[ReflectionHelper] Retrieved skeleton: " + (result != null ? "present" : "null"));
        return result;
    }

    /**
     * Get creature's skeleton renderer
     */
    public SkeletonRenderer getSkeletonRenderer(AbstractCreature creature) throws IllegalAccessException {
        if (srField == null) {
            throw new IllegalAccessException("srField not initialized");
        }
        return (SkeletonRenderer) srField.get(creature);
    }

    // ================================
    // MONSTER-SPECIFIC METHODS
    // ================================
    /**
     * Render monster's name using reflection
     */
    public void renderName(AbstractMonster monster, SpriteBatch sb) throws Exception {
        if (renderNameMethod == null) {
            throw new IllegalAccessException("renderNameMethod not initialized");
        }
        renderNameMethod.invoke(monster, sb);
    }
}