/*
 * Copyright 2022 Korea University(os.korea.ac.kr). All rights reserved.
 *
 * HeartRateActivity.java - Xalute (Digital Health Platform Project)
 *
 */
package com.example.xalute;
import androidx.fragment.app.FragmentActivity;

import android.os.Bundle;

import com.example.xalute.R;

public class HeartRateActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate);
    }
}