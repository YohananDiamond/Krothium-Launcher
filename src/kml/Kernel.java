package kml;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import kml.gui.BrowserFX;
import kml.gui.MainFX;
import org.json.JSONObject;

import javax.swing.*;
import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Set;


/**
 * @author DarkLBP
 *         website https://krothium.com
 */

public final class Kernel {
    private final File workingDir;
    private final Console console;
    private final Profiles profiles;
    private final Versions versions;
    private final Settings settings;
    private final Downloader downloader;
    private final Authentication authentication;
    private final GameLauncher gameLauncher;
    private final HostServices hostServices;
    private final MainFX mainForm;
    private final File configFile, cacheFolder;
    private JSONObject launcherProfiles;
    public static Kernel instance;
    public Kernel(Stage stage, HostServices hs) {
        this(Utils.getWorkingDirectory(), stage, hs);
    }

    private Kernel(File workDir, Stage stage, HostServices hs) {
        instance = this;
        this.workingDir = workDir;
        if (!this.workingDir.exists()) {
            this.workingDir.mkdirs();
        }
        this.cacheFolder = new File(this.workingDir, "cache");
        this.cacheFolder.mkdir();
        this.console = new Console(this);
        this.console.printInfo("KML v" + Constants.KERNEL_BUILD_NAME + " by DarkLBP (https://krothium.com)");
        this.console.printInfo("Kernel build: " + Constants.KERNEL_BUILD);
        this.console.printInfo("OS: " + System.getProperty("os.name"));
        this.console.printInfo("OS Version: " + System.getProperty("os.version"));
        this.console.printInfo("OS Architecture: " + System.getProperty("os.arch"));
        this.console.printInfo("Java Version: " + System.getProperty("java.version"));
        this.console.printInfo("Java Vendor: " + System.getProperty("java.vendor"));
        this.console.printInfo("Java Architecture: " + System.getProperty("sun.arch.data.model"));
        try {
            this.getClass().getClassLoader().loadClass("javafx.embed.swing.JFXPanel");
            this.console.printInfo("JavaFX loaded.");
        } catch (ClassNotFoundException e) {
            final File jfxrt = new File(System.getProperty("java.home"), "lib/jfxrt.jar");
            if (jfxrt.isFile()) {
                this.console.printInfo("Attempting to load JavaFX manually...");
                try {
                    if (addToSystemClassLoader(jfxrt)) {
                        this.console.printInfo("JavaFX loaded manually.");
                    } else {
                        this.console.printError("Found JavaFX but it couldn't be loaded!");
                        warnJavaFX();
                    }
                } catch (Throwable e2) {
                    this.console.printError("Found JavaFX but it couldn't be loaded!");
                    warnJavaFX();
                }
            } else {
                this.console.printError("JavaFX library not found. Please update Java!");
                warnJavaFX();
            }
        }
        console.printInfo("Using custom HTTPS certificate checker? | " + Utils.ignoreHTTPSCert());
        console.printInfo("Reading launcher profiles...");
        configFile = this.getConfigFile();
        try {
            if (configFile.exists() && configFile.isFile()) {
                launcherProfiles = new JSONObject(Utils.readURL(this.getConfigFile().toURI().toURL()));
            } else {
                console.printError("Launcher profiles file does not exists.");
            }
        } catch (Exception e) {
            console.printError("Malformed launcher profiles file.");
        }
        this.profiles = new Profiles(this);
        this.versions = new Versions(this);
        this.settings = new Settings(this);
        this.downloader = new Downloader(this);
        this.authentication = new Authentication(this);
        this.gameLauncher = new GameLauncher(this);
        this.hostServices = hs;
        this.loadSettings();
        this.loadVersions();
        this.loadProfiles();
        this.loadUsers();
        //this.mainForm = null;
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/kml/gui/fxml/Main.fxml"));
        Parent p;
        try {
            p = loader.load();
        } catch (IOException e) {
            p = null;
            this.console.printError("Failed to initialize JavaFX GUI!");
            this.console.printError(e.getMessage());
            exitSafely();
        }
        stage.getIcons().add(Constants.APPLICATION_ICON);
        stage.setTitle("Krothium Minecraft Launcher");
        stage.setScene(new Scene(p));
        stage.setResizable(false);
        stage.setMaximized(false);
        stage.setOnCloseRequest(e -> exitSafely());
        mainForm = loader.getController();
        mainForm.initialize(this, stage);
        stage.show();

//        FXMLLoader loader = new FXMLLoader();
//        loader.setLocation(getClass().getResource("/kml/gui/fxml/Browser.fxml"));
//        Parent p;
//        try {
//            p = loader.load();
//        } catch (IOException e) {
//            p = null;
//            this.console.printError("Failed to initialize JavaFX GUI!");
//            this.console.printError(e.getMessage());
//            exitSafely();
//        }
//        stage.getIcons().add(Constants.APPLICATION_ICON);
//        stage.setTitle("Krothium Minecraft Launcher");
//        stage.setScene(new Scene(p));
//        stage.setResizable(false);
//        stage.setMaximized(false);
//        stage.setOnCloseRequest(e -> exitSafely());
//        BrowserFX browser = loader.getController();
//        stage.show();
    }

    public static boolean addToSystemClassLoader(final File file) throws IntrospectionException {
        if (ClassLoader.getSystemClassLoader() instanceof URLClassLoader) {
            final URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            try {
                final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(classLoader, file.toURI().toURL());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    public Console getConsole() {
        return this.console;
    }

    public HostServices getHostServices() {
        return this.hostServices;
    }

    public File getWorkingDir() {
        return this.workingDir;
    }

    public File getCacheFolder() {
        return this.cacheFolder;
    }

    public void saveProfiles() {
        JSONObject output = new JSONObject();
        JSONObject profiles = this.profiles.toJSON();
        JSONObject authdata = this.authentication.toJSON();
        Set pset = profiles.keySet();
        for (Object aPset : pset) {
            String name = aPset.toString();
            output.put(name, profiles.get(name));
        }
        Set aset = authdata.keySet();
        for (Object anAset : aset) {
            String name = anAset.toString();
            output.put(name, authdata.get(name));
        }
        output.put("settings", this.settings.toJSON());
        if (!Utils.writeToFile(output.toString(4), this.getConfigFile())) {
            console.printError("Failed to save the profiles file!");
        }
    }

    private void loadProfiles() {
        profiles.fetchProfiles();
    }

    private void loadVersions() {
        versions.fetchVersions();
    }

    private void loadUsers() {
        authentication.fetchUsers();
    }

    private void loadSettings() {
        settings.loadSettings();
    }

    public Versions getVersions() {
        return this.versions;
    }

    public Profiles getProfiles() {
        return this.profiles;
    }

    public Settings getSettings() {
        return this.settings;
    }

    public Downloader getDownloader() {
        return this.downloader;
    }

    public Authentication getAuthentication() {
        return this.authentication;
    }

    private File getConfigFile() {
        return new File(this.workingDir + File.separator + "launcher_profiles.json");
    }

    public JSONObject getLauncherProfiles() {
        return launcherProfiles;
    }

    public GameLauncher getGameLauncher() {
        return this.gameLauncher;
    }

    public void exitSafely() {
        this.saveProfiles();
        this.console.printInfo("Shutting down launcher...");
        this.console.close();
        Platform.exit();
    }

    public String checkForUpdates() {
        try {
            String r = Utils.readURL(Constants.GETLATEST_URL);
            String[] data = r.split(":");
            int version = Integer.parseInt(Utils.fromBase64(data[0]));
            if (version > Constants.KERNEL_BUILD) {
                console.printInfo("New kernel build available: " + version);
                return data[1];
            }
        } catch (Exception ex) {
            console.printError("Failed to check for updates: " + ex);
            return null;
        }
        return null;
    }

    private void warnJavaFX() {
        JOptionPane.showMessageDialog(null, "Failed to load JavaFX. Please update Java.", "Error", JOptionPane.ERROR_MESSAGE);
        exitSafely();
    }

    public MainFX getGUI() {
        return this.mainForm;
    }
}
