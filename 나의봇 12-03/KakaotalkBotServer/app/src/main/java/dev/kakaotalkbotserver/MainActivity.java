package dev.kakaotalkbotserver;

import androidx.annotation.NonNull;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int EDIT_INPUT_ACTIVITY = 1;
    private static final int EDIT_ALERT_ACTIVITY = 2;
    private static final Object TIME_LOCK = new Object();
    private static File targetFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "KakaoBotServer/data.ser");
    private static File targetAlertFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "KakaoBotServer/alertData.ser");


    private static HashMap<String, NotificationResponse> resp = null;
    private static List<AutoAlertNotification> autoAlert = new ArrayList<>();
    private static RoomStorage roomStorage;
    private static boolean enabled = false;


    private LinearLayout contentListView;
    private LinearLayout usageView;
    private FloatingActionButton addButton;
    private TextView[] buttons = new TextView[3];
    private SwitchCompat enableSwitch;


    private Intent serv;
    private long lastBackPress = 0;
    private boolean listMode = true;
    private Notification notification;
    private Thread timeCheckThread;
    private int lastProcess = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1??? ????????? ??????????????? ???????????? ?????????
        // resp??? null??? ???????????? ?????? ???????????? ?????????????????????, ????????? ??????
        if(resp != null)
            throw new IllegalStateException();

        setContentView(R.layout.activity_main);
        // ???????????? ?????? ?????? ?????????
        resp = new HashMap<>();
        // ?????? ????????? ?????? ?????? ????????? ????????? ?????????
        roomStorage = new RoomStorage(getApplicationContext());
        // ?????? ?????????????????? ?????????????????????, ?????? ????????? ????????? ???????????? ?????? ?????? ??? ?????? ??????.
        requestStoragePermission();
        // ?????? ????????? ????????? ??????????????? ????????????.
        // ??? ????????? ?????????????????? ??????????????? ????????? ?????? ?????? ????????? ???????????? ????????? ????????? ????????????. ( ??????/???????????? ??? ?????? ???????????? ????????? ????????? ??? ??????, ?????????????????? ??????????????? ????????? ?????? )
        if (targetFile.exists()) {
            try {
                // NotificationResponse??? AutoAlertNotification??? ?????? Serializable??? implement ?????????????????? ?????? ?????? Serializer??? ????????? ??????????????? ??????
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
        initCatcher();
        // ?????? ?????? ????????? ???????????? ?????? 0.5????????? ???????????? ????????? ??????????????? ???????????? ????????? ????????????.
        startTimerTicking();
        formatAlertList();
        startService(serv = new Intent(getApplicationContext(), KakaotalkNotificationListener.class));

    }

    private void initCatcher() {
        // ?????? ????????? ??????????????? ????????? ???????????????.
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                System.exit(0);
            }
        });
    }


    private void startTimerTicking() {
        timeCheckThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        return;
                    }
                    Calendar c = Calendar.getInstance();
                    if (c.get(Calendar.SECOND) == 0) {
                        if (lastProcess != c.get(Calendar.SECOND)) {
                            // ???????????????????????? ????????? ???????????? ?????? synchronized ????????? ????????? ?????????
                            synchronized (TIME_LOCK) {
                                // ?????? ????????? ????????????, ??????????????? ????????? ?????? ???????????? ???????????? ????????????.
                                // ????????? ???????????? ?????? ?????? ????????????, ???????????? ????????? ?????? 1??? ????????? ????????? ????????? ???????????????.
                                for (AutoAlertNotification n : autoAlert) {
                                    if (n.isTime(c))
                                        getRoomStorage().alert(n.getMessage());
                                }
                            }
                        }
                    }
                    // ?????? ????????? ?????? ?????? ??????????????? ????????? ?????? ?????? ???????????? ????????????.
                    lastProcess = c.get(Calendar.SECOND);
                }
            }
        };
        timeCheckThread.start();
    }

    // ???????????????????????? finish()??? ???????????? ???????????? ???????????? ??????
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeCheckThread.interrupt();
        timeCheckThread.stop();
        stopService(serv);
        // ?????? ????????? ???????????? ?????? ???????????? ??????, ?????? ?????? ??????
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(0);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - lastBackPress <= 2000) {//????????????2????????? ?????????
            finishAndRemoveTask();
        } else {
            lastBackPress = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(), R.string.backpress_confirm, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_INPUT_ACTIVITY) {//????????? ??????
            if (resultCode == RESULT_OK) {
                addResponse(data.getStringExtra("Keyword"), data.getStringExtra("Response"));
                saveResponse();
                formatAlertList();
                Toast.makeText(getApplicationContext(), R.string.alert_successfully_added, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == EDIT_ALERT_ACTIVITY) {//???????????? ??????
            if (resultCode == RESULT_OK) {
                addAlert(data.getIntExtra("Hour", 1) - 1, data.getIntExtra("Minute", 0), data.getStringExtra("Message"));
                saveAlert();
                formatAlertList();
                Toast.makeText(getApplicationContext(), R.string.auto_alert_successfully_added, Toast.LENGTH_SHORT).show();
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
        enableSwitch = findViewById(R.id.enable_switch);
    }

    private void initListener() {
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {//+??????
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                if (listMode) {
                    startActivityForResult(new Intent(getApplicationContext(), InputActivity.class), EDIT_INPUT_ACTIVITY);
                } else {
                    startActivityForResult(new Intent(getApplicationContext(), TimeInputActivity.class), EDIT_ALERT_ACTIVITY);
                }
            }
        });
        buttons[0].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {//?????? ?????? ????????? ?????????
                listMode = true;
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
        buttons[1].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //???????????? ?????????
                // ????????? ????????? ????????????.
                listMode = false;
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
            public void onClick(View view) {//????????? ?????????
                disableColors();
                view.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                if (usageView.getVisibility() == View.GONE) {
                    contentListView.setVisibility(View.GONE);
                    addButton.hide();
                    usageView.setVisibility(View.VISIBLE);
                }
            }
        });
        // ?????? ?????? ??????
        findViewById(R.id.redirect_permission)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        startActivity(intent);
                    }
                });
        // ??????????????? ?????? ?????? ??????
        findViewById(R.id.redirect_store).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.wearable.app")));
            }
        });
        // ????????? ?????????
        (enableSwitch = findViewById(R.id.enable_switch))
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        setEnabled(b);
                    }
                });
    }

    private void disableColors() {
        for (TextView tv : buttons)
            tv.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
    }

    // ?????? ?????????
    private void initNotification() {
        // ??????????????? ????????? ??????????????? NotificationManager??? ???????????? ????????? ????????? ???????????? ??????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "dev.kakaotalkserver";
            String description = "???????????? ??? ??????";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(name, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // ????????? ??????
        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), "dev.kakaotalkserver")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("???????????? ???")
                .setContentText("???????????? ?????? ??????????????????.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(null)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, getIntent(), 0))
                .setAutoCancel(false);
        notification = b.build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
    }

    // ????????? ?????? ??????
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

    // ?????? ?????? ??????
    public void formatAlertList() {
        contentListView.removeAllViews();
        if (listMode) {
            for (NotificationResponse rep : resp.values())
                contentListView.addView(getNotificationLayout(rep));
        } else {
            for (AutoAlertNotification rep : autoAlert) {
                contentListView.addView(getAutoAlertLayout(rep));
            }
        }
    }


    // ?????? ????????????
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
        swtch.setGravity(Gravity.CENTER | Gravity.START);
        swtch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                resp.setEnabled(b);
                saveResponse();
            }
        });
        if (resp.isEnabled())
            swtch.setChecked(true);
        ll.addView(swtch);
        ll.setLongClickable(true);
        // ??????????????? ?????????, ?????? ???????????? ???????????? ???????????????.
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
        // ??????????????? ?????? ?????????, ????????? ??????????????????.
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


    // ?????? ?????? ???????????? ?????? ??????
    public LinearLayout getAutoAlertLayout(final AutoAlertNotification resp) {
        LinearLayout ll = new LinearLayout(getApplicationContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.notification_height));
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER;
        int margins = (int) getResources().getDimension(R.dimen.notification_margin);
        lp.setMargins(margins, margins, margins, margins);
        ll.setLayoutParams(lp);

        ll.addView(createText((resp.getTimer()[0]) + "??? " + resp.getTimer()[1] + "???", getResources().getDimension(R.dimen.title_text_size), Typeface.DEFAULT_BOLD));
        TextView tv = createText(resp.getMessage(), getResources().getDimension(R.dimen.content_text_size));
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
        swtch.setGravity(Gravity.CENTER | Gravity.START);
        // ????????? ?????????, ?????? ???????????? ??????
        swtch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                resp.setEnabled(b);
                saveAlert();
            }
        });
        if (resp.isEnabled())
            swtch.setChecked(true);
        ll.addView(swtch);
        ll.setLongClickable(true);
        // ?????? ?????????, ?????? ????????? ??????
        ll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                MainActivity.autoAlert.remove(resp);
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

    public static void addAlert(int hour, int min, String msg) {
        autoAlert.add(new AutoAlertNotification(new int[]{hour + 1, min}, msg));
        saveResponse();
    }

    private static void saveResponse() {
        try {
            if (!targetFile.exists()) {
                targetFile.getParentFile().mkdirs();
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


    private static void saveAlert() {
        try {
            if (!targetFile.exists()) {
                targetFile.getParentFile().mkdirs();
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

}
