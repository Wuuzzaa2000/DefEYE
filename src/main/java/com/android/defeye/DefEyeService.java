package com.android.defeye;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Hashtable;

public class DefEyeService extends Service {

    final String LOG_TAG = "DefEye";
    private static final int TRANSACTION_UPDATED = 101;
    private static final int SERVICE_STARTED = 1;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";

   // String mAddress = "df1q52dw525kze75vw53dnpwsed3fnl62pupmrpre3";
   String mAddress = "";
    String URL1 = "https://ocean.defichain.com/v0/mainnet/address/";
    String URL2 = "https://api.az-prod-0.saiive.live/api/v1/mainnet/DFI/tx/id/";
    String txid = "";
    String txType = "";
    int txTypeInt = -1;
    int height = 0;
    int heightSaved = 0;
    boolean transactionUpdated = false;
    int mInterval = 5000;
    Handler mainLooper;
    Vibrator vibrator;
    Hashtable<String, String> transactionCodes
            = new Hashtable<String, String>();

    private final IBinder binder = new LocalBinder();
    private boolean isRunning = false;

    Handler notificationHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            Log.d("LOG_TAG", "Transaction updated!");

            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            showNotification(getApplicationContext(), "DefEye", transactionCodes.get(txType)+" confirmed successfully", intent, TRANSACTION_UPDATED);
            vibrate(500,true);
        }
    };

    private void initHashTable()
    {
        transactionCodes.put("C","CreateMasternode");
        transactionCodes.put("R","ResignMasternode");
        transactionCodes.put("m","UpdateMasternode");
        transactionCodes.put("F","SetForcedRewardAddress");
        transactionCodes.put("f", "RemForcedRewardAddress");
        transactionCodes.put("T","CreateToken");
        transactionCodes.put("M","MintToken");
        transactionCodes.put("N","UpdateToken");
        transactionCodes.put("n", "UpdateTokenAny");
        // transactionCodes.put("O","CreateOrder");
        // transactionCodes.put("E","DestroyOrder");
        // transactionCodes.put("A","MatchOrders");
        transactionCodes.put("p","CreatePoolPair");
        transactionCodes.put("u","UpdatePoolPair");
        transactionCodes.put("s","PoolSwap");
        transactionCodes.put("i","PoolSwapV2");
        transactionCodes.put("l","AddPoolLiquidity");
        transactionCodes.put("r","RemovePoolLiquidity");
        transactionCodes.put("U","UtxosToAccount");
        transactionCodes.put("b","AccountToUtxos");
        transactionCodes.put("B","AccountToAccount");
        transactionCodes.put("a","AnyAccountsToAccounts");
        transactionCodes.put("K","SmartContract");
        transactionCodes.put("G","SetGovVariable");
        transactionCodes.put("j","SetGovVariableHeight");
        transactionCodes.put("A", "AutoAuthPrep");
        transactionCodes.put("o","AppointOracle");
        transactionCodes.put("h","RemoveOracleAppoint");
        transactionCodes.put("t","UpdateOracleAppoint");
        transactionCodes.put("y","SetOracleData");
        transactionCodes.put("1","ICXCreateOrder");
        transactionCodes.put("2","ICXMakeOffer");
        transactionCodes.put("3","ICXSubmitDFCHTLC");
        transactionCodes.put("4","ICXSubmitEXTHTLC");
        transactionCodes.put("5","ICXClaimDFCHTLC");
        transactionCodes.put("6","ICXCloseOrder");
        transactionCodes.put("7", "ICXCloseOffer");
        transactionCodes.put("c","SetLoanCollateralToken");
        transactionCodes.put("g","SetLoanToken");
        transactionCodes.put("x","UpdateLoanToken");
        transactionCodes.put("L","LoanScheme");
        transactionCodes.put("d","DefaultLoanScheme");
        transactionCodes.put("D","DestroyLoanScheme");
        transactionCodes.put("V","Vault");
        transactionCodes.put("e","CloseVault");
        transactionCodes.put("v","UpdateVault");
        transactionCodes.put("S","DepositToVault");
        transactionCodes.put("J","WithdrawFromVault");
        transactionCodes.put("X","TakeLoan");
        transactionCodes.put("H","PaybackLoan");
        transactionCodes.put("I","AuctionBid");
    }

    public void onCreate() {
        super.onCreate();
        initHashTable();
        vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        Log.d(LOG_TAG, "onCreate");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {

        Bundle extras = intent.getExtras();
        if (extras != null) {

            String extraAddress = (String) extras.get("Address");
            mAddress = extraAddress;
            String extraInterval = (String) extras.get("Interval");

            try {
                mInterval = Integer.parseInt(extraInterval);
                mInterval *= 1000;
            } catch(NumberFormatException nfe) {
                System.out.println("Could not parse " + nfe);
            }

            Notification not = createNotification(getApplicationContext(), "DefEye", "DefEye service is running", intent, SERVICE_STARTED);
            startForeground(1, not);
            stopMainLoop();
            startMainLoop(mAddress, mInterval);
            Log.d(LOG_TAG, "onStartCommand");
            //return super.onStartCommand(intent, flags, startId);
        }
        return START_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        stopMainLoop();
        stopSelf();
        Log.d(LOG_TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public synchronized boolean getServiceStatus()
    {
        return isRunning;
    }

    public synchronized String getAddress()
    {
        return mAddress;
    }

    public synchronized int getInterval()
    {
        return mInterval;
    }

    public void startMainLoop(String address, long interval)
    {
        mAddress = address;
        stopMainLoop();
        isRunning= true;
        mainLooper = new Handler();
        final Runnable r = new Runnable() {
            public void run() {
                getTXIDAndHeight();
                mainLooper.postDelayed(this, interval);
            }
        };
        // Start main loop:
        //
        mainLooper.postDelayed(r, 100);
    }

    public void stopMainLoop()
    {
        if (mainLooper != null) {
             mainLooper.removeCallbacksAndMessages(null);
        }
    }

    private void GetTxTypeAndConfirmations()
    {
        txType = "";
        txTypeInt = -1;

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        String url= URL2+txid;
        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(LOG_TAG,response.toString());
                    try {
                        JsonElement json = new JsonParser().parse(response.toString());
                        JsonObject jsObject = json.getAsJsonObject();
                        try {
                            txType = jsObject.get("txType").getAsString();
                        }
                        catch(Exception ex)
                        {
                            txTypeInt = jsObject.get("txType").getAsInt();
                        }
/*
                        jsObject = jsObject.getAsJsonObject("details");
                        JsonArray jsonDataArray = jsObject.getAsJsonArray("inputs");
                        jsObject = jsonDataArray.get(0).getAsJsonObject();
                        int confirmations = jsObject.get("confirmations").getAsInt();

                        if (confirmations > 0)
                            ;
*/
                        notificationHandler.sendEmptyMessage(TRANSACTION_UPDATED);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e(LOG_TAG, error.toString())
        );

        try
        {
            requestQueue.add(objectRequest);
        }
        catch (Exception ex)
        {
            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    private void getTXIDAndHeight()
    {
        transactionUpdated = false;
        height = 0;
        txid = "";
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        String url= URL1+mAddress+"/transactions?next=token&size=1";
        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(LOG_TAG,response.toString());
                    try {
                        JsonElement json = new JsonParser().parse(response.toString());
                        JsonObject jsObject = json.getAsJsonObject();
                        JsonArray jsonDataArray = jsObject.getAsJsonArray("data");
                        jsObject = jsonDataArray.get(0).getAsJsonObject();
                        txid = jsObject.get("txid").getAsString();

                        jsObject = jsObject.get("block").getAsJsonObject();
                        height = jsObject.get("height").getAsInt();

                        if (height > heightSaved)
                        {
                            if (heightSaved != 0)
                                GetTxTypeAndConfirmations();

                            heightSaved = height;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e(LOG_TAG,error.toString())
        );
        try
        {
            requestQueue.add(objectRequest);
        }
        catch (Exception ex)
        {
            Log.e(LOG_TAG,ex.getMessage());
        }
    }

    private void vibrate(long vibrateTime, boolean threeTimes)
    {
        try {
            if (vibrator == null)
                return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                {
                    if (threeTimes)
                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{ 0,vibrateTime,0,vibrateTime,0,vibrateTime },-1));
                    else
                        vibrator.vibrate(VibrationEffect.createOneShot(vibrateTime, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {

                // Vibrate 3 times:
                if (threeTimes)
                    for (int i=0;i<3;i++)
                    {
                        // Vibrate for 200 milliseconds
                        vibrator.vibrate(vibrateTime);
                        try{Thread.sleep(200);}catch(InterruptedException ie){}
                    }
                else
                    vibrator.vibrate(vibrateTime);
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void showNotification(Context context, String title, String message, Intent intent, int reqCode) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_ONE_SHOT);
        String CHANNEL_ID = "DefEye_Channel";
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.defeye))
                .setSmallIcon(R.drawable.defeye)
               // .setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000})
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(false)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "DefEye_Channel";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.enableVibration(true);
            //mChannel.setVibrationPattern(new long[]{ 0,2000,0,2000,0,2000 });
            notificationManager.createNotificationChannel(mChannel);
        }
        notificationManager.notify(reqCode, notificationBuilder.build());
    }

    public Notification createNotification(Context context, String title, String message, Intent intent, int reqCode) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_ONE_SHOT);
        String CHANNEL_ID = "DefEye_Channel1";
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                //.setSmallIcon(R.mipmap.ic_launcher)
               // .setSmallIcon(R.drawable.defi)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.defeye))
                .setSmallIcon(R.drawable.defeye)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder.setContentIntent(contentIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "DefEye_Channel";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(mChannel);
        }
        return notificationBuilder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public class LocalBinder extends Binder {
        DefEyeService getService() {
            return DefEyeService.this;
        }
    }
}
