package com.sktelecom.smartfleet.sdk.define;

public class CONFIGS {

    //디버깅로그 노출 여부
//    public static final boolean IS_DEBUG_LOG = BuildConfig.IS_DEBUG_LOG;

    /*
    MQTT broker 정보
    IP : 223.39.127.140
    PORT : 1883
    TOKEN : 00000000000000011111
    TOPIC : rpc/request

    실테스트 정보 (eclipse echo 서버인듯..)
    IP: iot.eclipse.org
    PORT : 1883
    TOKEN : A1_TEST_TOKEN (user name)
    TOPIC : planets/earth
     */


    public static String MQTT_SERVER_HOST;
    public static String MQTT_SERVER_PORT;
    public static String MQTT_USER_NAME;

    static {

            MQTT_SERVER_HOST = "smartfleet.sktelecom.com";
            MQTT_SERVER_PORT = "8883";
            MQTT_USER_NAME = "00000000000000000001";

    }

    public static final int qos = 1;
    public static final int microTripQos = 0;

    public static final int timeout = 15;
    public static final int keepalive = 60;

//    public static final String TAG = "SMARTFLEET.SDK";
//
//    static public final String ACTION_LOG_RECEIVER = "ACTION_LOG_RECEIVER";

}
