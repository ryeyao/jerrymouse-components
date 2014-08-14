package sensor.udp;

import gateway.Gateway;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wshare.dc.DC;
import wshare.dc.resource.DataItem;
import wshare.dc.resource.Property;
import wshare.dc.resource.Resource;
import wshare.dc.resource.ResourceInfo;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;

/**
 * Created by Rye on 2/22/14.
 */
public class UDPDataHandler{

    private static Logger logger = LogManager.getLogger(UDPDataHandler.class.getName());
    private Properties idmap = Gateway.getIDMap();

    public void onReceive(byte[] data) throws IOException {
        UDPPacket packet = new UDPPacket();
        packet.unpackData(data);
//        logger.info("raw data:\n");
//        logger.info(data);
        logger.info("\nIncoming data: " + packet);

        String resid = idmap.getProperty(String.valueOf(packet.getNodeid()));

        if (resid == null) {
            logger.error("Unknown data source");
            return;
        }

        ResourceInfo ri = new ResourceInfo();
        ri.setId(resid);
        ri.setCheck("123456");
        Resource res = DC.connect(ri);

        long datatype = packet.getType();
        Property p = null;

        if (datatype == 7) {
            p = res.getProperty("温度");
        }
        else if (datatype == 6) {
            p = res.getProperty("湿度");
        }
        else if (datatype == 4) {
            p = res.getProperty("红外数据");
        }
        else if (datatype == 10) {
            p = res.getProperty("展柜状态");
        }
        else if (datatype == 11) {
            p = res.getProperty("展灯状态");
        }
        else {
            logger.warn("Unknown data type");
            return;
        }
        DataItem dataItem = new DataItem(new Date(),  packet.getValue().getBytes());
        p.write(dataItem);

    }
}
