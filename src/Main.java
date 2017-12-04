import com.sktelecom.smartfleet.sdk.net.MqttWrapper;
import com.sktelecom.smartfleet.sdk.net.RPCType;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static com.sktelecom.smartfleet.sdk.define.CODES.*;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
	    MqttWrapper mqtt = MqttWrapper.getInstance();
        mqtt.setListener(new MqttWrapper.MqttWrapperListener() {
            @Override
            public void onMqttConnected() {
                logger.info("mqtt connected..");
            }

            @Override
            public void onMqttDisconnected() {
                logger.info("mqtt onMqttDisconnected..");
            }

            @Override
            public void onRPCMessageArrived(String topic, String request_id, String method, MqttMessage mqttMessage) {
                logger.info("onRPCMessageArrived..");
                logger.info("topic : "+topic);
                logger.info("method : "+method);
                logger.info("request_id : "+request_id);

                if (method.equals(DEVICE_ACTIVATION)) {
                    mqtt.resultDeviceActivation("00가0000",topic);
                } else if (method.equals(FIRMWARE_UPDATE)) {
                    mqtt.resultFirmwareUpdate(topic);
                } else if (method.equals(OBD_RESET)) {
                    mqtt.resultOBDReset(topic);
                } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK)) {
                    mqtt.resultDeviceSerialNumberCheck("70d71b00-71c9-11e7-b3e0-e5673983c7b9",topic);
                } else if (method.equals(CLEAR_DEVICE_DATA)) {
                    mqtt.resultClearDeviceData(topic);
                } else if (method.equals(FIRMWARE_UPDATE_CHUNK)) {
                    mqtt.resultFirmwareUpdateChunk(topic);
                }
            }
        });

        mqtt.setHost("localhost");
        mqtt.setPort("8443");
        mqtt.setToken("00000000000000000001");

        mqtt.TRE_Connect();

//        mqtt.TRE_SendTrip();
//        mqtt.TRE_SendMicroTrip();
//        mqtt.TRE_SendHfd();
//        mqtt.TRE_SendDiagInfo();
//        mqtt.TRE_SendDrivingCollisionWarning();
//        mqtt.TRE_SendParkingCollisionWarning();
//        mqtt.TRE_SendBatteryWarning();
//        mqtt.TRE_SendUnpluggedWarning();
//        mqtt.TRE_SendTurnOffWarning();
//
//        System.exit(-1);
    }
}
