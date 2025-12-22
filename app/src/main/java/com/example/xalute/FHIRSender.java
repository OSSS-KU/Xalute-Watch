package com.example.xalute;
import android.os.AsyncTask;
import android.util.Log;
import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import androidx.annotation.NonNull;

public class FHIRSender extends AsyncTask<String, Void, Boolean> {
    private static final String TAG = FHIRSender.class.getSimpleName();

    private Context context;
    private FHIRSenderListener listener;

    public FHIRSender(Context context, FHIRSenderListener fhirSenderListener) {
        this.context = context;
        this.listener = fhirSenderListener;
    }

    public interface FHIRSenderListener {
        void onSendCompleted(boolean success);
    }

    @Override
    protected Boolean doInBackground(@NonNull String... params) {
        String fhirData = params[0];
        String serverUrl = params[1];

        try {
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            // 요청 본문에 FHIR 데이터 설정
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(fhirData.getBytes());
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            InputStream inputStream;
            if (responseCode >= 400) {
                // 오류 발생 시
                inputStream = connection.getErrorStream();
            } else {
                // 성공 시
                inputStream = connection.getInputStream();
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                response.append(line);
            }
            bufferedReader.close();

            Log.d(TAG, "Response message: " + response.toString());

            // 응답 코드가 200~299 사이이면 성공으로 간주
            return responseCode >= 200 && responseCode < 300;

        } catch (Exception e) {
            Log.e(TAG, "Error sending FHIR data: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        // 전송 완료 후 Listener를 통해 결과 전달
        if (listener != null) {
            listener.onSendCompleted(success);
        }
    }
}