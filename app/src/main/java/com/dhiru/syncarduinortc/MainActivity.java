package com.dhiru.syncarduinortc;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

// References:
// https://github.com/mik3y/usb-serial-for-android/tree/master/usbSerialExamples
// https://www.gamasutra.com/view/feature/171774/getting_high_precision_timing_on_.php?print=1

public class MainActivity extends AppCompatActivity {
    ImageButton refreshButton;
    TextView logTextView;
    EditText diffEditText;
    SharedPreferences sharedPreferences;

    private UsbPermission usbPermission = UsbPermission.Unknown;

    private enum UsbPermission {Unknown, Requested, Granted, Denied}

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    public void sync() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            logTextView.setText("No USB devices were detected!");
            return;
        }

        // https://github.com/mik3y/usb-serial-for-android/blob/c917ac5c837afe434a40c9ac0ff01cc1939f0610/usbSerialExamples/src/main/java/com/hoho/android/usbserial/examples/TerminalFragment.java#L189
        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());

        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            logTextView.setText("Please press the 'Sync' button after granting USB perms.");
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                logTextView.setText("Connection failed: permission denied!");
            else
                logTextView.setText("Connection failed: open failed!");
            return;
        }

        UsbSerialPort usbSerialPort = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        logTextView.setText("Connection OK!");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                /* Not the smartest logic but it is good enough with some luck ;) */
                long currentTime = System.currentTimeMillis();
                double relevantTime = (currentTime / 1000) % 60;
                String value = diffEditText.getText().toString();
                double finalDiffValue = Double.parseDouble(value);
                relevantTime = relevantTime + finalDiffValue;
                int intSeconds = (int) relevantTime;
                String status = String.format("T%d", intSeconds);
                // System.out.println(status);
                usbSerialPort.write(status.getBytes(), 500);
                logTextView.setText("Time is synced!");
                Log.e("Sync Arduino RTC", status);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 3000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Map things
        logTextView = findViewById(R.id.log_messages);
        diffEditText = findViewById(R.id.diff);
        addListenerOnButton();

        // Handling 'diff'
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        float diff = sharedPreferences.getFloat("diff", 1.2f);
        diffEditText.setText(Float.toString(diff));
        diffEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                float diff = Float.parseFloat(s.toString());
                sharedPreferences.edit().putFloat("diff", diff).apply();
            }
        });
    }

    public void addListenerOnButton() {
        refreshButton = findViewById(R.id.button_refresh);
        refreshButton.setOnClickListener(
                (View arg0) -> sync()
        );
    }
}