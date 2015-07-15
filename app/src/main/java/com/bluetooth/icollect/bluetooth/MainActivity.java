package com.bluetooth.icollect.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.os.Handler;

import net.glxn.qrgen.android.QRCode;


public class MainActivity extends AppCompatActivity
{
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MESSAGE_READ = 2;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> arrayAdapter;
    private Set<BluetoothDevice> bluetoothDevices;
    private boolean receiverIsRegistered = false;
    private TextView statusTextView;
    UUID uuid = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");
    String deviceName;
    String deviceAddress;
    private ImageView imageView;
    AcceptThread acceptThread;
    ConnectedThread connectedThread;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket bluetoothSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    boolean acceptThreadIsStart = false;
    private final Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            String message = msg.getData().getString("message");
            statusTextView.setText(message);
            showDialog();
        }
    };

    private void showDialog()
    {
        if (!(this.isFinishing())) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setTitle("確認訊息").setMessage("確定為消費者增加點數?")
                    .setPositiveButton("好", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            //response
                            String response = "Sent from " + deviceName;
                            try {
                                mmOutStream.write(response.getBytes());
                                bluetoothSocket.close();
                                acceptThread.close();
                                if (receiverIsRegistered) {
                                    unregisterReceiver(mReceiver);
                                    receiverIsRegistered = false;
                                }
                                finish();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            dialog.dismiss();
                            System.exit(0);
                        }
                    }).create();

            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        statusTextView.setText("Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        statusTextView.setText("Turning Bluetooth off...");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        statusTextView.setText("Bluetooth on");
                        deviceName = bluetoothAdapter.getName();
                        deviceAddress = bluetoothAdapter.getAddress();
                        Bitmap QRcodeBitmap = QRCode.from(deviceAddress).bitmap();
                        imageView.setImageBitmap(QRcodeBitmap);
                        acceptThread = new AcceptThread();
                        acceptThread.start();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        statusTextView.setText("Turning Bluetooth on...");
                        break;
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            }
        }
    };

    @Override
    protected void onResume()
    {
        System.out.println("onResume");
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        System.out.println("onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        System.out.println("onDestroy");
        super.onDestroy();

        if(receiverIsRegistered) {
            unregisterReceiver(mReceiver);
            receiverIsRegistered = false;
        }

        acceptThread.close();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("onCreate");
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        statusTextView = (TextView) findViewById(R.id.status);
        imageView = (ImageView) findViewById(R.id.imageView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            statusTextView.setText("Not support Bluetooth.");
            Toast.makeText(this, "Not support Bluetooth.", Toast.LENGTH_SHORT);
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mReceiver, filter);
            receiverIsRegistered = true;
            if (bluetoothAdapter.isEnabled()) {
                statusTextView.setText("Bluetooth on");
                deviceName = bluetoothAdapter.getName();
                deviceAddress = bluetoothAdapter.getAddress();
                Bitmap QRcodeBitmap = QRCode.from(deviceAddress).bitmap();
                imageView.setImageBitmap(QRcodeBitmap);
                acceptThread = new AcceptThread();
                acceptThread.start();
            } else {
                statusTextView.setText("Bluetooth off");
                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(i, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                } else {
                    finish();
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class AcceptThread extends Thread
    {
        public void run()
        {
            // Keep listening until exception occurs or a socket is returned
            // MY_UUID is the app's UUID string, also used by the client code
            acceptThreadIsStart = true;
            if (bluetoothAdapter.isEnabled()) {
                try {
                    bluetoothServerSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(deviceName, uuid);
                    if (bluetoothServerSocket != null) {
                        BluetoothSocket socket = bluetoothServerSocket.accept();
                        if(socket != null) {
                            mHandler.post(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    statusTextView.setText("Accept");
                                }
                            });
                            if (socket.isConnected()) {
                                connectedThread = new ConnectedThread(socket);
                                connectedThread.start();
                                acceptThreadIsStart = false;
                            }
                        }
                    }
                } catch (IOException e) {
                    acceptThreadIsStart = false;
                    e.printStackTrace();
                }
            }

        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void close()
        {
            try {
                if (bluetoothServerSocket != null) {
                    bluetoothServerSocket.close();
                    acceptThreadIsStart = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread
    {
        boolean isSuccess = false;
        public ConnectedThread(BluetoothSocket socket)
        {
            bluetoothSocket = socket;
            mHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    statusTextView.setText("Connecting");
                }
            });
        }

        public void run()
        {
            byte[] buffer = new byte[10240];  // buffer store for the stream
            int bytes; // bytes returned from read()
            isSuccess = false;
            try {
                mmInStream = bluetoothSocket.getInputStream();
                mmOutStream = bluetoothSocket.getOutputStream();
                Thread watchDogThread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(!isSuccess) {
                            try {
                                mmInStream.close();
                                mmOutStream.close();
                                bluetoothServerSocket.close();
                                acceptThread = new AcceptThread();
                                acceptThread.start();
                                mHandler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        statusTextView.setText("Restart Accept Thread");
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                watchDogThread.start();
                bytes = mmInStream.read(buffer);
                isSuccess = true;
                mHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        statusTextView.setText("Connected");
                    }
                });
                // Send the obtained bytes to the UI activity
                String message = new String(buffer, "UTF-8");
                Message m = new Message();
                Bundle b = new Bundle();
                b.putString("message", message);
                m.setData(b);
                mHandler.sendMessage(m);
            } catch (IOException e) {
                mHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        statusTextView.setText("Read Fail");
                    }
                });
                /*
                try {
                    mmInStream.close();
                    mmOutStream.close();
                    bluetoothServerSocket.close();
                    acceptThread = new AcceptThread();
                    acceptThread.start();
                    mHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            statusTextView.setText("Restart Accept Thread");
                        }
                    });
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                */

                e.printStackTrace();
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes)
        {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel()
        {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
