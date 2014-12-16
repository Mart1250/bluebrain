package com.cannybots.cannybotsapp;




import android.app.AlertDialog;
import android.support.v4.app.ListFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.cannybots.ble.BLEDevicesListViewAdapter;
import com.cannybots.ble.BluetoothHelper;
import com.cannybots.ble.HexAsciiHelper;
import com.cannybots.ble.RFduinoService;
import com.cannybots.views.joystick.*;

import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import java.util.Timer;
import java.util.TimerTask;

// Disable Swiping, see: https://blog.svpino.com/2011/08/29/disabling-pagingswiping-on-android
// also see in project: /res/layout/activity_main.xml
class CustomViewPager extends ViewPager {

    private boolean enabled;

    public CustomViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.enabled = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.enabled) {
            return super.onTouchEvent(event);
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (this.enabled) {
            return super.onInterceptTouchEvent(event);
        }

        return false;
    }

    public void setPagingEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

public class MainActivity extends ActionBarActivity implements ActionBar.TabListener,BluetoothAdapter.LeScanCallback {

    private static final String TAG = "CannybotsActivity";

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    public static BluetoothDevice bluetoothDevice;

    public static RFduinoService rfduinoService;
    private boolean deviceHasBluetoothLE = false;


    // BLE callbacks

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "scanModeReceiver.onReceive");

            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            Log.i(TAG, "rfduinoServiceConnection.onServiceConnected");


            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (bluetoothDevice != null) {
                    if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "rfduinoServiceConnection.onServiceDisconnected");

            rfduinoService = null;
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "rfduinoReceiver.onReceive");

            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };


    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {

        byte advData[] = BluetoothHelper.parseAdvertisementFromScanRecord(scanRecord);
        Log.i(TAG, "Potential BLE Device found name = " + device.getName());
        if ( (advData[0] == 'C') && (advData[1] == 'B') && (advData[2] == '0') && (advData[3] == '1') ) {
           BLEDevicesListViewAdapter.addDevice(device);
           Log.i(TAG, "Found Cannybot! BLE Device Info = " + BluetoothHelper.getDeviceInfoText(device, rssi, scanRecord));
        }
    }

    private void BLE_onCreate() {
        Log.i(TAG, "BLE_onCreate");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            deviceHasBluetoothLE = true;
            bluetoothAdapter.enable();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("No Bluetooth LE hardware detected, you won't be able to connect to a Cannybot.")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //do things
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
            deviceHasBluetoothLE = false;
        }
    }


    private void BLE_onStart() {
        Log.i(TAG, "BLE_onStart");

        if (!deviceHasBluetoothLE)
            return;
        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        Joystick_stopTimer();

        joypadUpdateTimer = new Timer();
        joypadUpdateTimer.scheduleAtFixedRate(
                new TimerTask() {
                    public void run() {
                        JoypadFragment.sendJoypadUpdate(false);
                    }
                },
                0,
                50);
    }


    private void Joystick_stopTimer() {
        if (joypadUpdateTimer!=null) {
            joypadUpdateTimer.cancel();
            joypadUpdateTimer.purge();

        }
    }

    private void BLE_onStop() {
        Log.i(TAG, "BLE_onStop");

        if (!deviceHasBluetoothLE)
            return;

        Joystick_stopTimer();
        BLE_stopScanning();
        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }



    private void BLE_startScanning() {
        Log.i(TAG, "BLE_startScanning");

        if (!deviceHasBluetoothLE)
            return;
        // when scan pressed
        scanStarted = true;

        // find by 16 bit short code
        //bluetoothAdapter.startLeScan(new UUID[]{RFduinoService.UUID_SERVICE}, MainActivity.this);

        // Find all (then filter in 'onLeScan' callback )
        bluetoothAdapter.startLeScan(MainActivity.this);
    }

    public void BLE_stopScanning() {
        Log.i(TAG, "BLE_stopScanning");

        if (!deviceHasBluetoothLE)
            return;
        scanStarted = false;
        bluetoothAdapter.stopLeScan(this);
    }

    Timer joypadUpdateTimer;

    // when 'connect' pressed

    public void BLE_connect(BluetoothDevice device) {
        Log.i(TAG, "BLE_connect");


        if (!deviceHasBluetoothLE)
            return;
        bluetoothDevice = device;

        Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
        bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
    }

    // BLE UI
    private void addData(byte[] data) {
        Log.i(TAG, "RECV: " + HexAsciiHelper.bytesToHex(data));
    }


    private void BLE_disconnected() {
        Log.i(TAG, "BLE_disconnected");

        if (!deviceHasBluetoothLE)
            return;

        bluetoothDevice = null;
        BLE_onStop();
        BLE_onStart();
    }


    private void BLE_disconnect() {
        Log.i(TAG, "BLE_disconnect");

        if (rfduinoService != null) {
            rfduinoService.disconnect();
            rfduinoService.close();
            rfduinoService.initialize();
            BLE_disconnected();

        }
        bluetoothDevice = null;
    }


    @Override
    protected void onStart() {
        Log.i(TAG, "******  onStart *******");

        super.onStart();
        BLE_onStart();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "******  onStop *******");
        super.onStop();
        BLE_onStop();
    }

    /////////////////////////////////////////////




    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    CustomViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (CustomViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        BLE_onCreate();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        /*if (id == R.id.action_settings) {
            return true;
        }*/

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        Log.i(TAG, "onTabUnselected");

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        Log.i(TAG, "onTabReselected");

    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a JoypadFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    return JoypadFragment.newInstance();
                case 1:
                    return SettingsFragment.newInstance();
                case 2:
                    return ConnectionsFragment.newInstance();
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase();
                case 1:
                    return getString(R.string.title_section2).toUpperCase();
                case 2:
                    return getString(R.string.title_section3).toUpperCase();
            }
            return null;
        }


    }

    public static class SettingsFragment extends Fragment {

        public static SettingsFragment newInstance() {
            SettingsFragment fragment = new SettingsFragment();

            return fragment;
        }

        public SettingsFragment() {
        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_settings, container, false);

            return rootView;
        }
    }


        /**
     * Joystick fragment view.
     */
    public static class JoypadFragment extends Fragment {

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static JoypadFragment newInstance(/*int sectionNumber*/) {
            JoypadFragment fragment = new JoypadFragment();
            //Bundle args = new Bundle();
            //args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            //fragment.setArguments(args);
            return fragment;
        }

        public JoypadFragment() {
        }

        //static long lastTime   =0;
        static int xAxisValue  =0;
        static int yAxisValue  =0;
        static int zAxisValue  =0;
        static int buttonValue =0;

        static public void sendJoypadUpdate(boolean force) {
            /*long thisTime =  System.currentTimeMillis();
            if ( !force && (thisTime - lastTime ) < 100) {
                return;
            }
            lastTime=thisTime;*/
            Log.d(TAG, "joypad(x,y,z)=(" + xAxisValue + "," + yAxisValue  + "," + zAxisValue + ")");

            byte xAxis = (byte) ((xAxisValue+255)/2);
            byte yAxis = (byte) ((yAxisValue+255)/2);
            byte zAxis = (byte) ((zAxisValue+255)/2);
            byte button = (byte) buttonValue;

            if ( (MainActivity.rfduinoService != null) && (MainActivity.bluetoothDevice!=null)) {
                MainActivity.rfduinoService.send(new byte[]{xAxis,yAxis,button,zAxis});
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_joypad, container, false);
            FrameLayout joystickLayout = (FrameLayout) rootView.findViewById(R.id.joystick);
            FrameLayout throttleLayout = (FrameLayout) rootView.findViewById(R.id.throttle);

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams( FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT );


            JoystickView joystickView = new JoystickView(rootView.getContext());
            ThrottleView throttleView = new ThrottleView(rootView.getContext());
            throttleView.setLayoutParams(params);
            joystickLayout.addView(joystickView);


            throttleLayout.addView(throttleView, params);

            // add callbacks
            joystickView.setOnJostickMovedListener(new JoystickMovedListener() {
                @Override
                public void OnMoved(int pan, int tilt) {
                    xAxisValue=pan;
                    yAxisValue=-tilt;
                }
                @Override
                public void OnReleased() {
                    xAxisValue=0;
                    yAxisValue=0;
                    //sendJoypadUpdate(true);
                }
            });
            throttleView.setOnJostickMovedListener(new JoystickMovedListener() {
                @Override
                public void OnMoved(int pan, int tilt) {
                    zAxisValue=tilt;
                }
                @Override
                public void OnReleased() {
                    zAxisValue=255-75;
                    //sendJoypadUpdate(true);
                }
            });
            return rootView;
        }
    }

    // see: http://www.vogella.com/tutorials/AndroidListView/article.html
    public static class ConnectionsFragment extends ListFragment {

        private static BLEDevicesListViewAdapter adapter = new BLEDevicesListViewAdapter();
        public static ConnectionsFragment newInstance() {
            ConnectionsFragment fragment = new ConnectionsFragment();
            return fragment;
        }

        public ConnectionsFragment() {
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            /*Toast.makeText(l.getContext(),
                    "Click ListItem Number " + position, Toast.LENGTH_LONG)
                    .show();        }*/
            MainActivity activity = (MainActivity) getActivity();
            activity.BLE_stopScanning();
            BluetoothDevice device = (BluetoothDevice) getListAdapter().getItem(position);
            Log.i(TAG, "onListItemClick for BLE device:" + device);
            activity.BLE_connect(device);
            activity.getSupportActionBar().setSelectedNavigationItem(0);
        }

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            MainActivity activity = (MainActivity) getActivity();
            if (isVisibleToUser) {
                Log.i(TAG, "connections visible)");
                activity.BLE_disconnect();
                activity.BLE_startScanning();
            }
            else {
                Log.i(TAG, "connections NOT visible)");
                if (activity != null) {
                    //activity.BLE_stopScanning();
                    adapter.clear();
                }


            }
        }
    }


}
