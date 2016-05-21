/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialConsoleActivity extends Activity {

    private final String TAG = SerialConsoleActivity.class.getSimpleName();

    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     *
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;
    private CheckBox chkDTR;
    private CheckBox chkRTS;

    private Button BtnOpenPort;
    private Button BtnSendData;
    private Button BtnClosePort;
    private TextView textShowData;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SerialConsoleActivity.this.updateReceivedData(data);

                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        chkDTR = (CheckBox) findViewById(R.id.checkBoxDTR);
        chkRTS = (CheckBox) findViewById(R.id.checkBoxRTS);
        BtnClosePort = (Button)findViewById(R.id.BTNClosePort);
        BtnOpenPort = (Button)findViewById(R.id.BTNInitPort);
        BtnSendData = (Button)findViewById(R.id.BTNSendData);
        textShowData = (TextView)findViewById(R.id.TxtShowData);

        chkDTR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setDTR(isChecked);
                }catch (IOException x){}
            }
        });

        chkRTS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setRTS(isChecked);
                }catch (IOException x){}
            }
        });

        BtnOpenPort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sPort == null) {
                    mTitleTextView.setText("No serial device.");
                } else {
                    final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

                    UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
                    if (connection == null) {
                        mTitleTextView.setText("Opening device failed");
                        return;
                    }

                    try {
                        sPort.open(connection);
                        sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                        System.out.println("Open Success!");

                    } catch (IOException e) {
                        Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                        textShowData.setText("Error opening device: " + e.getMessage());
                        return;
                    }
                    mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
                }
            }
        });

        BtnSendData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte src[] = {0x55,0x66,0x77};
                try {
                    sPort.write(src,100);
                } catch (IOException e){
                    Log.e(TAG, "Error Send Data: " + e.getMessage(), e);
                    textShowData.setText("Error opening device: " + e.getMessage());
                    return;
                }
            }
        });

    }


    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                textShowData.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                showStatus(mDumpTextView, "CD  - Carrier Detect", sPort.getCD());
                showStatus(mDumpTextView, "CTS - Clear To Send", sPort.getCTS());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "DTR - Data Terminal Ready", sPort.getDTR());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "RI  - Ring Indicator", sPort.getRI());
                showStatus(mDumpTextView, "RTS - Request To Send", sPort.getRTS());

            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        for (int i = 0; i < data.length - 1 ; i++){
            System.out.print("Data arrave");
            System.out.println(data[i]);
            System.out.print(data[i]);
        }
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n";
        textShowData.append(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param driver
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

}
