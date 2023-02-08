package com.example.chat1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.rationalowl.minerva.client.android.DeviceRegisterResultListener;
import com.rationalowl.minerva.client.android.MinervaManager;
import com.rationalowl.minerva.client.android.Result;
import com.rationalowl.minerva.client.android.data.MinervaDataManager;
import com.rationalowl.minerva.client.android.util.Logger;

public class RegisterActivity extends Activity implements OnClickListener, DeviceRegisterResultListener {

    private static final String TAG = "RegisterActivity";


    RadioGroup mSCRg;
    RadioButton mAcceptRb;
    RadioButton mRejectRb;
    private static String pToken;
    String fcmToken;

    EditText mUrlEt;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        mUrlEt = (EditText) this.findViewById(R.id.url);
        Button regBtn = (Button) findViewById(R.id.regBtn);
        Button unreglBtn = (Button) findViewById(R.id.unregBtn);
        regBtn.setOnClickListener(this);
        unreglBtn.setOnClickListener(this);
    }


    @Override
    protected void onDestroy() {
        //Logger.debug(TAG, "onDestroy enter");
        super.onDestroy();

    }


    @Override
    protected void onStart() {
        //Logger.debug(TAG, "onStart() enter");
        super.onStart();
        mUrlEt.setText("gate.rationalowl.com");
        //mUrlEt.setText("13.125.25.251"); //aws dev

    }


    @Override
    protected void onResume() {
        //Logger.debug(TAG, "onResume enter");
        super.onResume();


        //set register callback listener
        MinervaManager minMgr = MinervaManager.getInstance();
        minMgr.setRegisterResultListener(this);
    }


    public  void getptoken(String fcmToken){
        fcmToken=pToken;
    }



    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.regBtn: {
                String url = mUrlEt.getText().toString();

                // sometimes, FCM onTokenRefresh() callback not called,
                // So, before registering we need to call it explicitly.
                //String fcmToken = FirebaseInstanceId.getInstance().getToken();

                //String fcmToken = FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener( .this,  new OnSuccessListener<InstanceIdResult>() {
                 //   @Override
                 //   public void onSuccess(InstanceIdResult instanceIdResult) {
                 //       String newToken = instanceIdResult.getToken();
                //        Log.e("newToken", newToken);
//
                //    }
                //});

                FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener( new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        pToken = instanceIdResult.getToken();
                    }

                });
                getptoken(fcmToken);

                MinervaManager mgr = MinervaManager.getInstance();
                mgr.setDeviceToken(fcmToken);

                // register device app.
                mgr.registerDevice(url, "906f56fe77db487f9d971b22fbc581e1","Android sample app"); //aws dev gate
                //mgr.registerDevice(url, "faebcfe844d54d449136491fb253619d","?⑤쭚?깅줉?대쫫2"); //hostway
                //mgr.registerDevice(url, "def829b853d046779e2227bdd091653c","寃쎈??뚯뒪?명룿"); //hostway
                //mgr.registerDevice(url, "c8574b6882c34db0a6e6691987de1221"); //aws test
                break;

            }
            case R.id.unregBtn:
                MinervaManager mgr = MinervaManager.getInstance();
                mgr.unregisterDevice("906f56fe77db487f9d971b22fbc581e1"); //aws dev gate
                //mgr.unregisterDevice("def829b853d046779e2227bdd091653c"); //hostway
                //mgr.unregisterDevice("c8574b6882c34db0a6e6691987de1221"); //aws test
                break;
            default:
                break;
        }
    }

    @Override
    public void onRegisterResult(int resultCode, String resultMsg, String deviceRegId) {
        Logger.debug(TAG, "onRegisterResult " + resultCode);
        String msg = resultMsg + "registration id : " + deviceRegId;
        //yes registration has completed successfully!
        if(resultCode == Result.RESULT_OK) {
            // save deviceRegId to local file
            // and send deviceRegId to app server using MinervaManager.sendUpstreamMsg()
            // MinervaManager minMgr = MinervaManager.getInstance();
            // minMgr.sendUpstreamMsg("data including deviceRegId", "your app server registration id");
        }
        //already registered
        else if(resultCode == Result.RESULT_DEVICE_ALREADY_REGISTERED) {
            // already registered.
        }
        //registration error has occurred!
        else {
            //error occurred while registering device app.
        }
        Logger.debug(TAG, msg);
    }

    @Override
    public void onUnregisterResult(int resultCode, String resultMsg) {

        //yes unregistration has completed successfully!
        if(resultCode == Result.RESULT_OK) {
        }
        //registration error has occurred!
        else {
        }
    }
}