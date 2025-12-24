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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;

import com.example.xalute.databinding.ActivityEcgBinding;
import com.google.android.gms.wearable.Asset;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
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

public class EcgActivity extends FragmentActivity {

    private final String TAG = EcgActivity.class.getSimpleName();
    private ActivityEcgBinding binding;
    private HealthTrackingService healthTrackingService = null;
    @NotNull
    private final String[] permissions = {"android.permission.BODY_SENSORS"};
    private final int REQUEST_ACCOUNT_PERMISSION = 100;
    private boolean isHandlerRunning;
    private final Handler handler = new Handler(Looper.myLooper());
    private HealthTracker ecgTracker = null;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String name = prefs.getString("name", "");
        String birthDate = prefs.getString("birthDate", "");
        Log.d("확인용", "EcgActivity에서 읽은 이름: " + name + ", 생일: " + birthDate);

        ecgContactState = ECG_NOT_CONTACTED;

        if (PermissionActivity.checkPermission(this, this.permissions)) {
            Log.i(TAG, "onCreate Permission granted");
            setUp();

            binding.btnSend.setOnClickListener(view -> {
                Log.d(TAG, "[Send] 버튼 클릭됨. ecgDataList 크기=" + ecgDataList.size());

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

                String server1Url = "http://35.238.174.154:3000/mutation/addData";
                String currentTime = getCurrentTime();

                // 6초 제거 알고리즘 삭제
                String fhirData = EcgDataConverter.convertEcgDataListToJsonString(getApplicationContext(), ecgDataList, currentTime);

                Log.d(TAG, "[Send] 저장할 JSON: " + fhirData);

                showProgressDialog();

                new Handler(Looper.getMainLooper()).post(() -> {
                    FHIRSender sender = new FHIRSender(getApplicationContext(), new FHIRSender.FHIRSenderListener() {
                        @Override
                        public void onSendCompleted(boolean success) {
                            runOnUiThread(() -> {
                                if (success) {
                                    Log.d(TAG, "✅ 저장 성공");
                                    File ecgFile = saveEcgDataToFile();
                                    if (ecgFile != null) {
                                        uploadEcgFileToServer(ecgFile);
                                    } else {
                                        dismissProgressDialog();
                                        Toast.makeText(getApplicationContext(), "❌ ECG 파일 저장 실패", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    dismissProgressDialog();
                                    Log.e(TAG, "❌ 저장 실패");
                                    Toast.makeText(getApplicationContext(), "❌ FHIR 데이터 전송 실패", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                    sender.execute(fhirData, server1Url);
                });
            });

        } else {
            Log.i(TAG, "onCreate Permission not granted");
            PermissionActivity.showPermissionPrompt(this, this.REQUEST_ACCOUNT_PERMISSION, this.permissions);
        }
    }


    private void StartCountTimer() {
        if ( ecgTracker != null ) {

            timer = new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    Log.i(TAG, " StartCountTimer: onTick called - "+ millisUntilFinished/1000);
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
        Log.d(TAG, "✅ 저장된 ECG 데이터 개수: " + ecgDataList.size());
    }

    private final HealthTracker.TrackerEventListener trackerEventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            int lth = list.size();
            total += lth;
            String l = String.valueOf(total);
            Log.i(TAG, "✅ Total Count : " + l);

            if (!list.isEmpty()) {
                Log.i(TAG, "✅ List Size : " + list.size());

                Log.i(TAG, "Batch size: " + list.size() +
                        ", First TS: " + list.get(0).getTimestamp() +
                        ", Last TS: " + list.get(list.size()-1).getTimestamp());


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
                                Log.w(TAG, "⚠️ LeadOff 감지됨, 무시하고 측정 유지 중 (" + leadOffCount + "/" + leadOffThreshold + ")");
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
            } else {
                Log.i(TAG, "⚠️ onDataReceived List is zero");
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

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://34.69.44.173:7001")
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
                Log.e(TAG, "네트워크 오류 발생", t);
                Toast.makeText(EcgActivity.this, "네트워크 오류 발생", Toast.LENGTH_LONG).show();
            }

        });
    }

    private File saveEcgDataToFile() {
        File dir = new File(getExternalFilesDir(null), "ecg_data");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "ecg_" + System.currentTimeMillis() + ".txt");

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();

            List<EcgData> dataToSave = ecgDataList; // 6초 제거 알고리즘 삭제
            int sampleCount = dataToSave.size();
            double intervalSec = 1.0 / 512.0;

            for (int i = 0; i < sampleCount; i++) {
                EcgData data = dataToSave.get(i);
                double syntheticTs = i * intervalSec;

                sb.append("(")
                        .append(data.getEcgValue()).append(", ")
                        .append(syntheticTs)
                        .append(") ");
            }

            writer.write(sb.toString().trim());
            writer.flush();
            Log.d(TAG, "✅ ECG data saved to " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일 저장 중 오류", e);
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
        LayoutInflater inflater = LayoutInflater.from(EcgActivity.this);
        View progressView = inflater.inflate(R.layout.activity_data_sending, null);

        progressDialog = new AlertDialog.Builder(EcgActivity.this)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();
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

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            Log.d(TAG, "📤 워치에서 전송할 ECG 파일 내용:\n" + fileContent.toString().trim());
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일 읽기 오류", e);
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
}