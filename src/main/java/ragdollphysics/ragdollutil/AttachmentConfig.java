package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.megacrit.cardcrawl.monsters.beyond.TimeEater;
import com.megacrit.cardcrawl.monsters.exordium.Sentry;
import com.megacrit.cardcrawl.monsters.exordium.SlaverRed;

import java.util.HashMap;

public class AttachmentConfig {
    private static boolean printMatchingLogs = false; // Set to true to enable attachment matching debug logs
    private static final HashMap<String, String[]> MONSTER_ATTACHMENTS =
            new HashMap<>();
    private static final String[] GLOBAL_ATTACHMENTS = {"weapon", "sword",
            "blade", "staff", "wand", "rod", "dagger", "spear", "axe", "club",
            "mace", "bow", "shield", "orb", "crystal", "gem", "whip"};

    static {
        // Configure which attachments should be physics bodies per monster
        MONSTER_ATTACHMENTS.put(TimeEater.ID, new String[] {"clock"});
        MONSTER_ATTACHMENTS.put(
                Sentry.ID, new String[] {"top", "bottom", "jewel"});
        MONSTER_ATTACHMENTS.put(SlaverRed.ID, new String[] {"weponred", "net"});
        // FIXED: Use string literal "OrbWalker" instead of OrbWalker.ID
        MONSTER_ATTACHMENTS.put("OrbWalker",
                new String[] {
                        "orb",
                        "leg_left",
                        "leg_right",
                        "head_sw",
                        "head_se",
                        "head_nw",
                        "head_ne",
                        "head_bg", // This wasn't matching before
                        "outline",
                });

        MONSTER_ATTACHMENTS.put("Repulsor",
                new String[] {
                        "orb",
                        "head",
                        "rib1",
                        "rib2",
                        "tailback",
                        "tailfront"
                });
        // DEBUG: Print what we configured
        if (printMatchingLogs) {
            BaseMod.logger.info("STATIC CONFIG DEBUG: OrbWalker attachments = "
                    + java.util.Arrays.toString(MONSTER_ATTACHMENTS.get("OrbWalker")));
        }
    }

    public static String[] getAttachmentsForMonster(String monsterName) {
        return MONSTER_ATTACHMENTS.getOrDefault(monsterName, new String[0]);
    }

    public static boolean shouldDetachAttachment(
            String monsterName, String attachmentName) {
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

            // Check exact match
            if (attachmentLower.equals(attachmentTarget)) {
                if (printMatchingLogs) {
                    BaseMod.logger.info(
                            "MATCHING DEBUG: EXACT MATCH found for '" + attachmentName + "'");
                }
                return true;
            }

            // Check contains
            if (attachmentLower.contains(attachmentTarget)) {
                if (printMatchingLogs) {
                    BaseMod.logger.info("MATCHING DEBUG: CONTAINS MATCH found for '"
                            + attachmentName + "' contains '" + attachmentTarget + "'");
                }
                return true;
            }
        }

        if (printMatchingLogs) {
            BaseMod.logger.info(
                    "MATCHING DEBUG: NO MATCH found for '" + attachmentName + "'");
        }
        return false;
    }
}