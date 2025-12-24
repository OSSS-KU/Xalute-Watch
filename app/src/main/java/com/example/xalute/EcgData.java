package com.example.xalute;
import java.io.Serializable;


public class EcgData implements Serializable {
<<<<<<< HEAD
    private int ecgValue;
    private long timestamp;


    public EcgData(int ecgValue, long timestamp) {
=======
    private float ecgValue;
    private long timestamp;


    public EcgData(float ecgValue, long timestamp) {
>>>>>>> 7b28331cc21bdfa3b70cd5c8e55db3804a0bcf5a
        this.ecgValue = ecgValue;
        this.timestamp = timestamp;
    }

<<<<<<< HEAD
    public int getEcgValue() {
        return ecgValue;
    }

    public void setEcgValue(int ecgValue) {
=======
    public float getEcgValue() {
        return ecgValue;
    }

    public void setEcgValue(float ecgValue) {
>>>>>>> 7b28331cc21bdfa3b70cd5c8e55db3804a0bcf5a
        this.ecgValue = ecgValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}