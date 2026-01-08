package com.example.xalute;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageService extends Service implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "MessageService";
    private static final String START_APP_PATH = "/start-app";
    private static final String PREFS_NAME = "MyPrefs"; // SharedPreferences 이름

    @Override
    public void onCreate() {
        super.onCreate();
        Wearable.getMessageClient(this).addListener(this);
        Log.d(TAG, "MessageService started and listener added");
    }

    @Override
    public void onDestroy() {
        Wearable.getMessageClient(this).removeListener(this);
        super.onDestroy();
        Log.d(TAG, "MessageService stopped and listener removed");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());

        if (START_APP_PATH.equals(messageEvent.getPath())) {
            String message = new String(messageEvent.getData());
            Log.d(TAG, "Message received: " + message);

            try {
                JSONObject jsonObject = new JSONObject(message);
                String action = jsonObject.getString("action");
                String name = jsonObject.getString("name");
                String birthDate = jsonObject.getString("birthDate");
                String token = jsonObject.getString("token");

                // 저장 로직 추가
                saveNameAndBirthDateAndToken(name, birthDate, token);

                if ("launch_app".equals(action)) {
                    Log.d(TAG, "Launching the target app...");
                    // Launch the desired app with additional data
                    /**Intent intent = getPackageManager().getLaunchIntentForPackage("org.xalute_galaxy");
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("name", name);
                        intent.putExtra("birthDate", birthDate);
                        startActivity(intent);
                        Log.d(TAG, "Target app launched successfully");
                    } else {
                        Log.e(TAG, "Target app not found");
                    }**/
                    Intent intent = new Intent(this, EcgInfoActivity.class);
                    //Intent intent = new Intent(this, EcgActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("name", name);
                    intent.putExtra("birthDate", birthDate);
                    intent.putExtra("token", token);
                    startActivity(intent);
                    Log.d(TAG, "EcgActivity launched successfully");
                } else {
                    Log.d(TAG, "Unexpected action received");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse JSON message", e);
            }
        } else {
            Log.d(TAG, "Unexpected path received: " + messageEvent.getPath());
        }
    }

    private void saveNameAndBirthDateAndToken(String name, String birthDate, String token) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("name", name);
        editor.putString("birthDate", birthDate);
        editor.putString("token", token);
        editor.apply();
        Log.d(TAG, "Name and Birth Date saved to SharedPreferences");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
