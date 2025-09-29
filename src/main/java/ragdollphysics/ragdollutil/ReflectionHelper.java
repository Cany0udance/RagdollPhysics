package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
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
    private static Field imgField;  // For monsters
    private static Field playerImgField;  // For players
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

            // Monster img field
            imgField = AbstractMonster.class.getDeclaredField("img");
            imgField.setAccessible(true);

            // Player img field detection
            initializePlayerImageField();

            skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);

            srField = AbstractCreature.class.getDeclaredField("sr");
            srField.setAccessible(true);

            renderNameMethod = AbstractMonster.class.getDeclaredMethod("renderName", SpriteBatch.class);
            renderNameMethod.setAccessible(true);

            fieldsInitialized = true;
        } catch (Exception e) {
            fieldsInitialized = false;
            e.printStackTrace();
        }
    }

    /**
     * Initialize player image field detection
     */
    private static void initializePlayerImageField() {
        // Try common field names for player images
        String[] possiblePlayerImageFields = {"img", "image", "texture", "playerImg", "characterImg"};

        for (String fieldName : possiblePlayerImageFields) {
            try {
                Field field = AbstractPlayer.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                playerImgField = field;
                return;
            } catch (NoSuchFieldException e) {
                // Continue to next field name
            } catch (Exception e) {
                // Continue to next field name
            }
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
     * Get creature's image texture - now supports both monsters and players
     */
    public Texture getImage(AbstractCreature creature) throws IllegalAccessException {
        if (creature instanceof AbstractMonster) {
            // Handle monsters
            if (imgField == null) {
                throw new IllegalAccessException("imgField not initialized");
            }
            return (Texture) imgField.get(creature);

        } else if (creature instanceof AbstractPlayer) {
            // Handle players
            if (playerImgField == null) {
                return null;
            }
            try {
                return (Texture) playerImgField.get(creature);
            } catch (Exception e) {
                return null;
            }

        } else {
            return null;
        }
    }

    /**
     * Get creature's skeleton
     */
    public Skeleton getSkeleton(AbstractCreature creature) throws IllegalAccessException {
        if (skeletonField == null) {
            throw new IllegalAccessException("skeletonField not initialized");
        }
        return (Skeleton) skeletonField.get(creature);
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

    // ================================
    // UTILITY METHODS
    // ================================

    /**
     * Check if player image field was successfully found
     */
    public boolean hasPlayerImageSupport() {
        return playerImgField != null;
    }

    /**
     * Get the name of the player image field that was found
     */
    public String getPlayerImageFieldName() {
        return playerImgField != null ? playerImgField.getName() : "none";
    }
}