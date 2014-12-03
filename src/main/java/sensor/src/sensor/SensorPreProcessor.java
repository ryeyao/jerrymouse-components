package sensor.src.sensor;

import gateway.abstracthandler.PreProcessor;
import sensor.src.sensor.udp.UDPDataHandler;
import sensor.src.sensor.udp.UDPServer;

import java.io.FileNotFoundException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 3/27/14
 * Time: 4:30 PM
 */
public class SensorPreProcessor implements PreProcessor {
    @Override
    public void prepare() {
        try {
            startDataCollector();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startDataCollector() throws SocketException, UnknownHostException, FileNotFoundException {
        UDPServer udpServer = new UDPServer();
        udpServer.init();

        UDPDataHandler udpHandler = new UDPDataHandler();

        udpServer.setDataHandler(udpHandler);
        udpServer.run();
    }
}
