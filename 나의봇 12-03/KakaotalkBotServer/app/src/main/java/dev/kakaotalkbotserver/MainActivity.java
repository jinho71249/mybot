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
        // 1개 이상의 인스턴스를 허용하지 않는다
        // resp가 null이 아니라면 이미 초기화된 인스턴스이므로, 생성을 거부
        if(resp != null)
            throw new IllegalStateException();

        setContentView(R.layout.activity_main);
        // 데이터가 담길 맵을 초기화
        resp = new HashMap<>();
        // 자동 공지를 위해 답장 부분을 캐시할 클래스
        roomStorage = new RoomStorage(getApplicationContext());
        // 이미 퍼미션으로는 설정되어있지만, 혹시 모르는 오류를 방지하기 위하 한번 더 권한 확인.
        requestStoragePermission();
        // 만약 데이터 파일이 존재한다면 불러온다.
        // 큰 용량이 아니기때문에 멀티스레드 환경이 아닌 단일 스레드 환경에서 저장과 로드를 실행한다. ( 저장/불러오기 할 대상 데이터나 파일의 용량이 클 경우, 멀티스레드로 구현하는것 좋다고 한다 )
        if (targetFile.exists()) {
            try {
                // NotificationResponse와 AutoAlertNotification은 미리 Serializable을 implement 해주었으므로 자바 기본 Serializer로 저장과 불러오기가 가능
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
        // 자동 알림 기능을 구현하기 위해 0.5초마다 반복하며 시간을 확인해주는 스레드를 생성해 실행한다.
        startTimerTicking();
        formatAlertList();
        startService(serv = new Intent(getApplicationContext(), KakaotalkNotificationListener.class));

    }

    private void initCatcher() {
        // 오류 발생시 프로그램을 강제로 종료시킨다.
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
                            // 멀티스레드에서의 오류를 방지하기 위해 synchronized 메서를 사용한 상태로
                            synchronized (TIME_LOCK) {
                                // 시간 일치를 확인하고, 일치한다면 인식된 전체 채팅방에 메시지를 전송한다.
                                // 인식된 채팅방은 해당 봇이 포함되고, 활성화된 이후로 최소 1번 이상의 채팅이 전송된 채팅방이다.
                                for (AutoAlertNotification n : autoAlert) {
                                    if (n.isTime(c))
                                        getRoomStorage().alert(n.getMessage());
                                }
                            }
                        }
                    }
                    // 중복 처리를 막기 위해 마지막으로 처리된 초를 현재 시간으로 설정한다.
                    lastProcess = c.get(Calendar.SECOND);
                }
            }
        };
        timeCheckThread.start();
    }

    // 어플리케이션에서 finish()가 호출되면 발생하는 액티비티 종료
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeCheckThread.interrupt();
        timeCheckThread.stop();
        stopService(serv);
        // 상단 알림을 삭제하고 현재 프로세스 종료, 어플 완전 종료
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(0);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - lastBackPress <= 2000) {//뒤로가기2번으로 앱종료
            finishAndRemoveTask();
        } else {
            lastBackPress = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(), R.string.backpress_confirm, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_INPUT_ACTIVITY) {//리스트 추가
            if (resultCode == RESULT_OK) {
                addResponse(data.getStringExtra("Keyword"), data.getStringExtra("Response"));
                saveResponse();
                formatAlertList();
                Toast.makeText(getApplicationContext(), R.string.alert_successfully_added, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == EDIT_ALERT_ACTIVITY) {//자동공지 추가
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
            public void onClick(View view) {//+버튼
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
            public void onClick(View view) {//하단 탭의 리스트 클릭시
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
            public void onClick(View view) { //자동공지 클릭시
                // 리스트 모드를 비활성화.
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
            public void onClick(View view) {//사용법 클릭시
                disableColors();
                view.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                if (usageView.getVisibility() == View.GONE) {
                    contentListView.setVisibility(View.GONE);
                    addButton.hide();
                    usageView.setVisibility(View.VISIBLE);
                }
            }
        });
        // 권한 설정 버튼
        findViewById(R.id.redirect_permission)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        startActivity(intent);
                    }
                });
        // 안드로이드 웨어 설치 버튼
        findViewById(R.id.redirect_store).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.wearable.app")));
            }
        });
        // 최상단 스위치
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

    // 알림 초기화
    private void initNotification() {
        // 안드로이드 오레오 이후부터는 NotificationManager을 등록하지 않으면 알림이 작동하지 않음
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

        // 최상단 알림
        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), "dev.kakaotalkserver")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("카카오톡 봇")
                .setContentText("카카오톡 봇이 작동중입니다.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(null)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, getIntent(), 0))
                .setAutoCancel(false);
        notification = b.build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
    }

    // 저장소 접근 권한
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

    // 알림 목록 갱신
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


    // 알림 레이아웃
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
        // 레이아웃을 클릭시, 수정 화면으로 넘어가게 설정합니다.
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
        // 레이아웃을 길게 클릭시, 삭제후 갱신시킵니다.
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


    // 자동 공지 레이아웃 수동 생성
    public LinearLayout getAutoAlertLayout(final AutoAlertNotification resp) {
        LinearLayout ll = new LinearLayout(getApplicationContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.notification_height));
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER;
        int margins = (int) getResources().getDimension(R.dimen.notification_margin);
        lp.setMargins(margins, margins, margins, margins);
        ll.setLayoutParams(lp);

        ll.addView(createText((resp.getTimer()[0]) + "시 " + resp.getTimer()[1] + "분", getResources().getDimension(R.dimen.title_text_size), Typeface.DEFAULT_BOLD));
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
        // 스위치 조작시, 값을 변경하고 저장
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
        // 길게 클릭시, 자동 공지를 삭제
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
