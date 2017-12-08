package com.sktelecom.smartfleet.sdk.net;

import com.sktelecom.smartfleet.sdk.define.CODES;
import com.sktelecom.smartfleet.sdk.define.CONFIGS;
import com.sktelecom.smartfleet.sdk.obj.RPCMessageRequest;
import com.sktelecom.smartfleet.sdk.obj.RPCMessageResponse;
import com.sktelecom.smartfleet.sdk.obj.RPCMessageResult;
import com.sktelecom.smartfleet.sdk.obj.TripMessage;
import com.sktelecom.smartfleet.sdk.obj.payload.BatteryWarning;
import com.sktelecom.smartfleet.sdk.obj.payload.DiagnosticInfomation;
import com.sktelecom.smartfleet.sdk.obj.payload.DrivingCollisionWarning;
import com.sktelecom.smartfleet.sdk.obj.payload.HFDCapabilityInfomation;
import com.sktelecom.smartfleet.sdk.obj.payload.MicroTrip;
import com.sktelecom.smartfleet.sdk.obj.payload.ParkingCollisionWarning;
import com.sktelecom.smartfleet.sdk.obj.payload.Trip;
import com.sktelecom.smartfleet.sdk.obj.payload.TurnoffWarning;
import com.sktelecom.smartfleet.sdk.obj.payload.UnpluggedWarning;
import com.sktelecom.smartfleet.sdk.obj.result.DeviceActivation;
import com.sktelecom.smartfleet.sdk.obj.result.DeviceSerialNumberCheck;

import com.sktelecom.smartfleet.sdk.util.BypassSSLContextFactory;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.util.Strings;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.sktelecom.smartfleet.sdk.define.CODES.CLEAR_DEVICE_DATA_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.DEVICE_ACTIVATION_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.DEVICE_SERIAL_NUMBER_CHECK_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.FIRMWARE_UPDATE_CHUNK_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.FIRMWARE_UPDATE_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.OBD_RESET_STR;
import static com.sktelecom.smartfleet.sdk.define.CODES.PUBLISH_TOPIC_ATTRIBUTES;
import static com.sktelecom.smartfleet.sdk.define.CODES.PUBLISH_TOPIC_TELEMETRY;
import static com.sktelecom.smartfleet.sdk.define.CODES.PUBLISH_TOPIC_TRE;
import static com.sktelecom.smartfleet.sdk.define.CODES.RPC_REQUEST_TOPIC;
import static com.sktelecom.smartfleet.sdk.define.CODES.SUBSCRIBE_TOPIC;
/**
 * MQTT 프로토콜 Wrapper class
 *
 * @author 유엔젤
 * @version 0.1
 * @see org.eclipse.paho.client.mqttv3.MqttClient
 * @see org.eclipse.paho.client.mqttv3
 */
public class SFMqttWrapper implements IMqttActionListener, MqttCallback, MqttCallbackExtended {

    private final Logger logger = Logger.getLogger(SFMqttWrapper.class);

    /**
     * 1차 10초 마다 재시도 횟수
     * 2차 10분 당 조정 후 재시도 횟수
     */
    private final static int MAX_RETRY_COUNT_1 = 6; // 10초 6번
    private final static int MAX_RETRY_COUNT_2 = MAX_RETRY_COUNT_1+(6*24); // 10분 * 6 * 24 = 하루
    /**
     * 재시도 시간 간격
     */
    private final static int RETRY_INTERVAL_1 = 1000 * 10;
    private final static int RETRY_INTERVAL_2 = 1000 * 60 * 10 ;

    private static SFMqttWrapper SFMqttWrapper = null;

    private MqttClient mqttClient;
    private String clientId;
    private MqttConnectionStatus mMqttClientStatus = MqttConnectionStatus.NONE;
    private MqttWrapperListener mListener;
    private int attempts;

    private enum MqttConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        ERROR,
        NONE
    }

    private TripMessage tripMessage = new TripMessage();
    private RPCMessageResponse rpcMessageResponse = new RPCMessageResponse();
    private RPCMessageResult rpcMessageResult = new RPCMessageResult();

    public String serverHost = CONFIGS.MQTT_SERVER_HOST;
    public String serverPort = CONFIGS.MQTT_SERVER_PORT;
    public String userName = CONFIGS.MQTT_USER_NAME;
    public String password = CONFIGS.MQTT_PASSWORD;

    final private int qos = CONFIGS.qos;
    final private int microTripQos = CONFIGS.microTripQos;

    private MqttConnectOptions conOpt;

    public static SFMqttWrapper getInstance() {
        if (SFMqttWrapper == null) {
            SFMqttWrapper = new SFMqttWrapper();
        }

        return SFMqttWrapper;
    }

    private SFMqttWrapper() {

    }

    /**
     * 서버호스트 정보를 설정한다.
     * @param host
     */
    public void setHost(String host) {
        this.serverHost = host;
    }

    /**
     * 서버 포트 정보를 설정한다.
     * @param port
     */
    public void setPort(String port) {
        this.serverPort = port;
    }

    /**
     * 서버 유저네임을 설정한다.
     * @param userName
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * 서버 비밀번호를 설정한다. (미사용)
     * @return
     */
    public boolean isMqttConnectStatus() {

        boolean isConnected;

        SFMqttWrapper.MqttConnectionStatus status = SFMqttWrapper.getClientStatus();

        if (status == MqttConnectionStatus.DISCONNECTED ||
                status == MqttConnectionStatus.NONE ||
                status == MqttConnectionStatus.ERROR) {
            isConnected = false;
        } else {
            isConnected = true;
        }
        return isConnected;
    }

    public void initialize() {
        initializer();
    }

    /**
     * 지정된 서버 정보로 TRE 플랫폼에 MQTT 프로토콜로 접속한다.
     *
     * @return N/A
     */
    public void mqttConnect() {
        initialize();
    }

    /**
     * 파라미터로 넘겨진 서버 정보로 TRE 플랫폼에 MQTT 프로토콜로 접속한다.
     * @param serverHost
     * @param serverPort
     * @param userName
     * @param password
     */
    public void mqttConnect(String serverHost, String serverPort, String userName, String password) {

        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.userName = userName;
        this.password = password;

        initialize();

    }

    /**
     * MQTT Broker 연결 해제
     *
     * @return N/A
     */
    public void mqttDisconnect() {
        disconnect();
    }

    /**
     * 지정된 Topic 정보로 MQTT subscribe 구독한다
     *
     * @return N/A
     */
    public void subscribeTopic() {
        subscribe(SUBSCRIBE_TOPIC, qos);
    }

    /**
     * 파라미터로 넘겨진 Topic 정보로 MQTT subscribe 구독한다*
     * @param topic
     */
    public void subscribeTopic(String topic) {
        subscribe(topic, qos);
    }

    /**
     * 파라미터로 넘겨진 Topic, qos 정보로 MQTT subscribe 구독한다
     * @param topic
     * @param qos
     */
    public void subscribeTopic(String topic, int qos) {

        logger.info("[Publish] onSuccess");
        if(Strings.isEmpty(topic)){
            logger.info("topic is empty");
            return;
        }
        if(qos<0 || qos > 2){
            logger.info("qos is invalid");
            return;
        }

        subscribe(topic, qos);
    }

    /**
     * 파라미터로 넘겨진 Topic 정보로 MQTT subscribe 구독을 해지한다
     * @param topic
     */
    public void unsubscribeTopic(String topic) {
        unsubscribe(topic);
    }

    /**
     * 데모앱을 위한 콜백 리스너
     * onMqttConnected(), onMqttDisconnected(), onMessageArrived(), onRPCMessageArrived()
     */
    public void setListener(MqttWrapperListener listener) {
        mListener = listener;
    }

    /**
     * MQTT Broker 로 접속을 위한 초기화 작업을 진행한다.
     */
    private void initializer() {

        SFMqttWrapper.MqttConnectionStatus status = SFMqttWrapper.getClientStatus();
        attempts = 0;

        if (status == MqttConnectionStatus.DISCONNECTED || status == MqttConnectionStatus.NONE || status == MqttConnectionStatus.ERROR) {
            SFMqttWrapper.connect(serverHost, serverPort, userName);
        } else {
            SFMqttWrapper.disconnect();
            SFMqttWrapper.connect(serverHost, serverPort, userName);
        }

    }


    /**
     * 현재 클라이언트의 MQTT Broker 연결 상태를 조회한다.
     * @return
     */
    private MqttConnectionStatus getClientStatus() {
        return mMqttClientStatus;
    }

    /**
     * publish Action 을 위한 IMqttActionLisener
     */
    IMqttActionListener publishMqttActionListener = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            logger.info("[Publish] onSuccess");
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            if (exception != null) {
                logger.info("[Publish] onFailure: " + exception.toString());
            } else {
                logger.info("[Publish] onFailure");
            }
        }
    };

    /**
     * subscribe Action 을 위한 IMqttActionLisener
     */
    IMqttActionListener subscribeMqttActionListener = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            logger.info("[Subscribe] onSuccess ");
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            logger.info("[Subscribe] onFailure: " + exception.toString());
        }
    };

    /**
     * 전달된 파라미터 정보로 MQTT Broker 에 접속한다.
     * @param host
     * @param port
     * @param username
     */
    private void connect(String host, String port, String username) {

        clientId = "TRE" + System.currentTimeMillis();

//        String uri = "tcp://" + host + ":" + port;
        String uri = "ssl://" + host + ":" + port;
        logger.info("MQTT : uri : " + uri);
        logger.info("MQTT : Client Connected : " + clientId);

        try {
            mqttClient = new MqttClient(uri, clientId,new MemoryPersistence());
        } catch (MqttException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        conOpt = new MqttConnectOptions();
        conOpt.setCleanSession(true);
        conOpt.setConnectionTimeout(CONFIGS.timeout);
        conOpt.setAutomaticReconnect(false);
        conOpt.setKeepAliveInterval(CONFIGS.keepalive);

        if (username != null && username.length() > 0) {
            conOpt.setUserName(username);
        }

        mqttClient.setCallback(this);

        try {
            conOpt.setSocketFactory(BypassSSLContextFactory.createClientSSLContext().getSocketFactory());
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        logger.info(conOpt.toString());
        try {
            mMqttClientStatus = MqttConnectionStatus.CONNECTING;
            mqttClient.connect(conOpt);
        } catch (MqttException e) {
            logger.error("MQTT : Connection error: " + e.toString());
        }
    }

    /**
     * 전달된 파라미터 (Topic, qos)로 MQTT Broker 에 구독한다.
     * @param topic
     * @param qos
     */
    private void subscribe(String topic, int qos) {

        if (mqttClient != null && mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {

            try {
                logger.info("MQTT : Subscribe to " + topic + ", QoS:" + qos);
                mqttClient.subscribe(topic, qos);

            } catch (MqttException e) {
                logger.error("MQTT : Subscribe error");
            }
        }
    }

    /**
     * 구독을 해지 한다.
     * @param topic
     */
    private void unsubscribe(String topic) {

        if (mqttClient != null && mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {

            try {
                logger.info("MQTT : Unsubscribe from " + topic);
                mqttClient.unsubscribe(topic);

            } catch (MqttException e) {
                logger.error("MQTT : Unsubscribe error");
            }
        }
    }

    /**
     * MQTT Broker 로 publish 한다.
     * @param pubMessage
     * @param topic
     * @param qos
     */
    private void publish(final JSONObject pubMessage, String topic, int qos) {

        logger.info("MQTT : mMqttClientStatus=" + mMqttClientStatus);

        if (mqttClient != null &&
                mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {

            try {

                MqttMessage message = new MqttMessage();
                message.setPayload(pubMessage.toString().getBytes());
                logger.info("[Publish] Message Publishing [" + topic + "] " + message + " qos:" + qos);
                mqttClient.publish(topic, message);
            } catch (MqttException e) {
                logger.error("MQTT : Publish error: " + e.toString());
            }
        }
    }

    /**
     * 현재 Mqtt Connect 연결이 정상 상태인 경우 disconnect 한다.
     */
    private void disconnect() {
        if (mqttClient != null) {
            try {

                mMqttClientStatus = MqttConnectionStatus.DISCONNECTING;
                mqttClient.disconnect();

                logger.info("MQTT : Disconnected");

            } catch (MqttException e) {
                logger.error("[ConnectFail] Disconnection error: " + e.toString());
            }
        }
    }

    /**
     * 현재시간을 조회한다.
     * @return
     */
    private String getCurrentTime() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formatDate = sdfNow.format(date);

        return formatDate;
    }

    /**
     * IMqttActionListener Interface 구현체
     * onSuccess(IMqttToken asyncActionToken ) 구현
     * @param asyncActionToken
     */
    @Override
    public void onSuccess(IMqttToken asyncActionToken) {

        if (mMqttClientStatus == MqttConnectionStatus.CONNECTING) {

            logger.info("[Connect] Connected to server!");
            mMqttClientStatus = MqttConnectionStatus.CONNECTED;
            attempts = 0;

            if (mListener != null) {
                mListener.onMqttConnected();
            }

        } else if (mMqttClientStatus == MqttConnectionStatus.DISCONNECTING) {

            logger.info("[DisConnect] DisConnected to server!");

            mMqttClientStatus = MqttConnectionStatus.DISCONNECTED;

            if (mListener != null) {
                mListener.onMqttDisconnected();
            }

//            mqttClient.unregisterResources();
            mqttClient = null;

        } else {
            logger.info("MQTT : Unknown onSuccess");
        }
    }

    /**
     * IMqttActionListener Interface 구현체
     * onFailure(IMqttToken asyncActionToken, Throwable exception) 구현
     * @param asyncActionToken
     */
    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

        logger.info("[Connect] onFailure: " + exception.toString());
        if (attempts < MAX_RETRY_COUNT_1) {
            attempts++;

            Runnable ReconnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mqttClient != null) {
                            logger.info("[onFailure] Reconnect. attempts= " + attempts);
                            mqttClient.connect(conOpt);
                        }
                    } catch (MqttException e) {
                        logger.info("MQTT : Connection error: " + e.toString());
                    }
                }
            };
//            new Handler().postDelayed(ReconnectRunnable, RETRY_INTERVAL_1);
            return;

        }else if (attempts < MAX_RETRY_COUNT_2) {
            attempts++;

            Runnable ReconnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mqttClient != null) {
                            logger.info("[onFailure] Reconnect. attempts= " + attempts);
                            mqttClient.connect(conOpt);
                        }
                    } catch (MqttException e) {
                        logger.info("MQTT : Connection error: " + e.toString());
                    }
                }
            };
//            new Handler().postDelayed(ReconnectRunnable, RETRY_INTERVAL_2);
            return;
        }

        attempts = 0;
        mMqttClientStatus = MqttConnectionStatus.ERROR;

        if (mListener != null) {
            mListener.onMqttDisconnected();
        }
    }

    /**
     * MqttCallback Interface 구현체
     * onSuccess(IMqttToken asyncActionToken ), messageArrived(String topic, MqttMessage message), deliveryComplete(IMqttDeliveryToken token) 구현
     * @param cause
     */

    @Override
    public void connectionLost(Throwable cause) {

        logger.info("MQTT connection is lost : " + cause);

        if (attempts < MAX_RETRY_COUNT_1) {
            attempts++;

            Runnable ReconnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mqttClient != null) {
                            logger.info("[connectionLost] Reconnect. attempts= " + attempts);
                            mqttClient.connect(conOpt);
                        }
                    } catch (MqttException e) {
                        logger.info("MQTT : Connection error: " + e.toString());
                    }
                }
            };
//            new Handler().postDelayed(ReconnectRunnable, RETRY_INTERVAL_1);
            return;

        }else if (attempts < MAX_RETRY_COUNT_2) {
            attempts++;

            Runnable ReconnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mqttClient != null) {
                            logger.info("[connectionLost] Reconnect. attempts= " + attempts);
                            mqttClient.connect(conOpt);
                        }
                    } catch (MqttException e) {
                        logger.info("MQTT : Connection error: " + e.toString());
                    }
                }
            };
//            new Handler().postDelayed(ReconnectRunnable, RETRY_INTERVAL_2);
            return;
        }

        attempts = 0;
        mMqttClientStatus = MqttConnectionStatus.DISCONNECTED;

        if (mListener != null) {
            mListener.onMqttDisconnected();
        }
    }


    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        logger.info("[Subcribe] Message Arrived [" + topic + "] " + message);
        try {
            JSONObject receivedMessageObj = new JSONObject(new String(message.getPayload()));

            if (receivedMessageObj.length() > 0) {

                //성공 시 데모앱으로 전달
                if (!Strings.isEmpty(receivedMessageObj.getString("method"))) {

                    String method = receivedMessageObj.getString("method");

                    String rpcReqId = "";

                    if(!Strings.isEmpty(topic)){
                        rpcReqId = topic.replace(RPC_REQUEST_TOPIC, "");
                    }

                    if (method.equals(DEVICE_ACTIVATION_STR)) {
                        responseDeviceActivation(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    } else if (method.equals(FIRMWARE_UPDATE_STR)) {
                        responseFirmwareUpdate(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    } else if (method.equals(OBD_RESET_STR)) {
                        responseOBDReset(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK_STR)) {
                        responseDeviceSerialNumberCheck(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    } else if (method.equals(CLEAR_DEVICE_DATA_STR)) {
                        responseClearDeviceData(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    } else if (method.equals(FIRMWARE_UPDATE_CHUNK_STR)) {
                        responseFirmwareUpdateChunk(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                    }

                    if (mListener != null) {
                        //mListener.onMqttMessageArrived(topic, message);
                        mListener.onRPCMessageArrived(CODES.RPC_RESULT_TOPIC+rpcReqId, rpcReqId, method, message);
                    }

                }

                message.clearPayload();
            }

        } catch (JSONException e) {
            logger.info("Unexpected JSON exception in MessageArrived");
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        logger.info("[Publish] Message Delivered");
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {

        mMqttClientStatus = MqttConnectionStatus.CONNECTED;
        attempts = 0;

        logger.info("[Connect] connect Complete: " + serverURI);

    }

    /**
     * Demo 앱에서 사용하기 위한 I/F 제공
     */
    public interface MqttWrapperListener {
        void onMqttConnected();

        void onMqttDisconnected();

//        void onMqttMessageArrived(String topic, MqttMessage mqttMessage);

        void onRPCMessageArrived(String topic, String request_id, String method, MqttMessage mqttMessage);

    }

    /**
     * Trip  전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendTrip() {
        Trip obj = new Trip();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.TRIP.ordinal(), obj), PUBLISH_TOPIC_TRE, qos);
    }
    public void sendTrip(Trip obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.TRIP.ordinal(), obj), PUBLISH_TOPIC_TRE, qos);
    }
    /**
     * MicroTrip 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendMicroTrip() {
        MicroTrip obj = new MicroTrip();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.MICRO_TRIP.ordinal(), obj), PUBLISH_TOPIC_TRE, microTripQos);
    }
    public void sendMicroTrip(MicroTrip obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.MICRO_TRIP.ordinal(), obj), PUBLISH_TOPIC_TRE, microTripQos);
    }
    /**
     * High Frequency Diag-nostic전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendHfd() {
        HFDCapabilityInfomation obj = new HFDCapabilityInfomation();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.HFD_CAPABILITY_INFORMATION.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }
    public void sendHfd(HFDCapabilityInfomation obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.HFD_CAPABILITY_INFORMATION.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }
    /**
     * Diagnostic Info 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendDiagInfo() {
        DiagnosticInfomation obj = new DiagnosticInfomation();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.DIAGNOSTIC_INFORMATION.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }
    public void sendDiagInfo(DiagnosticInfomation obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.DIAGNOSTIC_INFORMATION.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    /**
     * Collision Warning (Driv-ing) 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendDrivingCollisionWarning() {
        DrivingCollisionWarning obj = new DrivingCollisionWarning();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.DRIVING_COLLISION_WARNING.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }
    public void sendDrivingCollisionWarning(DrivingCollisionWarning obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.DRIVING_COLLISION_WARNING.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    /**
     * Collision Warning (Park-ing) 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendParkingCollisionWarning() {
        ParkingCollisionWarning obj = new ParkingCollisionWarning();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.PARKING_COLLISION_WARNING.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }
    public void sendParkingCollisionWarning(ParkingCollisionWarning obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.PARKING_COLLISION_WARNING.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    /**
     * Battery Warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendBatteryWarning() {
        BatteryWarning obj = new BatteryWarning();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.BATTERY_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }
    public void sendBatteryWarning(BatteryWarning obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.BATTERY_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }

    /**
     * Unplugged warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendUnpluggedWarning() {
        UnpluggedWarning obj = new UnpluggedWarning();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.UNPLUGGED_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }
    public void sendUnpluggedWarning(UnpluggedWarning obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.UNPLUGGED_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }

    /**
     * trun off warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void sendTurnOffWarning() {
        TurnoffWarning obj = new TurnoffWarning();
        obj.setDemoData();
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.TURNOFF_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }
    public void sendTurnOffWarning(TurnoffWarning obj) {
        publish(tripMessage.messagePackage(System.currentTimeMillis(), TripType.TURNOFF_WARNING.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }


    /**
     * Device DeviceActivation
     * 차량용 센서를 차량에 부착한 후 활성화하기 위해 필요한 RPC 메시지를 명세
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseDeviceActivation(String topic) {
        DeviceActivation da = new DeviceActivation();
        da.setDemoData();
        publish(rpcMessageResponse.messagePackage(RPCType.DEVICE_ACTIVATION.ordinal()), topic, qos);
    }

    /**
     * Firmware Update
     * 차량용 OBD의 펌웨어 업데이트를 위한 RPC 메시지를 명세합니다.
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseFirmwareUpdate(String topic) {
        publish(rpcMessageResponse.messagePackage(RPCType.FIRMWARE_UPDATE.ordinal()), topic, qos);
    }

    /**
     * OBD Reset
     * 차량용 OBD의 재시작을 위한 RPC 메시지
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseOBDReset(String topic) {
        publish(rpcMessageResponse.messagePackage(RPCType.ODB_RESET.ordinal()), topic, qos);
    }

    /**
     * Device Serial Number Check
     * 차량용 OBD의 시리얼 번호 확인용 RPC 메시지
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseDeviceSerialNumberCheck(String topic) {
        publish(rpcMessageResponse.messagePackage(RPCType.DEVICE_SERIAL_NUMBER_CHECK.ordinal()), topic, qos);
    }

    /**
     * Clear Device Data
     * 차량용 OBD 데이터 삭제
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseClearDeviceData(String topic) {
        publish(rpcMessageResponse.messagePackage(RPCType.CLEAR_DEVICE_DATA.ordinal()), topic, qos);
    }

    /**
     * Firmware Update (Chunk-based)
     * Chunk 기반으로 차량용 OBD의 펌웨어 업데이트를 위한 RPC 메시지를 명세합니다
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void responseFirmwareUpdateChunk(String topic) {
        publish(rpcMessageResponse.messagePackage(RPCType.FIRMWARE_UPDATE_CHUNK.ordinal()), topic, qos);
    }

    //for RPC result

    /**
     * DeviceActivation RPC 요청에 대한 처리 결과를 publish 한다.
     * @param vid
     * @param topic
     */
    public void resultDeviceActivation(String vid, String topic) {
        DeviceActivation obj = new DeviceActivation(vid);
        publish(rpcMessageResult.messagePackage(RPCType.DEVICE_ACTIVATION.ordinal(), obj), topic, qos);
    }

    /**
     * FrimwareUpdate RPC 요청에 대한 처리 결과를 publish 한다.
     * @param topic
     */
    public void resultFirmwareUpdate(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.FIRMWARE_UPDATE.ordinal(), null), topic, qos);
    }

    /**
     * OBDReset RPC 요청에 대한 처리 결과를 publish 한다.
     * @param topic
     */
    public void resultOBDReset(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.ODB_RESET.ordinal(), null), topic, qos);
    }

    /**
     * DeviceSerialNumberCheck RPC 요청에 대한 처리 결과를 publish 한다.
     * @param sn
     * @param topic
     */
    public void resultDeviceSerialNumberCheck(String sn, String topic) {
        DeviceSerialNumberCheck obj = new DeviceSerialNumberCheck(sn);
        publish(rpcMessageResult.messagePackage(RPCType.DEVICE_SERIAL_NUMBER_CHECK.ordinal(), obj), topic, qos);
    }

    /**
     * ClearDeviceData RPC 요청에 대한 처리 결과를 publish 한다.
     * @param topic
     */
    public void resultClearDeviceData(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.CLEAR_DEVICE_DATA.ordinal(), null), topic, qos);
    }

    /**
     * FirmwareUpdateChunk RPC 요청에 대한 처리 결과를 publish 한다.
     * @param topic
     */
    public void resultFirmwareUpdateChunk(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.FIRMWARE_UPDATE_CHUNK.ordinal(), null), topic, qos);
    }


}
