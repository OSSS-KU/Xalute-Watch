/*
 * Copyright 2022 Korea University(os.korea.ac.kr). All rights reserved.
 *
 * EcgInfoActivity.java - Xalute (Digital Health Platform Project)
 *
 */
package com.example.xalute;

import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.os.Bundle;

import com.example.xalute.R;


public class EcgInfoActivity extends FragmentActivity {

    private boolean isDefaultMessage;
    Button btn_ok,btn_send;
    TextView textView_ecgInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg_info);

        isDefaultMessage = true;
        // id 매핑

        textView_ecgInfo = (TextView) findViewById(R.id.ecg_info);

        btn_ok = (Button) findViewById(R.id.button);
        btn_ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //textView_ecgInfo.setText(R.string.Ecg_info_measure);
                //isDefaultMessage = false;

                //if (!isDefaultMessage) {
                Intent intent = new Intent(getApplicationContext(), EcgActivity.class);
                startActivity(intent);
                finish();
                //}
            }
        });

        /*send sample data
        String fhirData = "{\"type\":\"batch\",\"resourceType\":\"Bundle\",\"entry\":[{\"request\":{\"url\":\"Observation\",\"method\":\"POST\"},\"resource\":{\"resourceType\":\"Observation\",\"id\":\"mjkim515\",\"component\":[{\"code\":{\"coding\":[{\"display\":\"MDC_ECG_ELEC_POTL_I\"},{\"code\":\"mV\",\"display\":\"microvolt\",\"system\":\"http:\"}]},\"valueSampledData\":{\"origin\":{\"value\":55},\"period\":29.998046875,\"data\":\"(0.0006958216552734375, 0.0) (0.0009062403564453125, 0.001953125) (0.0010620897216796874, 0.00390625) (0.00018967477416992185, 29.9765625) (0.00019661900329589842, 29.978515625) (0.00020674461364746094, 29.98046875) (0.00022144409179687498, 29.982421875) (0.0002362389678955078, 29.984375) (0.0002381348419189453, 29.986328125) (0.00022038430786132813, 29.98828125) (0.00018931239318847656, 29.990234375) (0.00016846464538574217, 29.9921875) (0.00019641093444824217, 29.994140625) (0.0002990796508789062, 29.99609375) (0.00047561154174804684, 29.998046875) \",\"dimensions\":2}}],\"subject\":{\"reference\":\"Patient/테스트19: 20221114 2022-11-23 19:59\"},\"status\":\"final\",\"code\":{}}}]}";

        String serverUrl = "https://webhook.site/9323136c-1922-4370-bba3-f81b210ec7e1";


        btn_send = (Button) findViewById(R.id.button);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FHIRSender sender = new FHIRSender(getApplicationContext());

                sender.execute(fhirData, serverUrl);
            }
        }); */
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}