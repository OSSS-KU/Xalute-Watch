package com.example.xalute;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.example.xalute.R;

public class DataSendingActivity extends FragmentActivity {

    private ProgressBar progressBar;
    private Context context;
    private String fhirData;
    private String serverUrl;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // activity_data_sending.xml 레이아웃 설정
        setContentView(R.layout.activity_data_sending);

        // ProgressBar 초기화
        progressBar = findViewById(R.id.progressBar);

        context = this;

        // Intent로부터 데이터 받기
        Intent intent = getIntent();
        fhirData = intent.getStringExtra("fhirData");
        serverUrl = intent.getStringExtra("serverUrl");

        // 데이터 전송 시작
        sendData();
    }

    private void sendData() {
        FHIRSender sender = new FHIRSender(context, new FHIRSender.FHIRSenderListener() {
            @Override
            public void onSendCompleted(boolean success) {
                // 전송 완료 후 처리
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(context, "데이터 전송 성공", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "데이터 전송 실패", Toast.LENGTH_SHORT).show();
                    }
                    // 현재 Activity 종료하고 이전 화면으로 돌아감
                    finish();
                });
            }
        });

        // 데이터 전송 실행
        sender.execute(fhirData, serverUrl);
    }
}
