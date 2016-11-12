package com.example.omer.chat42;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import com.example.omer.chat42.BluetoothService.MyBinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * This activity does all the work of setting up and managing bluetooth and wifi connection between
 * two devices
 **/
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // Constants that indicate the current mode
    private static final int WIFI_MODE = 0;
    private static final int BLUETOOTH_MODE = 1;
    private final static int REQUEST_ENABLE_BT = 1; // request to enable bluetooth

    // Constants that indicate the current connection state
    public static final int STATE_OFF = 0;
    public static final int STATE_TURNING_OFF = 1;
    public static final int STATE_ON = 2;
    public static final int STATE_TURNING_ON = 3;
    // Constants that indicate the current scan mode
    public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 4;
    public static final int SCAN_MODE_CONNECTABLE = 5;
    public static final int SCAN_MODE_NONE = 6;
    // Constants that indicate the connection status
    public static final int ACTION_ACL_CONNECTED = 7;
    public static final int ACTION_ACL_DISCONNECTED = 8;

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("ca87c0d0-afac-11de-8a39-0800200c9a66");

    // Layout's views
    private  Toolbar mMyToolbar;
    private  View mDistanceLayout;
    private  View mDiscoverableLayout;
    private  Button mSearchButton;
    private  Switch mDiscoverable;
    private  ListView mDevicesList;

    // Member fields
    private ArrayList<BluetoothDevice> mArrayDevices;
    private ArrayAdapter<String> mArrayAdapter;
    private static BroadcastReceiver mReceiver;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;
    private BluetoothDevice mConnectedDevice;
    private AcceptThread mServersideThread;
    private ConnectThread mConnectThread;
    private static int mState;
    private static int mScanMode;
    private static int mConnectionStatus;
    private static int mDeviceMode;
    private String mUserName = "user";

    BluetoothService mBluetoothService;
    boolean mServiceBound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // General init
        generalInit();

        // Bluetooth init
        bluetoothInit();

        // Check the device state - wifi or bluetooth
        mDeviceMode = checkState();


        // register to Receiver that created in bluetootInit method
        IntentFilter mIntentFilter = new IntentFilter();
        // Add all all the actions to filter
        mIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver,mIntentFilter);
        //registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        // initilaize the app with with device mode
        if (mDeviceMode == BLUETOOTH_MODE)
            initBluetoothMode();
        else
            initWifiMode();

        /////////////
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

    /**
     *  Create bounding between service class to this activity
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyBinder myBinder = (MyBinder) service;
            mBluetoothService = myBinder.getService();
            mServiceBound = true;
        }
    };

    /** on click listener */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.button_search):
                discoveringDevices();
                break;
            case (R.id.switch_discoverable):
                discoverable();
                break;
        }

    }

    @Override
    public void onDestroy(){
        unregisterReceiver(mReceiver);
    }

    /** Listener to the menu **/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Go to setting
            case R.id.action_settings:
                //check service
                if (mServiceBound)
                    Toast.makeText(this,mBluetoothService.getCheck(), Toast.LENGTH_SHORT).show();
                return true;

            // Log out from the current user
            case R.id.action_chat_history:
                Toast.makeText(this, "Chat history", Toast.LENGTH_SHORT).show();
                // unbind service
                if (mServiceBound) {
                    unbindService(mServiceConnection);
                    mServiceBound = false;
                }
                return true;

            // Log out from the current user
            case R.id.action_logout:
                Toast.makeText(this, "Log Out", Toast.LENGTH_SHORT).show();
                return true;

            // Exit app
            case R.id.action_exit:
                finish();
                System.exit(0);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * Initilaize the bluetooth connection
     */
    private void bluetoothInit() {

        //TODO check if the device is already connected to somebody else
        //TODO add option to disconnect
        //TODO add status line in the bottom screen

        mState = -1;
        mScanMode = -1;
        mConnectionStatus = -1;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(),"Your device does not support Bluetooth",
                    Toast.LENGTH_LONG).show();
        }

        // Enable bluetooth if not open
        enableBluetooth();

        // Set name for user
        mBluetoothAdapter.setName(mUserName);

        // Init serverside thread
        mServersideThread = new AcceptThread();

        // Create a BroadcastReceiver
        createBroadcastReceiver();

    }

    /**
     * Create a BroadcastReceiver for ACTION and STATES of the bluetooth
     * and register to the activity
     */
    private void createBroadcastReceiver(){

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                switch (action){
                    // When discovery finds a device
                    case BluetoothDevice.ACTION_FOUND:
                        // Get the BluetoothDevice object from the Intent
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        // Add the name and address to an array adapter to show in a ListView
                        mArrayAdapter.add(device.getName()+" ("+device.getAddress()+")");
                        mArrayDevices.add(device);
                        mArrayAdapter.notifyDataSetChanged();
                        break;

                    // When bluetooth state changed
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        switch(state) {
                            case BluetoothAdapter.STATE_OFF:
                                mState = STATE_OFF;
                                Toast.makeText(getApplicationContext(),"state: STATE_OFF",
                                        Toast.LENGTH_LONG).show();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                mState = STATE_TURNING_OFF;
                                Toast.makeText(getApplicationContext(),"state: STATE_TURNING_OFF",
                                        Toast.LENGTH_LONG).show();
                                break;
                            case BluetoothAdapter.STATE_ON:
                                mState = STATE_ON;
                                Toast.makeText(getApplicationContext(),"state: STATE_ON",
                                        Toast.LENGTH_LONG).show();
                                break;
                            case BluetoothAdapter.STATE_TURNING_ON:
                                mState = STATE_TURNING_ON;
                                Toast.makeText(getApplicationContext(),"state: STATE_TURNING_ON",
                                        Toast.LENGTH_LONG).show();
                                break;
                        }
                        break;
                    // When bluetooth mode changed
                    case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:
                        int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                        switch(mode) {
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                                mScanMode = SCAN_MODE_CONNECTABLE_DISCOVERABLE;
                                break;
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                                mScanMode = SCAN_MODE_CONNECTABLE;
                                break;
                            case BluetoothAdapter.SCAN_MODE_NONE:
                                mScanMode = SCAN_MODE_NONE;
                                break;
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        mConnectionStatus = ACTION_ACL_CONNECTED;
                        Toast.makeText(getApplicationContext(),"CONNECTED",
                                Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        mConnectionStatus = ACTION_ACL_DISCONNECTED;
                        Toast.makeText(getApplicationContext(),"DISCONNECTED",
                                Toast.LENGTH_LONG).show();
                        break;
                }


            }
        };

    }

    /**
     * Enable bluetooth if not enable
     */
    private void enableBluetooth() {

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Start discover other devices and update the device list
     */
    private void discoveringDevices(){
        if (mBluetoothAdapter.isDiscovering()) {
            // the button is pressed when it discovers, so cancel the discovery
            mBluetoothAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Stop discovering",
                    Toast.LENGTH_LONG).show();
        }
        else {
            mArrayAdapter.clear();  // clear adapter
            mArrayDevices.clear();  // clear bluetooth array devices
            mBluetoothAdapter.startDiscovery();
            Toast.makeText(getApplicationContext(),"Start discovering",
                    Toast.LENGTH_LONG).show();
        }
    }




    /**
     * General initilaize of the activity
     */
    private void generalInit() {

        // setup toolbar
        mMyToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(mMyToolbar);

        // Bind layout's view to class
        mDistanceLayout = (View)findViewById(R.id.layout_distance);
        mDiscoverableLayout = (View)findViewById(R.id.layout_discoverable);
        mSearchButton = (Button)findViewById(R.id.button_search);
        mDiscoverable = (Switch)findViewById(R.id.switch_discoverable);
        mDevicesList = (ListView)findViewById(R.id.listview_available_devices);

        // bind device list
        mArrayDevices = new ArrayList<>();
        mArrayAdapter = new ArrayAdapter<>(this,R.layout.item_device);
        mDevicesList.setAdapter(mArrayAdapter);

        // Set on click listener
        mSearchButton.setOnClickListener(this);
        mDiscoverable.setOnClickListener(this);

        mDevicesList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Toast.makeText(MainActivity.this,"connceting\n"+mArrayDevices.get(position).getName(), Toast.LENGTH_SHORT).show();
                connectToDevice(mArrayDevices.get(position));
            }
        });
    }

    /**
     * TODO Barr
     */
    private void initWifiMode() {

        //TODO Change icon mode to wifi
        // Disable discoverable function
        mDiscoverableLayout.setVisibility(View.GONE);
    }

    /**
     * init app in bluetooth mode
     */
    private void initBluetoothMode() {
        // TODO Change icon mode to bluetooth
        // Disable distance function
        mDistanceLayout.setVisibility(View.GONE);
    }

    /** todo is it need to be here?
     *  The method checks if there is available internet in the device.
     *  if there is available internet bluetooth will set off
     *  otherwise the app will be on bluetooth mode.
     */
    private int checkState() {
        //TODO check if there is available internet
        return BLUETOOTH_MODE;
    }


    /** Make the action button appear in old devices **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    /**
     *  Make the device be discoverable
     */
    private void discoverable(){
        if (!mDiscoverable.isChecked()) {

            stopDiscoverable();

            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1);
            startActivity(discoverableIntent);

            Toast.makeText(getApplicationContext(),"Device is not discoverable",
                    Toast.LENGTH_LONG).show();
        }
        else {

            if (mBluetoothAdapter.isEnabled())
                enableBluetooth();

            Toast.makeText(getApplicationContext(),"Device is discoverable",
                    Toast.LENGTH_LONG).show();

            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
            startDiscoverable();

        }
    }




    /**
     * The Handler that gets information back from the threads
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.SOCKET_RECEIVED:

                    // close discover devices
                    mBluetoothAdapter.cancelDiscovery();

                    // close discoverable
                    if (mDiscoverable.isChecked()){
                        turnOffDiscoverableButton();
                    }

                    // close the server side socket
                    cancelThreadIfAlive();

                    //TODO go to chatActivity and pass the relevant info
                    break;

                case Constants.CONNECTING_FAILURE:

                    // close the thread
                   // mConnectThread.cancel();

                    Toast.makeText(getApplicationContext(),"Connecting failed",
                            Toast.LENGTH_LONG).show();
                    break;

                case Constants.NO_BLUETOOTH_SUPPORT:

                    break;

                    }

            }
        };

    /**
     * Turnoff discoverable Button
     */
    private void turnOffDiscoverableButton() {
        stopDiscoverable();
        mDiscoverable.setChecked(false);
    }

    /**
     * Connect to Selected device
     * @param bluetoothDevice
     */
    private void connectToDevice(BluetoothDevice bluetoothDevice){
        mConnectThread = new ConnectThread(bluetoothDevice);
        mConnectThread.start();
    }

    /**
     *  Destroy thread if it's alive
     */
    private void cancelThreadIfAlive(){

        if (mServersideThread.isAlive())
            mServersideThread.cancel();

        mServersideThread = new AcceptThread();
    }

    /** Start discoverable mode **/
    private void startDiscoverable() {mServersideThread.start();}


    /**
     * Turnoff discoverable mode
     */
    private void stopDiscoverable(){
        mServersideThread.cancel();
        mServersideThread = new AcceptThread();
    }


    /**
     *  Thread connecting as a server side
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("RELAY", MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {

                    // update socket
                    mBluetoothSocket = socket;

                    // TODO find who is the device

                    // Send message back to the Activity for a new Socket received
                    mHandler.obtainMessage(Constants.SOCKET_RECEIVED).sendToTarget();
                }
            }
        }
        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }



    /**
     *  Thread connecting as a client side
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                // Send failure message back to the  main Activity
                mHandler.obtainMessage(Constants.CONNECTING_FAILURE).sendToTarget();
                return;
            }
            // update socket
            mBluetoothSocket = mmSocket;

            // update the connected device
            mConnectedDevice = mmDevice;
            // Send message back to the Activity for a new Socket received
            mHandler.obtainMessage(Constants.SOCKET_RECEIVED).sendToTarget();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

    }

}
