package sensor.udp;

import cn.iie.gaia.util.StoppableLoopThread;
import gateway.util.ConfigurationFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Properties;

/**
 * Created by Rye on 2/22/14.
 */
public class UDPServer extends StoppableLoopThread {

    private String host;
    private int port;
    private static final Logger logger = LogManager.getLogger(UDPServer.class.getName());
    private DatagramSocket serverSocket;
    private byte[] recvBuffer;
    private byte[] sendBuffer;
    private UDPDataHandler dh = null;

    public void setDataHandler(UDPDataHandler udph) {
        dh = udph;
    }

    public void init() throws SocketException {
        logger.info("Initializing cn.iie.jerrymouse.sensor.udp Server...");
        ConfigurationFile cf = new ConfigurationFile();
        Properties config = cf.loadConfiguration();
        host = config.getProperty("udp.host");
        port = Integer.valueOf(config.getProperty("udp.port"));
        serverSocket = new DatagramSocket(port);
        recvBuffer = new byte[1024];
//        sendBuffer = new byte[1024];
    }

    @Override
    public void loopTask() {

        logger.info("cn.iie.jerrymouse.sensor.udp Server listening on port " + port);
        DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
        try {
            serverSocket.receive(recvPacket);
            if (dh != null) {
                dh.onReceive(recvPacket.getData());
            } else {
                String sentence = new String(recvPacket.getData());
                logger.info("Receive:\n    " + sentence + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}