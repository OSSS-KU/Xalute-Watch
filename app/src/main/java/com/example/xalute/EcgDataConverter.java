package com.example.xalute;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;


public class EcgDataConverter {
    public static String convertEcgDataListToServerBodyJson(
            Context context,
            List<EcgData> ecgDataList,
            String currentTime,
            double fallbackSamplingRateHz
    ) {
        try {
            JSONObject root = new JSONObject();

            JSONArray entry = new JSONArray();
            root.put("entry", entry);

            JSONObject entry0 = new JSONObject();
            entry.put(entry0);

            JSONObject resource = new JSONObject();
            entry0.put("resource", resource);

            SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            String name = sharedPreferences.getString("name", "");
            String birthDate = sharedPreferences.getString("birthDate", "");
            String subjectRef = "Patient/" + name + ":" + birthDate + " " + currentTime;

            JSONObject subject = new JSONObject();
            subject.put("reference", subjectRef);
            resource.put("subject", subject);

            JSONArray component = new JSONArray();
            resource.put("component", component);

            JSONObject comp0 = new JSONObject();
            component.put(comp0);

            JSONObject vsd = new JSONObject();
            comp0.put("valueSampledData", vsd);

            JSONObject origin = new JSONObject();
            origin.put("value", 55);
            vsd.put("origin", origin);

            vsd.put("dimension", 2);

            long periodMs = estimatePeriodMillis(ecgDataList, fallbackSamplingRateHz);
            vsd.put("period", periodMs);

            JSONArray dataArr = new JSONArray();
            for (EcgData d : ecgDataList) {
                JSONArray pair = new JSONArray();
                pair.put((double) d.getEcgValue());
                pair.put(d.getTimestamp());
                dataArr.put(pair);
            }
            vsd.put("data", dataArr);

            String finalJson = root.toString();

            int maxLength = 3000;
            for (int i = 0; i < finalJson.length(); i += maxLength) {
                int end = Math.min(finalJson.length(), i + maxLength);
                android.util.Log.d("ECG_ADD_ECGDATA_JSON", finalJson.substring(i, end));
            }

            return finalJson;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static long estimatePeriodMillis(List<EcgData> list, double fallbackFsHz) {
        try {
            if (list == null || list.size() < 3) {
                return Math.max(1L, Math.round(1000.0 / fallbackFsHz));
            }

            int n = Math.min(list.size() - 1, 2000);
            long[] diffs = new long[n];
            for (int i = 0; i < n; i++) {
                diffs[i] = list.get(i + 1).getTimestamp() - list.get(i).getTimestamp();
            }
            java.util.Arrays.sort(diffs);
            long median = diffs[n / 2];

            if (median <= 0 || median > 1000) {
                return Math.max(1L, Math.round(1000.0 / fallbackFsHz));
            }
            return median;
        } catch (Exception e) {
            return Math.max(1L, Math.round(1000.0 / fallbackFsHz));
        }
    }
}
/**
public class EcgDataConverter {

    public static String convertEcgDataListToJsonString(Context context, List<EcgData> ecgDataList, String currentTime) {
        try {

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "batch");
            jsonObject.put("resourceType", "Bundle");

            JSONArray entryArray = new JSONArray();
            JSONObject firstEntry = new JSONObject();
            entryArray.put(firstEntry);
            jsonObject.put("entry", entryArray);

            firstEntry.put("request", new JSONObject().put("url", "Observation").put("method", "POST"));

            JSONObject resource = new JSONObject();
            firstEntry.put("resource", resource);

            resource.put("resourceType", "Observation");
            resource.put("id", "lsk");

            JSONArray componentArray = new JSONArray();
            JSONObject firstComponent = new JSONObject();
            componentArray.put(firstComponent);
            resource.put("component", componentArray);

            JSONObject code = new JSONObject();
            JSONArray codingArray = new JSONArray();
            codingArray.put(new JSONObject().put("display", "MDC_ECG_ELEC_POTL_I"));
            codingArray.put(new JSONObject().put("code", "mV").put("display", "microvolt").put("system", "http:"));
            code.put("coding", codingArray);
            firstComponent.put("code", code);

            JSONObject valueSampledData = new JSONObject();
            valueSampledData.put("origin", new JSONObject().put("value", 55));
            valueSampledData.put("period", 29.998046875);

            StringBuilder ecgDataStringBuilder = new StringBuilder();
            for (int i = 0; i < ecgDataList.size(); i++) {
                EcgData ecgData = ecgDataList.get(i);
                ecgDataStringBuilder.append("(")
                        .append(ecgData.getEcgValue())
                        .append(", ")
                        .append(ecgData.getTimestamp())
                        .append(")");
                if (i < ecgDataList.size() - 1) {
                    ecgDataStringBuilder.append(" ");
                }
            }
            String ecgDataString = ecgDataStringBuilder.toString();

            valueSampledData.put("data", ecgDataString);
            valueSampledData.put("dimensions", 2);
            firstComponent.put("valueSampledData", valueSampledData);

            SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            String name = sharedPreferences.getString("name", "");
            String birthDate = sharedPreferences.getString("birthDate", "");
            //String birthDate = "";
            // subject 설정 필요. 이름 + 생년월일 입력 받기, 측정 시간 확인해서 넣기.
            resource.put("subject", new JSONObject().put("reference", "Patient/"+name + ": "  + birthDate +" " + currentTime));
            resource.put("status", "final");
            resource.put("code", new JSONObject());

            String finalJson = jsonObject.toString();

            int maxLength = 3000;
            for (int i = 0; i < finalJson.length(); i += maxLength) {
                int end = Math.min(finalJson.length(), i + maxLength);
                android.util.Log.d("FHIR_JSON", finalJson.substring(i, end));
            }

            return finalJson;

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}**/