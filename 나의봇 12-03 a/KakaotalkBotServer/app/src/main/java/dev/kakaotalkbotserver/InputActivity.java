package dev.kakaotalkbotserver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class InputActivity extends AppCompatActivity {
    private LinearLayout completeBtn;
    private EditText keyword;
    private EditText response;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_response);
        initContent();
        initListener();
        if (getIntent().hasExtra("Edit")) {
            String[] resp = getIntent().getStringArrayExtra("Edit");
            keyword.setText(resp[0]);
            response.setText(resp[1]);
            keyword.setEnabled(false);
        }
    }

    private void initListener() {
        completeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String k = keyword.getText().toString();
                String v = response.getText().toString();
                if (k.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.err_empty_keyword, Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent out = new Intent();
                out.putExtra("Keyword", k);
                out.putExtra("Response", v);
                setResult(RESULT_OK, out);
                finish();
            }
        });
    }


    private void initContent() {
        completeBtn = findViewById(R.id.edit_complete_btn);
        keyword = findViewById(R.id.edit_keyword);
        response = findViewById(R.id.edit_response);
    }
}
