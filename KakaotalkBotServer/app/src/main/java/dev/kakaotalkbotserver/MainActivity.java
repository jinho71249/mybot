package dev.kakaotalkbotserver;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private static final int EDIT_INPUT_ACTIVITY = 1;

    private LinearLayout contentListView;
    private LinearLayout usageView;
    private FloatingActionButton addButton;
    private TextView[] buttons = new TextView[3];

    private static HashMap<String, NotificationResponse> resp = null;
    private static RoomStorage roomStorage;
    private static File targetFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "KakaoBotServer/data.ser");
    private static boolean enabled = true;
    private Intent serv;
    private long lastBackPress = 0;
    private Notification notification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resp = new HashMap<>();
        setContentView(R.layout.activity_main);
        roomStorage = new RoomStorage(getApplicationContext());
        if (targetFile.exists()) {
            try {
                resp = (HashMap<String, NotificationResponse>) new ObjectInputStream(new FileInputStream(targetFile)).readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        initContent();
        initNotification();
        initListener();
        formatAlertList();
        for (NotificationResponse re : resp.values()) {
            contentListView.addView(getNotificationLayout(re));
        }

        startService(serv = new Intent(getApplicationContext(), KakaotalkNotificationListener.class));
        requestStoragePermission();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(serv);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(0);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - lastBackPress <= 2000) {
//            finish();
            finishAndRemoveTask();
        } else {
            lastBackPress = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(), R.string.backpress_confirm, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_INPUT_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                addResponse(data.getStringExtra("Keyword"), data.getStringExtra("Response"));
                saveResponse();
                formatAlertList();
                Toast.makeText(getApplicationContext(), R.string.alert_successfully_added, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initContent() {
        contentListView = findViewById(R.id.content_list);
        usageView = findViewById(R.id.usage_layout);
        addButton = findViewById(R.id.floatingButton);
        buttons[0] = findViewById(R.id.redirect_list);
        buttons[1] = findViewById(R.id.redirect_auto_alert_list);
        buttons[2] = findViewById(R.id.redirect_usage);
    }

    private void initListener() {
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                startActivityForResult(new Intent(getApplicationContext(), InputActivity.class), EDIT_INPUT_ACTIVITY);
            }
        });
        buttons[0].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disableColors();
                view.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                if (contentListView.getVisibility() == View.GONE) {
                    usageView.setVisibility(View.GONE);
                    contentListView.setVisibility(View.VISIBLE);
                    addButton.show();
                }
                formatAlertList();
            }
        });

        buttons[2].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disableColors();
                view.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                if (usageView.getVisibility() == View.GONE) {
                    contentListView.setVisibility(View.GONE);
                    addButton.hide();
                    usageView.setVisibility(View.VISIBLE);
                }
            }
        });

        findViewById(R.id.redirect_permission)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        startActivity(intent);
                    }
                });

        findViewById(R.id.redirect_store).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.wearable.app")));
            }
        });
    }

    private void disableColors() {
        for (TextView tv : buttons)
            tv.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
    }

    private void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "dev.kakaotalkserver";
            String description = "카카오톡 봇 서버";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(name, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), "dev.kakaotalkserver")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("카카오톡 봇")
                .setContentText("카카오톡 봇이 작동중입니다.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(null)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, getIntent(), 0))
                .setAutoCancel(false);
        notification = b.build();
        notification.defaults = 0;
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
    }

    private void requestStoragePermission() {
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 23);

        }

        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 23);

        }

    }


    public void formatAlertList() {
        contentListView.removeAllViews();
        for (NotificationResponse rep : resp.values())
            contentListView.addView(getNotificationLayout(rep));
    }


    public LinearLayout getNotificationLayout(final NotificationResponse resp) {
        LinearLayout ll = new LinearLayout(getApplicationContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.notification_height));
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER;
        int margins = (int) getResources().getDimension(R.dimen.notification_margin);
        lp.setMargins(margins, margins, margins, margins);
        ll.setLayoutParams(lp);

        ll.addView(createText(resp.getKeyword(), getResources().getDimension(R.dimen.title_text_size), Typeface.DEFAULT_BOLD));
        TextView tv = createText(resp.getResponse(), getResources().getDimension(R.dimen.content_text_size));
//        tv.setGravity(Gravity.END);
        tv.setSingleLine(false);
        ll.addView(tv);
        ll.setBackground(getDrawable(R.drawable.black_border));
        ll.setPadding(margins, margins, margins, margins);
        Switch swtch = new Switch(getApplicationContext());
        swtch.setTextOn("on");
        swtch.setTextOff("off");
        swtch.setScaleX(1.2f);
        swtch.setScaleY(1.2f);
        lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = .15f;
        lp.gravity = Gravity.CENTER;
        swtch.setLayoutParams(lp);
        swtch.setGravity(Gravity.CENTER);
        swtch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                resp.setEnabled(b);
                saveResponse();
            }
        });
//        swtch.setBackground(getResources().getDrawable(R.drawable.black_border));
        ll.addView(swtch);
        ll.setLongClickable(true);
        ll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), InputActivity.class);
                intent.putExtra("Edit", new String[]{
                        resp.getKeyword(),
                        resp.getResponse()
                });
                startActivityForResult(intent, EDIT_INPUT_ACTIVITY);
            }
        });
        ll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                MainActivity.resp.remove(resp.getKeyword());
                saveResponse();
                formatAlertList();
                Toast.makeText(getApplicationContext(), R.string.alert_removed, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        return ll;
    }

    private TextView createText(String text, float size) {
        TextView tv = new AppCompatTextView(getApplicationContext());
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, 1, 30, 1, TypedValue.COMPLEX_UNIT_SP);
        TextViewCompat.setAutoSizeTextTypeWithDefaults(tv, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        tv.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.3f);
        lp.gravity = Gravity.CENTER;

        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);
//        tv.setTextSize(getResources().getDimension(R.dimen.title_text_size));
//        tv.setTextSize(size);
        tv.setSingleLine(true);
        tv.setTextColor(getResources().getColor(android.R.color.black));
//        tv.setBackground(getDrawable(R.drawable.black_border));
        return tv;
    }

    private TextView createText(String text, float size, Typeface type) {
        TextView v = createText(text, size);
        v.setTypeface(type);
        return v;
    }

    public static void addResponse(String text, String msg) {
        resp.put(text, new NotificationResponse(text, msg));
        saveResponse();
    }

    private static void saveResponse() {
        try {
            if (!targetFile.exists()) {
                System.out.println(targetFile.canWrite());
                targetFile.getParentFile().mkdirs();
                System.out.println(targetFile.getParentFile().canWrite());
                System.out.println(targetFile.getParentFile());
                System.out.println(targetFile.exists());
                targetFile.createNewFile();
            }

            ObjectOutput oos = new ObjectOutputStream(new FileOutputStream(targetFile));
            oos.writeObject(resp);
            oos.flush();
            oos.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static RoomStorage getRoomStorage() {
        return roomStorage;
    }

    public static NotificationResponse getResponse(String msg) {
        return resp.get(msg);
    }


    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean e) {
        enabled = e;
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
