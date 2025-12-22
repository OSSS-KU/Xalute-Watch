package com.example.xalute;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

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
}