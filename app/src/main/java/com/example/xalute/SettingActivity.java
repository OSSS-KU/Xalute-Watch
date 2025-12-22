package com.example.xalute;
import android.content.SharedPreferences;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.example.xalute.R;

import java.text.SimpleDateFormat;

import java.util.Calendar;

public class SettingActivity extends FragmentActivity {

    private EditText editTextName;
    private String selectedDate;
    private String birthDate;
    private Button buttonDatePicker, buttonShow;
    private DatePickerDialog datePickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        editTextName = findViewById(R.id.edittext_name);
        buttonDatePicker = findViewById(R.id.button_datepicker);
        buttonShow = findViewById(R.id.button_show);

        // 기존 값 불러오기
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        birthDate = sharedPreferences.getString("birthDate", "");
        String name = sharedPreferences.getString("name", "");
        editTextName.setText(name);
        if (!birthDate.isEmpty()) {
            buttonDatePicker.setText("Birth Date: " + birthDate);
        }
        buttonDatePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                datePickerDialog = new DatePickerDialog(SettingActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                                Calendar selectedCalendar = Calendar.getInstance();
                                selectedCalendar.set(year, monthOfYear, dayOfMonth);

                                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                                birthDate = sdf.format(selectedCalendar.getTime());

                                // Save selectedDate
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("birthDate", birthDate);
                                editor.apply();
                            }
                        }, year, month, day);
                datePickerDialog.show();
            }
        });

        buttonShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = editTextName.getText().toString();

                onMessage("Information saved");

                // 값 저장하기
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("name", name);
                editor.apply();
            }
        });
    }

    void onMessage(String message) {
        Toast.makeText(SettingActivity.this, message, Toast.LENGTH_LONG).show();
    }

}