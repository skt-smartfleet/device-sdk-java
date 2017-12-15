import com.sktelecom.smartfleet.sdk.net.SFMqttWrapper;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static com.sktelecom.smartfleet.sdk.define.CODES.*;
// zip -d *.jar META-INF/*.RSA META-INF/*.DSA META-INF/*.SF
public class Main {
    private static final Logger logger = Logger.getLogger(Main.class);
    /*
        param
        localhost 8333 00000000000000000001 SFSDK–003
     */
    public static void main(String[] args) throws InterruptedException {
        if (args.length == 4) {
            String test_id = args[0];
            String host = args[1];
            String port = args[2];
            String key =  args[3];
            Main.connect(test_id,host,port,key);
        } else {
            logger.error("테스트 코드를 입력하세요");
            System.exit(0);
        }
    }

    static void log(String s) {
        System.out.println(s);
    }

    static void connect(String host, String port, String key,String test_id) throws InterruptedException {
        SFMqttWrapper mqtt = SFMqttWrapper.getInstance();
        // MQTT메세지 응답 리스너
        mqtt.setListener(new SFMqttWrapper.MqttWrapperListener() {
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
             * Response응답은 SDK 에서 자동으로 처리되고 아래 함수내에서 method조건을 구현후 Result 함수를호출하도록한다.
             */
            public void onRPCMessageArrived(String topic, String request_id, String method, MqttMessage mqttMessage) throws InterruptedException {
                logger.info("onRPCMessageArrived request_id > "+request_id);
                logger.info("onRPCMessageArrived method > "+method);
                if (method.equals(DEVICE_ACTIVATION_STR)) {
                    // 단말이 Activation이 필요한 경우에 Activation Flow에 따라 정상적으로 접속이 되는지 확인
                    mqtt.resultDeviceActivation("00가0000",topic);
                } else if (method.equals(FIRMWARE_UPDATE_STR)) {
                    // F/W Update에 대한 원격 요청을 정상적으로 수행하는지 확인
                    mqtt.resultFirmwareUpdate(topic);
                } else if (method.equals(OBD_RESET_STR)) {
                    // 단말 리셋을 정상적으로 수행하는지 확인
                    mqtt.resultOBDReset(topic);
                } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK_STR)) {
                    // 단말 시리얼키 검사
                    mqtt.resultDeviceSerialNumberCheck("70d71b00-71c9-11e7-b3e0-e5673983c7b9",topic);
                } else if (method.equals(CLEAR_DEVICE_DATA_STR)) {
                    // 단말 데이터초기화
                    mqtt.resultClearDeviceData(topic);
                } else if (method.equals(FIRMWARE_UPDATE_CHUNK_STR)) {
                    // Firmware Update Chunk 이벤트
                    mqtt.resultFirmwareUpdateChunk(topic);
                }
                Thread.sleep(1000);
                System.exit(0);
            }
        });
        // MQTT서버 연결주소 설정
        mqtt.setHost(host);
        mqtt.setPort(port);
        // 사용자 인증키(20자리)
        mqtt.setUserName(key);
        mqtt.mqttConnect();
        switch (test_id) {
            case "SFSDK–003" : logger.info("(" + test_id + ") 단말의 인증프로그램(MQTTS) 접속");
                System.exit(0);
                break;
            case "SFSDK–004" : logger.info("(" + test_id + ") 단말의 인증프로그램(MQTTS) 접속 후 Subscription확인");
                Thread.sleep(1000);
                System.exit(0);
                break;
            case "SFSDK–005" : logger.info("(" + test_id + ") [RPC] 단말 Activation Flow처리(필요 조건)");
                break;
            case "SFSDK–006" : logger.info("(" + test_id + ") [RPC] 단말 Activation Flow처리 (불필요 조건)");
                break;
            case "SFSDK–007" : logger.info("(" + test_id + ") Microtrip 전송테스트");
                mqtt.sendMicroTrip();
                System.exit(0);
                break;
            case "SFSDK–008" : logger.info("(" + test_id + ") Microtrip 전송 실패 테스트");
                mqtt.sendTrip();
                System.exit(0);
                break;
            case "SFSDK–009" : logger.info("(" + test_id + ") Trip 전송 테스트");
                mqtt.sendTrip();
                System.exit(0);
                break;
            case "SFSDK–010" : logger.info("(" + test_id + ") Trip 전송 실패 테스트");
                mqtt.sendMicroTrip();
                System.exit(0);
                break;
            case "SFSDK–011" : logger.info("(" + test_id + ") 진단 정보 전송 테스트");
                mqtt.sendDiagInfo();
                System.exit(0);
                break;
            case "SFSDK–012" : logger.info("(" + test_id + ") 진단 정보 전송 실패 테스트");
                mqtt.sendTrip();
                System.exit(0);
                break;
            case "SFSDK–013" : logger.info("(" + test_id + ") 배터리 전압 경고 전송 테스트");
                mqtt.sendBatteryWarning();
                System.exit(0);
                break;
            case "SFSDK–014" : logger.info("(" + test_id + ") 배터리 전압 경고 전송 실패 테스트");
                mqtt.sendMicroTrip();
                System.exit(0);
                break;
            case "SFSDK–015" : logger.info("(" + test_id + ") OBD탈착 이벤트 전송 테스트");
                mqtt.sendUnpluggedWarning();
                System.exit(0);
                break;
            case "SFSDK–016" : logger.info("(" + test_id + ") OBD탈착 이벤트 전송 실패 테스트");
                mqtt.sendTrip();
                System.exit(0);
                break;
            case "SFSDK–017" : logger.info("(" + test_id + ") 종료 요청 이벤트 전송 테스트");
                mqtt.sendTurnOffWarning();
                System.exit(0);
                break;
            case "SFSDK–018" : logger.info("(" + test_id + ") 종료 요청 이벤트 전송 실패 테스트");
                mqtt.sendMicroTrip();
                System.exit(0);
                break;
            case "SFSDK–019" : logger.info("(" + test_id + ") [RPC] 원격요청 테스트");
                break;
            case "SFSDK–020" : logger.info("(" + test_id + ") [RPC] 단말기 F/W Update");
                break;
            case "SFSDK–021" : logger.info("(" + test_id + ") [RPC] 단말기 리셋 요청 테스트");
                break;
            default    : logger.error("(" + test_id + ") 테스트아이디가 존재하지않습니다.");
                System.exit(0);
                break;
        }
    }
}
