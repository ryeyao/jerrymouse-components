package gateway;

import cn.iie.gaia.LifecycleException;
import cn.iie.gaia.entity.ComponentBase;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gateway.abstracthandler.CommandHandler;
import gateway.abstracthandler.PreProcessor;
import gateway.util.ConfigurationFile;
import gateway.util.Json2ResourceDef;
import gateway.util.MoreResourceInfo;
import gateway.util.ResourceCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;
import wshare.dc.DC;
import wshare.dc.resource.*;
import wshare.dc.util.DefinitionHelper;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Rye on 2/21/14.
 */
public class Gateway extends ComponentBase {

    static {
//        System.setProperty("log4j.configurationFile", "log4j2.xml");
        Thread.currentThread().setContextClassLoader(Gateway.class.getClassLoader());
    }
    static final Logger logger = LogManager.getLogger("[Framework] " + Gateway.class.getName());

    public static Properties idmap;
    public static String IDMAP_FILE = "idmap.ini";
    public static String XML_DIR = "xmls";
    public static String RES_DEF_DIR = "resources";
    public static String BASE_DIR = "";

    private HashMap<String, Property> handlers = new HashMap<String, Property>();

    //private ArrayList<Resource> resources = new ArrayList<Resource>();
    private static final Object idmapLock = new Object();
    private static final Object resourcesLock = new Object();
    private static final Object registerLock = new Object();

    // Configurable vars
    private class ConfigurableVars {
        public boolean useAsync = false;
        public boolean forceUpdateDef = false;
        public boolean autoRetry = true;
        public int retryIntervalSecond = 1; // available only if autoReconnect is true;
    }
    private ConfigurableVars confVars = new ConfigurableVars();

    private Class<CommandHandler> commandHandlerClass;
    private Class<PreProcessor> preprocessorClass;

    public Properties loadConfiguration() {
        IDMAP_FILE = getComponentBase() + File.separatorChar + IDMAP_FILE;
        XML_DIR = getComponentBase() + File.separatorChar + XML_DIR;
        RES_DEF_DIR = getComponentBase() + File.separatorChar + RES_DEF_DIR;
        BASE_DIR = getComponentBase();

        ConfigurationFile cf = ConfigurationFile.instance();
        cf.setFilePath(getComponentBase() + File.separatorChar + ConfigurationFile.fileName);

        Properties config = cf.loadConfiguration();
        return config;
    }

    @Override
    public void initInternal() throws LifecycleException {

        Thread.currentThread().setContextClassLoader(Gateway.class.getClassLoader());
        logger.info("Initialize gateway");
        Properties config = loadConfiguration();

        if (config == null) {
            throw new LifecycleException("Configuration file {} not found, exiting...");
        }

        if (!config.containsKey("server.host") || !config.containsKey("server.port")
                || !config.containsKey("client.commandhandler")
                || !config.containsKey("client.preprocessor")) {
            throw new LifecycleException("property [server.host], [server.port], [client.commandhandler] and [client.preprocessor] must be specified.");
        }

        if (config.containsKey("use_async")) {
            confVars.useAsync = Boolean.valueOf(config.getProperty("use_async"));
        }

        if (config.containsKey("force_update_def")) {
            confVars.forceUpdateDef = Boolean.valueOf(config.getProperty("force_update_def"));
        }

        if (config.containsKey("auto_retry")) {
            confVars.autoRetry = Boolean.valueOf(config.getProperty("auto_retry"));
            if (confVars.autoRetry && config.containsKey("retry_interval_second")) {
                confVars.retryIntervalSecond = Integer.valueOf(config.getProperty("retry_interval_second"));
            }
        }

        System.setProperty(DC.SP_HOST, config.getProperty("server.host"));
        System.setProperty(DC.SP_PORT, config.getProperty("server.port"));

        // load class
        ClassLoader classLoader = this.getClass().getClassLoader();

        try {
            commandHandlerClass = (Class<CommandHandler>)classLoader.loadClass(config.getProperty("client.commandhandler"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }
        try {
            preprocessorClass = (Class<PreProcessor>)classLoader.loadClass(config.getProperty("client.preprocessor"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try {
            while (!(confVars.useAsync? initializeResourcesAsync(): initializeResources())) {
                if (!confVars.autoRetry) {
                    throw new LifecycleException("Initialization failed. Finished working.");
                }

                logger.error("Initialization failed. Try again after {} seconds...", confVars.retryIntervalSecond);
                Thread.sleep(confVars.retryIntervalSecond * 1000);
            }
            logger.info("Initialization done.");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void bindCommandHandler(Resource res) {
        CommandHandler commandHandler = null;
        try {
            commandHandler = commandHandlerClass.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        commandHandler.setRes(res);
        commandHandler.init();
        String localid = res.getDefinition().description.get("localid");
        logger.info("[{}] Binding handlers for resource {}.", localid, res.getId());
        for (String ctrlPropID : res.getDefinition().relationship.keySet()) {
            Property p = res.getProperty(ctrlPropID);
            String cmdh = p.registerReader(commandHandler, null);
        }
    }

    private Resource initializeResource(ResourceInfo resInfo) {
        MoreResourceInfo ri = (MoreResourceInfo) resInfo;
        String resid = ri.getId();
        String localid = ri.getLocalId();
        logger.info("\n{}:{} config={}", resid, localid, ConfigurationFile.instance().loadConfiguration().getProperty("server.host"));

        String defFileName = localid + ".json";
        File jsonDef = new File(RES_DEF_DIR, defFileName);

        if(jsonDef == null) {
            logger.error("Definition file {} not found", RES_DEF_DIR + '/' + defFileName);
            return null;
        }
        BufferedReader fr = null;
        try {
            fr = new BufferedReader(new InputStreamReader(new FileInputStream(jsonDef), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(fr);
        JsonObject jo = je.getAsJsonObject();
        String check = jo.get("check").getAsString();

        // FIXME Check regex pattern
        ri.setCheck(check);

        Resource res = null;
        try {
            res = DC.connect(ri);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (res == null) {
            logger.error("[{}] ResourceID[{}] unavailable.", localid, resid);
            return null;
        }

        ResourceDefinition oldDef = res.getDefinition();

        if (confVars.forceUpdateDef || jsonDef.lastModified() > oldDef.lastModified.getTime()) {
            ResourceDefinition localDef = Json2ResourceDef.parse(je);

            if (confVars.forceUpdateDef) {
                logger.info("[{}] Force update resource[{}]", localid, resid);
                res.setDefinition(localDef);
            } else {
                logger.info("[{}] Update resource[{}]", localid, resid);
                res.setDefinition(DefinitionHelper.delta(oldDef, localDef));
            }
        }

        return res;
    }

    private boolean initializeResourcesAsync() throws IOException, ParserConfigurationException, SAXException, IllegalAccessException, InstantiationException {

        final Object wait4jobs = new Object();
        logger.info("Initializing resources...");
        idmap = getIDMap();
        if (idmap == null || idmap.size() == 0) {
            logger.error("File idmap.ini not found or is empty.");
            return false;
        } else {
            final ExecutorService exec = Executors.newCachedThreadPool();
            final CyclicBarrier barrier = new CyclicBarrier(idmap.size(), new Runnable() {
                @Override
                public void run() {
                    logger.info("Initialization done.");
                    synchronized (wait4jobs) {
                        wait4jobs.notify();
                    }
                    return;
                }
            });

            // Retrieve resources and update resources' definition
            logger.info("Checking definition modification for each resource...");
            for(final Object localid : idmap.keySet()) {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {

                        String resid = (String) idmap.get(localid);

                        ResourceInfo ri = new MoreResourceInfo();
                        ri.setId(resid);
                        ((MoreResourceInfo) ri).setLocalId((String) localid);

                        Resource res = initializeResource(ri);
                        if(res == null) {
                            logger.error("Skip resource [{}] : [{}]", (String)localid, resid);
                            return;
                        }

                        bindCommandHandler(res);
                        synchronized (resourcesLock) {
                            ResourceCache.instance().addResource(res);
                        }
                        try {
                            barrier.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (BrokenBarrierException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            exec.shutdown();
        }

        synchronized (wait4jobs) {
            try {
                wait4jobs.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private boolean initializeResources() throws IOException, ParserConfigurationException, SAXException, IllegalAccessException, InstantiationException {

        logger.info("Initializing resources...");
        idmap = getIDMap();
        if (idmap == null || idmap.size() == 0) {
            logger.error("File idmap.ini not found or is empty.");
            return false;
        } else {
            // Retrieve resources and update resources' definition
            logger.info("Checking definition modification...");
            for(Object localid : idmap.keySet()) {
                String resid = (String)idmap.get(localid);

                ResourceInfo ri = new MoreResourceInfo();
                ri.setId(resid);
                ((MoreResourceInfo) ri).setLocalId((String)localid);
                Resource res = initializeResource(ri);
                if(res == null) {
                    logger.error("Skip resource [{}] : [{}]", (String)localid, resid);
                    continue;
                }

                bindCommandHandler(res);
                ResourceCache.instance().addResource(res);
            }
        }

        return true;
    }

    public static Properties getIDMap() {
        ConfigurationFile cf = ConfigurationFile.instance();
        Properties map = cf.loadConfiguration(IDMAP_FILE);

        return map;
    }


    private void cleanAndExit() {
        logger.info("Clean and exit.");
        for(String hdlr : handlers.keySet()) {
            Property cp = handlers.get(hdlr);
            cp.unregisterReader(hdlr);
        }
        ResourceCache.instance().clearCache();
    }

    @Override
    public void startInternal() throws LifecycleException {

        super.startInternal();
        logger.info("Loading preProcessor...");
        PreProcessor preProcessor = null;
        try {
            preProcessor = preprocessorClass.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        preProcessor.prepare();

        logger.info("Started.");
    }


}
