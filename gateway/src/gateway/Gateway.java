package gateway;

import cn.iie.gaia.LifecycleException;
import cn.iie.gaia.entity.ComponentBase;
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
    static final Logger logger = LogManager.getLogger(Gateway.class);

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
    private boolean useAsync = false;

    private Class<CommandHandler> commandHandlerClass;
    private Class<PreProcessor> preprocessorClass;

    public Properties loadConfiguration() {
        IDMAP_FILE = getComponentBase() + File.separatorChar + IDMAP_FILE;
        XML_DIR = getComponentBase() + File.separatorChar + XML_DIR;
        RES_DEF_DIR = getComponentBase() + File.separatorChar + RES_DEF_DIR;
        BASE_DIR = getComponentBase();
        ConfigurationFile.fileName = getComponentBase() + File.separatorChar + ConfigurationFile.fileName;

        ConfigurationFile cf = new ConfigurationFile();
//        String configPath = getComponentBase() + File.separatorChar + ConfigurationFile.fileName;
//        System.out.println(configPath);

//        System.out.println("***\n" + IDMAP_FILE + "\n" + XML_DIR + "\n" + RES_DEF_DIR + "\n***");

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

        if(!config.containsKey("server.host") || !config.containsKey("server.port")
                || !config.containsKey("client.commandhandler")
                || !config.containsKey("client.preprocessor")) {
            logger.error("property [server.host], [server.port], [client.commandhandler] and [client.preprocessor] must be specified.");
            return;
        }

        if(config.containsKey("use_async")) {
            useAsync = Boolean.valueOf(config.getProperty("use_async"));
        }

        System.setProperty(DC.SP_HOST, config.getProperty("server.host"));
        System.setProperty(DC.SP_PORT, config.getProperty("server.port"));
//        DC.getConfiguration().setProperty("server.host", config.getProperty("server.host"));
//        DC.getConfiguration().setProperty("server.port", config.getProperty("server.port"));

        // load class
        ClassLoader classLoader = this.getClass().getClassLoader();
//        ClassLoader classLoader = new URLClassLoader(new URL[0], Gateway.class.getClassLoader());
//        ClassLoader classLoader = Gateway.class.getClassLoader();

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
            while (!(useAsync?initializeResourceAsync():initializeResource())) {
                logger.error("Initialization failed.");
//                idmap = registerAllResources(XML_DIR);
//
//                ConfigurationFile cf = new ConfigurationFile();
//                // update idmap
//                cf.updateFile(idmap, IDMAP_FILE);
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

//    private Resource registerResource(ResourceDefinition def) throws IOException {
//        ResourceLibrary lib;
//        lib = DC.newSession(null);
//        String rid = lib.addResource(def, null);
//        Resource res = lib.getResource(rid);
//
//        return res;
//    }
//
//    private Properties registerAllResourecsFromJson(String jsonDir) throws IOException {
//        File jsonDirFile = new File(jsonDir);
//        Properties loc2rem = new Properties();
//
//        for(File jsonFile : jsonDirFile.listFiles()) {
//            logger.info("Parsing {}", jsonFile.getCanonicalFile());
//            BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), "utf-8"));
//
//            ResourceDefinition def = Json2ResourceDef.parse(fr);
//
//            //FIXME(Rye): Fake location
//            Random rand = new Random(System.currentTimeMillis());
//            double yMax = 58, yMin = -38, xMax = 170, xMin = -2;
//            double x = rand.nextDouble() * (xMax - xMin) + xMin;
//            double y = rand.nextDouble() * (yMax - yMin) + yMin;
//            if(!def.description.containsKey("geo")) {
//                def.description.put("geo", x + "," + y);
//                def.description.put("geo", "20,50");
//            }
//
//
//            Resource res = registerResource(def);
//            resources.add(res);
//
//            String localid = def.description.get("localid");
//            loc2rem.setProperty(localid, res.getId());
//        }
//
//        return loc2rem;
//    }
//
//    private Properties registerAllResourecsFromJsonAsync(String jsonDir) throws IOException {
//
//        File jsonDirFile = new File(jsonDir);
//        final Properties loc2rem = new Properties();
//
//        ExecutorService exec = Executors.newCachedThreadPool();
//        final CyclicBarrier barrier = new CyclicBarrier(jsonDirFile.list().length, new Runnable() {
//            @Override
//            public void run() {
//                return;
//            }
//        });
//
//        for(final File jsonFile : jsonDirFile.listFiles()) {
//            exec.execute(new Runnable() {
//                @Override
//                public void run() {
//
//                    try {
//                        logger.info("Parsing {}", jsonFile.getCanonicalFile());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    BufferedReader fr = null;
//                    try {
//                        fr = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), "utf-8"));
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }
//
//                    ResourceDefinition def = Json2ResourceDef.parse(fr);
//
//                    //FIXME(Rye): Fake location
//                    Random rand = new Random(System.currentTimeMillis());
//                    double yMax = 58, yMin = -38, xMax = 170, xMin = -2;
//                    double x = rand.nextDouble() * (xMax - xMin) + xMin;
//                    double y = rand.nextDouble() * (yMax - yMin) + yMin;
//                    if(!def.description.containsKey("geo")) {
//                        def.description.put("geo", x + "," + y);
//                        def.description.put("geo", "20,50");
//                    }
//
//
//                    Resource res = null;
//                    try {
//                        res = registerResource(def);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    synchronized (resourcesLock) {
//                        resources.add(res);
//                        String localid = def.description.get("localid");
//                        loc2rem.setProperty(localid, res.getId());
//                    }
//
//
//                    try {
//                        barrier.await();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    } catch (BrokenBarrierException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//        }
//        exec.shutdown();
//
//        return loc2rem;
//    }
//
//    private Properties registerAllResources(String xmlDir) throws ParserConfigurationException, IOException, SAXException {
//        // TODO: Register resource while idmap doesn't exist so that we can get a new resource id.
//
//        // Get resource definition from xml
//        File xmlDirFile = new File(xmlDir);
////        XML2ResourceDef xml2r = new XML2ResourceDef();
//        Properties loc2rem = new Properties();
////        Properties rem2loc = new Properties();
//
//        for (File xmlFile : xmlDirFile.listFiles()) {
//            String xmlPath = xmlFile.getPath();
//            ResourceDefinition def = XML2ResourceDef.parse(xmlPath);
//
//            //FIXME(Rye): Fake location
//            Random rand = new Random(System.currentTimeMillis());
//            double yMax = 58, yMin = -38, xMax = 170, xMin = -2;
//            double x = rand.nextDouble() * (xMax - xMin) + xMin;
//            double y = rand.nextDouble() * (yMax - yMin) + yMin;
//            if(!def.description.containsKey("geo")) {
//                def.description.put("geo", x + "," + y);
//                def.description.put("geo", "20,50");
//            }
//
//
//            Resource res = registerResource(def);
//            resources.add(res);
//
//            String localid = def.description.get("localid");
//            loc2rem.setProperty(localid, res.getId());
////            rem2loc.setProperty(res.getId(), localid);
//
//            // Generate json definition
//            JsonObject jo = ResourceDef2Json.createJson(res);
////            jo.add("handlers", new GsonBuilder().create().toJsonTree(hdlrs));
//            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//            jo.addProperty("lastModified", df.format(new Date()));
//            ResourceDef2Json.writeJson(RES_DEF_DIR + File.separator + localid + ".json", jo);
//        }
//
//        return loc2rem;
//    }

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
            logger.info("[{}] 1", localid);
            Property p = res.getProperty(ctrlPropID);
            logger.info("[{}] 2", localid);
            String cmdh = p.registerReader(commandHandler, null);
            logger.info("[{}] 3", localid);
        }
        logger.info("[{}] done", localid);
    }

    private boolean initializeResourceAsync() throws IOException, ParserConfigurationException, SAXException, IllegalAccessException, InstantiationException {

        final Object wait4jobs = new Object();
        logger.info("Initializing resources...");
        idmap = getIDMap();
        if (idmap == null || idmap.size() == 0) {
            logger.error("IDMap not found, register all resources and creating a new map file...");
//            idmap = registerAllResources(XML_DIR);
//            idmap = registerAllResourecsFromJsonAsync(RES_DEF_DIR);

            // update idmap
//            ConfigurationFile.updateFile(idmap, IDMAP_FILE);
        } else {
            final ExecutorService exec = Executors.newCachedThreadPool();
//            System.out.println("idmap size: " + idmap.size());
            final CyclicBarrier barrier = new CyclicBarrier(idmap.size(), new Runnable() {
                @Override
                public void run() {
                    logger.info("Initialization done.");
                    synchronized (wait4jobs) {
                        wait4jobs.notify();
                    }
//                    // Bind command handlers
//                    logger.info("Binding command handlers...");
//                    for(Resource res : resources) {
//                        bindCommandHandler(res);
//                    }
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
                        ResourceInfo ri = new ResourceInfo();
                        ri.setId(resid);
                        // FIXME check should be read from local definition file.
                        ri.setCheck("123456");
                        Resource res = null;
                        try {
                            res = DC.connect(ri);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        if (res == null) {
                            logger.error("[{}] ResourceID[{}] unavailable.", localid, resid);
                            return;
//                            return false;
                        }

                        ResourceDefinition oldDef = res.getDefinition();

                        File jsonDef = new File(RES_DEF_DIR, localid + ".json");
                        if (jsonDef.lastModified() > oldDef.lastModified.getTime()) {
                            logger.info("[{}] Update resource[{}]", localid, resid);
//                    ResourceDefinition newDef = XML2ResourceDef.parse(xmlDef.getPath());

                            BufferedReader fr = null;
                            try {
                                fr = new BufferedReader(new InputStreamReader(new FileInputStream(jsonDef), "utf-8"));
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            ResourceDefinition newDef = Json2ResourceDef.parse(fr);
                            res.setDefinition(DefinitionHelper.delta(oldDef, newDef));
                        }

//                File xmlDef = new File(XML_DIR, localid + ".xml");
//                if(xmlDef.lastModified() > oldDef.lastModified.getTime()) {
//                    logger.info("Update resource[{}]", resid);
//                    ResourceDefinition newDef = XML2ResourceDef.parse(xmlDef.getPath());
//                    res.setDefinition(DefinitionHelper.delta(oldDef, newDef));
//                }
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

    private boolean initializeResource() throws IOException, ParserConfigurationException, SAXException, IllegalAccessException, InstantiationException {

        logger.info("Initializing resources...");
        idmap = getIDMap();
        if (idmap == null) {
            logger.error("IDMap not found, register all resources and creating a new map file...");
//            idmap = registerAllResources(XML_DIR);
//            idmap = registerAllResourecsFromJson(RES_DEF_DIR);

            // update idmap
//            ConfigurationFile.updateFile(idmap, IDMAP_FILE);
        } else {
            // Retrieve resources and update resources' definition
            logger.info("Checking definition modification...");
            for(Object localid : idmap.keySet()) {
                String resid = (String)idmap.get(localid);

                // read local resource definition
                String defFileName = localid+".json";
                File jsonDef = new File(RES_DEF_DIR, defFileName);

                if(jsonDef == null) {
                    logger.error("Definition file {} not found.", RES_DEF_DIR + '/' + defFileName);
                    return false;
                }

                BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(jsonDef), "utf-8"));
                ResourceDefinition localDef = Json2ResourceDef.parse(fr);

                ResourceInfo ri = new ResourceInfo();
                ri.setId(resid);
                // FIXME check should be read from local definition json file.
                ri.setCheck("123456");
                Resource res = DC.connect(ri);

                if(res == null) {
                    logger.error("ResourceID[{}] unavailable.", resid);
                    return false;
                }

                ResourceDefinition oldDef = res.getDefinition();

                if(jsonDef.lastModified() > oldDef.lastModified.getTime()) {
//                if(oldDef.lastModified == null || jsonDef.lastModified() > oldDef.lastModified.getTime()) {
                    logger.info("Update resource[{}]", resid);
//                    ResourceDefinition newDef = XML2ResourceDef.parse(xmlDef.getPath());

                    res.setDefinition(DefinitionHelper.delta(oldDef, localDef));
                }

//                File xmlDef = new File(XML_DIR, localid + ".xml");
//                if(xmlDef.lastModified() > oldDef.lastModified.getTime()) {
//                    logger.info("Update resource[{}]", resid);
//                    ResourceDefinition newDef = XML2ResourceDef.parse(xmlDef.getPath());
//                    res.setDefinition(DefinitionHelper.delta(oldDef, newDef));
//                }
                resources.add(res);
            }
        }

        // Bind command handlers
        logger.info("Binding command handlers...");
        for(Resource res : resources) {
            CommandHandler commandHandler = commandHandlerClass.newInstance();
            commandHandler.setRes(res);
            commandHandler.init();
            logger.info("Binding handlers for resource {}.", res.getId());
            for (String ctrlPropID : res.getDefinition().relationship.keySet()) {
                Property p = res.getProperty(ctrlPropID);
                String cmdh = p.registerReader(commandHandler, null);
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
