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
    static final Logger logger = LogManager.getLogger("[Gateway] " + Gateway.class.getName());

    public static Properties idmap;
    public static String IDMAP_FILE = "idmap.ini";
    public static String XML_DIR = "xmls";
    public static String RES_DEF_DIR = "resources";
    public static String BASE_DIR = "";

    private HashMap<String, Property> handlers = new HashMap<String, Property>();
    private ArrayList<Resource> resources = new ArrayList<Resource>();
    private static final Object idmapLock = new Object();
    private static final Object resourcesLock = new Object();
    private static final Object registerLock = new Object();

    // Configurable vars
    private class ConfigurableVars {
        public boolean useAsync = false;
        public boolean forceUpdateDef = false;
    }
    private ConfigurableVars confVars = new ConfigurableVars();

    private Class<CommandHandler> commandHandlerClass;
    private Class<PreProcessor> preprocessorClass;

    public Properties loadConfiguration() {
        IDMAP_FILE = getComponentBase() + File.separatorChar + IDMAP_FILE;
        XML_DIR = getComponentBase() + File.separatorChar + XML_DIR;
        RES_DEF_DIR = getComponentBase() + File.separatorChar + RES_DEF_DIR;
        BASE_DIR = getComponentBase();
        ConfigurationFile.fileName = getComponentBase() + File.separatorChar + ConfigurationFile.fileName;

        ConfigurationFile cf = new ConfigurationFile();

        Properties config = cf.loadConfiguration();

        if (config == null) {
            logger.info("Configuration file not found, recreating...");
            config = cf.initDefaultConfiguration();
            cf.updateFile(config);
        }

        return config;
    }

    @Override
    public void initInternal() throws LifecycleException {

        Thread.currentThread().setContextClassLoader(Gateway.class.getClassLoader());
        logger.info("Initialize gateway");
        Properties config = loadConfiguration();

        if (!config.containsKey("server.host") || !config.containsKey("server.port")
                || !config.containsKey("client.commandhandler")
                || !config.containsKey("client.preprocessor")) {
            logger.error("property [server.host], [server.port], [client.commandhandler] and [client.preprocessor] must be specified.");
            return;
        }

        if (config.containsKey("use_async")) {
            confVars.useAsync = Boolean.valueOf(config.getProperty("use_async"));
        }

        if (config.containsKey("force_update_def")) {
            confVars.forceUpdateDef = Boolean.valueOf(config.getProperty("force_update_def"));
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
                logger.error("Initialization failed.");
            }
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

    private Resource initializeResource(String resid, String localid) {
        ResourceInfo ri = new ResourceInfo();

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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(fr);
        JsonObject jo = je.getAsJsonObject();
        String check = jo.get("check").getAsString();

        // FIXME Check regex pattern
        ri.setCheck(check);
        ri.setId(resid);

        Resource res = null;
        try {
            res = DC.connect(ri);
        } catch (IOException e) {
            e.printStackTrace();
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
            logger.error("IDMap not found, register all resources and creating a new map file...");
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

                        Resource res = initializeResource(resid, (String) localid);
                        bindCommandHandler(res);
                        synchronized (resourcesLock) {
                            resources.add(res);
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
        if (idmap == null) {
            logger.error("IDMap not found, register all resources and creating a new map file...");
        } else {
            // Retrieve resources and update resources' definition
            logger.info("Checking definition modification...");
            for(Object localid : idmap.keySet()) {
                String resid = (String)idmap.get(localid);

                Resource res = initializeResource(resid, (String)localid);

                bindCommandHandler(res);
                resources.add(res);
            }
        }

        return true;
    }

    public static Properties getIDMap() {
        ConfigurationFile cf = new ConfigurationFile();
        Properties map = cf.loadConfiguration(IDMAP_FILE);

        return map;
    }


    private void cleanAndExit() {
        logger.info("Clean and exit.");
        for(String hdlr : handlers.keySet()) {
            Property cp = handlers.get(hdlr);
            cp.unregisterReader(hdlr);
        }
        resources = null;
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
