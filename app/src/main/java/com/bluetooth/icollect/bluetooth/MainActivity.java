package com.bluetooth.icollect.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
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


public class MainActivity extends Activity
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
    ConnectedThread connectedThread;
    private final Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            String message = msg.getData().getString("message");
            statusTextView.setText(message);
        }
    };

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
                        arrayAdapter.clear();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        statusTextView.setText("Turning Bluetooth off...");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        statusTextView.setText("Bluetooth on");
                        showDevices();
                        bluetoothAdapter.startDiscovery();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        statusTextView.setText("Turning Bluetooth on...");
                        break;
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int size = arrayAdapter.getCount();
                for (int i = 0; i < size; i++) {
                    String row = arrayAdapter.getItem(i);
                    if (row.contains(device.getAddress())) {
                        return;
                    }
                }
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (receiverIsRegistered) {
            unregisterReceiver(mReceiver);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        statusTextView = (TextView) findViewById(R.id.status);
        imageView = (ImageView) findViewById(R.id.imageView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            statusTextView.setText("Not support Bluetooth.");
            Toast.makeText(this, "Not support Bluetooth.", Toast.LENGTH_SHORT);
        } else {
            deviceName = bluetoothAdapter.getName();
            deviceAddress = bluetoothAdapter.getAddress();
            Bitmap QRcodeBitmap = QRCode.from(deviceAddress).bitmap();
            imageView.setImageBitmap(QRcodeBitmap);
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mReceiver, filter);
            receiverIsRegistered = true;
            if (bluetoothAdapter.isEnabled()) {
                statusTextView.setText("Bluetooth on");
                bluetoothAdapter.startDiscovery();
                showDevices();
                new AcceptThread().start();
            } else {
                statusTextView.setText("Bluetooth off");
                Intent turnOnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnOnIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                new AcceptThread().start();
                break;
        }
    }

    private void showDevices()
    {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                int size = arrayAdapter.getCount();
                for (int i = 0; i < size; i++) {
                    String row = arrayAdapter.getItem(i);
                    if (row.contains(device.getAddress())) {
                        return;
                    }
                }
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
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
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread()
        {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(deviceName, uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        public void run()
        {
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                BluetoothSocket socket = null;
                try {
                    socket = mmServerSocket.accept();
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel()
        {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            mHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    statusTextView.setText("Connected");
                }
            });

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run()
        {
            byte[] buffer = new byte[4096];  // buffer store for the stream
            StringBuffer sb = new StringBuffer();
            StringBuilder stringBuilder = new StringBuilder();
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    if(mmSocket.isConnected()) {
                        bytes = mmInStream.read(buffer);
                        // Send the obtained bytes to the UI activity
                        final String message = new String(buffer, 0, bytes);
                        mHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                statusTextView.setText(message);
                            }
                        });
                        Message m = new Message();
                        Bundle b = new Bundle();
                        b.putString("message", message);
                        m.setData(b);
                        mHandler.sendMessage(m);

                        //response
                        String response = "Sent from " + deviceName;
                        connectedThread.write(response.getBytes());
                        mmSocket.close();
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
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
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
