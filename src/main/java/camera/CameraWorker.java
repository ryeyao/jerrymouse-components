package camera;

import gateway.abstracthandler.Worker;
import gateway.util.ResourceCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wshare.dc.resource.DataItem;
import wshare.dc.resource.Resource;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 10/6/14
 * Time: 4:55 PM
 */
public class CameraWorker extends Worker {

    private static final Logger logger = LogManager.getLogger(Worker.class.getName());

    @Override
    public void work() {
        Resource res = ResourceCache.instance().getResourceByLocalId("cam_1000");

        String url = "rtmp://10.10.12.183/flvplayback/cam_1000.mp4";
        res.getProperty("视频流地址").write(new DataItem(new Date(), url.getBytes()));
        logger.info("Send video stream url {}", url);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
