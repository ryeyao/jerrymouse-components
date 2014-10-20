package gateway.util;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Properties;

/**
 * Created by Rye on 2/20/14.
 */
public class Configurations extends CompositeConfiguration {

    public static final String fileName = "config.properties";
    static final Logger logger = LogManager.getLogger(Configurations.class.getName());

    private static volatile String filePath = null;
    private static volatile Properties config = null;
    private static volatile boolean isFilePathSet = false;

    // Configurable vars
    public static class ConfigurableVars {
        public static boolean useAsync = false;
        public static final String USE_ASYNC = "use_async";

        public static boolean forceUpdateDef = false;
        public static final String FORCE_UPDATE_DEF = "force_update_def";

        public static boolean autoRetry = true;
        public static final String AUTO_RETRY = "auto_retry";

        public static int retryIntervalSecond = 1; // available only if autoReconnect is true;
        public static final String RETRY_INTERVAL_SECOND = "retry_interval_second";

        public static String serverHost = null;
        public static final String SERVER_HOST = "server.host";

        public static String serverPort = null;
        public static final String SERVER_PORT = "server.port";

        public static class clientVars {
            public static String commandHandler = null;
            public static final String COMMAND_HANDLER = "client.commandhandler";

            public static String preProcessor = null;
            public static final String PREPROCESSOR = "client.preprocessor";

            public static String worker = null;
            public static final String WORKER = "client.worker";
        }
    }

    private static final class ConfigurationsSingleton {
        private static final Configurations instance = new Configurations();
    }

    private Configurations() {}

    public static Configurations instance() {
        return ConfigurationsSingleton.instance;
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
}
