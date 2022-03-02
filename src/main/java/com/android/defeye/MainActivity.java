package com.android.defeye;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;

import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.Hashtable;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    TextView tvStatus;
    EditText etAddress;
    Spinner spinner;

    String interval = "";
    String address = "";
    boolean isServiceRunning = false;

    DefEyeService mService;
    boolean mBound = false;
    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        etAddress = (EditText)findViewById(R.id.editTextAddress);
        spinner = (Spinner)findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.interval_array, android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        address = preferences.getString("address","");
        interval = preferences.getString("interval","5");

        etAddress.setText(address);
        int selectionPosition= adapter.getPosition(interval);
        spinner.setSelection(selectionPosition);

        //etAddress.setText("df1q52dw525kze75vw53dnpwsed3fnl62pupmrpre3");
    }

    public void onButtonStartPressed(View view) {
        Intent serviceIntent = new Intent(this, DefEyeService.class);
        String addr = etAddress.getText().toString();
        if (addr.isEmpty())
        {
            Toast.makeText(getApplicationContext(),
                    "Address field is empty",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("address", addr);
        editor.putString("interval", interval);
        editor.commit();

        serviceIntent.putExtra("Address", address);
        serviceIntent.putExtra("Interval", interval);
        startService(serviceIntent);

        Toast.makeText(getApplicationContext(),
                "Process started!",
                Toast.LENGTH_LONG).show();
    }

    public void onButtonStopPressed(View view) {
        stopService(new Intent(this, DefEyeService.class));
        Toast.makeText(getApplicationContext(),
                "Process stopped!",
                Toast.LENGTH_LONG).show();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
    {
        interval = parent.getItemAtPosition(pos).toString();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to DefEyeService, cast the IBinder and get DefEyeService instance
            DefEyeService.LocalBinder binder = (DefEyeService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}