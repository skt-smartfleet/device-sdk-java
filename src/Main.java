import com.sktelecom.smartfleet.sdk.net.MqttWrapper;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static com.sktelecom.smartfleet.sdk.define.CODES.*;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
	    MqttWrapper mqtt = MqttWrapper.getInstance();
	    // MQTT메세지 응답 리스너
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
            /**
             * RPC 메세지 수신
             * Response응답은 SDK 에서 처리되고 아래 함수내에서 요청을 조건 구현후 조건에 맞는 Result 함수를호출하도록한다.
             */
            public void onRPCMessageArrived(String topic, String request_id, String method, MqttMessage mqttMessage) {
                if (method.equals(DEVICE_ACTIVATION)) {
                    // 단말이 Activation이 필요한 경우에 Activation Flow에 따라 정상적으로 접속이 되는지 확인
                    mqtt.resultDeviceActivation("00가0000",topic);
                } else if (method.equals(FIRMWARE_UPDATE)) {
                    // F/W Update에 대한 원격 요청을 정상적으로 수행하는지 확인
                    mqtt.resultFirmwareUpdate(topic);
                } else if (method.equals(OBD_RESET)) {
                    // 단말 리셋을 정상적으로 수행하는지 확인
                    mqtt.resultOBDReset(topic);
                } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK)) {
                    // 단말 시리얼키 검사
                    mqtt.resultDeviceSerialNumberCheck("70d71b00-71c9-11e7-b3e0-e5673983c7b9",topic);
                } else if (method.equals(CLEAR_DEVICE_DATA)) {
                    // 단말 데이터초기화
                    mqtt.resultClearDeviceData(topic);
                } else if (method.equals(FIRMWARE_UPDATE_CHUNK)) {
                    // Firmware Update Chunk 이벤트
                    mqtt.resultFirmwareUpdateChunk(topic);
                }
            }
        });
        // MQTT서버 연결주소 설정
        mqtt.setHost("localhost");
        mqtt.setPort("8443");
        // 사용자 인증키(20자리)
        mqtt.setToken("00000000000000000001");
        // MQTT서버연결(ssl)
        mqtt.TRE_Connect();
    }
}
