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
 * Encapsulates all reflection operations for accessing monster internals.
 * Centralizes field access and provides clean interface for monster data.
 */
public class ReflectionHelper {
    // Reflection fields for accessing monster internals
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

    /**
     * Get monster's texture atlas
     */
    public TextureAtlas getAtlas(AbstractMonster monster) throws IllegalAccessException {
        if (atlasField == null) {
            throw new IllegalAccessException("atlasField not initialized");
        }
        return (TextureAtlas) atlasField.get(monster);
    }

    /**
     * Get monster's image texture
     */
    public Texture getImage(AbstractMonster monster) throws IllegalAccessException {
        if (imgField == null) {
            throw new IllegalAccessException("imgField not initialized");
        }
        return (Texture) imgField.get(monster);
    }

    /**
     * Get monster's skeleton
     */
    public Skeleton getSkeleton(AbstractMonster monster) throws IllegalAccessException {
        if (skeletonField == null) {
            throw new IllegalAccessException("skeletonField not initialized");
        }
        return (Skeleton) skeletonField.get(monster);
    }

    /**
     * Get monster's skeleton renderer
     */
    public SkeletonRenderer getSkeletonRenderer(AbstractMonster monster) throws IllegalAccessException {
        if (srField == null) {
            throw new IllegalAccessException("srField not initialized");
        }
        return (SkeletonRenderer) srField.get(monster);
    }

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