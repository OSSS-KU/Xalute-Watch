/*
 * Copyright 2022 Korea University(os.korea.ac.kr). All rights reserved.
 *
 * EcgActivity.java - Xalute (Digital Health Platform Project)
 *
 */

package com.example.xalute;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;

import com.example.xalute.databinding.ActivityEcgBinding;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.ValueKey;

import com.samsung.android.service.health.tracking.data.ValueKey.SpO2Set;
import com.samsung.android.service.health.tracking.data.ValueKey.HeartRateSet;
import com.samsung.android.service.health.tracking.data.ValueKey.SkinTemperatureSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class EcgActivity extends FragmentActivity implements MessageClient.OnMessageReceivedListener {

    private final String TAG = EcgActivity.class.getSimpleName();
    private ActivityEcgBinding binding;
    private HealthTrackingService healthTrackingService = null;
    @NotNull
    private final String[] permissions = {"android.permission.BODY_SENSORS"};
    private final int REQUEST_ACCOUNT_PERMISSION = 100;
    private boolean isHandlerRunning;
    private final Handler handler = new Handler(Looper.myLooper());
    private HealthTracker ecgTracker = null;
    private HealthTracker spo2Tracker = null;
    private HealthTracker heartRateTracker = null;
    private HealthTracker skinTempTracker = null;

    private List<Integer> spo2DataList = new ArrayList<>();
    private List<Integer> heartRateDataList = new ArrayList<>();
    private List<Float> skinTempDataList = new ArrayList<>();

    private boolean isTimerRunning = false;
    private boolean isFirst;

    private int ecgContactState;
    public static final int ECG_NOT_CONTACTED = 0;
    public static final int ECG_CONTACTED = 1;

    private List<EcgData> ecgDataList = new ArrayList<>();
    CountDownTimer timer;
    private int leadOffCount = 0;
    private final int leadOffThreshold = 500;
    private final int NO_CONTACT = 5;

    private boolean hasNavigated = false;
    private static final double RR_THRESHOLD = 0.31;

    private long previousBatchTime = 0;

    private int total = 0;

    private long lastUiUpdateTime = 0;

    private static final String GET_TOKEN_PATH = "/get-token";
    private static final String TOKEN_RESPONSE_PATH = "/token-response";
    private static final long TOKEN_TIMEOUT_MS = 5000;
    private Runnable pendingSendAction = null;
    private final Handler tokenTimeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String token = prefs.getString("token", "");
        String name = prefs.getString("name", "");
        String birthDate = prefs.getString("birthDate", "");
        Log.d("확인용", "EcgActivity에서 읽은 이름: " + name + ", 생일: " + birthDate + ", token:" + token);

        ecgContactState = ECG_NOT_CONTACTED;

        if (PermissionActivity.checkPermission(this, this.permissions)) {
            Log.i(TAG, "onCreate Permission granted");
            setUp();

            binding.btnSend.setOnClickListener(view -> {
                Log.d(TAG, "[Send] 버튼 클릭됨. 토큰 갱신 요청 시작");
                requestTokenFromPhone(this::performSend);
            });


        } else {
            Log.i(TAG, "onCreate Permission not granted");
            PermissionActivity.showPermissionPrompt(this, this.REQUEST_ACCOUNT_PERMISSION, this.permissions);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(this).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(this).removeListener(this);
        tokenTimeoutHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());

        if (TOKEN_RESPONSE_PATH.equals(messageEvent.getPath())) {
            String data = new String(messageEvent.getData());
            Log.d(TAG, "토큰 응답 수신: " + data);

            try {
                String newToken;
                try {
                    JSONObject json = new JSONObject(data);
                    newToken = json.getString("token");
                } catch (Exception e) {
                    newToken = data.trim();
                }

                SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                prefs.edit().putString("token", newToken).apply();
                Log.d(TAG, "토큰 갱신 완료");
            } catch (Exception e) {
                Log.e(TAG, "토큰 파싱 오류", e);
            }

            tokenTimeoutHandler.removeCallbacksAndMessages(null);

            if (pendingSendAction != null) {
                Runnable action = pendingSendAction;
                pendingSendAction = null;
                new Handler(Looper.getMainLooper()).post(action);
            }
        }
    }

    private void requestTokenFromPhone(Runnable onComplete) {
        pendingSendAction = onComplete;

        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes.isEmpty()) {
                Log.w(TAG, "연결된 폰이 없음, 기존 토큰으로 진행");
                pendingSendAction = null;
                new Handler(Looper.getMainLooper()).post(onComplete);
                return;
            }

            Node phoneNode = nodes.get(0);
            Wearable.getMessageClient(this).sendMessage(phoneNode.getId(), GET_TOKEN_PATH, null)
                    .addOnSuccessListener(i -> {
                        Log.d(TAG, "/get-token 전송 성공, 응답 대기 중...");
                        tokenTimeoutHandler.postDelayed(() -> {
                            if (pendingSendAction != null) {
                                Log.w(TAG, "토큰 응답 타임아웃, 기존 토큰으로 진행");
                                pendingSendAction = null;
                                onComplete.run();
                            }
                        }, TOKEN_TIMEOUT_MS);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "/get-token 전송 실패, 기존 토큰으로 진행", e);
                        pendingSendAction = null;
                        new Handler(Looper.getMainLooper()).post(onComplete);
                    });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "노드 조회 실패, 기존 토큰으로 진행", e);
            pendingSendAction = null;
            new Handler(Looper.getMainLooper()).post(onComplete);
        });
    }

    private void performSend() {
        Log.d(TAG, "[performSend] 시작. ecgDataList 크기=" + ecgDataList.size());

        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        final String token = prefs.getString("token", "");

        File rawFile = saveRawEcgDataToFile();
        if (rawFile != null) {
            Log.d(TAG, "✅ 원본 ECG 데이터가 저장되었습니다: " + rawFile.getAbsolutePath());
        } else {
            Log.e(TAG, "❌ 원본 ECG 데이터 저장 실패");
        }

        if (ecgDataList.isEmpty()) {
            Toast.makeText(getApplicationContext(), "❗ ECG 데이터가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String server1Url = "http://35.216.60.242:9101/mutation/addEcgData";

        final String currentTime = getCurrentTime();

        final String requestBody = EcgDataConverter.convertEcgDataListToServerBodyJson(
                getApplicationContext(),
                ecgDataList,
                currentTime,
                500.0
        );

        Log.d(TAG, "[performSend] 전송 URL: " + server1Url);

        if (requestBody == null) {
            Toast.makeText(getApplicationContext(), "❌ 요청 바디 생성 실패", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog();
        sendVitalSigns(token, currentTime);

        new Handler(Looper.getMainLooper()).post(() -> {

            final String userAgent = "android";

            EcgAddDataSender sender = new EcgAddDataSender();
            sender.postAddEcgData(token, server1Url, userAgent, requestBody, new EcgAddDataSender.Listener() {
                @Override
                public void onSuccess(String responseBody) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "✅ 서버1 응답 수신: " + responseBody);

                        File ecgFile = saveEcgDataToFile(ecgDataList);
                        if (ecgFile != null) {
                            sendFileToPhone(ecgFile, "", responseBody);
                        } else {
                            Log.e(TAG, "❌ ECG 파일 저장 실패");
                        }

                        dismissProgressDialog();
                        Toast.makeText(getApplicationContext(), "✅ 전송 완료", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(EcgActivity.this, EcgInfoActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onFailure(String errorMsg) {
                    runOnUiThread(() -> {
                        dismissProgressDialog();
                        Log.e(TAG, "❌ 서버1 전송 실패");
                        Log.e(TAG, "  URL: " + server1Url);
                        Log.e(TAG, "  Token: " + token);
                        Log.e(TAG, "  ErrorMsg: " + errorMsg);
                        Toast.makeText(getApplicationContext(), "❌ 서버 전송 실패: " + errorMsg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void StartCountTimer() {
        if ( ecgTracker != null ) {

            timer = new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                        if ( ecgContactState == ECG_CONTACTED )
                        binding.ecgSecond.setText("남은 시간: " + millisUntilFinished/1000);
                    else {
                        binding.ecgSecond.setText(String.valueOf(30));
                        if ( ecgContactState == ECG_CONTACTED ) {
                            StopCountTimer();
                            StopTrackerListner();
                        }
                    }
                }

                @Override
                public void onFinish() {
                    if(!hasNavigated){
                        Log.d("ECGActivity", "onFinish called");

                        OnMessage("ECG Measurement Ended...!!");
                        Toast.makeText(getApplicationContext(), "측정완료! SEND버튼을 눌러 전송하세요!", Toast.LENGTH_LONG).show();
                        StopTrackerListner();
                    }
                    total = 0;
                }
            }.start();

            isTimerRunning = true;
        }
    }
    private CountDownTimer countDownTimer;
    private long lastToastTime = 0;
    private void StopCountTimer() {
        try {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
                Log.d("EcgActivity", "⏹ CountDownTimer stopped safely.");
            } else {
                Log.w("EcgActivity", "⚠️ StopCountTimer called but countDownTimer is null.");
            }
        } catch (Exception e) {
            Log.e("EcgActivity", "❌ Error while stopping CountDownTimer: " + e.getMessage());
        }
    }

    private void ECGMeasurementError() {
        Log.i("EcgActivity", "ECGMeasurementError detected");

        long now = System.currentTimeMillis();
        if (now - lastToastTime > 3000) {
            Toast.makeText(this, "ECG 측정 중 연결이 불안정합니다.", Toast.LENGTH_SHORT).show();
            lastToastTime = now;
        }
        StopCountTimer();
    }

    private void StartTrackerListner() {
        Log.i(TAG, " StartListner: setEventListener called ");
        if(!isHandlerRunning) {
            handler.post(() -> {
                ecgTracker.setEventListener(trackerEventListener);
                isHandlerRunning = true;
            });
        }
    }

    private void StopTrackerListner() {
        Log.i(TAG, " StopListner: unsetEventListener called ");
        if (ecgTracker != null) {
            ecgTracker.unsetEventListener();
        }
        if (spo2Tracker != null) {
            spo2Tracker.unsetEventListener();
        }
        if (heartRateTracker != null) {
            heartRateTracker.unsetEventListener();
        }
        if (skinTempTracker != null) {
            skinTempTracker.unsetEventListener();
        }
        handler.removeCallbacksAndMessages(null);
        isHandlerRunning = false;

        if(healthTrackingService != null) {
            healthTrackingService.disconnectService();
        }
    }


    private final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onConnectionSuccess() {
            Toast.makeText(
                    getApplicationContext(),"Connected to HSP",Toast.LENGTH_SHORT
            ).show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                ecgTracker = healthTrackingService.getHealthTracker(HealthTrackerType.ECG_ON_DEMAND);
                if (ecgTracker != null) {
                    StartTrackerListner();
                }

                spo2Tracker = healthTrackingService.getHealthTracker(HealthTrackerType.SPO2);
                if (spo2Tracker != null) {
                    spo2Tracker.setEventListener(spo2EventListener);
                    Log.i(TAG, "SpO2 tracker started");
                }

                heartRateTracker = healthTrackingService.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS);
                if (heartRateTracker != null) {
                    heartRateTracker.setEventListener(heartRateEventListener);
                    Log.i(TAG, "HeartRate tracker started");
                }

                try {
                    skinTempTracker = healthTrackingService.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE);
                    if (skinTempTracker != null) {
                        skinTempTracker.setEventListener(skinTempEventListener);
                        Log.i(TAG, "SkinTemperature tracker started");
                    }
                } catch (UnsupportedOperationException e) {
                    Log.w(TAG, "SkinTemperature not supported on this device, skipping.");
                }

            } catch (final IllegalArgumentException e) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                finish();
                }
            }, 1001);
        }

        @Override
        public void onConnectionEnded() {
            Log.i(TAG, "onConnectionEnded()");
        }

        @Override
        public void onConnectionFailed(HealthTrackerException e) {
            if(e.hasResolution()) {
                e.resolve(EcgActivity.this);
            }
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Unable to connect to HSP", Toast.LENGTH_LONG).show()
            );
            finish();
        }
    };


    public void addEcgData(float ecgValue, long timestamp) {
        EcgData newEcgData = new EcgData(ecgValue, timestamp);
        ecgDataList.add(newEcgData);
    }

    private final HealthTracker.TrackerEventListener trackerEventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            int lth = list.size();
            total += lth;

            if (!list.isEmpty()) {


                for (int i = 0; i < list.size(); i++) {
                    DataPoint dp = list.get(i);

                    long baseTimestamp = dp.getTimestamp();
                    long correctedTimestamp = baseTimestamp + (long)(i * 2);

                    float ecgObj = dp.getValue(ValueKey.EcgSet.ECG_MV);
                    float ecgVal = ecgObj;

                    //워치 자원 과부하로 로그는 주석 처리
                    //Log.i(TAG, "Timestamp : " + correctedTimestamp);
                    //Log.i(TAG, "ECG value : " + ecgVal);

                    addEcgData(ecgVal, correctedTimestamp);
                }

                long currentTime = System.currentTimeMillis();
                //업데이트 주기는 125로 임의 설정
                if (currentTime - lastUiUpdateTime > 125) {
                    lastUiUpdateTime = currentTime;
                    runOnUiThread(() -> {
                        int leadOff = list.get(0).getValue(ValueKey.EcgSet.LEAD_OFF);
                        float sampleEcgObj = list.get(0).getValue(ValueKey.EcgSet.ECG_MV);
                        float sampleEcg = sampleEcgObj;

                        if (leadOff == 0) {
                            leadOffCount = 0;
                            ecgContactState = ECG_CONTACTED;
                            if (!isTimerRunning) StartCountTimer();

                            binding.ecgAverage.setText(String.valueOf(sampleEcg));
                            binding.ecg1DataValue.setText(String.valueOf(sampleEcg));
                            binding.leadOffDataValue.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));

                        } else if (leadOff == NO_CONTACT) {// NO_CONTACT == 5 by SDK configuration
                            leadOffCount++;
                            if (leadOffCount >= leadOffThreshold) {
                                //Initial value of leadOffThreshold == 1000

                                ecgContactState = ECG_NOT_CONTACTED;
                                binding.leadOffDataValue.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                                ECGMeasurementError();

                                //Convert to EcgInfoActivity
                                if (!hasNavigated) {
                                    hasNavigated = true;
                                    Intent intent = new Intent(EcgActivity.this, EcgInfoActivity.class);
                                    startActivity(intent);
                                    finish();
                                }
                            } else {
                                binding.leadOffDataValue.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gray));
                            }
                        }


                        binding.leadOffDataValue.setText(String.valueOf(leadOffCount));
                        binding.sequenceValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.SEQUENCE)));

                        if (list.size() >= 6) {
                            int avgGreen = (list.get(0).getValue(ValueKey.EcgSet.PPG_GREEN) + list.get(5).getValue(ValueKey.EcgSet.PPG_GREEN)) / 2;
                            binding.ecgGreenDataValue.setText(String.valueOf(avgGreen));
                        } else {
                            binding.ecgGreenDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.PPG_GREEN)));
                        }

                        binding.thresholdMaxDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.MAX_THRESHOLD_MV)));
                        binding.thresholdMinDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.MIN_THRESHOLD_MV)));
                    });
                }
            }
        }

        @Override
        public void onFlushCompleted() {
            Log.i(TAG, "✅ onFlushCompleted called");
        }

        @Override
        public void onError(HealthTracker.TrackerError trackerError) {
            Log.i(TAG, "❌ onError called");

            runOnUiThread(() -> {
                if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                    Toast.makeText(getApplicationContext(), "Permissions Check Failed", Toast.LENGTH_SHORT).show();
                } else if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                    Toast.makeText(getApplicationContext(), "SDK Policy denied", Toast.LENGTH_SHORT).show();
                }
            });

            isHandlerRunning = false;
        }
    };

    private final HealthTracker.TrackerEventListener spo2EventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            for (DataPoint dp : list) {
                spo2DataList.add(dp.getValue(SpO2Set.SPO2));
            }
        }
        @Override public void onFlushCompleted() {}
        @Override public void onError(HealthTracker.TrackerError e) {
            Log.e(TAG, "SpO2 tracker error: " + e);
        }
    };

    private final HealthTracker.TrackerEventListener heartRateEventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            for (DataPoint dp : list) {
                heartRateDataList.add(dp.getValue(HeartRateSet.HEART_RATE));
            }
        }
        @Override public void onFlushCompleted() {}
        @Override public void onError(HealthTracker.TrackerError e) {
            Log.e(TAG, "HeartRate tracker error: " + e);
        }
    };

    private final HealthTracker.TrackerEventListener skinTempEventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            for (DataPoint dp : list) {
                skinTempDataList.add(dp.getValue(SkinTemperatureSet.OBJECT_TEMPERATURE));
            }
        }
        @Override public void onFlushCompleted() {}
        @Override public void onError(HealthTracker.TrackerError e) {
            Log.e(TAG, "SkinTemperature tracker error: " + e);
        }
    };

    public final void setUp() {
        Log.i(TAG, "setUp");
        binding = DataBindingUtil.setContentView(this, R.layout.activity_ecg);
        healthTrackingService = new HealthTrackingService(connectionListener, getApplicationContext());
        healthTrackingService.connectService();
    }


    private void OnMessage(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult requestCode = " + requestCode + " resultCode = " + resultCode);
        if (requestCode == this.REQUEST_ACCOUNT_PERMISSION) {
            if (resultCode == -1) {
                setUp();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (ecgTracker != null) {
            ecgTracker.unsetEventListener();
            ecgTracker = null;
        }
        if (spo2Tracker != null) {
            spo2Tracker.unsetEventListener();
            spo2Tracker = null;
        }
        if (heartRateTracker != null) {
            heartRateTracker.unsetEventListener();
            heartRateTracker = null;
        }
        if (skinTempTracker != null) {
            skinTempTracker.unsetEventListener();
            skinTempTracker = null;
        }

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        handler.removeCallbacksAndMessages(null);
        isHandlerRunning = false;

        if (healthTrackingService != null) {
            healthTrackingService.disconnectService();
            healthTrackingService = null;
        }
    }

    private void sendVitalSigns(String token, String currentTime) {
        if (spo2DataList.isEmpty() && heartRateDataList.isEmpty() && skinTempDataList.isEmpty()) return;

        String body = buildVitalSignsJson(currentTime);
        if (body == null) return;

        final String url = "http://35.216.60.242:9101/mutation/addVitalSigns";
        EcgAddDataSender sender = new EcgAddDataSender();
        sender.postAddEcgData(token, url, "android", body, new EcgAddDataSender.Listener() {
            @Override
            public void onSuccess(String responseBody) {
                Log.d(TAG, "✅ Vital Signs 전송 성공");
            }
            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "❌ Vital Signs 전송 실패: " + errorMsg);
            }
        });
    }

    private String buildVitalSignsJson(String currentTime) {
        try {
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            String name = prefs.getString("name", "");
            String birthDate = prefs.getString("birthDate", "");

            JSONObject root = new JSONObject();
            JSONArray entry = new JSONArray();
            root.put("entry", entry);

            JSONObject entryItem = new JSONObject();
            entry.put(entryItem);

            JSONObject resource = new JSONObject();
            entryItem.put("resource", resource);

            JSONObject subject = new JSONObject();
            subject.put("reference", "Patient/" + name + ":" + birthDate);
            resource.put("subject", subject);
            resource.put("effectiveDateTime", currentTime);

            JSONArray component = new JSONArray();
            resource.put("component", component);

            if (!spo2DataList.isEmpty()) {
                JSONArray vals = new JSONArray();
                for (int v : spo2DataList) vals.put(v);
                JSONObject comp = new JSONObject();
                comp.put("code", "SpO2");
                comp.put("unit", "%");
                comp.put("values", vals);
                component.put(comp);
            }

            if (!heartRateDataList.isEmpty()) {
                JSONArray vals = new JSONArray();
                for (int v : heartRateDataList) vals.put(v);
                JSONObject comp = new JSONObject();
                comp.put("code", "HeartRate");
                comp.put("unit", "bpm");
                comp.put("values", vals);
                component.put(comp);
            }

            if (!skinTempDataList.isEmpty()) {
                JSONArray vals = new JSONArray();
                for (float v : skinTempDataList) vals.put(v);
                JSONObject comp = new JSONObject();
                comp.put("code", "SkinTemperature");
                comp.put("unit", "°C");
                comp.put("values", vals);
                component.put(comp);
            }

            return root.toString();
        } catch (JSONException e) {
            Log.e(TAG, "❌ Vital Signs JSON 생성 오류", e);
            return null;
        }
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    interface ApiService {
        @Multipart
        @POST("/predict_single_lead")
        Call<ResponseBody> uploadEcgFile(@Part MultipartBody.Part file);
    }

    private void uploadEcgFileToServer(File file) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "파일이 존재하지 않습니다.");
            Toast.makeText(this, "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "📤 전송할 ECG 파일 경로: " + file.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            Log.d(TAG, "📤 전송할 ECG 파일 내용:\n" + fileContent.toString().trim());
        } catch (IOException e) {
            Log.e(TAG, "❌ ECG 파일 내용 읽기 오류", e);
        }

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://35.216.60.242:9102")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);

        Call<ResponseBody> call = apiService.uploadEcgFile(filePart);
        Log.d(TAG, ">>>> 업로드 요청을 보낼 URL: " + call.request().url().toString());
        Log.d(TAG, ">>>> Retrofit Base URL: " + retrofit.baseUrl());
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                dismissProgressDialog();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String resultJson = response.body().string();
                        Log.d(TAG, "서버 응답: " + resultJson);

                        JSONObject root = new JSONObject(resultJson);
                        String finalFlag = calcAbnormalFlag(root);

                        showServerResponse(finalFlag);
                        sendFileToPhone(file, finalFlag, resultJson);
                    } catch (Exception e) {
                        Log.e(TAG, "응답 처리 중 오류 발생", e);
                    }
                } else {
                    Log.e(TAG, "서버 오류: " + response.code());
                    if (response.errorBody() != null) {
                        try {
                            String error = response.errorBody().string();
                            Log.e(TAG, "❌ 서버 응답 내용(errorBody): " + error);
                        } catch (IOException e) {
                            Log.e(TAG, "❌ 서버 오류 본문 읽기 실패", e);
                        }
                    } else {
                        Log.e(TAG, "⚠️ 서버 errorBody가 null입니다.");
                    }

                    Toast.makeText(EcgActivity.this, "서버 오류: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                dismissProgressDialog();
                Log.e(TAG, "네트워크 오류 발생: " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);
                Toast.makeText(EcgActivity.this, "네트워크 오류: " + t.getClass().getSimpleName() + "\n" + t.getMessage(), Toast.LENGTH_LONG).show();
            }

        });
    }

    private File saveEcgDataToFile(List<EcgData> dataToSave) {
        File dir = new File(getExternalFilesDir(null), "ecg_data");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "ecg_" + System.currentTimeMillis() + ".txt");

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();
            int lth;
            double intervalSec = 1.0 / 500.0;

            if(dataToSave.size() >= 15100) {
                lth = 15100;
            }else{
                lth = dataToSave.size();
            }
            for (int i = 0; i < lth; i++){
                EcgData d = dataToSave.get(i);

                // 1. 내용은 '바뀐 대로': 실제 타임스탬프와 원본 ECG 값 사용
                sb.append("(")
                        .append(d.getEcgValue()).append(", ")
                        .append(i*intervalSec)
                        .append(") ");
            }

            writer.write(sb.toString().trim());
            writer.flush();
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private File saveRawEcgDataToFile() {
        File dir = new File(getExternalFilesDir(null), "ecg_raw_data_cut6");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "raw_ecg_" + System.currentTimeMillis() + ".txt");

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();

            List<EcgData> dataToSave = ecgDataList; // 6초 제거 알고리즘 삭제

            for (EcgData data : dataToSave) {
                sb.append("(")
                        .append(data.getEcgValue()).append(", ")
                        .append(data.getTimestamp())
                        .append(") ");
            }

            writer.write(sb.toString());
            writer.flush();
            Log.d(TAG, "✅ Raw ECG data saved to " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "❌ Raw ECG 파일 저장 오류", e);
            return null;
        }
    }

    private void showServerResponse(String result) {
        runOnUiThread(() -> {
            try {
                String translatedStatus = result.equals("normal") ? "정상" :
                        result.equals("abnormal") ? "의상 소견 의심" : "알 수 없음";

                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("분석 결과")
                        .setMessage("분석 결과: " + translatedStatus)
                        .setPositiveButton("확인", (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(EcgActivity.this, EcgInfoActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "❌ 결과 다이얼로그 처리 중 오류", e);
                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("오류")
                        .setMessage("결과를 분석할 수 없습니다.")
                        .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private AlertDialog progressDialog;

    private void showProgressDialog() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.activity_data_sending, null);
        progressDialog = new AlertDialog.Builder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();

        Window window = progressDialog.getWindow();
        if (window != null) {
            // 1. 다이얼로그 자체 배경은 투명하게 (커스텀 레이아웃만 보이게)
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 2. 바탕을 어둡게 만드는 설정 추가
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();

            // 어두운 정도 (0.0: 투명 ~ 1.0: 완전 검정). 0.5f ~ 0.7f 정도가 적당합니다.
            params.dimAmount = 0.6f;

            // 3. 스크롤 방지를 위해 창 크기를 화면에 꽉 채움
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;

            // 4. 내용물을 하단 중앙으로 배치
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

            // 5. 바닥에서의 여백
            params.y = (int) (120 * getResources().getDisplayMetrics().density);

            window.setAttributes(params);
        }
    }
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void sendFileToPhone(File file, String result, String resultJson) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "파일이 존재하지 않음");
            return;
        }

        Asset asset = createAssetFromFile(file);
        if (asset == null) {
            Log.e(TAG, "Asset 변환 실패");
            return;
        }

        long epochMillis = System.currentTimeMillis();

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/ecg_file");
        putDataMapRequest.getDataMap().putAsset("ecg_data", asset);
        putDataMapRequest.getDataMap().putLong("timestamp", epochMillis);
        putDataMapRequest.getDataMap().putString("result", result);
        putDataMapRequest.getDataMap().putString("result_json", resultJson);

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();

        Wearable.getDataClient(this).putDataItem(putDataRequest)
                .addOnSuccessListener(dataItem -> Log.d(TAG, "✅ ECG 파일 + 결과 전송 성공!"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ 전송 실패", e));
    }

    private Asset createAssetFromFile(File file) {
        try (InputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteStream.write(buffer, 0, bytesRead);
            }
            return Asset.createFromBytes(byteStream.toByteArray());
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일을 Asset으로 변환하는 중 오류 발생", e);
            return null;
        }
    }

    private String calcAbnormalFlag(JSONObject root) {
        try {
            if (!root.has("result")) return "normal";

            JSONObject resultObj = root.getJSONObject("result");
            if (!resultObj.has("distance_from_median")) return "normal";

            JSONArray distArr = resultObj.getJSONArray("distance_from_median");
            for (int i = 0; i < distArr.length(); i++) {
                if (distArr.getDouble(i) > RR_THRESHOLD) {
                    return "abnormal";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "distance_from_median 판독 오류", e);
        }
        return "normal";
    }

    private List<EcgData> parseEcgListFromAddEcgResponse(String responseJson) throws Exception {
        JSONObject root = new JSONObject(responseJson);

        List<EcgData> list = new ArrayList<>();
        if (!root.has("data")) return list;

        JSONArray data = root.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONArray pair = data.getJSONArray(i);
            double v = pair.getDouble(0);
            long ts = pair.getLong(1);
            list.add(new EcgData((float) v, ts));
        }
        return list;
    }

}


/*
*     private File saveRawEcgDataToFile() {
        File dir = new File(getExternalFilesDir(null), "ecg_raw_data_cut6");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "raw_ecg_" + System.currentTimeMillis() + ".txt");

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();

            List<EcgData> dataToSave = ecgDataList; // 6초 제거 알고리즘 삭제

            for (EcgData data : dataToSave) {
                sb.append("(")
                        .append(data.getEcgValue()).append(", ")
                        .append(data.getTimestamp())
                        .append(") ");
            }

            writer.write(sb.toString());
            writer.flush();
            Log.d(TAG, "✅ Raw ECG data saved to " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "❌ Raw ECG 파일 저장 오류", e);
            return null;
        }
    }

    private void showServerResponse(String result) {
        runOnUiThread(() -> {
            try {
                String translatedStatus = result.equals("normal") ? "정상" :
                        result.equals("abnormal") ? "의상 소견 의심" : "알 수 없음";

                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("분석 결과")
                        .setMessage("분석 결과: " + translatedStatus)
                        .setPositiveButton("확인", (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(EcgActivity.this, EcgInfoActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "❌ 결과 다이얼로그 처리 중 오류", e);
                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("오류")
                        .setMessage("결과를 분석할 수 없습니다.")
                        .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private AlertDialog progressDialog;
 */