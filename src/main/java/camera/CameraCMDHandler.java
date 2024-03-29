package camera;

import gateway.abstracthandler.CommandHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wshare.dc.resource.DataItem;
import wshare.dc.resource.PID;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static java.lang.Thread.sleep;

/**
 * Created by Rye on 2/19/14.
 */
public class CameraCMDHandler extends CommandHandler {

    private static final Logger logger = LogManager.getLogger(CameraCMDHandler.class.getName());
    private InetSocketAddress address;
    private Socket socket;
    private HashMap<String, byte[]> cmdHex = new HashMap<String, byte[]>();
    private HashMap ip2camera = new HashMap();
    private String localid;

    @Override
    public void init() {
        ip2camera.put("192.168.111.240", (byte)0x01);
        ip2camera.put("192.168.111.241", (byte)0x02);
        ip2camera.put("192.168.111.242", (byte)0x03);
        ip2camera.put("192.168.110.221", (byte)0x04);

        String host = this.getRes().getDefinition().description.get("localaddr");
        int port = Integer.valueOf(this.getRes().getDefinition().description.get("localport"));
        localid = this.getRes().getDefinition().description.get("localid");

        byte cameraID;
        if(ip2camera.containsKey(host)) {
            cameraID = (Byte)ip2camera.get(host);
        } else {
            cameraID = 0x00;
        }

        cmdHex.put("stop", new byte[]{0x08,cameraID,0x04,0x00});
        cmdHex.put("up", new byte[]{0x08,cameraID,0x04,0x01});
        cmdHex.put("down", new byte[]{0x08,cameraID,0x04,0x02});
        cmdHex.put("right", new byte[]{0x08,cameraID,0x04,0x03});
        cmdHex.put("left", new byte[]{0x08,cameraID,0x04,0x04});
        cmdHex.put("zoomout", new byte[]{0x08,cameraID,0x04,0x05});
        cmdHex.put("zoomin", new byte[]{0x08,cameraID,0x04,0x06});
        cmdHex.put("setposition", new byte[]{0x08,cameraID,0x04,0x07});
        cmdHex.put("gotoposition", new byte[]{0x08,cameraID,0x04,0x08});
//        cmdHex.put("takePhoto", new byte[]{0x83,0x01,0x04});
        cmdHex.put("twoDirectionPos", new byte[]{});

        address = new InetSocketAddress(host, port);
        logger.info("Initialize camera [{}] [{}]", localid, address);

        socket = new Socket();
    }

    @Override
    public void handle(PID propertyId, DataItem... data) {
        String cmdStr = propertyId.getLocalId();
        cmdStr = cmdStr.substring(cmdStr.indexOf("/") + 1);
        byte[] cmd = cmdHex.get(cmdStr);
        logger.info("Send command to [{}] [{}]: [{}]", localid, address, cmdStr);
        if(cmdStr == "twoDirectionPos") {
            int x = ByteBuffer.wrap(data[0].data).getInt();
            int y = ByteBuffer.wrap(data[1].data).getInt();
            try {
                twoDirectionPos(x, y);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        else if(cmdStr == "setposition" || cmdStr == "gotoposition") {
            ByteBuffer bb = ByteBuffer.wrap(data[0].data);
            byte[] tmp = new byte[cmd.length + 1];
            System.arraycopy(cmd, 0, tmp, 0, cmd.length);
            tmp[cmd.length] = (byte)bb.getInt();
            cmd = tmp;
        }

        execCmd(cmd);
    }

    private void execCmd(byte[] cmd) {
        if(!socket.isConnected()) {
            try {
                socket = new Socket();
                socket.connect(address, 1000);
            } catch (IOException e) {
                logger.error("Camera [{}] [{}] unreachable.\nReason: {}", localid, address, e.getLocalizedMessage());
//                e.printStackTrace();
                return;
            }
        }
        try {
            socket.getOutputStream().write(cmd);
        } catch (IOException e) {
            logger.error("Write to camera [{}] failed . \nReason: {}", address, e.getLocalizedMessage());
        }
    }

    private void twoDirectionPos(int x, int y) throws InterruptedException, IOException {
        if(x < 0) {
            execCmd(cmdHex.get("left"));
        }
        else {
            execCmd(cmdHex.get("right"));
        }

        sleep(Math.abs(x));

        if(y < 0) {
            execCmd(cmdHex.get("up"));
        }
        else {
            execCmd(cmdHex.get("down"));
        }

        sleep(Math.abs(y));

        execCmd(cmdHex.get("stop"));

    }
}
