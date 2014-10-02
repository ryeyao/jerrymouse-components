package gateway.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URLClassLoader;
import java.util.Properties;

/**
 * Created by Rye on 2/20/14.
 */
public class ConfigurationFile {

    public static final String fileName = "config.properties";
    static final Logger logger = LogManager.getLogger(ConfigurationFile.class.getName());

    private static volatile String filePath = null;
    private static volatile Properties config = null;
    private static volatile boolean isFilePathSet = false;

    private static final class ConfigurationSingleton {
        private static final ConfigurationFile instance = new ConfigurationFile();
    }

    private ConfigurationFile() {}

    public static ConfigurationFile instance() {
        return ConfigurationSingleton.instance;
    }

    @Deprecated
    public void updateFile(Properties prop) {
        updateFile(prop, filePath);
    }

    public static void updateFile(Properties prop, String fname) {

        logger.info("Updating configuration in file {}", fname);
        OutputStream output = null;

        try {
            output = new FileOutputStream(fname);
            prop.store(output, null);
            output.flush();

        } catch (IOException io) {
            io.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
    }

    public Properties loadConfiguration() {
        if (filePath == null) {
            return null;
        }

        if (config == null) {
            config = loadConfiguration(filePath);
        }

        return config;
    }

    public static Properties loadConfiguration(String fname) {
        logger.info("Loading configuration file {}", fname);
        Properties prop = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream(fname);
            prop.load(input);

            if (input != null) {
                try {
                    input.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
            return prop;
        } catch (IOException io) {
//            io.printStackTrace();
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    @Deprecated
    private Properties initDefaultConfiguration() {

        Properties prop = new Properties();
        prop.setProperty("server.host", "192.168.119.175");
        prop.setProperty("server.port", "8111");
//        prop.setProperty("cn.iie.jerrymouse.sensor.udp.host", "192.168.111.25");
//        prop.setProperty("cn.iie.jerrymouse.sensor.udp.port", "50002");
//        prop.setProperty("gateway.host", "192.168.110.225");
//        prop.setProperty("gateway.port", "50003");
        prop.setProperty("client.commandhandler", "   #add your own implementation of CommandHandler here");
        prop.setProperty("client.preprocessor", "   #add your own implementation of PreProcessor here");

        return prop;
    }

    public String getFilePath() {
        return new String(filePath);
    }

    public void setFilePath(String filePath) {
        if (isFilePathSet) {
            return;
        }
        this.filePath = filePath;
        isFilePathSet = true;
    }
}
