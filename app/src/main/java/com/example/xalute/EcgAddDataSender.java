package com.example.xalute;

import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class EcgAddDataSender {

    public interface Listener {
        void onSuccess(String responseBody);
        void onFailure(String errorMsg);
    }

    private final OkHttpClient client = new OkHttpClient();

    public void postAddEcgData(
            String token,
            String url,
            String userAgent,
            String jsonBody,
            Listener listener
    ) {
        try {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);
                    Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", userAgent)
                    .addHeader("authorization", String.format("Bearer %s", token))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    listener.onFailure("network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resp = (response.body() != null) ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.e("EcgAddDataSender", "HTTP " + response.code() + " resp=" + resp);
                        listener.onFailure("http " + response.code() + " : " + resp);
                        return;
                    }
                    listener.onSuccess(resp);
                }
            });

        } catch (Exception e) {
            listener.onFailure("exception: " + e.getMessage());
        }
    }
}
