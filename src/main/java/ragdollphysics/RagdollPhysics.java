package ragdollphysics;

import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.interfaces.EditKeywordsSubscriber;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.FontHelper;
import ragdollphysics.util.GeneralUtils;
import ragdollphysics.util.KeywordInfo;
import ragdollphysics.util.TextureLoader;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglFileHandle;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scannotation.AnnotationDB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpireInitializer
public class RagdollPhysics implements
        EditStringsSubscriber,
        PostInitializeSubscriber {
    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    static { loadModInfo(); }
    private static final String resourcesFolder = checkResourcesPath();
    public static final Logger logger = LogManager.getLogger(modID); //Used to output to the console.

    private static SpireConfig config;
    public static boolean enableZeroGravity;
    public static boolean enableDebugSquares;
    public static boolean enableImageRagdolls;

    public static String makeID(String id) {
        return modID + ":" + id;
    }

    //This will be called by ModTheSpire because of the @SpireInitializer annotation at the top of the class.
    public static void initialize() throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("enableZeroGravity", "false");
        defaults.setProperty("enableDebugSquares", "false");
        defaults.setProperty("enableImageRagdolls", "true");
        config = new SpireConfig(modID, "config", defaults);
        enableZeroGravity = config.getBool("enableZeroGravity");
        enableDebugSquares = config.getBool("enableDebugSquares");
        enableImageRagdolls = config.getBool("enableImageRagdolls");
        new RagdollPhysics();
    }

    public RagdollPhysics() {
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");
    }

    @Override
    public void receivePostInitialize() {
        ModPanel settingsPanel = new ModPanel();

        String toggleText1 = "Enable zero gravity mode";
        String toggleText2 = "Enable debug squares (requires restart)";
        String toggleText3 = "Enable ragdolls for image-based enemies";


        // Always display names toggle
        settingsPanel.addUIElement(new ModLabeledToggleButton(toggleText1, 350, 700, Settings.CREAM_COLOR, FontHelper.charDescFont, config.getBool("enableZeroGravity"), settingsPanel, label -> {}, button -> {
            enableZeroGravity = button.enabled;
            config.setBool("enableZeroGravity", button.enabled);
            try {config.save();} catch (Exception e) {}
        }));

        // Should name bosses toggle
        settingsPanel.addUIElement(new ModLabeledToggleButton(toggleText2, 350, 650, Settings.CREAM_COLOR, FontHelper.charDescFont, config.getBool("enableDebugSquares"), settingsPanel, label -> {}, button -> {
            enableDebugSquares = button.enabled;
            config.setBool("enableDebugSquares", button.enabled);
            try {config.save();} catch (Exception e) {}
        }));

        settingsPanel.addUIElement(new ModLabeledToggleButton(toggleText3, 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont, config.getBool("enableImageRagdolls"), settingsPanel, label -> {}, button -> {
            enableImageRagdolls = button.enabled;
            config.setBool("enableImageRagdolls", button.enabled);
            try {config.save();} catch (Exception e) {}
        }));

        Texture badgeTexture = TextureLoader.getTexture(imagePath("badge.png"));
        BaseMod.registerModBadge(badgeTexture, info.Name, GeneralUtils.arrToString(info.Authors), info.Description, settingsPanel);
    }

    /*----------Localization----------*/

    //This is used to load the appropriate localization files based on language.
    private static String getLangString()
    {
        return Settings.language.name().toLowerCase();
    }
    private static final String defaultLanguage = "eng";

    public static final Map<String, KeywordInfo> keywords = new HashMap<>();

    @Override
    public void receiveEditStrings() {
        /*
            First, load the default localization.
            Then, if the current language is different, attempt to load localization for that language.
            This results in the default localization being used for anything that might be missing.
            The same process is used to load keywords slightly below.
        */
        loadLocalization(defaultLanguage); //no exception catching for default localization; you better have at least one that works.
        if (!defaultLanguage.equals(getLangString())) {
            try {
                loadLocalization(getLangString());
            }
            catch (GdxRuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadLocalization(String lang) {
        BaseMod.loadCustomStringsFile(UIStrings.class,
                localizationPath(lang, "UIStrings.json"));
    }

    //These methods are used to generate the correct filepaths to various parts of the resources folder.
    public static String localizationPath(String lang, String file) {
        return resourcesFolder + "/localization/" + lang + "/" + file;
    }

    public static String imagePath(String file) {
        return resourcesFolder + "/images/" + file;
    }
    public static String characterPath(String file) {
        return resourcesFolder + "/images/character/" + file;
    }
    public static String powerPath(String file) {
        return resourcesFolder + "/images/powers/" + file;
    }
    public static String relicPath(String file) {
        return resourcesFolder + "/images/relics/" + file;
    }

    /**
     * Checks the expected resources path based on the package name.
     */
    private static String checkResourcesPath() {
        String name = RagdollPhysics.class.getName(); //getPackage can be iffy with patching, so class name is used instead.
        int separator = name.indexOf('.');
        if (separator > 0)
            name = name.substring(0, separator);

        FileHandle resources = new LwjglFileHandle(name, Files.FileType.Internal);
        if (resources.child("images").exists() && resources.child("localization").exists()) {
            return name;
        }

        throw new RuntimeException("\n\tFailed to find resources folder; expected it to be named \"" + name + "\"." +
                " Either make sure the folder under resources has the same name as your mod's package, or change the line\n" +
                "\t\"private static final String resourcesFolder = checkResourcesPath();\"\n" +
                "\tat the top of the " + RagdollPhysics.class.getSimpleName() + " java file.");
    }

    /**
     * This determines the mod's ID based on information stored by ModTheSpire.
     */
    private static void loadModInfo() {
        Optional<ModInfo> infos = Arrays.stream(Loader.MODINFOS).filter((modInfo)->{
            AnnotationDB annotationDB = Patcher.annotationDBMap.get(modInfo.jarURL);
            if (annotationDB == null)
                return false;
            Set<String> initializers = annotationDB.getAnnotationIndex().getOrDefault(SpireInitializer.class.getName(), Collections.emptySet());
            return initializers.contains(RagdollPhysics.class.getName());
        }).findFirst();
        if (infos.isPresent()) {
            info = infos.get();
            modID = info.ID;
        }
        else {
            throw new RuntimeException("Failed to determine mod info/ID based on initializer.");
        }
    }
}
