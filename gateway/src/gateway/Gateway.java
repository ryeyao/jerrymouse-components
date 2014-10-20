package gateway;

import cn.iie.gaia.LifecycleException;
import cn.iie.gaia.entity.ComponentBase;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gateway.abstracthandler.CommandHandler;
import gateway.abstracthandler.PreProcessor;
import gateway.abstracthandler.Worker;
import gateway.util.Configurations;
import gateway.util.Json2ResourceDef;
import gateway.util.MoreResourceInfo;
import gateway.util.ResourceCache;
import static gateway.util.Configurations.ConfigurableVars.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;
import wshare.dc.DC;
import wshare.dc.resource.*;
import wshare.dc.util.DefinitionHelper;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

//    private ConfigurableVars confVars = new ConfigurableVars();

    private Class<CommandHandler> commandHandlerClass = null;
    private Class<PreProcessor> preprocessorClass = null;
    private Class<Worker> workerClass = null;

    public void loadConfiguration() throws ConfigurationException {
        IDMAP_FILE = getComponentBase() + File.separatorChar + IDMAP_FILE;
        XML_DIR = getComponentBase() + File.separatorChar + XML_DIR;
        RES_DEF_DIR = getComponentBase() + File.separatorChar + RES_DEF_DIR;
        BASE_DIR = getComponentBase();

        Configurations cf = Configurations.instance();
        cf.addConfiguration(new PropertiesConfiguration(getComponentBase() + File.separator + Configurations.fileName), true);
        cf.addConfiguration(new PropertiesConfiguration(IDMAP_FILE));
    }

    @Override
    public void initInternal() throws LifecycleException {

        Thread.currentThread().setContextClassLoader(Gateway.class.getClassLoader());
        logger.info("Initialize gateway");
        try {
            loadConfiguration();
        } catch (ConfigurationException e) {
            throw new LifecycleException("Load configuration error.");
        }
        Configurations config = Configurations.instance();

//        if (config == null) {
//            throw new LifecycleException("Configuration file {} not found, exiting...");
//        }

        serverHost = config.getString(SERVER_HOST, serverHost);
        serverPort = config.getString(SERVER_PORT, serverPort);
        clientVars.commandHandler = config.getString(clientVars.COMMAND_HANDLER, clientVars.commandHandler);
        clientVars.preProcessor = config.getString(clientVars.PREPROCESSOR, clientVars.preProcessor);


        if (serverHost == null || serverPort == null
                || clientVars.commandHandler == null
                || clientVars.preProcessor == null) {
            throw new LifecycleException(String.format("property [%s], [%s], [%s] and [%s] must be specified.", SERVER_HOST, SERVER_PORT, clientVars.COMMAND_HANDLER, clientVars.PREPROCESSOR));
        }

        useAsync = config.getBoolean(USE_ASYNC, useAsync);

        forceUpdateDef = config.getBoolean(FORCE_UPDATE_DEF, forceUpdateDef);

        autoRetry = config.getBoolean(AUTO_RETRY, autoRetry);
        if (autoRetry) {
            retryIntervalSecond = config.getInt(RETRY_INTERVAL_SECOND, retryIntervalSecond);
        }

        System.setProperty(DC.SP_HOST, serverHost);
        System.setProperty(DC.SP_PORT, serverPort);

        clientVars.worker = config.getString(clientVars.WORKER, clientVars.worker);

        // load class
        ClassLoader classLoader = this.getClass().getClassLoader();

        try {
            commandHandlerClass = (Class<CommandHandler>)classLoader.loadClass(clientVars.commandHandler);
            preprocessorClass = (Class<PreProcessor>)classLoader.loadClass(clientVars.preProcessor);

            if (clientVars.worker != null) {
                workerClass = (Class<Worker>)classLoader.loadClass(clientVars.worker);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try {
            while (!(useAsync? initializeResourcesAsync(): initializeResources())) {
                if (!autoRetry) {
                    throw new LifecycleException("Initialization failed. Finished working.");
                }

                logger.error("Initialization failed. Try again after {} seconds...", retryIntervalSecond);
                Thread.sleep(retryIntervalSecond * 1000);
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
            res = DC.connect(ri.getResourceInfo());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (res == null) {
            logger.error("[{}] ResourceID[{}] unavailable.", localid, resid);
            return null;
        }

        ResourceDefinition oldDef = res.getDefinition();

        if (forceUpdateDef || jsonDef.lastModified() > oldDef.lastModified.getTime()) {
            ResourceDefinition localDef = Json2ResourceDef.parse(je);

            if (forceUpdateDef) {
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
            final String componentName = this.getName();
            ThreadFactory namedThreadFactory = new ThreadFactory() {
                private final AtomicInteger poolNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, componentName + "-" + poolNumber.getAndIncrement());
                }
            };
            final ExecutorService exec = Executors.newCachedThreadPool(namedThreadFactory);
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

                        MoreResourceInfo ri = new MoreResourceInfo();
                        ri.setId(resid);
                        ri.setLocalId((String) localid);

                        Resource res = initializeResource(ri);
                        if(res == null) {
                            logger.error("Skip resource [{}] : [{}]", localid, resid);
                            return;
                        }

                        bindCommandHandler(res);
                        ResourceCache.instance().addResource(res, ri);
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

                MoreResourceInfo ri = new MoreResourceInfo();
                ri.setId(resid);
                ri.setLocalId((String)localid);
                Resource res = initializeResource(ri);
                if(res == null) {
                    logger.error("Skip resource [{}] : [{}]", localid, resid);
                    continue;
                }

                bindCommandHandler(res);
                ResourceCache.instance().addResource(res, ri);
            }
        }

        return true;
    }

    public static Properties getIDMap() {
        Configurations cf = Configurations.instance();
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

        if (workerClass != null) {
            Worker worker = null;
            try {
                worker = workerClass.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            worker.start();
        }

        logger.info("Started.");
    }


}
