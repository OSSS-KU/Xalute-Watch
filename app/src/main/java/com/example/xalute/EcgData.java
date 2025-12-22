package com.example.xalute;
import java.io.Serializable;


public class EcgData implements Serializable {
    private float ecgValue;
    private long timestamp;


    public EcgData(float ecgValue, long timestamp) {
        this.ecgValue = ecgValue;
        this.timestamp = timestamp;
    }

    public float getEcgValue() {
        return ecgValue;
    }

    public void setEcgValue(float ecgValue) {
        this.ecgValue = ecgValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}