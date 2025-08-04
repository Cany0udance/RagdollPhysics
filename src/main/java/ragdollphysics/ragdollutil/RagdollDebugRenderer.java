package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import static ragdollphysics.RagdollPhysics.enableDebugSquares;

/**
 * Debug visualization for ragdoll physics.
 * Renders colored squares showing physics centers, visual positions, and collision boundaries.
 */
public class RagdollDebugRenderer {
    private static Texture debugSquareTexture = null;
    private static boolean debugRenderingEnabled = enableDebugSquares;
    private static boolean printFieldLogs = false; // Set to true to enable field discovery logs

    /**
     * Renders debug squares for a ragdoll system.
     * Color legend:
     * RED: Physics center (where physics simulation happens)
     * MAGENTA: Expected visual center (where we're trying to position the skeleton)
     * ORANGE: Monster's drawX/drawY position
     * LIME: Actual skeleton position (skeleton.getX/Y)
     * CYAN: "body" bone world position (the actual visual body center)
     * YELLOW: Detached attachment physics bodies
     * WHITE: Ground level indicator line
     * PINK: Ceiling level indicator line
     */
    public static void renderDebugSquares(SpriteBatch sb, MultiBodyRagdoll ragdoll) {
        if (!debugRenderingEnabled) return;

        loadDebugTexture();
        if (debugSquareTexture == null) return;

        // Store original color
        Color originalColor = sb.getColor();

        // Debug square size
        float squareSize = 20f * Settings.scale;

        // 1. Main body physics center (RED)
        sb.setColor(Color.RED);
        sb.draw(debugSquareTexture,
                ragdoll.mainBody.x - squareSize/2f,
                ragdoll.mainBody.y - squareSize/2f,
                squareSize, squareSize);

        // 2. Visual center based on fixed relationship (MAGENTA)
        float visualCenterX = ragdoll.mainBody.x + ragdoll.getPhysicsToVisualOffsetX();
        float visualCenterY = ragdoll.mainBody.y + ragdoll.getPhysicsToVisualOffsetY();
        sb.setColor(Color.MAGENTA);
        sb.draw(debugSquareTexture,
                visualCenterX - squareSize/2f,
                visualCenterY - squareSize/2f,
                squareSize, squareSize);

        // 3. Monster drawX/drawY (ORANGE) - Always show this as baseline
        AbstractMonster monster = ragdoll.getAssociatedMonster();
        sb.setColor(Color.ORANGE);
        sb.draw(debugSquareTexture,
                monster.drawX - squareSize/2f,
                monster.drawY - squareSize/2f,
                squareSize, squareSize);

        // 4. Try to find and show skeleton position (LIME GREEN)
        if (!ragdoll.isImageBased() && monster != null) {
            renderSkeletonDebugInfo(sb, squareSize, ragdoll, monster);
        }

        // 5. Attachment physics bodies (YELLOW)
        sb.setColor(Color.YELLOW);
        for (AttachmentPhysics attachment : ragdoll.getAttachmentBodies().values()) {
            sb.draw(debugSquareTexture,
                    attachment.x - squareSize/2f,
                    attachment.y - squareSize/2f,
                    squareSize, squareSize);
        }

        // 6. Ground level indicator (WHITE line of squares)
        renderGroundIndicator(sb, ragdoll.mainBody.x, ragdoll.getGroundY());

        // 7. Ceiling level indicator (PINK line of squares)
        renderCeilingIndicator(sb, ragdoll.mainBody.x);

        // Restore original color
        sb.setColor(originalColor);
    }

    private static void renderSkeletonDebugInfo(SpriteBatch sb, float squareSize, MultiBodyRagdoll ragdoll, AbstractMonster monster) {
        // COMPREHENSIVE field search with detailed logging
        Class<?> monsterClass = monster.getClass();
        String monsterClassName = monsterClass.getSimpleName();

        // Log every few seconds for debugging
        if (ragdoll.getUpdateCount() % 180 == 0 && printFieldLogs) {
            BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] === SKELETON SEARCH DEBUG ===");
            BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] Monster class: " + monsterClassName);
            BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] Searching for skeleton field...");

            // List ALL fields in the monster class
            java.lang.reflect.Field[] allFields = monsterClass.getDeclaredFields();
            BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] Monster has " + allFields.length + " fields:");
            for (java.lang.reflect.Field field : allFields) {
                BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] - Field: " + field.getName() + " (type: " + field.getType().getSimpleName() + ")");
            }

            // Also check parent classes
            Class<?> parentClass = monsterClass.getSuperclass();
            while (parentClass != null && !parentClass.equals(Object.class)) {
                java.lang.reflect.Field[] parentFields = parentClass.getDeclaredFields();
                BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] Parent class " + parentClass.getSimpleName() + " has " + parentFields.length + " fields:");
                for (java.lang.reflect.Field field : parentFields) {
                    BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] - Parent Field: " + field.getName() + " (type: " + field.getType().getSimpleName() + ")");
                }
                parentClass = parentClass.getSuperclass();
            }
        }

        // Try to find skeleton field
        Skeleton foundSkeleton = findSkeletonField(monster);
        String foundFieldName = null;

        if (foundSkeleton != null) {
            // Show skeleton position as LIME GREEN square
            sb.setColor(Color.LIME);
            float skeletonX = foundSkeleton.getX();
            float skeletonY = foundSkeleton.getY();
            sb.draw(debugSquareTexture,
                    skeletonX - squareSize/2f,
                    skeletonY - squareSize/2f,
                    squareSize, squareSize);

            // Find and show the "body" bone position (CYAN)
            renderBodyBoneDebugInfo(sb, squareSize, foundSkeleton, ragdoll);

        } else if (ragdoll.getUpdateCount() % 180 == 0 && printFieldLogs) {
            BaseMod.logger.warn("[" + ragdoll.getRagdollId() + "] NO SKELETON FIELD FOUND in " + monsterClassName + " or its parent classes!");
        }
    }

    private static void renderBodyBoneDebugInfo(SpriteBatch sb, float squareSize, Skeleton skeleton, MultiBodyRagdoll ragdoll) {
        Bone bodyBone = skeleton.findBone("body");
        if (bodyBone != null) {
            sb.setColor(Color.CYAN);
            float bodyX = skeleton.getX() + bodyBone.getWorldX() * Settings.scale;
            float bodyY = skeleton.getY() + bodyBone.getWorldY() * Settings.scale;
            sb.draw(debugSquareTexture,
                    bodyX - squareSize/2f,
                    bodyY - squareSize/2f,
                    squareSize, squareSize);

            if (ragdoll.getUpdateCount() % 60 == 0 && printFieldLogs) {
                BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] BODY BONE FOUND at world pos: (" + String.format("%.1f", bodyX) + ", " + String.format("%.1f", bodyY) + ")");
            }
        } else {
            // Try alternative bone names if "body" doesn't exist
            String[] alternativeNames = {"torso", "chest", "spine", "hip", "pelvis", "trunk"};
            boolean foundAlternative = false;

            for (String boneName : alternativeNames) {
                Bone bone = skeleton.findBone(boneName);
                if (bone != null) {
                    sb.setColor(Color.CYAN);
                    float boneX = skeleton.getX() + bone.getWorldX() * Settings.scale;
                    float boneY = skeleton.getY() + bone.getWorldY() * Settings.scale;
                    sb.draw(debugSquareTexture,
                            boneX - squareSize/2f,
                            boneY - squareSize/2f,
                            squareSize, squareSize);

                    if (ragdoll.getUpdateCount() % 60 == 0 && printFieldLogs) {
                        BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] BODY-LIKE BONE '" + boneName + "' found at world pos: (" + String.format("%.1f", boneX) + ", " + String.format("%.1f", boneY) + ")");
                    }
                    foundAlternative = true;
                    break;
                }
            }

            if (!foundAlternative && ragdoll.getUpdateCount() % 180 == 0 && printFieldLogs) {
                BaseMod.logger.warn("[" + ragdoll.getRagdollId() + "] NO BODY BONE FOUND - available bones:");
                for (int i = 0; i < skeleton.getBones().size; i++) {
                    Bone bone = skeleton.getBones().get(i);
                    BaseMod.logger.info("[" + ragdoll.getRagdollId() + "] - Bone: " + bone.getData().getName());
                }
            }
        }
    }

    private static Skeleton findSkeletonField(AbstractMonster monster) {
        Class<?> searchClass = monster.getClass();
        while (searchClass != null) {
            java.lang.reflect.Field[] fields = searchClass.getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(monster);

                    if (fieldValue instanceof Skeleton) {
                        return (Skeleton) fieldValue;
                    }
                } catch (Exception e) {
                    // Ignore and continue
                }
            }

            searchClass = searchClass.getSuperclass();
        }
        return null;
    }

    private static void renderGroundIndicator(SpriteBatch sb, float centerX, float groundY) {
        sb.setColor(Color.WHITE);
        float groundSquareSize = 10f * Settings.scale;
        float startX = centerX - 200f;
        float endX = centerX + 200f;
        for (float x = startX; x <= endX; x += groundSquareSize * 2) {
            sb.draw(debugSquareTexture,
                    x - groundSquareSize/2f,
                    groundY - groundSquareSize/2f,
                    groundSquareSize, groundSquareSize);
        }
    }

    private static void renderCeilingIndicator(SpriteBatch sb, float centerX) {
        sb.setColor(Color.PINK);
        float groundSquareSize = 10f * Settings.scale;
        float startX = centerX - 200f;
        float endX = centerX + 200f;
        float CEILING_Y = 1100f; // Move this to a constants class if you create one
        for (float x = startX; x <= endX; x += groundSquareSize * 2) {
            sb.draw(debugSquareTexture,
                    x - groundSquareSize/2f,
                    CEILING_Y - groundSquareSize/2f,
                    groundSquareSize, groundSquareSize);
        }
    }

    private static void loadDebugTexture() {
        if (debugSquareTexture == null) {
            try {
                debugSquareTexture = ImageMaster.loadImage("ragdollphysics/images/DebugSquare.png");
                BaseMod.logger.info("Debug square texture loaded successfully");
            } catch (Exception e) {
                BaseMod.logger.warn("Could not load debug square texture: " + e.getMessage());
                debugRenderingEnabled = false;
            }
        }
    }
}