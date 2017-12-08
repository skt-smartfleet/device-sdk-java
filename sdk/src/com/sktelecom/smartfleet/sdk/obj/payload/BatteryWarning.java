package com.sktelecom.smartfleet.sdk.obj.payload;

public class BatteryWarning {

    private int wbv;

    public BatteryWarning() {
    }

    public BatteryWarning(int wbv) {
        this.setWbv(wbv);
    }

    public void setDemoData(){
        this.setWbv(10);
    }

    @Override
    public String toString() {

        StringBuffer stringBuffer = new StringBuffer();

        stringBuffer.append("wbv="+ getWbv() +"\n");

        return stringBuffer.toString();
    }

    public int getWbv() {
        return wbv;
    }

    public void setWbv(int wbv) {
        this.wbv = wbv;
    }
}
