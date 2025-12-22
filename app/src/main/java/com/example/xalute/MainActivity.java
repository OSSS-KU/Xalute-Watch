
/*
 * Copyright 2022 Korea University(os.korea.ac.kr). All rights reserved.
 *
 * MainActivity.java - Xalute (Digital Health Platform Project)
 *
 */
package com.example.xalute;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.example.xalute.databinding.ActivityMainBinding;
import com.example.xalute.R;

public class MainActivity extends FragmentActivity {

    private ActivityMainBinding binding;

    private final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //binding = ActivityMainBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot());

        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");
        String[] values = new String[]{"Contact","ECG"};
        //String[] values = new String[]{"Contact", "ECG", "Settings"};
        ListView mListView = findViewById(R.id.sensors_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, values);/* {
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                /// Get the Item from ListView
                View view = super.getView(position, convertView, parent);

                TextView tv = (TextView) view.findViewById(android.R.id.text1);

                // Set the text size 12 dip for ListView each item
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);

                // Return the view
                return view;
            }
        };*/
        mListView.setAdapter(adapter);

        mListView.setOnItemClickListener( (__,___,position,____) -> {
            switch (position) {
                case 0: {
                    startActivity(new Intent(this, HeartRateActivity.class));
                    break;
                }
                case 1: {
                    startActivity(new Intent(this, EcgInfoActivity.class));
                    break;
                }
                case 2: {
                    startActivity(new Intent(this, SettingActivity.class));
                    break;
                }

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }
}