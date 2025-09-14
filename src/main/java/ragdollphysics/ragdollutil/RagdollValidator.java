package ragdollphysics.ragdollutil;

import com.badlogic.gdx.graphics.Texture;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import basemod.BaseMod;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Validates whether a monster can have a ragdoll physics effect applied.
 * Determines viability based on skeleton structure, renderer availability, and previous failures.
 */
public class RagdollValidator {
    private static Field playerImgField;
    private static Field imgField;
    private static Field skeletonField;
    private static Field srField;
    static {
        initializeReflectionFields();
    }
    // ================================
    // INSTANCE VARIABLES
    // ================================
    private final String validatorId;
    private int validationCount = 0;
    // ================================
    // CONSTRUCTOR
    // ================================
    public RagdollValidator() {
        this.validatorId = "Validator_" + System.currentTimeMillis() % 10000;
        BaseMod.logger.info("[RagdollValidator] Created validator with ID: " + validatorId);
    }
    // ================================
    // REFLECTION INITIALIZATION
    // ================================
    /** Initialize reflection fields for accessing monster components */
    private static void initializeReflectionFields() {
        try {
            imgField = AbstractMonster.class.getDeclaredField("img");
            imgField.setAccessible(true);
            skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);
            srField = AbstractCreature.class.getDeclaredField("sr");
            srField.setAccessible(true);
            BaseMod.logger.info("[RagdollValidator] Static reflection fields initialized successfully");
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] ERROR: Failed to initialize static reflection fields: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // ================================
    // MAIN VALIDATION METHODS
    // ================================
    /** Main validation method - determines if a monster can have ragdoll physics applied */
    public boolean isRagdollViable(AbstractMonster monster, Set<AbstractMonster> failedRagdolls) {
        validationCount++;
        String monsterName = monster.getClass().getSimpleName();
        BaseMod.logger.info("[RagdollValidator] isRagdollViable called for " + monsterName + " (validation #" + validationCount + ")");

        try {
            // Quick check: if this monster has failed before, don't try again
            if (failedRagdolls.contains(monster)) {
                BaseMod.logger.info("[RagdollValidator] Monster " + monsterName + " already in failed set, returning false");
                return false;
            }

            // Check if this is an image-based monster (special case)
            if (isImageBasedMonster(monster)) {
                BaseMod.logger.info("[RagdollValidator] Monster " + monsterName + " is image-based, returning true");
                return true;
            }

            // For skeleton-based monsters, validate skeleton components
            BaseMod.logger.info("[RagdollValidator] Validating skeleton components for " + monsterName);
            ValidationResult skeletonValidation = validateSkeletonComponents(monster);
            if (!skeletonValidation.isValid) {
                BaseMod.logger.info("[RagdollValidator] Skeleton validation failed for " + monsterName + ": " + skeletonValidation.details);
                failedRagdolls.add(monster);
                return false;
            }

            BaseMod.logger.info("[RagdollValidator] Skeleton validation passed for " + monsterName + ": " + skeletonValidation.details);
            return true;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] ERROR: Exception during validation for " + monsterName + ": " + e.getMessage());
            failedRagdolls.add(monster);
            return false;
        }
    }

    /** Check if this is an image-based monster (has image but no skeleton) */
    public boolean isImageBasedMonster(AbstractMonster monster) {
        try {
            Texture img = getMonsterImage(monster);
            Skeleton skeleton = getMonsterSkeleton(monster);
            boolean result = img != null && skeleton == null;
            BaseMod.logger.info("[RagdollValidator] " + monster.name + " image-based check: img=" +
                    (img != null) + ", skeleton=" + (skeleton != null) + ", result=" + result);
            return result;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] " + monster.name + " image-based check failed: " + e.getMessage());
            return false;
        }
    }

    // ================================
    // PLAYER VALIDATION METHODS
    // ================================
    /** Main validation method for players - determines if a player can have ragdoll physics applied */
    public boolean isPlayerRagdollViable(AbstractPlayer player, Set<AbstractPlayer> failedPlayerRagdolls) {
        validationCount++;
        String playerName = player.getClass().getSimpleName();
        BaseMod.logger.info("[RagdollValidator] isPlayerRagdollViable called for " + playerName + " (validation #" + validationCount + ")");

        try {
            // Quick check: if this player has failed before, don't try again
            if (failedPlayerRagdolls.contains(player)) {
                BaseMod.logger.info("[RagdollValidator] Player " + playerName + " already in failed set, returning false");
                return false;
            }

            // Check if this is an image-based player (special case)
            if (isImageBasedPlayer(player)) {
                BaseMod.logger.info("[RagdollValidator] Player " + playerName + " is image-based, returning true");
                return true;
            }

            // For skeleton-based players, validate skeleton components
            BaseMod.logger.info("[RagdollValidator] Validating skeleton components for player " + playerName);
            ValidationResult skeletonValidation = validatePlayerSkeletonComponents(player);
            if (!skeletonValidation.isValid) {
                BaseMod.logger.info("[RagdollValidator] Player skeleton validation failed for " + playerName + ": " + skeletonValidation.details);
                failedPlayerRagdolls.add(player);
                return false;
            }

            BaseMod.logger.info("[RagdollValidator] Player skeleton validation passed for " + playerName + ": " + skeletonValidation.details);
            return true;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] ERROR: Exception during player validation for " + playerName + ": " + e.getMessage());
            failedPlayerRagdolls.add(player);
            return false;
        }
    }

    /** Check if this is an image-based player (has image but no skeleton) */
    public boolean isImageBasedPlayer(AbstractPlayer player) {
        try {
            Texture img = getPlayerImage(player);
            Skeleton skeleton = getPlayerSkeleton(player);
            boolean result = img != null && skeleton == null;
            BaseMod.logger.info("[RagdollValidator] " + player.getClass().getSimpleName() + " image-based check: img=" +
                    (img != null) + ", skeleton=" + (skeleton != null) + ", result=" + result);
            return result;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] " + player.getClass().getSimpleName() + " image-based check failed: " + e.getMessage());
            return false;
        }
    }

    /** Validate skeleton-based player components */
    private ValidationResult validatePlayerSkeletonComponents(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollValidator] validatePlayerSkeletonComponents for " + player.getClass().getSimpleName());
        try {
            // Check skeleton accessibility and validity
            Skeleton skeleton = getPlayerSkeleton(player);
            if (skeleton == null) {
                BaseMod.logger.info("[RagdollValidator] Player skeleton is null");
                return ValidationResult.failure("Player skeleton is null");
            }

            // Check if skeleton has bones
            if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
                String boneInfo = skeleton.getBones() == null ? "null" : "size=" + skeleton.getBones().size;
                BaseMod.logger.info("[RagdollValidator] Player has no bones (bones: " + boneInfo + ")");
                return ValidationResult.failure("Player has no bones (bones: " + boneInfo + ")");
            }

            // Check skeleton renderer accessibility and validity
            SkeletonRenderer sr = getPlayerSkeletonRenderer(player);
            if (sr == null) {
                BaseMod.logger.info("[RagdollValidator] Player SkeletonRenderer is null");
                return ValidationResult.failure("Player SkeletonRenderer is null");
            }

            // All checks passed
            String successMsg = "player skeleton has " + skeleton.getBones().size + " bones, renderer exists";
            BaseMod.logger.info("[RagdollValidator] Player validation successful: " + successMsg);
            return ValidationResult.success(successMsg);
        } catch (IllegalAccessException e) {
            String errorMsg = "Cannot access player skeleton components: " + e.getMessage();
            BaseMod.logger.info("[RagdollValidator] ERROR: " + errorMsg);
            return ValidationResult.failure(errorMsg);
        } catch (Exception e) {
            String errorMsg = "Player skeleton validation error: " + e.getMessage();
            BaseMod.logger.info("[RagdollValidator] ERROR: " + errorMsg);
            return ValidationResult.failure(errorMsg);
        }
    }

    // ================================
    // SKELETON VALIDATION
    // ================================
    /** Validate skeleton-based monster components */
    private ValidationResult validateSkeletonComponents(AbstractMonster monster) {
        try {
            // Check skeleton accessibility and validity
            Skeleton skeleton = getMonsterSkeleton(monster);
            if (skeleton == null) {
                return ValidationResult.failure("Skeleton is null");
            }

            // Check if skeleton has bones
            if (skeleton.getBones() == null || skeleton.getBones().size == 0) {
                String boneInfo = skeleton.getBones() == null ? "null" : "size=" + skeleton.getBones().size;
                return ValidationResult.failure("No bones (bones: " + boneInfo + ")");
            }

            // Check skeleton renderer accessibility and validity
            SkeletonRenderer sr = getMonsterSkeletonRenderer(monster);
            if (sr == null) {
                return ValidationResult.failure("SkeletonRenderer is null");
            }

            // All checks passed
            return ValidationResult.success("skeleton has " + skeleton.getBones().size + " bones, renderer exists");
        } catch (IllegalAccessException e) {
            return ValidationResult.failure("Cannot access skeleton components: " + e.getMessage());
        } catch (Exception e) {
            return ValidationResult.failure("Skeleton validation error: " + e.getMessage());
        }
    }

    // ================================
    // REFLECTION ACCESS METHODS
    // ================================
    /** Get monster's image texture via reflection */
    private Texture getMonsterImage(AbstractMonster monster) throws IllegalAccessException {
        if (imgField == null) {
            throw new IllegalAccessException("imgField not initialized");
        }
        return (Texture) imgField.get(monster);
    }

    /** Get monster's skeleton via reflection */
    private Skeleton getMonsterSkeleton(AbstractMonster monster) throws IllegalAccessException {
        if (skeletonField == null) {
            throw new IllegalAccessException("skeletonField not initialized");
        }
        return (Skeleton) skeletonField.get(monster);
    }

    /** Get monster's skeleton renderer via reflection */
    private SkeletonRenderer getMonsterSkeletonRenderer(AbstractMonster monster) throws IllegalAccessException {
        if (srField == null) {
            throw new IllegalAccessException("srField not initialized");
        }
        return (SkeletonRenderer) srField.get(monster);
    }

    // ================================
    // PLAYER REFLECTION ACCESS METHODS
    // ================================
    /** Get player's image texture via reflection */
    private Texture getPlayerImage(AbstractPlayer player) throws IllegalAccessException {
        BaseMod.logger.info("[RagdollValidator] getPlayerImage called for " + player.getClass().getSimpleName());

        if (playerImgField != null) {
            // Try player-specific img field first
            try {
                Texture result = (Texture) playerImgField.get(player);
                BaseMod.logger.info("[RagdollValidator] Player image via playerImgField: " + (result != null ? "present" : "null"));
                return result;
            } catch (Exception e) {
                BaseMod.logger.info("[RagdollValidator] Failed to get player image via playerImgField: " + e.getMessage());
            }
        }

        // Fallback: try the monster img field (this will likely fail)
        if (imgField == null) {
            BaseMod.logger.info("[RagdollValidator] ERROR: imgField not initialized for player image access");
            throw new IllegalAccessException("imgField not initialized");
        }

        try {
            Texture result = (Texture) imgField.get(player);
            BaseMod.logger.info("[RagdollValidator] Player image via imgField: " + (result != null ? "present" : "null"));
            return result;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] Failed to get player image via imgField (expected): " + e.getMessage());
            return null; // Return null instead of throwing - this is expected to fail
        }
    }

    /** Get player's skeleton via reflection */
    private Skeleton getPlayerSkeleton(AbstractPlayer player) throws IllegalAccessException {
        BaseMod.logger.info("[RagdollValidator] getPlayerSkeleton called for " + player.getClass().getSimpleName());
        if (skeletonField == null) {
            BaseMod.logger.info("[RagdollValidator] ERROR: skeletonField not initialized for player skeleton access");
            throw new IllegalAccessException("skeletonField not initialized");
        }
        Skeleton result = (Skeleton) skeletonField.get(player);
        BaseMod.logger.info("[RagdollValidator] Player skeleton result: " + (result != null ? "present" : "null"));
        return result;
    }

    /** Get player's skeleton renderer via reflection */
    private SkeletonRenderer getPlayerSkeletonRenderer(AbstractPlayer player) throws IllegalAccessException {
        BaseMod.logger.info("[RagdollValidator] getPlayerSkeletonRenderer called for " + player.getClass().getSimpleName());
        if (srField == null) {
            BaseMod.logger.info("[RagdollValidator] ERROR: srField not initialized for player renderer access");
            throw new IllegalAccessException("srField not initialized");
        }
        SkeletonRenderer result = (SkeletonRenderer) srField.get(player);
        BaseMod.logger.info("[RagdollValidator] Player skeleton renderer result: " + (result != null ? "present" : "null"));
        return result;
    }

    // ================================
    // UTILITY METHODS
    // ================================
    /** Check if specific monster types are known to be problematic */
    public boolean isKnownProblematicMonster(AbstractMonster monster) {
        String monsterName = monster.getClass().getSimpleName();
        // Add known problematic monster types here if needed
        return false;
    }

    /** Check if a monster has the minimum required components for ragdoll physics */
    public boolean hasMinimumRequiredComponents(AbstractMonster monster) {
        try {
            // Image-based monsters need at least an image
            if (isImageBasedMonster(monster)) {
                return getMonsterImage(monster) != null;
            }

            // Skeleton-based monsters need skeleton and renderer
            Skeleton skeleton = getMonsterSkeleton(monster);
            SkeletonRenderer renderer = getMonsterSkeletonRenderer(monster);
            return skeleton != null && renderer != null
                    && skeleton.getBones() != null && skeleton.getBones().size > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Get validation statistics */
    public ValidationStats getStats() {
        return new ValidationStats(validationCount);
    }

    // ================================
    // PLAYER UTILITY METHODS
    // ================================
    /** Check if a player has the minimum required components for ragdoll physics */
    public boolean playerHasMinimumRequiredComponents(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollValidator] playerHasMinimumRequiredComponents for " + player.getClass().getSimpleName());
        try {
            // Image-based players need at least an image
            if (isImageBasedPlayer(player)) {
                boolean hasImg = getPlayerImage(player) != null;
                BaseMod.logger.info("[RagdollValidator] Player is image-based, has image: " + hasImg);
                return hasImg;
            }

            // Skeleton-based players need skeleton and renderer
            Skeleton skeleton = getPlayerSkeleton(player);
            SkeletonRenderer renderer = getPlayerSkeletonRenderer(player);
            boolean hasRequirements = skeleton != null && renderer != null
                    && skeleton.getBones() != null && skeleton.getBones().size > 0;
            BaseMod.logger.info("[RagdollValidator] Player skeleton requirements: skeleton=" + (skeleton != null) +
                    ", renderer=" + (renderer != null) +
                    ", bones=" + (skeleton != null && skeleton.getBones() != null ? skeleton.getBones().size : "null") +
                    ", hasRequirements=" + hasRequirements);
            return hasRequirements;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollValidator] ERROR in playerHasMinimumRequiredComponents: " + e.getMessage());
            return false;
        }
    }

    // ================================
    // HELPER CLASSES
    // ================================
    /** Helper class to encapsulate validation results */
    private static class ValidationResult {
        final boolean isValid;
        final String details;
        private ValidationResult(boolean isValid, String details) {
            this.isValid = isValid;
            this.details = details;
        }
        static ValidationResult success(String details) {
            return new ValidationResult(true, details);
        }
        static ValidationResult failure(String details) {
            return new ValidationResult(false, details);
        }
    }

    /** Statistics about validation operations */
    public static class ValidationStats {
        public final int totalValidations;
        public ValidationStats(int totalValidations) {
            this.totalValidations = totalValidations;
        }
        @Override
        public String toString() {
            return String.format("ValidationStats{totalValidations=%d}", totalValidations);
        }
    }
}