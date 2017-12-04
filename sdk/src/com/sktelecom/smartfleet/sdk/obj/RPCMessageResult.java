package com.sktelecom.smartfleet.sdk.obj;

import com.google.gson.Gson;
import com.sktelecom.smartfleet.sdk.net.RPCType;
import com.sktelecom.smartfleet.sdk.obj.payload.Trip;
import com.sktelecom.smartfleet.sdk.obj.request.FirmwareUpdate;
import com.sktelecom.smartfleet.sdk.obj.result.DeviceActivation;
import com.sktelecom.smartfleet.sdk.obj.result.DeviceSerialNumberCheck;
import com.sktelecom.smartfleet.sdk.util.LogWrapper;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import static com.sktelecom.smartfleet.sdk.define.CODES.SUCCESS_RESULT;

public class RPCMessageResult {

    private final Logger logger = Logger.getLogger(RPCMessageResult.class);
    String rst;
    String aif;

    public RPCMessageResult() {
    }

    public JSONObject messagePackage(int ty, Object obj) {

        Gson gson = new Gson();

        JSONObject message = new JSONObject();

        try {

            message.put("result", SUCCESS_RESULT);

            if (ty == RPCType.DEVICE_ACTIVATION.ordinal()) {
                message.put("aif", new JSONObject(gson.toJson((DeviceActivation) obj)));
            } else if (ty == RPCType.FIRMWARE_UPDATE.ordinal()) {
            } else if (ty == RPCType.ODB_RESET.ordinal()) {
            } else if (ty == RPCType.DEVICE_SERIAL_NUMBER_CHECK.ordinal()) {
                message.put("aif", new JSONObject(gson.toJson((DeviceSerialNumberCheck) obj)));
            } else if (ty == RPCType.CLEAR_DEVICE_DATA.ordinal()) {
            } else if (ty == RPCType.FIRMWARE_UPDATE_CHUNK.ordinal()) {

            }

        } catch (Exception e) {

            logger.error("Unexpected JSON exception in message:::" + e.toString());

        }

        return message;

    }
}
