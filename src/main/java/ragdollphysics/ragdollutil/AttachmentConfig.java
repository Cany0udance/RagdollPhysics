package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.monsters.beyond.*;
import com.megacrit.cardcrawl.monsters.city.*;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Configuration class for managing monster attachment behaviors in ragdoll physics.
 * Handles both guaranteed detachments and chance-based dismemberment based on overkill damage.
 */
public class AttachmentConfig {

    // ================================
    // CONFIGURATION FLAGS
    // ================================

    private static final boolean FORCE_DISMEMBERMENT = false; // Set to true for 100% dismemberment chance (testing)

    // ================================
    // RANDOM GENERATOR
    // ================================

    private static final Random random = new Random();

    // ================================
    // GLOBAL ATTACHMENTS
    // ================================

    /** Weapon and item attachments that always detach regardless of monster type */
    private static final String[] GLOBAL_ATTACHMENTS = {
            "weapon", "sword", "blade", "staff", "wand", "rod", "dagger", "spear",
            "axe", "club", "mace", "bow", "shield", "orb", "crystal", "gem", "whip"
    };

    // ================================
    // MONSTER-SPECIFIC MAPPINGS
    // ================================

    /** Maps monster IDs to their guaranteed detachment parts */
    private static final HashMap<String, String[]> MONSTER_ATTACHMENTS = new HashMap<>();

    /** Maps monster IDs to parts that can be dismembered based on overkill damage */
    private static final HashMap<String, String[]> DISMEMBERABLE_PARTS = new HashMap<>();

    /** Maps "monsterID:parentPart" to child attachments that follow their parent */
    private static final HashMap<String, String[]> MONSTER_CHILD_ATTACHMENTS = new HashMap<>();

    // ================================
    // STATIC INITIALIZATION
    // ================================

    static {
        initializeGuaranteedAttachments();
        initializeDismemberableParts();
        initializeChildAttachments();
    }

    /**
     * Configure attachments that ALWAYS detach when the monster dies
     */
    private static void initializeGuaranteedAttachments() {
        MONSTER_ATTACHMENTS.put(Cultist.ID, new String[] {});
        MONSTER_ATTACHMENTS.put(JawWorm.ID, new String[] {"jaw"});
        MONSTER_ATTACHMENTS.put(GremlinThief.ID, new String[] {"wepon"});
        MONSTER_ATTACHMENTS.put(Looter.ID, new String[] {"bandana", "pouch"});
        MONSTER_ATTACHMENTS.put(Mugger.ID, new String[] {"goggles", "pouch"});
        MONSTER_ATTACHMENTS.put(BanditLeader.ID, new String[] {"mask"});
        MONSTER_ATTACHMENTS.put(Sentry.ID, new String[] {"top", "bottom", "jewel"});
        MONSTER_ATTACHMENTS.put(Snecko.ID, new String[] {"eye"});
        MONSTER_ATTACHMENTS.put("TheSnecko", new String[] {"eye"});
        MONSTER_ATTACHMENTS.put(Centurion.ID, new String[] {"shield"});
        MONSTER_ATTACHMENTS.put(Healer.ID, new String[] {"book"});
        MONSTER_ATTACHMENTS.put(SlaverRed.ID, new String[] {"weponred", "net"});
        MONSTER_ATTACHMENTS.put(SphericGuardian.ID, new String[] {"orb (left)", "orb (right)", "orb (back)"});
        MONSTER_ATTACHMENTS.put(Repulsor.ID, new String[] {"orb", "head", "rib1", "rib2", "tailback", "tailfront"});
        MONSTER_ATTACHMENTS.put(Exploder.ID, new String[] {"shell"});
        MONSTER_ATTACHMENTS.put(Spiker.ID, new String[] {"body", "cover", "nose"});

        MONSTER_ATTACHMENTS.put(OrbWalker.ID, new String[] {
                "orb", "leg_left", "leg_right", "head_sw", "head_se",
                "head_nw", "head_ne", "head_bg", "outline"
        });

        MONSTER_ATTACHMENTS.put(ShelledParasite.ID, new String[] {
                "leftLeg", "tentacleHorn1", "tentacleHorn2", "tentacleHorn3",
                "tentacleHorn4", "rightLeg", "lowerBody"
        });

        MONSTER_ATTACHMENTS.put(SnakePlant.ID, new String[] {
                "t1", "t2", "t3", "leafCenter", "leafFront", "leafLeftFront",
                "leafLeftBack", "leafRightFront", "leafRightBack", "leafRightBack2"
        });

        MONSTER_ATTACHMENTS.put(SlimeBoss.ID, new String[] {"hat"});
        MONSTER_ATTACHMENTS.put(Champ.ID, new String[] {"crown", "sword", "shield"});
        MONSTER_ATTACHMENTS.put(TimeEater.ID, new String[] {"clock", "tail"});
        MONSTER_ATTACHMENTS.put(Nemesis.ID, new String[] {"scythe", "skull", "leftMass", "rightMass"});
        MONSTER_ATTACHMENTS.put(SpireGrowth.ID, new String[] {"tentacle_left", "tentacle_right"});

        MONSTER_ATTACHMENTS.put(TheGuardian.ID, new String[] {
                "b2", "b3", "b4", "b5", "b6", "b7", "b8", "base", "body", "core",
                "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
                "f11", "f12", "f13", "f14", "f15", "f16",
                "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10",
                "m11", "m12", "m13", "m14",
                "bgBody", "fgBody", "left_foot", "left_leg", "right_foot", "right_leg",
                "leftarmbg1", "leftarmbg2", "leftarmbg3", "leftarmbg4", "leftarmbg5",
                "leftarmfg1", "leftarmfg2", "leftarmfg3", "leftarmfg4", "leftarmfg5",
                "leftarmfg6", "leftarmfg7", "leftarmfg8", "leftarmfg9", "leftarmfg10", "leftarmfg11",
                "rightarmbg1", "rightarmbg2", "rightarmbg3", "rightarmbg4", "rightarmbg5", "rightarmbg6", "rightarmbg7",
                "rightarmfg1", "rightarmfg2", "rightarmfg3", "rightarmfg4", "rightarmfg5",
                "rightarmfg6", "rightarmfg7", "rightarmfg8", "rightarmfg9", "rightarmfg10"
        });

        MONSTER_ATTACHMENTS.put("Defect", new String[] {
                "Eye_down", "Eye_up", "crackedOrb", "head",
                "left_arm", "left_finger_1", "left_finger_2", "left_finger_3", "left_foot", "left_forearm", "left_quad", "left_shin", "left_thumb",
                "mantle", "mantle_behind",
                "right_arm", "right_finger_1", "right_finger_2", "right_foot", "right_forearm", "right_hand", "right_quad", "right_shin", "right_thumb",
                "wire_main_neck", "wire_neck_l", "wire_neck_l2", "wire_neck_r",
                "wires_body_l_bg", "wires_body_l_fg", "wires_body_r"
        });

        MONSTER_ATTACHMENTS.put(BronzeAutomaton.ID, new String[] {
                "abdomen", "armLeft", "armRight", "chest", "footleft", "footright",
                "forearmLeft", "forearmRight", "handRight", "quadLeft", "quadRight",
                "shoulderLeft", "shoulderRight", "thighleft", "thighright"
        });

        MONSTER_ATTACHMENTS.put(TorchHead.ID, new String[] {
                "halberd"
        });

        MONSTER_ATTACHMENTS.put("TheSilent", new String[] {
                "kris"
        });

        // The Pilot
        MONSTER_ATTACHMENTS.put("MyCharacter", new String[] {
                "tube", "legs", "feet", "hand", "hand2", "shoulder", "torso", "shoulder 2"
        });

        // The Automaton (Downfall)
        MONSTER_ATTACHMENTS.put("AutomatonChar", new String[] {
                "abdomen", "armLeft", "armRight", "chest", "footleft", "footright",
                "forearmLeft", "forearmRight", "handRight", "quadLeft", "quadRight",
                "shoulderLeft", "shoulderRight", "thighleft", "thighright"
        });

        // The Champ (Downfall)
        MONSTER_ATTACHMENTS.put("ChampChar", new String[] {
                "crown", "sword", "shield"
        });

        // The Guardian (Downfall)
        MONSTER_ATTACHMENTS.put("GuardianCharacter", new String[] {
                "b2", "b3", "b4", "b5", "b6", "b7", "b8", "base", "body", "core",
                "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
                "f11", "f12", "f13", "f14", "f15", "f16",
                "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10",
                "m11", "m12", "m13", "m14",
                "bgBody", "fgBody", "left_foot", "left_leg", "right_foot", "right_leg",
                "leftarmbg1", "leftarmbg2", "leftarmbg3", "leftarmbg4", "leftarmbg5",
                "leftarmfg1", "leftarmfg2", "leftarmfg3", "leftarmfg4", "leftarmfg5",
                "leftarmfg6", "leftarmfg7", "leftarmfg8", "leftarmfg9", "leftarmfg10", "leftarmfg11",
                "rightarmbg1", "rightarmbg2", "rightarmbg3", "rightarmbg4", "rightarmbg5", "rightarmbg6", "rightarmbg7",
                "rightarmfg1", "rightarmfg2", "rightarmfg3", "rightarmfg4", "rightarmfg5",
                "rightarmfg6", "rightarmfg7", "rightarmfg8", "rightarmfg9", "rightarmfg10"
        });

        // The Hermit (Downfall)
        MONSTER_ATTACHMENTS.put("hermit", new String[] {
                "gun"
        });

        // The Slime Boss (Downfall)
        MONSTER_ATTACHMENTS.put("SlimeboundCharacter", new String[] {
                "hat"
        });
    }

    /**
     * Configure parts that have a chance to detach based on overkill damage
     */
    private static void initializeDismemberableParts() {
    //    DISMEMBERABLE_PARTS.put(Cultist.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinThief.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinWarrior.ID, new String[] {"legleft", "legright"});
        DISMEMBERABLE_PARTS.put(GremlinWizard.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinNob.ID, new String[] {"hornleft", "hornright"});
        DISMEMBERABLE_PARTS.put(Byrd.ID, new String[] {"wingback", "wingfront", "wingleft", "wingright"});
        DISMEMBERABLE_PARTS.put(BanditPointy.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(BanditBear.ID, new String[] {"mask"});
        DISMEMBERABLE_PARTS.put(Snecko.ID, new String[] {"tail"});
    }

    /**
     * Configure parent-child relationships where child attachments follow their parent
     * Format: "MonsterID:parentPart" -> [childPart1, childPart2, ...]
     */
    private static void initializeChildAttachments() {
        MONSTER_CHILD_ATTACHMENTS.put(GremlinThief.ID + ":head", new String[] {"eyes", "hornleft", "hornright"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWarrior.ID + ":legleft", new String[] {"footleft"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWarrior.ID + ":legright", new String[] {"footright"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWizard.ID + ":head", new String[] {"eyes", "eyelids", "horns"});
        MONSTER_CHILD_ATTACHMENTS.put(BanditBear.ID + ":mask", new String[] {"right_eye", "left_eye"});
        MONSTER_CHILD_ATTACHMENTS.put(Snecko.ID + ":eye", new String[] {"pupil", "iris"});
        MONSTER_CHILD_ATTACHMENTS.put("TheSnecko" + ":eye", new String[] {"pupil", "iris"});
        MONSTER_CHILD_ATTACHMENTS.put(ShelledParasite.ID + ":lowerBody", new String[] {"mouth"});
        MONSTER_CHILD_ATTACHMENTS.put(Centurion.ID + ":shield", new String[] {"shieldhandles"});
        MONSTER_CHILD_ATTACHMENTS.put(Champ.ID + ":sword", new String[] {"swordHilt"});
        MONSTER_CHILD_ATTACHMENTS.put(Champ.ID + ":shield", new String[] {"handle"});
        MONSTER_CHILD_ATTACHMENTS.put("ChampChar" + ":sword", new String[] {"swordHilt"});
        MONSTER_CHILD_ATTACHMENTS.put("ChampChar" + ":shield", new String[] {"handle"});
    }

    // ================================
    // PUBLIC API METHODS
    // ================================

    /**
     * Get all guaranteed detachment parts for a specific monster
     */
    public static String[] getAttachmentsForMonster(String monsterName) {
        return MONSTER_ATTACHMENTS.getOrDefault(monsterName, new String[0]);
    }

    /**
     * Get all dismemberable parts for a specific monster
     */
    public static String[] getDismemberablePartsForMonster(String monsterName) {
        return DISMEMBERABLE_PARTS.getOrDefault(monsterName, new String[0]);
    }

    /**
     * Check if an attachment should detach (guaranteed attachments only)
     */
    public static boolean shouldDetachAttachment(String monsterName, String attachmentName) {
        String attachmentLower = attachmentName.toLowerCase();

        // Check global attachments first (weapons, shields, etc.)
        for (String globalAttachment : GLOBAL_ATTACHMENTS) {
            if (attachmentLower.contains(globalAttachment.toLowerCase())) {
                return true;
            }
        }

        // Check monster-specific guaranteed attachments
        String[] attachments = getAttachmentsForMonster(monsterName);
        for (String attachment : attachments) {
            String attachmentTarget = attachment.toLowerCase();
            if (attachmentLower.equals(attachmentTarget) || attachmentLower.contains(attachmentTarget)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an attachment is from the Haberdashery mod
     */
    public static boolean isHaberdasheryAttachment(String attachmentName) {
        return attachmentName != null && attachmentName.toLowerCase().startsWith("haberdashery");
    }

    /**
     * Enhanced shouldDetachAttachment that includes Haberdashery logic
     */
    public static boolean shouldDetachAttachment(String entityClassName, String attachmentName, float overkillDamage) {
        String attachmentLower = attachmentName.toLowerCase();

        // Check for Haberdashery attachments first
        if (attachmentLower.startsWith("haberdashery")) {
          // BaseMod.logger.info("Found Haberdashery attachment: " + attachmentName + " -> DETACHING");
            return true;
        }

        // Check global attachments
        for (String globalAttachment : GLOBAL_ATTACHMENTS) {
            if (attachmentLower.contains(globalAttachment.toLowerCase())) {
          //      BaseMod.logger.info("Found global attachment: " + attachmentName + " -> DETACHING");
                return true;
            }
        }

        // Check monster-specific guaranteed attachments
        String[] attachments = getAttachmentsForMonster(entityClassName);
        for (String attachment : attachments) {
            String attachmentTarget = attachment.toLowerCase();
            if (attachmentLower.equals(attachmentTarget) || attachmentLower.contains(attachmentTarget)) {
           //     BaseMod.logger.info("Found monster-specific attachment: " + attachmentName + " -> DETACHING");
                return true;
            }
        }

        // Check dismemberment
        boolean shouldDismember = shouldDismember(entityClassName, attachmentName, overkillDamage);
        if (shouldDismember) {
         //   BaseMod.logger.info("Dismemberment triggered for: " + attachmentName + " -> DETACHING");
        }

        return shouldDismember;
    }

    // ================================
    // CHILD ATTACHMENT METHODS
    // ================================

    /**
     * Get child attachments for a specific monster and parent attachment
     */
    public static String[] getChildAttachments(String monsterName, String parentAttachmentName) {
        if (monsterName == null || parentAttachmentName == null) {
            return new String[0];
        }

        String key = monsterName + ":" + parentAttachmentName.toLowerCase();
        String[] directMatch = MONSTER_CHILD_ATTACHMENTS.get(key);
        if (directMatch != null) {
            return directMatch;
        }

        // Check for partial matches (e.g., "head_main" matches "head")
        String parentLower = parentAttachmentName.toLowerCase();
        for (Map.Entry<String, String[]> entry : MONSTER_CHILD_ATTACHMENTS.entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey.startsWith(monsterName + ":")) {
                String entryParent = entryKey.substring((monsterName + ":").length());
                if (parentLower.contains(entryParent) || entryParent.contains(parentLower)) {
                    return entry.getValue();
                }
            }
        }

        return new String[0];
    }

    /**
     * Check if an attachment should be a child of a given parent for a specific monster
     */
    public static boolean isChildAttachment(String monsterName, String parentName, String potentialChildName) {
        String[] childPatterns = getChildAttachments(monsterName, parentName);
        String childLower = potentialChildName.toLowerCase();

        for (String pattern : childPatterns) {
            String patternLower = pattern.toLowerCase();
            if (childLower.contains(patternLower) || childLower.equals(patternLower)) {
                return true;
            }
        }
        return false;
    }

    // ================================
    // PRIVATE HELPER METHODS
    // ================================

    /**
     * Calculate and roll for dismemberment based on overkill damage
     */
    private static boolean shouldDismember(String monsterName, String attachmentName, float overkillDamage) {
        String[] dismemberableParts = getDismemberablePartsForMonster(monsterName);
        String attachmentLower = attachmentName.toLowerCase();

        // Check if this attachment is dismemberable for this monster
        for (String part : dismemberableParts) {
            String partLower = part.toLowerCase();

            if (attachmentLower.equals(partLower) || attachmentLower.contains(partLower)) {
                return calculateDismembermentChance(overkillDamage);
            }
        }

        return false;
    }

    /**
     * Calculate dismemberment chance based on overkill damage
     * - Testing mode: 100% chance
     * - Normal mode: 2% at 25 overkill, scaling to 50% at 50+ overkill
     */
    private static boolean calculateDismembermentChance(float overkillDamage) {
        if (FORCE_DISMEMBERMENT) {
            return true;
        }

        if (overkillDamage < 25f) {
            return false;
        }

        // Linear scaling: 2% at 25 damage, 50% at 50+ damage
        float chance = Math.min(2f + (overkillDamage - 25f) * (48f / 25f), 50f) / 100f;
        return random.nextFloat() < chance;
    }
}