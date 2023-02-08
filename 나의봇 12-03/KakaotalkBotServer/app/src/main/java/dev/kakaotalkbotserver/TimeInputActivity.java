package dev.kakaotalkbotserver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class TimeInputActivity extends AppCompatActivity {
    private LinearLayout completeBtn;

    private EditText response;
    private TimePicker picker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_autoalert);
        initContent();
        initListener();
    }

    private void initListener() {
        completeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String v = response.getText().toString();
                Intent out = new Intent();
//                out.putExtra("Keyword", k);
                out.putExtra("Hour",picker.getHour());
                out.putExtra("Minute",picker.getMinute());
                out.putExtra("Message", v);
                setResult(RESULT_OK, out);
                finish();
            }
        });
    }


    private void initContent() {
        completeBtn = findViewById(R.id.edit_complete_btn);
        picker = findViewById(R.id.time_picker);
        response = findViewById(R.id.edit_response);
    }
}
