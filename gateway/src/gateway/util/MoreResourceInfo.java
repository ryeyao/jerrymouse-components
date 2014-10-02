package gateway.util;

import wshare.dc.resource.ResourceInfo;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 10/2/14
 * Time: 2:52 PM
 */
public class MoreResourceInfo extends ResourceInfo {

    private String localId = null;

    public String getLocalId() {
        return localId;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }

    public ResourceInfo getResourceInfo() {
        ResourceInfo resInfo = new ResourceInfo();
        resInfo.setId(super.getId());
        resInfo.setCheck(super.getCheck());
        return resInfo;
    }
}
