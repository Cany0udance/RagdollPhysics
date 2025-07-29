package ragdollphysics.ragdollutil;

import com.badlogic.gdx.graphics.Texture;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonRenderer;
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
    // Reflection fields for accessing monster internals
    private static Field imgField;
    private static Field skeletonField;
    private static Field srField;

    // Validation state
    private final String validatorId;
    private int validationCount = 0;

    static {
        initializeReflectionFields();
    }

    public RagdollValidator() {
        this.validatorId = "Validator_" + System.currentTimeMillis() % 10000;
        BaseMod.logger.info("[" + validatorId + "] RagdollValidator initialized");
    }

    /**
     * Initialize reflection fields for accessing monster components
     */
    private static void initializeReflectionFields() {
        try {
            imgField = AbstractMonster.class.getDeclaredField("img");
            imgField.setAccessible(true);

            skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
            skeletonField.setAccessible(true);

            srField = AbstractCreature.class.getDeclaredField("sr");
            srField.setAccessible(true);

            BaseMod.logger.info("RagdollValidator reflection fields initialized successfully");
        } catch (Exception e) {
            BaseMod.logger.error("Failed to initialize RagdollValidator reflection fields: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main validation method - determines if a monster can have ragdoll physics applied
     *
     * @param monster The monster to validate
     * @param failedRagdolls Set of monsters that have previously failed ragdoll creation
     * @return true if ragdoll is viable, false otherwise
     */
    public boolean isRagdollViable(AbstractMonster monster, Set<AbstractMonster> failedRagdolls) {
        validationCount++;
        String monsterName = monster.getClass().getSimpleName();

        try {
            // Quick check: if this monster has failed before, don't try again
            if (failedRagdolls.contains(monster)) {
                // Don't log repeatedly for failed monsters
                return false;
            }

            BaseMod.logger.info("[" + validatorId + "] Validating ragdoll viability for "
                    + monsterName + " (validation #" + validationCount + ")");

            // Check if this is an image-based monster (special case)
            if (isImageBasedMonster(monster)) {
                BaseMod.logger.info("[" + validatorId + "] " + monsterName
                        + " is image-based, ragdoll viable");
                return true;
            }

            // For skeleton-based monsters, validate skeleton components
            ValidationResult skeletonValidation = validateSkeletonComponents(monster);

            if (skeletonValidation.isValid) {
                BaseMod.logger.info("[" + validatorId + "] " + monsterName
                        + " skeleton validation PASSED - " + skeletonValidation.details);
                return true;
            } else {
                BaseMod.logger.info("[" + validatorId + "] " + monsterName
                        + " skeleton validation FAILED - " + skeletonValidation.details);
                failedRagdolls.add(monster);
                return false;
            }

        } catch (Exception e) {
            BaseMod.logger.info("[" + validatorId + "] Ragdoll viability check failed for "
                    + monsterName + ", using default death: " + e.getMessage());
            failedRagdolls.add(monster);
            return false;
        }
    }

    /**
     * Check if this is an image-based monster (has image but no skeleton)
     */
    public boolean isImageBasedMonster(AbstractMonster monster) {
        try {
            Texture img = getMonsterImage(monster);
            Skeleton skeleton = getMonsterSkeleton(monster);

            boolean isImageBased = img != null && skeleton == null;

            if (isImageBased) {
                BaseMod.logger.info("[" + validatorId + "] " + monster.getClass().getSimpleName()
                        + " detected as image-based monster");
            }

            return isImageBased;
        } catch (Exception e) {
            BaseMod.logger.info("[" + validatorId + "] Could not determine if "
                    + monster.getClass().getSimpleName() + " is image-based: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate skeleton-based monster components
     */
    private ValidationResult validateSkeletonComponents(AbstractMonster monster) {
        String monsterName = monster.getClass().getSimpleName();

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
            return ValidationResult.success("skeleton has " + skeleton.getBones().size
                    + " bones, renderer exists");

        } catch (IllegalAccessException e) {
            return ValidationResult.failure("Cannot access skeleton components: " + e.getMessage());
        } catch (Exception e) {
            return ValidationResult.failure("Skeleton validation error: " + e.getMessage());
        }
    }

    /**
     * Get monster's image texture via reflection
     */
    private Texture getMonsterImage(AbstractMonster monster) throws IllegalAccessException {
        if (imgField == null) {
            throw new IllegalAccessException("imgField not initialized");
        }
        return (Texture) imgField.get(monster);
    }

    /**
     * Get monster's skeleton via reflection
     */
    private Skeleton getMonsterSkeleton(AbstractMonster monster) throws IllegalAccessException {
        if (skeletonField == null) {
            throw new IllegalAccessException("skeletonField not initialized");
        }
        return (Skeleton) skeletonField.get(monster);
    }

    /**
     * Get monster's skeleton renderer via reflection
     */
    private SkeletonRenderer getMonsterSkeletonRenderer(AbstractMonster monster) throws IllegalAccessException {
        if (srField == null) {
            throw new IllegalAccessException("srField not initialized");
        }
        return (SkeletonRenderer) srField.get(monster);
    }

    /**
     * Check if specific monster types are known to be problematic
     * This can be expanded based on testing results
     */
    public boolean isKnownProblematicMonster(AbstractMonster monster) {
        String monsterName = monster.getClass().getSimpleName();

        // Add known problematic monster types here
        // Example: some monsters might have complex custom rendering that conflicts
        switch (monsterName) {
            // Currently no known problematic monsters, but structure is here for future use
            default:
                return false;
        }
    }

    /**
     * Check if a monster has the minimum required components for ragdoll physics
     */
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
            BaseMod.logger.info("[" + validatorId + "] Error checking minimum components for "
                    + monster.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Get validation statistics
     */
    public ValidationStats getStats() {
        return new ValidationStats(validationCount);
    }

    /**
     * Helper class to encapsulate validation results
     */
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

    /**
     * Statistics about validation operations
     */
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