package sensor.udp;

import org.omg.gaia.util.StoppableLoopThread;
import gateway.util.Configurations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
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

    public void init() throws SocketException, UnknownHostException {
        logger.info("Initializing UDP Server...");
        Configurations cf = Configurations.instance();
        Properties config = cf.loadConfiguration();
        host = config.getProperty("udp.host");
        port = Integer.valueOf(config.getProperty("udp.port"));
        InetAddress addr = InetAddress.getByName(host);
        serverSocket = new DatagramSocket(port, addr);
        logger.info("UDP Server listening on {} port {}", addr.getHostAddress(), port);
        recvBuffer = new byte[1024];
//        sendBuffer = new byte[1024];
    }

    @Override
    public void loopTask() {

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
