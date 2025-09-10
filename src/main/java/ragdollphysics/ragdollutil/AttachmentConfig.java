package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.megacrit.cardcrawl.monsters.beyond.OrbWalker;
import com.megacrit.cardcrawl.monsters.beyond.Repulsor;
import com.megacrit.cardcrawl.monsters.beyond.TimeEater;
import com.megacrit.cardcrawl.monsters.exordium.Cultist;
import com.megacrit.cardcrawl.monsters.exordium.Sentry;
import com.megacrit.cardcrawl.monsters.exordium.SlaverRed;
import com.megacrit.cardcrawl.monsters.exordium.TheGuardian;

import java.util.HashMap;
import java.util.Random;

public class AttachmentConfig {
    private static boolean printMatchingLogs = false;
    private static boolean forceDismemberment = false; // Set to true for 100% dismemberment chance (testing)

    private static final HashMap<String, String[]> MONSTER_ATTACHMENTS = new HashMap<>();
    private static final HashMap<String, String[]> DISMEMBERABLE_PARTS = new HashMap<>();
    private static final String[] GLOBAL_ATTACHMENTS = {"weapon", "sword",
            "blade", "staff", "wand", "rod", "dagger", "spear", "axe", "club",
            "mace", "bow", "shield", "orb", "crystal", "gem", "whip"};
    private static final Random random = new Random();

    static {
        // Configure attachments that ALWAYS detach
        MONSTER_ATTACHMENTS.put(TimeEater.ID, new String[] {"clock"});
        MONSTER_ATTACHMENTS.put(Sentry.ID, new String[] {"top", "bottom", "jewel"});
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

        // Configure parts that have a CHANCE to detach based on overkill
        DISMEMBERABLE_PARTS.put(Cultist.ID, new String[] {"head"});

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