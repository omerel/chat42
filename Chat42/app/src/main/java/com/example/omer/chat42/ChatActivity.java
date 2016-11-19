package com.example.omer.chat42;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class ChatActivity extends AppCompatActivity  implements View.OnClickListener,Constants {

    // Layout's views
    private Toolbar mMyToolbar;
    private EditText mCommandChat;
    private ImageButton mSendButton;
    private ListView mListViewChat;

    // fileds
    private String mDeviceAddress;
    private String mConnectedDeviceAddress;
    private static BroadcastReceiver mReceiver;
    private static IntentFilter mIntentFilter;
    private boolean mServiceBound = false;
    private static BluetoothService mBluetoothService;
    private String mUserName;
    private ChatMessage mTempMessage;
    private ChatAdapter mChatAdapter;
    private ArrayList<ChatMessage> mChatDialogList;

    protected final Messenger mMessenger = new Messenger(new IncomingHandler());
    private static Messenger mServiceMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mUserName =  getIntent().getStringExtra("USER");

        // start bluetooth service and bind it to this activity
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // General init
        generalInit();
    }

    private void generalInit() {

        // setup toolbar
        mMyToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(mMyToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle("Chat with "+mUserName);

        // Bind layout's view to class
        mCommandChat = (EditText)findViewById(R.id.editText_command_chat);
        mSendButton = (ImageButton)findViewById(R.id.imageButton_send_msg);
        mListViewChat = (ListView)findViewById(R.id.listview_chat);


        // Init ChatDialog list
        mChatDialogList =  new ArrayList<>();
        // Add test msg to show as history
        mTempMessage = new ChatMessage(mDeviceAddress, mConnectedDeviceAddress, "This is a test",Calendar.getInstance());
      //  mChatDialogList.add(mTempMessage);

        mChatAdapter = new ChatAdapter(this,mChatDialogList);
        mListViewChat.setAdapter(mChatAdapter);

        // Set on click listener
        mSendButton.setOnClickListener(this);

        // Create a BroadcastReceiver
        createBroadcastReceiver();

        // register to Receiver
        mIntentFilter = new IntentFilter();
        // Add all all the actions to filter
        mIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver,mIntentFilter);
    }

    /**
     * Make the action button appear in old devices
     **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Listener to the menu
     **/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Go to setting
            case R.id.action_settings:
                //check service
                //  if (mIsServiceBound)
                //TODO
                Toast.makeText(this, "Replace me!", Toast.LENGTH_SHORT).show();
                return true;

            // Log out from the current user
            case R.id.action_chat_history:
                //TODO
                Toast.makeText(this, "Replace me!", Toast.LENGTH_SHORT).show();
                return true;

            // Log out from the current user
            case R.id.action_logout:
                //TODO
                Toast.makeText(this, "Replace me!", Toast.LENGTH_SHORT).show();
                return true;

            // go back;
            case android.R.id.home:
                goToMainActivity();
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


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case (R.id.imageButton_send_msg):
                String content = mCommandChat.getText().toString();
                mCommandChat.setText("");
                if (!content.isEmpty()){
                    sendMessageToService(content);
                    addMessageToConversation(mDeviceAddress,mConnectedDeviceAddress,
                            mBluetoothService.getName()+":\n"+content);
                }
                break;
        }
    }

    private void addMessageToConversation(String sender, String receiver ,String content) {

        // create message;
        mTempMessage = new ChatMessage(sender,receiver,content,Calendar.getInstance());
        // add to adapter
        mChatAdapter.add(mTempMessage);
        mChatAdapter.notifyDataSetChanged();
        mListViewChat.setSelection(mListViewChat.getAdapter().getCount()-1);
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
            BluetoothService.MyBinder myBinder = (BluetoothService.MyBinder) service;
            mBluetoothService = myBinder.getService();
            mServiceMessenger = myBinder.getMessenger();
            mServiceBound = true;
            // register Messenger in the service
            registerToServiceMessenger();
            // get devices addresses
            mDeviceAddress = mBluetoothService.getAddress();
            mConnectedDeviceAddress = mBluetoothService.getConnectedAddress();
            // update address in adapter
            mChatAdapter.setMyAddress(mDeviceAddress);

        }
    };

    private void goToMainActivity() {

        sendRequestToService(CLOSE_SOCKET);

        unregisterReceiver(mReceiver);

        unbindService(mServiceConnection);
        mServiceBound = false;

        finish();

        Intent goToMainActivity = new Intent(this,MainActivity.class);
        startActivity(goToMainActivity);
    }

    /**
     * Handler of incoming messages from service
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    String content = msg.getData().getString("string");
                    addMessageToConversation(mConnectedDeviceAddress,mDeviceAddress,content);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    /**
     * Send string ChatMessage value to service
     */
    private void sendMessageToService(String message)  {

        // Send data as a String
        Bundle bundle = new Bundle();
        bundle.putString("string",message);
        Message msg = Message.obtain(null, MESSAGE_WRITE);
        msg.setData(bundle);
        try {
            mServiceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     *   Send request to service
     */
    private void sendRequestToService(int request)  {
        try {
            mServiceMessenger.send(Message.obtain(null, request));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void registerToServiceMessenger(){
        Message msg = Message.obtain(null, REGISTER_ACTIVITY);
        msg.replyTo = mMessenger;
        try {
            mServiceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
                    // When bluetooth state changed
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        switch(state) {
                            case BluetoothAdapter.STATE_OFF:
                                Toast.makeText(getApplicationContext(),"Bluetooth: STATE_OFF",
                                        Toast.LENGTH_LONG).show();
                                goToMainActivity();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                Toast.makeText(getApplicationContext(),"Bluetooth: STATE_TURNING_OFF",
                                        Toast.LENGTH_LONG).show();
                                break;
                            case BluetoothAdapter.STATE_ON:
                                Toast.makeText(getApplicationContext(),"Bluetooth: STATE_ON",
                                        Toast.LENGTH_LONG).show();
                                break;
                            case BluetoothAdapter.STATE_TURNING_ON:
                                Toast.makeText(getApplicationContext(),"Bluetooth: STATE_TURNING_ON",
                                        Toast.LENGTH_LONG).show();
                                break;
                        }
                        break;

                    // When bluetooth mode changed
                    case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:
                        int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                        switch(mode) {
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                                break;
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                                break;
                            case BluetoothAdapter.SCAN_MODE_NONE:
                                break;
                        }
                        break;

                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Toast.makeText(getApplicationContext(),"CONNECTED",
                                Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Toast.makeText(getApplicationContext(),"DISCONNECTED",
                                Toast.LENGTH_LONG).show();
                        goToMainActivity();
                        break;
                }
            }
        };
    }

}
