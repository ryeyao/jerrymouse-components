package gateway.util;

import wshare.dc.resource.Resource;

import java.util.Hashtable;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 10/2/14
 * Time: 3:10 PM
 */
public final class ResourceCache {

    private static class ResourceCacheSingleton {
        private static ResourceCache instance = new ResourceCache();
    }
    private static Hashtable<String, Resource> resources;

    private ResourceCache() {
        resources = new Hashtable<String, Resource>();
    }

    public static ResourceCache instance() {
        return ResourceCacheSingleton.instance;
    }

    public Resource getByResourceId(String resid) {
        return resources.get(resid);
    }

    public void addResource(Resource res) {
        resources.put(res.getId(), res);
    }

    public void removeResource(Resource res) {
        resources.remove(res.getId());
    }

    public void removeResourceById(String resid) {
        resources.remove(resid);
    }

    public void clearCache() {
        resources.clear();
    }
}
