package sensor.udp;

import gateway.util.Configurations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Properties;

/**
 * Created by Rye on 2/22/14.
 */
public class UDPClient {

    private static Logger logger = LogManager.getLogger(UDPClient.class.getName());

    private DatagramSocket clientSocket;
    private String host;
    private int port;
    private byte[] sendBuffer;
    private byte[] recvBuffer;

    public void init() throws SocketException, FileNotFoundException {
        logger.info("Initializing udp Client...");

        Configurations cf = Configurations.instance();
        Properties config = cf.loadConfiguration();
        if (config == null) {
            throw new FileNotFoundException("Configuration file not found.");
        }
        host = config.getProperty("gateway.host");
        port = Integer.valueOf(config.getProperty("gateway.port"));
//        clientSocket = new DatagramSocket(new InetSocketAddress(host, port));
        clientSocket = new DatagramSocket();

        sendBuffer = new byte[1024];
        recvBuffer = new byte[1024];
    }

    public void send(byte[] data) throws IOException {

        DatagramPacket sendPacket = new DatagramPacket(data, data.length, new InetSocketAddress(host, port));
        clientSocket.send(sendPacket);
    }

    public void shutDown() {
        logger.info("Shutting down udp Client");

        clientSocket.close();
        sendBuffer = null;
        recvBuffer = null;
    }
}
