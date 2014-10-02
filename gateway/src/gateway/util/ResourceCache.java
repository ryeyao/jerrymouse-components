package gateway.util;

import wshare.dc.resource.Resource;
import wshare.dc.resource.ResourceInfo;

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
    private static Hashtable<String, MoreResourceInfo> resInfos;
    private static Hashtable<String, String> localIdToResId;

    private ResourceCache() {
        resources = new Hashtable<String, Resource>();
        resInfos = new Hashtable<String, MoreResourceInfo>();
        localIdToResId = new Hashtable<String, String>();
    }

    public static ResourceCache instance() {
        return ResourceCacheSingleton.instance;
    }

    public Resource getResourceById(String resid) {
        if (resid == null) {
            return null;
        }
        return resources.get(resid);
    }

    public Resource getResourceByLocalId(String localid) {
        if (localid == null) {
            return null;
        }

        return getResourceById(localIdToResId.get(localid));
    }

    public MoreResourceInfo getResourceInfoById(String resid) {
        if (resid == null) {
            return null;
        }
        return resInfos.get(resid);
    }

    public MoreResourceInfo getResourceInfoByLocalId(String localid) {
        if (localid == null) {
            return null;
        }

        return getResourceInfoById(localIdToResId.get(localid));
    }

    public void addResource(Resource res, MoreResourceInfo resInfo) {
        resources.put(res.getId(), res);
        resInfos.put(res.getId(), resInfo);
        localIdToResId.put(resInfo.getLocalId(), resInfo.getId());
    }

    public Resource removeResource(Resource res) {
        return removeResourceById(res.getId());
    }

    public Resource removeResourceById(String resid) {
        if (resid == null) {
            return null;
        }

        localIdToResId.remove(resInfos.get(resid).getLocalId());
        resInfos.remove(resid);
        return resources.remove(resid);
    }

    public Resource removeResourceByLocalId(String localid) {
        if (localid == null) {
            return null;
        }
        return removeResourceById(localIdToResId.get(localid));
    }

    public void clearCache() {
        resInfos.clear();
        resources.clear();
        resInfos.clear();
    }
}
