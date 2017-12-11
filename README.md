# Java Source for T-RemotEye

본 코드는 T-RemotEye 기반 Java SDK를 제공합니다.

## Configure

### MQTT Broker 정보

|Attribute | Value | Note |
| --- | --- | --- |
|IP | smartfleet.sktelecom.com |`MQTT_SERVER_HOST`|
|Port | 8883|`MQTT_SERVER_PORT`|
|UserName | 00000000000000011111 |`MQTT_USER_NAME`|

### MQTTS 설정

|Attribute | Value | Note |
| --- | --- | --- |
|QoS | 1 |`qos`|
|Microtrip QoS | 0 |`microTripQos`|
|timeout | 15 |`timeout`|
|keepalive | 60 |`keepalive`|
|cleanSession | true | `setCleanSession(boolean)` |

`$project/sdk/src/main/java/com/sktelecom/smartfleet/sdk/define/CONFIGS.java`:
```
static {
  MQTT_SERVER_HOST = "smartfleet.sktelecom.com";
  MQTT_SERVER_PORT = "8883";
  MQTT_USER_NAME = "00000000000000000001";
}
```
```
public static final int qos = 1;
public static final int microTripQos = 0;

public static final int timeout = 15;
public static final int keepalive = 60;
```
`$project/sdk/src/main/java/com/sktelecom/smartfleet/sdk/net/SFMqttWrapper.java`:
```
conOpt = new MqttConnectOptions();
conOpt.setCleanSession(true);
conOpt.setConnectionTimeout(CONFIGS.timeout);
conOpt.setAutomaticReconnect(false);
conOpt.setKeepAliveInterval(CONFIGS.keepalive);

if (username != null && username.length() > 0) {
  conOpt.setUserName(username);
}
```

## Code Guide

T-RemotEye Proxy에 접속, 메시지 전송 등을 위해 `defaultPackage.net`의 SFMqttWrapper인 Wrapper Class를 제공합니다.

### RPC Result 구현 예시

Response응답은 SDK내에서 처리되며, Result응답은 `MqttWrapperListener` 인터페이스의 `onRPCMessageArrived` 함수에서 수신된 RPC메세지 종류에 따라 구현후 SDK에서 제공하는 Result함수를 호출해야 합니다.

```
import com.sktelecom.smartfleet.sdk.net.SFMqttWrapper인;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static com.sktelecom.smartfleet.sdk.define.CODES.*;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
        SFMqttWrapper mqtt = SFMqttWrapper.getInstance();
	      // MQTT메세지 응답 리스너를 등록합니다.
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
             * Response응답은 SDK 에서 자동으로 처리되고 아래 함수내에서 method조건을 구현후 Result 함수를호출하도록합니다.
             */
            public void onRPCMessageArrived(String topic, String request_id, String method, MqttMessage mqttMessage) {
                if (method.equals(DEVICE_ACTIVATION)) {
                    // 단말이 Activation이 필요한 경우에 Activation Flow에 따라 정상적으로 접속이 되는지 확인합니다.
                    mqtt.resultDeviceActivation("00가0000",topic);
                } else if (method.equals(FIRMWARE_UPDATE)) {
                    // F/W Update에 대한 원격 요청을 정상적으로 수행하는지 확인합니다.
                    mqtt.resultFirmwareUpdate(topic);
                } else if (method.equals(OBD_RESET)) {
                    // 단말 리셋을 정상적으로 수행하는지 확인합니다.
                    mqtt.resultOBDReset(topic);
                } else if (method.equals(DEVICE_SERIAL_NUMBER_CHECK)) {
                    // 단말 시리얼키 검사합니다.
                    mqtt.resultDeviceSerialNumberCheck("70d71b00-71c9-11e7-b3e0-e5673983c7b9",topic);
                } else if (method.equals(CLEAR_DEVICE_DATA)) {
                    // 단말 데이터초기화합니다.
                    mqtt.resultClearDeviceData(topic);
                } else if (method.equals(FIRMWARE_UPDATE_CHUNK)) {
                    // Firmware Update Chunk 이벤트 처리응답입니다.
                    mqtt.resultFirmwareUpdateChunk(topic);
                }
            }
        });
        // MQTTS서버 연결주소를 설정합니다. 
        mqtt.setHost("localhost");
        // MQTTS포토를 입력합니다.
        mqtt.setPort("8443");
        // 사용자 인증키(20자리)를 입력합니다.
        mqtt.setToken("00000000000000000001");
        // MQTTS서버와 연결합니다.
        mqtt.initialize();
    }
}

```
## API

### Initialize

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.initialize()
```

MQTT Broker 로 접속을 위한 초기화 작업을 진행합니다.
지정된 정보로 MQTTS 클라이언트를 생성하고 연결을 시도합니다.

* Returns
  * N/A

### Connect

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.mqttConnect(String host, String port, String username)
```

지정된 정보로 MQTTS 클라이언트를 생성하고 연결을 시도합니다.

* Parameters
  * **host** 플랫폼 서버 호스트
  * **port** 플랫폼 서버 포트
  * **username** 디바이스 Credentials ID
* Returns
  * N/A


```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.onSuccess(IMqttToken asyncActionToken)
```

연결 성공 시 실행하는 콜백 함수입니다.

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.onFailure(IMqttToken asyncActionToken)
```

연결 실패 시 실행하는 콜백 함수입니다.

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A



### Subscribe

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.subscribeTopic(String topic, int qos)
```

연결이 성공한 뒤 토픽을 구독할 때 사용하는 함수입니다.

* Parameters
  * **topic** 구독할 토픽
  * **qos** Quality of service의 약자로 서비스 품질을 선택.
* Returns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.subscribeMqttActionListener.onSuccess(IMqttToken asyncActionToken)
```

구독 성공 시 실행하는 콜백 함수입니다

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.subscribeMqttActionListener.onFailure(IMqttToken asyncActionToken)
```

구독 실패 시 실행하는 콜백 함수입니다

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A

### Publish

#### Common

```
JSONObject com.sktelecom.smartfleet.sdk.obj.TripMessage.messagePackage(long ts, int ty, Object obj)
```

`messagePackage()` 함수는 전달받은 오브젝트 형태의 파라미터 중 일부 변수들 값을 변경합니다.

* Parameters
  * **ts** 정보 수집시간. UNIX Timestamp
  * **ty** 페이로드 타입
  * **obj** 발행할 파라미터
* Retruns
  * **message** JSONObject 형태로 메시지를 발행

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.publish(final JSONObject pubMessage, String topic, int qos)
```
토픽을 발행할 때 사용하는 함수입니다

* Parameters
  * **pubMessage** 토픽에 대한 파라미터
  * **topic** 발행할 토픽
  * **qos** Quality of service의 약자로 서비스 품질을 선택.
* Returns
  * N/A

```
org.eclipse.paho.client.mqttv3.MqttMessage.setPayload(byte[] payload)
```

메시지의 페이로드를 지정된 바이트 배열로 설정합니다.

* Parameters
  * **payload** 메시지 페이로드
* Returns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.publishMqttActionListener.onSuccess(IMqttToken asyncActionToken)
```

발행 성공 시 실행하는 콜백 함수입니다.

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.publishMqttActionListener.onFailure(IMqttToken asyncActionToken)
```

발행 실패 시 실행하는 콜백 함수입니다.

* Parameters
  * **asyncActionToken** Public Interface로 비동기 작업을 추적하는 메커니즘 제공. 해당 토큰을 사용하여 작업이 완료될 때까지 대기가 가능.
* Returns
  * N/A

#### Trip

```
void com.sktelecom.smartfleet.sdk.obj.payload.Trip.Trip(int tid, long stt, long edt, int dis, int tdis, int fc, double stlat, double stlon, double edlat, double edlon, int ctp, double coe, int fct, int hsts, int mesp, int idt, double btv, double gnv, int wut, int usm, int est, String fwv, int dtvt)
```

전달 받은 파라미터로 Trip 오브젝트를 세팅합니다.

* Parameters
  * **tid** Trip 고유 번호
  * **stt** Trip의 시작 날짜 및 시간(UTC)
  * **edt** Trip의 종료 날짜 및 시간(UTC)
  * **dis** Trip의 주행거리
  * **tdis** 차량의 총 주행거리
  * **fc** 연료소모량
  * **stlat** 운행 시작 좌표의 위도
  * **stlon** 운행 시작 좌표의 경도
  * **edlat** 운행 종료 좌표의 위도
  * **edlon** 운행 종료 좌표의 경도
  * **ctp** 부동액(냉각수) 평균온도
  * **coe** Trip의 탄소 배출량
  * **fct** 연료차단 상태의 운행시간
  * **hsts** Trip의 최고 속도
  * **mesp** Trip의 평균 속도
  * **idt** Trip의 공회전 시간
  * **btv** 배터리 전압(시동OFF후 전압)
  * **gnv** 발전기 전압(주행중 최고 전압)
  * **wut** Trip의 웜업시간(주행전 시동 시간)
  * **usm** BT가 연결된 휴대폰 번호
  * **est** 80~100km 운행 시간
  * **fwv** 펌웨어 버전
  * **dtvt** 주행시간
* Returns
  * N/A

#### Microtrip

```
void com.sktelecom.smartfleet.sdk.obj.payload.MicroTrip.MicroTrip(int tid, int fc, double lat, double lon, int lc, long clt, int cdit, int rpm, int sp, int em, int el, String xyz, double vv, int tpos)
```

전달 받은 파라미터로 Microtrip 오브젝트를 세팅합니다.

* Parameters
  * **tid** Trip 고유 번호
  * **fc** 연료소모량
  * **lat** 위도 (WGS84)
  * **lon** 경도 (WGS84)
  * **lc** 측정 한 위치 값의 정확도
  * **clt** 단말기 기준 수집 시간
  * **cdit** Trip의 현재시점까지 주행거리
  * **rpm** rpm
  * **sp** 차량 속도
  * **em** 한 주기 동안 발생한 이벤트(Hexastring)
  * **el** 엔진 부하
  * **xyz** 가속도 X, Y 및 각속도 Y 값
  * **vv** 배터리 전압 (시동 OFF 후 전압)
  * **tpos** 엑셀 포지션 값
* Returns
  * N/A

#### HFD Capability Infomation

```
void com.sktelecom.smartfleet.sdk.obj.payload.HFDCapabilityInfomation.HFDCapabilityInfomation(int cm)
```

전달 받은 파라미터로 HFD Capability Infomation 오브젝트를 세팅합니다.

* Parameters
  * **cm** OBD가 전송할 수 있는 HFD 항목 (Hexastring)
* Returns
  * N/A

#### Diagnostic Information

임의로 Diagnostic Information 파라미터 값을 세팅합니다.

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.publishDiagnosticInfomation(TripType eventType, int tid, String dtcc, int dtck, int dtcs)
```

Diagnostic Information을 발행하는 함수입니다.

* Parameters
  * **eventType** Event Type 구별
  * **tid** Trip 고유 번호
  * **dtcc** 차량고장코드
  * **dtck** 0=confirm 1=pending 2=permanent
  * **dtcs** DTC Code의 개수
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.obj.payload.DiagnosticInfomation.DiagnosticInfomation(int tid, String dtcc, int dtck, int dtcs)
```

전달 받은 파라미터로 Diagnostic Information 오브젝트를 세팅합니다.

* Parameters
  * **tid** Trip 고유 번호
  * **dtcc** 차량고장코드
  * **dtck** 0=confirm 1=pending 2=permanent
  * **dtcs** DTC Code의 개수
* Retruns
  * N/A

#### Driving Collision Warning

```
void com.sktelecom.smartfleet.sdk.obj.payload.DrivingCollisionWarning.DrivingCollisionWarning(int tid, double dclat, double dclon)
```

전달 받은 파라미터로 Driving Collision Warning 오브젝트를 세팅합니다.

* Parameters
  * **tid** Trip 고유 번호
  * **dclat** 위도
  * **dclon** 경도
* Retruns
  * N/A

#### Parking Collision Warning

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.publishParkingCollisionWarning(TripType eventType, double pclat, double pclon)
```

Parking Collision Warning을 발행하는 함수입니다.

* Parameters
  * **eventType** Event Type 구별
  * **pclat** 위도
  * **pclon** 경도
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.obj.payload.ParkingCollisionWarning.ParkingCollisionWarning(double pclat, double pclon)
```

전달 받은 파라미터로 Parking Collision Warning 오브젝트를 세팅합니다.

* Parameters
  * **pclat** 위도
  * **pclon** 경도
* Retruns
  * N/A

#### Battery Warning

```
void com.sktelecom.smartfleet.sdk.obj.payload.BatteryWarning.BatteryWarning(int wbv)
```

전달 받은 파라미터로 Battery Warning 오브젝트를 세팅합니다.

* Parameters
  * **wbv** 배터리 전압
* Retruns
  * N/A

#### Unplugged Warning

```
void com.sktelecom.smartfleet.sdk.obj.payload.UnpluggedWarning.UnpluggedWarning(int unpt, int pt)
```

전달 받은 파라미터로 Unplugged Warning 오브젝트를 세팅합니다.

* Parameters
  * **unpt** 탈착 시간(UTC Timestamp)
  * **pt** 부착 시간(UTC Timestamp)
* Retruns
  * N/A

#### Turn Off Warning

```
void com.sktelecom.smartfleet.sdk.obj.payload.TurnoffWarning.TurnoffWarning(String rs)
```

전달 받은 파라미터로 Turn Off Warning 오브젝트를 세팅합니다.

* Parameters
  * **rs** 단말 종료 원인
* Retruns
  * N/A

#### Device RPC

##### Common

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.messageArrived(String topic, MqttMessage message)
```

구독한 토픽으로 메세지를 받을 시 실행하는 콜백 함수입니다. RPC 요청은 해당 함수를 통해 처리합니다.

* Parameters
  * **topic** 메시지 온 토픽
  * **message** 메시지 내용
* Retruns
  * N/A

```
JSONObject com.sktelecom.smartfleet.sdk.obj.RPCMessageResponse.messagePackage(int ty)
```

Device RPC Response 토픽을 발행할 때 사용하는 함수입니다.

* Parameters
  * **ty** RPC Type
* Retruns
  * **message** JSONObject 형태로 메시지를 발행

```
JSONObject com.sktelecom.smartfleet.sdk.obj.RPCMessageResult.messagePackage(int ty, Object obj)
```

Device RPC Result 토픽을 발행할 때 사용하는 함수입니다.

* Parameters
  * **ty** RPC Type
  * **obj** 발행할 파라미터
* Retruns
  * **message** JSONObject 형태로 메시지를 발행한다


##### Device Activation

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseDeviceActivation(String topic)
```

Device Activation 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultDeviceActivation(String vid, String topic)
```

Device Activation 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **vid** 차량 식별 번호
  * **topic** 발행할 토픽
* Retruns
  * N/A

##### Firmware Update


```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseFirmwareUpdate(String topic)
```

Firmware Update 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A


```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultFirmwareUpdate(String topic)
```

Firmware Update 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

##### OBD Reset

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseOBDReset(String topic)
```

OBD Reset 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultOBDReset(String topic)
```

OBD Reset 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

##### Device Serial NumberCheck

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseDeviceSerialNumberCheck(String topic)
```

Device Serial NumberCheck 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultDeviceSerialNumberCheck(String sn, String topic)
```

Device Serial NumberCheck 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **sn** 디바이스 시리얼 넘버
  * **topic** 발행할 토픽
* Retruns
  * N/A


##### Clear Device Data

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseClearDeviceData(String topic)
```

Clear Device Data 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultClearDeviceData(String topic)
```

Clear Device Data 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A


##### Firmware Update Chunk

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.responseFirmwareUpdateChunk(String topic)
```

Firmware Update Chunk 이벤트에 대해 Response 토픽 메시지를 보내는 함수 입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A

```
void com.sktelecom.smartfleet.sdk.net.SFMqttWrapper.resultFirmwareUpdateChunk(String topic)
```

Firmware Update Chunk 이벤트에 대해 Result를 발행하는 함수입니다.

* Parameters
  * **topic** 발행할 토픽
* Retruns
  * N/A
