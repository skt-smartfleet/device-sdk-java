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
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.sktelecom.smartfleet.sdk.define.CODES.CLEAR_DEVICE_DATA;
import static com.sktelecom.smartfleet.sdk.define.CODES.DEVICE_ACTIVATION;
import static com.sktelecom.smartfleet.sdk.define.CODES.DEVICE_SERIAL_NUMBER_CHECK;
import static com.sktelecom.smartfleet.sdk.define.CODES.FIRMWARE_UPDATE;
import static com.sktelecom.smartfleet.sdk.define.CODES.FIRMWARE_UPDATE_CHUNK;
import static com.sktelecom.smartfleet.sdk.define.CODES.OBD_RESET;
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
public class MqttWrapper implements IMqttActionListener, MqttCallback, MqttCallbackExtended {

    private final Logger logger = Logger.getLogger(MqttWrapper.class);
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

    private static MqttWrapper mqttWrapper = null;

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
    private RPCMessageRequest rpcMessageRequest = new RPCMessageRequest();
    private RPCMessageResponse rpcMessageResponse = new RPCMessageResponse();
    private RPCMessageResult rpcMessageResult = new RPCMessageResult();

    public String serverHost = CONFIGS.MQTT_SERVER_HOST;
    public String serverPort = CONFIGS.MQTT_SERVER_PORT;
    public String userName = CONFIGS.MQTT_USER_NAME;
    //    public String passWord = CONFIGS.MQTT_USER_PASSWORD;
//    public String topic = CONFIGS.MQTT_TOPIC;
    final private int qos = CONFIGS.qos;
    final private int microTripQos = CONFIGS.microTripQos;

    private MqttConnectOptions conOpt;

    public static MqttWrapper getInstance() {
        if (mqttWrapper == null) {
            mqttWrapper = new MqttWrapper();
        }

        return mqttWrapper;
    }

    private MqttWrapper() {

    }

    public void setHost(String host) {
        this.serverHost = host;
    }

    public void setPort(String port) {
        this.serverPort = port;
    }

    public void setToken(String token) {
        this.userName = token;
    }

    public boolean isMqttConnectStatus() {

        boolean isConnected;

        MqttWrapper.MqttConnectionStatus status = mqttWrapper.getClientStatus();

        if (status == MqttWrapper.MqttConnectionStatus.DISCONNECTED ||
                status == MqttWrapper.MqttConnectionStatus.NONE ||
                status == MqttWrapper.MqttConnectionStatus.ERROR) {
            isConnected = false;
        } else {
            isConnected = true;
        }
        return isConnected;
    }

    public void initialize() {
//        this.mContext = context.getApplicationContext();
        initializer();
    }

    private void initializer() {

        MqttWrapper.MqttConnectionStatus status = mqttWrapper.getClientStatus();
        attempts = 0;

        if (status == MqttWrapper.MqttConnectionStatus.DISCONNECTED ||
                status == MqttWrapper.MqttConnectionStatus.NONE ||
                status == MqttWrapper.MqttConnectionStatus.ERROR) {
            mqttWrapper.connect(serverHost, serverPort, userName);
        } else {
            mqttWrapper.disconnect();
            mqttWrapper.connect(serverHost, serverPort, userName);
        }

    }

    public void subscribeLinkId() {
        subscribeTopic(SUBSCRIBE_TOPIC, qos);
    }

    public void subscribeLinkId(String linkId) {
        subscribeTopic(linkId, qos);
    }

    private void unsubscribe(String linkId) {
        unsubscribeTopic(linkId);
    }


    @Override
    protected void finalize() throws Throwable {

        try {
            if (mqttClient != null) {
//                mqttClient.unregisterResources();
            }
        } finally {
            super.finalize();
        }
    }

    private MqttConnectionStatus getClientStatus() {
        return mMqttClientStatus;
    }

    /**
     * 데모앱을 위한 콜백 리스너
     */
    public void setListener(MqttWrapperListener listener) {
        mListener = listener;
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
                logger.error("[Publish] onFailure: " + exception.toString());
            } else {
                logger.error("[Publish] onFailure");
            }
        }
    };

    /**
     * subscribe Action 을 위한 IMqttActionLisener
     */
//    IMqttActionListener subscribeMqttActionListener = new IMqttActionListener() {
//        @Override
//        public void onSuccess(IMqttToken asyncActionToken) {
//            logger.info("[Subscribe] onSuccess ");
//        }
//
//        @Override
//        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//            logger.error("[Subscribe] onFailure: " + exception.toString());
//        }
//    };

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

    private void subscribeTopic(String topic, int qos) {
        if (mqttClient != null &&
                mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {

            try {
                logger.info("MQTT : Subscribe to " + topic + ", QoS:" + qos);
                mqttClient.subscribe(topic, qos);//, (IMqttMessageListener) subscribeMqttActionListener);

            } catch (MqttException e) {
                logger.error("MQTT : Subscribe error");
            }
        }
    }

    private void unsubscribeTopic(String topic) {
        if (mqttClient != null &&
                mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {

            try {
                logger.info("MQTT : Unsubscribe from " + topic);
                mqttClient.unsubscribe(topic);

            } catch (MqttException e) {
                logger.error( "MQTT : Unsubscribe error");
            }
        }
    }

    private void publish(final JSONObject pubMessage, String topic, int qos) {

//        logger.info("MQTT : mMqttClientStatus=" + mMqttClientStatus);

        if (mqttClient != null &&
                mMqttClientStatus == MqttConnectionStatus.CONNECTED && topic != null) {
            try {

                MqttMessage message = new MqttMessage();
                message.setPayload(pubMessage.toString().getBytes());
                logger.info("[Publish] Message Publishing [" + topic + "] " + message + " qos:" + qos);
                //mqttClient.publish(topic, message, qos, null);
                mqttClient.publish(topic, message);

            } catch (MqttException e) {
                logger.error("MQTT : Publish error: " + e.toString());
            }
        }
    }


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

    private String getCurrentTime() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formatDate = sdfNow.format(date);

        return formatDate;
    }

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

        logger.info("topic [" + topic + "] " + message);

        try {
            JSONObject receivedMessageObj = new JSONObject(new String(message.getPayload()));

            if (receivedMessageObj.length() > 0) {

                String method = receivedMessageObj.getString("method");

                String rpcReqId = topic.replace(RPC_REQUEST_TOPIC, "");
                if (method.equals(DEVICE_ACTIVATION)) {
                    RESPONSE_DeviceActivation(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                } else if (method.equals(FIRMWARE_UPDATE)) {
                    RESPONSE_FirmwareUpdate(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                } else if (method.equals(OBD_RESET)) {
                    RESPONSE_OBDReset(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK)) {
                    RESPONSE_DeviceSerialNumberCheck(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                } else if (method.equals(CLEAR_DEVICE_DATA)) {
                    RESPONSE_ClearDeviceData(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                } else if (method.equals(FIRMWARE_UPDATE_CHUNK)) {
                    RESPONSE_FirmwareUpdateChunk(CODES.RPC_RESONSE_TOPIC+rpcReqId);
                }
                if (mListener != null) {
                    mListener.onRPCMessageArrived(topic, rpcReqId,method, message);
                }
                message.clearPayload();
            }
        } catch (JSONException e) {
            logger.info("Unexpected JSON exception in MessageArrived");
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
//        logger.info("[Publish] Message Delivered");
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {

        mMqttClientStatus = MqttConnectionStatus.CONNECTED;
        attempts = 0;

        logger.info("[Connect] connect Complete: " + serverURI);

        subscribeLinkId(SUBSCRIBE_TOPIC);

        if (mListener != null) {
            mListener.onMqttConnected();
        }

    }

    /**
     *
     */
    public interface MqttWrapperListener {
        void onMqttConnected();

        void onMqttDisconnected();

        void onRPCMessageArrived(String topic, String request_id,String method, MqttMessage mqttMessage);
    }


    /**
     * 지정된 서버 정보로 TRE 플랫폼에 MQTT 프로토콜로 접속한다.
     * 접속 후 토픽 (rpc/request/+) subscribe
     *
     * @param host         서버 호스트 값
     * @param port         포트 값
     * @param access_token 접속토큰값
     * @return N/A
     */
    public void TRE_Connect(String host, String port, String access_token) {
        initialize();
        subscribeLinkId();
    }

    /**
     * 지정된 서버 정보로 TRE 플랫폼에 MQTT 프로토콜로 접속한다.
     * 접속 후 토픽 (rpc/request/+) subscribe
     * @return N/A
     */
    public void TRE_Connect() {
        initialize();
        subscribeLinkId();
    }

    /**
     * MQTT Broker 연결 해제
     *
     * @return N/A
     */
    public void TRE_Disconnect() {
        disconnect();
    }

    /**
     * Trip  전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendTrip() {
        Trip trip = new Trip();
        trip.setDemoData();
        //logger.info("trip.toString()="+trip.toString());
        publishTrip(TripType.TRIP, trip.tid, trip.stt, trip.edt, trip.dis, trip.tdis, trip.fc, trip.stlat, trip.stlon, trip.edlat, trip.edlon, trip.ctp, trip.coe, trip.fct, trip.hsts, trip.mesp, trip.idt, trip.btv, trip.gnv, trip.wut, trip.usm, trip.est, trip.fwv, trip.dtvt);
    }

    /**
     * MicroTrip 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendMicroTrip() {
        MicroTrip microTrip = new MicroTrip();
        microTrip.setDemoData();
        publishMicroTrip(TripType.MICRO_TRIP, microTrip.tid, microTrip.fc, microTrip.lat, microTrip.lon, microTrip.lc, microTrip.clt, microTrip.cdit, microTrip.rpm, microTrip.sp, microTrip.em, microTrip.el, microTrip.xyz, microTrip.vv, microTrip.tpos);
    }

    /**
     * High Frequency Diag-nostic전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendHfd() {
        HFDCapabilityInfomation hci = new HFDCapabilityInfomation();
        hci.setDemoData();
        publishHFDCapabilityInfomation(TripType.HFD_CAPABILITY_INFORMATION, hci.cm);
    }

    /**
     * Diagnostic Info 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendDiagInfo() {
        DiagnosticInfomation di = new DiagnosticInfomation();
        di.setDemoData();
        publishDiagnosticInfomation(TripType.DIAGNOSTIC_INFORMATION, di.tid, di.dtcc, di.dtck, di.dtcs);
    }

    /**
     * Collision Warning (Driv-ing) 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendDrivingCollisionWarning() {
        DrivingCollisionWarning dcw = new DrivingCollisionWarning();
        dcw.setDemoData();
        publishDrivingCollisionWarning(TripType.DRIVING_COLLISION_WARNING, dcw.tid, dcw.dclat, dcw.dclon);
    }

    /**
     * Collision Warning (Park-ing) 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendParkingCollisionWarning() {
        ParkingCollisionWarning pcw = new ParkingCollisionWarning();
        pcw.setDemoData();
        publishParkingCollisionWarning(TripType.PARKING_COLLISION_WARNING, pcw.pclat, pcw.pclon);
    }

    /**
     * Battery Warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendBatteryWarning() {
        BatteryWarning bw = new BatteryWarning();
        bw.setDemoData();
        publishBatteryWarning(TripType.BATTERY_WARNING, bw.wbv);
    }

    /**
     * Unplugged warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendUnpluggedWarning() {
        UnpluggedWarning uw = new UnpluggedWarning();
        uw.setDemoData();
        publishUnpluggedWarning(TripType.UNPLUGGED_WARNING, uw.unpt, uw.pt);
    }

    /**
     * trun off warning 전송
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void TRE_SendTurnOffWarning() {
        TurnoffWarning tw = new TurnoffWarning();
        tw.setDemoData();
        publishTurnoffWarning(TripType.TURNOFF_WARNING, tw.rs);
    }


    public void publishTrip(TripType eventType, int tid, long stt, long edt, int dis, int tdis, int fc, double stlat, double stlon, double edlat, double edlon, int ctp, double coe, int fct, int hsts, int mesp, int idt, double btv, double gnv, int wut, int usm, int est, String fwv, int dtvt) {
        // tripMessage need to be redefine
        Trip obj = new Trip(tid, stt, edt, dis, tdis, fc, stlat, stlon, edlat, edlon, ctp, coe, fct, hsts, mesp, idt, btv, gnv, wut, usm, est, fwv, dtvt);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TRE, qos);
    }

    public void publishMicroTrip(TripType eventType, String tid, int fc, double lat, double lon, int lc, long clt, int cdit, int rpm, int sp, int em, int el, String xyz, double vv, int tpos) {
        // tripMessage need to be redefine
        MicroTrip obj = new MicroTrip(tid, fc, lat, lon, lc, clt, cdit, rpm, sp, em, el, xyz, vv, tpos);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TRE, microTripQos);
    }

    public void publishHFDCapabilityInfomation(TripType eventType, int cm) {
        // tripMessage need to be redefine
        HFDCapabilityInfomation obj = new HFDCapabilityInfomation(cm);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    public void publishDiagnosticInfomation(TripType eventType, int tid, String dtcc, int dtck, int dtcs) {
        // tripMessage need to be redefine
        DiagnosticInfomation obj = new DiagnosticInfomation(tid, dtcc, dtck, dtcs);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    public void publishDrivingCollisionWarning(TripType eventType, int tid, double dclat, double dclon) {
        // tripMessage need to be redefine
        DrivingCollisionWarning obj = new DrivingCollisionWarning(tid, dclat, dclon);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    public void publishParkingCollisionWarning(TripType eventType, double pclat, double pclon) {
        // tripMessage need to be redefine
        ParkingCollisionWarning obj = new ParkingCollisionWarning(pclat, pclon);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_TELEMETRY, qos);
    }

    public void publishBatteryWarning(TripType eventType, int wbv) {
        // tripMessage need to be redefine
        BatteryWarning obj = new BatteryWarning(wbv);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }

    public void publishUnpluggedWarning(TripType eventType, int unpt, int pt) {
        // tripMessage need to be redefine
        UnpluggedWarning obj = new UnpluggedWarning(unpt, pt);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }

    public void publishTurnoffWarning(TripType eventType, String rs) {
        // tripMessage need to be redefine
        TurnoffWarning obj = new TurnoffWarning(rs);
        publish(tripMessage.messagePackage(System.currentTimeMillis(), eventType.ordinal(), obj), PUBLISH_TOPIC_ATTRIBUTES, qos);
    }


    /**
     * Device DeviceActivation
     * 차량용 센서를 차량에 부착한 후 활성화하기 위해 필요한 RPC 메시지를 명세
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_DeviceActivation(String topic) {
        DeviceActivation da = new DeviceActivation();
        da.setDemoData();
        publishDeviceActivationResponse(RPCType.DEVICE_ACTIVATION, topic);
    }

    public void RESULT_DeviceActivation(String topic) {
        DeviceActivation da = new DeviceActivation();
        da.setDemoData();
        resultDeviceActivation(RPCType.DEVICE_ACTIVATION, da.vid, topic);
    }

    /**
     * Firmware Update
     * 차량용 OBD의 펌웨어 업데이트를 위한 RPC 메시지를 명세합니다.
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_FirmwareUpdate(String topic) {
        publishFirmwareUpdateResponse(RPCType.FIRMWARE_UPDATE, topic);
    }

    public void RESULT_FirmwareUpdate(String topic) {
        resultFirmwareUpdate(RPCType.FIRMWARE_UPDATE, topic);
    }

    /**
     * OBD Reset
     * 차량용 OBD의 재시작을 위한 RPC 메시지
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_OBDReset(String topic) {
        publishOBDResetResponse(RPCType.ODB_RESET, topic);
    }

    public void RESULT_OBDReset(String topic) {
        resultOBDReset(RPCType.ODB_RESET, topic);
    }

    /**
     * Device Serial Number Check
     * 차량용 OBD의 시리얼 번호 확인용 RPC 메시지
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_DeviceSerialNumberCheck(String topic) {
        publishDeviceSerialNumberCheckResponse(RPCType.DEVICE_SERIAL_NUMBER_CHECK, topic);
    }

    public void RESULT_DeviceSerialNumberCheck(String topic) {
        DeviceSerialNumberCheck da = new DeviceSerialNumberCheck();
        da.setDemoData();
        resultDeviceSerialNumberCheck(RPCType.DEVICE_SERIAL_NUMBER_CHECK, da.sn, topic);
    }

    /**
     * Clear Device Data
     * 차량용 OBD 데이터 삭제
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_ClearDeviceData(String topic) {
        publishClearDeviceDataResponse(RPCType.CLEAR_DEVICE_DATA, topic);
    }

    public void RESULT_ClearDeviceData(String topic) {
        resultClearDeviceData(RPCType.CLEAR_DEVICE_DATA, topic);
    }

    /**
     * Firmware Update (Chunk-based)
     * Chunk 기반으로 차량용 OBD의 펌웨어 업데이트를 위한 RPC 메시지를 명세합니다
     *
     * @param @T-RemotEye_ARC_TS 문서 참조
     * @return N/A
     */
    public void RESPONSE_FirmwareUpdateChunk(String topic) {
        publishFirmwareUpdateChunkResponse(RPCType.FIRMWARE_UPDATE_CHUNK, topic);
    }

    public void RESULT_FirmwareUpdateChunk(String topic) {
        resultFirmwareUpdateChunk(RPCType.FIRMWARE_UPDATE_CHUNK, topic);
    }


    //for RPC response
    public void publishDeviceActivationResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }

    public void publishFirmwareUpdateResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }

    public void publishOBDResetResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }

    public void publishDeviceSerialNumberCheckResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }

    public void publishClearDeviceDataResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }

    public void publishFirmwareUpdateChunkResponse(RPCType type, String topic) {
        publish(rpcMessageResponse.messagePackage(type.ordinal()), topic, qos);
    }


    //for RPC result
    public void resultDeviceActivation(String vid, String topic) {
        DeviceActivation obj = new DeviceActivation(vid);
        publish(rpcMessageResult.messagePackage(RPCType.DEVICE_ACTIVATION.ordinal(), obj), topic, qos);
    }

    private void resultDeviceActivation(RPCType type, String vid, String topic) {
        DeviceActivation obj = new DeviceActivation(vid);
        publish(rpcMessageResult.messagePackage(type.ordinal(), obj), topic, qos);
    }

    public void resultFirmwareUpdate(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.FIRMWARE_UPDATE.ordinal(), null), topic, qos);
    }

    private void resultFirmwareUpdate(RPCType type, String topic) {
        publish(rpcMessageResult.messagePackage(type.ordinal(), null), topic, qos);
    }

    public void resultOBDReset(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.ODB_RESET.ordinal(), null), topic, qos);
    }
    private void resultOBDReset(RPCType type, String topic) {
        publish(rpcMessageResult.messagePackage(type.ordinal(), null), topic, qos);
    }

    public void resultDeviceSerialNumberCheck(String sn, String topic) {
        DeviceSerialNumberCheck obj = new DeviceSerialNumberCheck(sn);
        publish(rpcMessageResult.messagePackage(RPCType.DEVICE_SERIAL_NUMBER_CHECK.ordinal(), obj), topic, qos);
    }

    private void resultDeviceSerialNumberCheck(RPCType type, String sn, String topic) {
        DeviceSerialNumberCheck obj = new DeviceSerialNumberCheck(sn);
        publish(rpcMessageResult.messagePackage(type.ordinal(), obj), topic, qos);
    }

    public void resultClearDeviceData(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.CLEAR_DEVICE_DATA.ordinal(), null), topic, qos);
    }

    private void resultClearDeviceData(RPCType type, String topic) {
        publish(rpcMessageResult.messagePackage(type.ordinal(), null), topic, qos);
    }

    public void resultFirmwareUpdateChunk(String topic) {
        publish(rpcMessageResult.messagePackage(RPCType.FIRMWARE_UPDATE_CHUNK.ordinal(), null), topic, qos);
    }

    private void resultFirmwareUpdateChunk(RPCType type, String topic) {
        publish(rpcMessageResult.messagePackage(type.ordinal(), null), topic, qos);
    }
}
