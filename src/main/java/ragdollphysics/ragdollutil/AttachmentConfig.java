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

public class AttachmentConfig {
    private static boolean printMatchingLogs = false;
    private static boolean forceDismemberment = false; // Set to true for 100% dismemberment chance (testing)

    private static final HashMap<String, String[]> MONSTER_ATTACHMENTS = new HashMap<>();
    private static final HashMap<String, String[]> DISMEMBERABLE_PARTS = new HashMap<>();

    // NEW: Child attachment mapping - defines which attachments follow their parent
    private static final HashMap<String, String[]> MONSTER_CHILD_ATTACHMENTS = new HashMap<>();
    private static final String[] GLOBAL_ATTACHMENTS = {"weapon", "sword",
            "blade", "staff", "wand", "rod", "dagger", "spear", "axe", "club",
            "mace", "bow", "shield", "orb", "crystal", "gem", "whip"};
    private static final Random random = new Random();

    static {
        // Configure attachments that ALWAYS detach
        MONSTER_ATTACHMENTS.put(TimeEater.ID, new String[] {"clock"});
        MONSTER_ATTACHMENTS.put(Sentry.ID, new String[] {"top", "bottom", "jewel"});
        MONSTER_ATTACHMENTS.put(GremlinThief.ID, new String[] {"wepon"});
        MONSTER_ATTACHMENTS.put(SlaverRed.ID, new String[] {"weponred", "net"});
        MONSTER_ATTACHMENTS.put(OrbWalker.ID,
                new String[] {
                        "orb",
                        "leg_left",
                        "leg_right",
                        "head_sw",
                        "head_se",
                        "head_nw",
                        "head_ne",
                        "head_bg",
                        "outline",
                });

        MONSTER_ATTACHMENTS.put(Repulsor.ID,
                new String[] {
                        "orb",
                        "head",
                        "rib1",
                        "rib2",
                        "tailback",
                        "tailfront"
                });

        MONSTER_ATTACHMENTS.put(Exploder.ID,
                new String[] {
                        "shell"
                });

        MONSTER_ATTACHMENTS.put(Spiker.ID,
                new String[] {
                        "body",
                        "cover",
                        "nose"
                });

        MONSTER_ATTACHMENTS.put(JawWorm.ID,
                new String[] {
                        "jaw"
                });

        MONSTER_ATTACHMENTS.put(Looter.ID,
                new String[] {
                        "bandana",
                        "pouch"
                });

        MONSTER_ATTACHMENTS.put(Mugger.ID,
                new String[] {
                        "goggles",
                        "pouch"
                });


        MONSTER_ATTACHMENTS.put(BanditLeader.ID,
                new String[] {
                        "mask"
                });

        MONSTER_ATTACHMENTS.put(SphericGuardian.ID,
                new String[] {
                        "orb (left)",
                        "orb (right)",
                        "orb (back)"
                });

        MONSTER_ATTACHMENTS.put(ShelledParasite.ID,
                new String[] {
                        "leftLeg",
                        "tentacleHorn1",
                        "tentacleHorn2",
                        "tentacleHorn3",
                        "tentacleHorn4",
                        "rightLeg",
                        "lowerBody"
                });

        MONSTER_ATTACHMENTS.put(SnakePlant.ID,
                new String[] {
                        "t1",
                        "t2",
                        "t3",
                        "leafCenter",
                        "leafFront",
                        "leafLeftFront",
                        "leafLeftBack",
                        "leafRightFront",
                        "leafRightBack",
                        "leafRightBack2"
                });

        MONSTER_ATTACHMENTS.put(Snecko.ID,
                new String[] {
                        "eye"
                });

        MONSTER_ATTACHMENTS.put(Centurion.ID,
                new String[] {
                        "shield"
                });

        MONSTER_ATTACHMENTS.put(Healer.ID,
                new String[] {
                        "book"
                });

        MONSTER_ATTACHMENTS.put(Nemesis.ID,
                new String[] {
                        "scythe",
                        "skull"
                });

        MONSTER_ATTACHMENTS.put(Nemesis.ID,
                new String[] {
                        "leftMass",
                        "rightMass"
                });

        MONSTER_ATTACHMENTS.put(SpireGrowth.ID,
                new String[] {
                        "tentacle_left",
                        "tentacle_right"
                });

        MONSTER_ATTACHMENTS.put(SlimeBoss.ID,
                new String[] {
                        "hat"
                });

        MONSTER_ATTACHMENTS.put(Champ.ID,
                new String[] {
                        "crown",
                        "sword",
                        "shield"
                });

        MONSTER_ATTACHMENTS.put(TimeEater.ID,
                new String[] {
                        "tail"
                });

        MONSTER_ATTACHMENTS.put(TheGuardian.ID,
                new String[] {
                        "b2", "b3", "b4", "b5", "b6", "b7", "b8",
                        "base", "body", "core",
                        "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
                        "f11", "f12", "f13", "f14", "f15", "f16",
                        "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10",
                        "m11", "m12", "m13", "m14",
                        "bgBody", "fgBody",
                        "left_foot", "left_leg",
                        "leftarmbg1", "leftarmbg2", "leftarmbg3", "leftarmbg4", "leftarmbg5",
                        "leftarmfg1", "leftarmfg2", "leftarmfg3", "leftarmfg4", "leftarmfg5",
                        "leftarmfg6", "leftarmfg7", "leftarmfg8", "leftarmfg9", "leftarmfg10", "leftarmfg11",
                        "right_foot", "right_leg",
                        "rightarmbg1", "rightarmbg2", "rightarmbg3", "rightarmbg4", "rightarmbg5", "rightarmbg6", "rightarmbg7",
                        "rightarmfg1", "rightarmfg2", "rightarmfg3", "rightarmfg4", "rightarmfg5",
                        "rightarmfg6", "rightarmfg7", "rightarmfg8", "rightarmfg9", "rightarmfg10"
                });

        MONSTER_ATTACHMENTS.put(BronzeAutomaton.ID,
                new String[] {
                        "abdomen", "armLeft", "armRight", "chest",
                        "footleft", "footright",
                        "forearmLeft", "forearmRight", "handRight",
                        "quadLeft", "quadRight",
                        "shoulderLeft", "shoulderRight",
                        "thighleft", "thighright"
                });

        // NEW: Configure parent-child relationships
        // Format: Parent attachment name -> array of child attachment patterns
        MONSTER_CHILD_ATTACHMENTS.put(GremlinThief.ID + ":head", new String[] {"eyes", "hornleft", "hornright"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWarrior.ID + ":legleft", new String[] {"footleft"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWarrior.ID + ":legright", new String[] {"footright"});
        MONSTER_CHILD_ATTACHMENTS.put(GremlinWizard.ID + ":head", new String[] {"eyes", "eyelids", "horns"});
        MONSTER_CHILD_ATTACHMENTS.put(BanditBear.ID + ":mask", new String[] {"right_eye", "left_eye"});
        MONSTER_CHILD_ATTACHMENTS.put(Snecko.ID + ":eye", new String[] {"pupil", "iris"});
        MONSTER_CHILD_ATTACHMENTS.put(ShelledParasite.ID + ":lowerBody", new String[] {"mouth"});
        MONSTER_CHILD_ATTACHMENTS.put(Centurion.ID + ":shield", new String[] {"shieldhandles"});
        MONSTER_CHILD_ATTACHMENTS.put(Champ.ID + ":sword", new String[] {"swordHilt"});
        MONSTER_CHILD_ATTACHMENTS.put(Champ.ID + ":shield", new String[] {"handle"});

        // Configure parts that have a CHANCE to detach based on overkill
        DISMEMBERABLE_PARTS.put(Cultist.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinThief.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinWarrior.ID, new String[] {"legleft", "legright"});
        DISMEMBERABLE_PARTS.put(GremlinWizard.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(GremlinNob.ID, new String[] {"hornleft", "hornright"});
        DISMEMBERABLE_PARTS.put(Byrd.ID, new String[] {"wingback", "wingfront", "wingleft", "wingright"});
        DISMEMBERABLE_PARTS.put(BanditPointy.ID, new String[] {"head"});
        DISMEMBERABLE_PARTS.put(BanditBear.ID, new String[] {"mask"});
        DISMEMBERABLE_PARTS.put(Snecko.ID, new String[] {"tail"});

        if (printMatchingLogs) {
            BaseMod.logger.info("STATIC CONFIG DEBUG: OrbWalker attachments = "
                    + java.util.Arrays.toString(MONSTER_ATTACHMENTS.get("OrbWalker")));
        }
    }

    public static String[] getAttachmentsForMonster(String monsterName) {
        return MONSTER_ATTACHMENTS.getOrDefault(monsterName, new String[0]);
    }

    public static String[] getDismemberablePartsForMonster(String monsterName) {
        return DISMEMBERABLE_PARTS.getOrDefault(monsterName, new String[0]);
    }

    // NEW: Get child attachments for a specific monster and parent attachment
    public static String[] getChildAttachments(String monsterName, String parentAttachmentName) {
        if (monsterName == null || parentAttachmentName == null) return new String[0];

        String key = monsterName + ":" + parentAttachmentName.toLowerCase();
        String[] directMatch = MONSTER_CHILD_ATTACHMENTS.get(key);
        if (directMatch != null) {
            return directMatch;
        }

        // Check for partial matches of parent name (e.g., "head_main" matches "head")
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

    // NEW: Check if an attachment should be a child of a given parent for a specific monster
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

    // Original method for guaranteed attachments only
    public static boolean shouldDetachAttachment(String monsterName, String attachmentName) {
        String attachmentLower = attachmentName.toLowerCase();

        if (printMatchingLogs) {
            BaseMod.logger.info("MATCHING DEBUG: Monster='" + monsterName
                    + "', Attachment='" + attachmentName + "'");
        }

        // Check global attachments first
        for (String globalAttachment : GLOBAL_ATTACHMENTS) {
            if (attachmentLower.contains(globalAttachment.toLowerCase())) {
                if (printMatchingLogs) {
                    BaseMod.logger.info("MATCHING DEBUG: Found global match for '"
                            + attachmentName + "' with '" + globalAttachment + "'");
                }
                return true;
            }
        }

        // Check monster-specific attachments
        String[] attachments = getAttachmentsForMonster(monsterName);
        if (printMatchingLogs) {
            BaseMod.logger.info("MATCHING DEBUG: Monster-specific attachments: "
                    + java.util.Arrays.toString(attachments));
        }

        for (String attachment : attachments) {
            String attachmentTarget = attachment.toLowerCase();
            if (printMatchingLogs) {
                BaseMod.logger.info("MATCHING DEBUG: Comparing '" + attachmentLower
                        + "' with '" + attachmentTarget + "'");
            }

            if (attachmentLower.equals(attachmentTarget) || attachmentLower.contains(attachmentTarget)) {
                if (printMatchingLogs) {
                    BaseMod.logger.info("MATCHING DEBUG: MATCH found for '" + attachmentName + "'");
                }
                return true;
            }
        }

        if (printMatchingLogs) {
            BaseMod.logger.info("MATCHING DEBUG: NO MATCH found for '" + attachmentName + "'");
        }
        return false;
    }

    // New overloaded method that includes dismemberment logic
    public static boolean shouldDetachAttachment(String monsterName, String attachmentName, float overkillDamage) {
        // First check if it's a guaranteed attachment
        if (shouldDetachAttachment(monsterName, attachmentName)) {
            return true;
        }

        // Then check if it should be dismembered
        return shouldDismember(monsterName, attachmentName, overkillDamage);
    }

    private static boolean shouldDismember(String monsterName, String attachmentName, float overkillDamage) {
        String[] dismemberableParts = getDismemberablePartsForMonster(monsterName);
        String attachmentLower = attachmentName.toLowerCase();

        for (String part : dismemberableParts) {
            String partLower = part.toLowerCase();

            if (attachmentLower.equals(partLower) || attachmentLower.contains(partLower)) {
                // Check if we're forcing dismemberment for testing
                if (forceDismemberment) {
                    BaseMod.logger.info("DISMEMBERMENT: " + attachmentName + " from " + monsterName
                            + " - FORCED DISMEMBERMENT (testing mode, overkill: "
                            + String.format("%.1f", overkillDamage) + ")");
                    return true;
                }

                // Normal mode: require 25+ overkill damage
                if (overkillDamage < 25f) {
                    return false;
                }

                // Calculate normal dismemberment chance: 2% at 25 overkill, 50% at 50 overkill
                float chance = Math.min(2f + (overkillDamage - 25f) * (48f / 25f), 50f) / 100f;
                boolean dismembered = random.nextFloat() < chance;

                if (printMatchingLogs || dismembered) {
                    BaseMod.logger.info("DISMEMBERMENT: " + attachmentName + " from " + monsterName
                            + " - Overkill: " + String.format("%.1f", overkillDamage)
                            + ", Chance: " + String.format("%.1f%%", chance * 100)
                            + ", Result: " + (dismembered ? "DISMEMBERED" : "intact"));
                }

                return dismembered;
            }
        }

        return false;
    }

    // Utility method for debugging monster-specific child relationships
    public static void debugChildRelationships(String monsterName) {
        BaseMod.logger.info("=== CHILD ATTACHMENT DEBUG FOR " + monsterName + " ===");

        String prefix = monsterName + ":";
        for (Map.Entry<String, String[]> entry : MONSTER_CHILD_ATTACHMENTS.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                String parent = key.substring(prefix.length());
                String[] children = entry.getValue();
                BaseMod.logger.info("Parent '" + parent + "' -> Children: " + java.util.Arrays.toString(children));
            }
        }
    }


    public static void debugAttachmentScaling(String monsterName, Skeleton skeleton) {
        if (!monsterName.equals(TheGuardian.ID)) return;

        BaseMod.logger.info("=== GUARDIAN ATTACHMENT SCALING DEBUG ===");

        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String attachmentName = slot.getAttachment().getName();
                Bone bone = slot.getBone();

                BaseMod.logger.info("Attachment: " + attachmentName);
                BaseMod.logger.info("  Bone: " + bone.getData().getName());
                BaseMod.logger.info("  Bone Scale: (" + bone.getScaleX() + ", " + bone.getScaleY() + ")");
                BaseMod.logger.info("  Bone World Transform: (" + bone.getA() + ", " + bone.getB() + ", " + bone.getC() + ", " + bone.getD() + ")");

                if (slot.getAttachment() instanceof RegionAttachment) {
                    RegionAttachment ra = (RegionAttachment) slot.getAttachment();
                    BaseMod.logger.info("  RegionAttachment Scale: (" + ra.getScaleX() + ", " + ra.getScaleY() + ")");
                    BaseMod.logger.info("  RegionAttachment Size: " + ra.getWidth() + "x" + ra.getHeight());
                }

                Bone parent = bone.getParent();
                while (parent != null) {
                    BaseMod.logger.info("  Parent '" + parent.getData().getName() + "' Scale: ("
                            + parent.getScaleX() + ", " + parent.getScaleY() + ")");
                    parent = parent.getParent();
                }
            }
        }
    }
}