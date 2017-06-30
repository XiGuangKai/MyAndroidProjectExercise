/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.gps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class GPS extends Test_Base
{
    private LocationManager mLocationManager = null;
    private TextView mGpsInfoTextView = null;
    private TextView mGpsLocationInfoTextView = null;
    private TextView mGpsErrorInfoTextView = null;
    private TextView mGpsLocationValueTextView = null;
    private TextView mSatelliteInfoTextView = null;
    private boolean mInitGpsStatus = false;
    private boolean mCurrGpsStatus = false;
    private List<GpsSatellite> mNumSatelliteList = new ArrayList<GpsSatellite>();
    private ListView mlistView;
    private ArrayList<HashMap<String, String>> mGpslist = new ArrayList<HashMap<String, String>>();
    double mLatitude = -999;
    double mLongitude = -999;

    private static long GPS_ENABLE_TIMEOUT_MSECS = 10000;

    private boolean isPermissionAllowed = false;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "GPS";
        super.onCreate(savedInstanceState);

        dbgLog(TAG, "onCreate", 'v');

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.gps_layout);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (1001 == requestCode)
        {
            if (grantResults.length > 0)
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    isPermissionAllowed = true;
                }
                else
                {
                    isPermissionAllowed = false;
                    finish();
                }
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        dbgLog(TAG, "onResume", 'i');

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1001);
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            mGpsInfoTextView = (TextView) findViewById(com.motorola.motocit.R.id.gpstextview_info);
            mGpsErrorInfoTextView = (TextView) findViewById(com.motorola.motocit.R.id.gpstextview_nouse_1);
            mGpsLocationInfoTextView = (TextView) findViewById(com.motorola.motocit.R.id.gpstextview_gpslocation);
            mGpsLocationValueTextView = (TextView) findViewById(com.motorola.motocit.R.id.gpstextview_gpslocation_value);
            mSatelliteInfoTextView = (TextView) findViewById(com.motorola.motocit.R.id.gpstextview_satelliteinfo);
            mGpsInfoTextView.setText("GPS Test ");
            mGpsLocationInfoTextView.setText("GPS Location\n" + "Latitude:\n" + "Longitude:");
            mSatelliteInfoTextView.setText("Satellite Info\n" + "Satellite Count: 0");
            mlistView = (ListView) findViewById(com.motorola.motocit.R.id.list_gpsinfo);

            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            // Get current gps status
            if (mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
            {
                dbgLog(TAG, "Gps enabled", 'v');
                mInitGpsStatus = true;
                mCurrGpsStatus = true;
            }
            else
            {
                dbgLog(TAG, "Gps disabled", 'v');
                mInitGpsStatus = false;

                // pause a bit so user can read message if not started from
                // CommServer
                if (wasActivityStartedByCommServer() == false)
                {
                    mGpsInfoTextView.setText("GPS Test: Enabling GPS");

                    // pause a bit so user can read message
                    try
                    {
                        Thread.sleep(200);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    if (enableGPS())
                    {
                        mGpsInfoTextView.setText("GPS Test: GPS successfully enabled");
                    }
                    else
                    {
                        mGpsInfoTextView.setText("GPS Test: Failed to enabled GPS");
                    }

                    // pause a bit so user can read message
                    try
                    {
                        Thread.sleep(200);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

                mGpsInfoTextView.setText("GPS Test ");
            }

            if (mCurrGpsStatus)
            {
                // set location listener, update every 1s, shortest distance.
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsLocationListener);
                mLocationManager.addGpsStatusListener(gpsStatusListener);

                getGpsLocation();
            }

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run GPS test");
        }
    }

    private class runEnableGps implements Runnable
    {
        @Override
        public void run()
        {
            enableGPS();
        }
    }

    private boolean enableGPS()
    {
        if (mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
        {
            dbgLog(TAG, "Gps already enabled", 'v');

            // set location listener, update every 1s, shortest distance.
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsLocationListener);
            mLocationManager.addGpsStatusListener(gpsStatusListener);

            mCurrGpsStatus = true;

            return true;
        }

        dbgLog(TAG, "Enabling GPS", 'v');

        Context context = getApplicationContext();
        requestStateChange(context, true);

        dbgLog(TAG, "Wait for GPS to be enabled", 'v');

        long startTime = System.currentTimeMillis();
        while (!mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
        {
            dbgLog(TAG, "Waiting for GPS to start", 'v');

            if ((System.currentTimeMillis() - startTime) > GPS_ENABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to start GPS", 'e');
                return false;
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                return false;
            }
        }
        dbgLog(TAG, "GPS should be enabled", 'v');

        // set location listener, update every 1s, shortest distance.
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsLocationListener);
        mLocationManager.addGpsStatusListener(gpsStatusListener);

        mCurrGpsStatus = true;

        return true;
    }

    private boolean disableGPS()
    {
        if (!mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
        {
            dbgLog(TAG, "Gps already disabled", 'v');
            mCurrGpsStatus = false;
            mNumSatelliteList.clear();

            return true;
        }

        dbgLog(TAG, "disabling GPS", 'v');

        Context context = getApplicationContext();
        requestStateChange(context, false);

        dbgLog(TAG, "Wait for GPS to be disabled", 'v');

        long startTime = System.currentTimeMillis();
        while (mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
        {
            dbgLog(TAG, "Waiting for GPS to stop", 'v');

            if ((System.currentTimeMillis() - startTime) > GPS_ENABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to stop GPS", 'e');
                return false;
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                return false;
            }
        }
        dbgLog(TAG, "GPS should be disabled", 'v');

        mCurrGpsStatus = false;
        mNumSatelliteList.clear();

        return true;
    }

    // Pulled this function from:
    // http://sse.am.mot.com/source/xref/main-dev/packages/apps/Settings/src/com/android/settings/widget/SettingsAppWidgetProvider.java
    public void requestStateChange(final Context context, final boolean desiredState)
    {
        final ContentResolver resolver = context.getContentResolver();
        new AsyncTask<Void, Void, Boolean>()
        {
            @Override
            protected Boolean doInBackground(Void...args)
            {
                Settings.Secure.setLocationProviderEnabled(resolver, LocationManager.GPS_PROVIDER, desiredState);
                return desiredState;
            }

        }.execute();
    }

    private void getGpsLocation()
    {
        dbgLog(TAG, "getGpsLocation", 'v');

        String mProvider = LocationManager.GPS_PROVIDER;
        Location mCurrentLocation = mLocationManager.getLastKnownLocation(mProvider);

        updateToNewLocation(mCurrentLocation);
    }

    private GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener()
    {
        @Override
        public void onGpsStatusChanged(int event)
        {
            dbgLog(TAG, "onGpsStatusChanged", 'v');

            switch (event)
            {
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    dbgLog(TAG, "First locationing", 'v');
                    break;
                case GpsStatus.GPS_EVENT_STARTED:
                    dbgLog(TAG, "start locationing", 'v');
                    break;
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    dbgLog(TAG, "GPS_EVENT_SATELLITE_STATUS", 'v');
                    GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                    int mMaxSatellites = gpsStatus.getMaxSatellites();
                    Iterator<GpsSatellite> mSatellitesIterator = gpsStatus.getSatellites().iterator();
                    int mSatelliteCount = 0;
                    mNumSatelliteList.clear();
                    while (mSatellitesIterator.hasNext() && (mSatelliteCount <= mMaxSatellites))
                    {
                        GpsSatellite mSatellites = mSatellitesIterator.next();
                        mNumSatelliteList.add(mSatellites);
                        mSatelliteCount++;
                    }
                    dbgLog(TAG, "Satellite Count:" + mSatelliteCount, 'v');
                    mSatelliteInfoTextView.setText("Satellite Info\n" + "Satellite Count: " + mSatelliteCount);
                    mGpslist.clear();
                    for (int i = 0; i < mNumSatelliteList.size(); i++)
                    {
                        HashMap<String, String> map = new HashMap<String, String>();
                        map.put("SatelliteInfo", (i + 1) + "  Snr: " + (mNumSatelliteList.get(i).getSnr()));
                        mGpslist.add(map);
                    }

                    SimpleAdapter mlistAdapter = new SimpleAdapter(GPS.this,
                            mGpslist,
                            com.motorola.motocit.R.layout.gpslist_item,
                            new String[] { "SatelliteInfo" },
                            new int[] { com.motorola.motocit.R.id.GpsItemInfo });
                    mlistView.setAdapter(mlistAdapter);
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    dbgLog(TAG, "GPS_EVENT_STOPPED", 'v');
                    break;
                default:
                    break;
            }
        }
    };

    private final LocationListener gpsLocationListener = new LocationListener()
    {

        @Override
        public void onLocationChanged(Location location)
        {
            dbgLog(TAG, "onLocationChanged", 'v');
            updateToNewLocation(location);
        }

        // call this when provider is disabled.
        @Override
        public void onProviderDisabled(String provider)
        {
            dbgLog(TAG, "Provider now is disabled..", 'v');
        }

        // call this when provider is enabled.
        @Override
        public void onProviderEnabled(String provider)
        {
            dbgLog(TAG, "Provider now is enabled..", 'v');
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            if (status == LocationProvider.AVAILABLE)
            {
                dbgLog(TAG, "gps status: available\n", 'v');
            }
            else if (status == LocationProvider.OUT_OF_SERVICE)
            {
                dbgLog(TAG, "gps status: out of service\n", 'v');
            }
            else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
            {
                dbgLog(TAG, "gps status: temporaily unavailable\n", 'v');
            }
        }
    };

    private void updateToNewLocation(Location location)
    {
        dbgLog(TAG, "updateToNewLocation", 'v');
        if (location != null)
        {
            mLatitude = location.getLatitude();
            mLongitude = location.getLongitude();
            mGpsLocationValueTextView.setText("\n" + mLatitude + "\n" + mLongitude);
            dbgLog(TAG, "Latitude: " + mLatitude + "  Longitude: " + mLongitude, 'd');
        }
        else
        {
            mLatitude = -999;
            mLongitude = -999;
            mGpsLocationValueTextView.setText("\nUNKNOWN" + "\nUNKNOWN");
            dbgLog(TAG, "Latitude: UNKNOWN" + "\nLongitude: UNKNOWN", 'd');
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        dbgLog(TAG, "onPause", 'v');

        if ((!wasActivityStartedByCommServer() || isFinishing()) && isPermissionAllowed)
        {
            mLocationManager.removeUpdates(gpsLocationListener);
        }

        // Turn off GPS
        if (!mInitGpsStatus && !wasActivityStartedByCommServer() && isPermissionAllowed)
        {
            disableGPS();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "GPS Test:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "GPS Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("ENABLE_GPS"))
        {
            runEnableGps enableGps = new runEnableGps();
            runOnUiThread(enableGps);

            long startTime = System.currentTimeMillis();
            while (mCurrGpsStatus != true)
            {
                if ((System.currentTimeMillis() - startTime) > GPS_ENABLE_TIMEOUT_MSECS)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to enable GPS"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_GPS"))
        {
            boolean disableStatus = disableGPS();

            if (disableStatus == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to disable GPS"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("GET_GPS_STATE"))
        {
            List<String> strDataList = new ArrayList<String>();

            String gpsState;

            boolean enableStatus = mLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);

            if (enableStatus == true)
            {
                gpsState = "ON";
            }
            else
            {
                gpsState = "OFF";
            }

            strDataList.add("GPS_STATE=" + gpsState);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("GET_LOCATION_INFO"))
        {
            getGpsLocation();

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("LATITUDE=" + mLatitude);
            strDataList.add("LONGITUDE=" + mLongitude);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SATELLITE_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("NUMBER_OF_SATELLITES_FOUND=" + mNumSatelliteList.size());

            for (int i = 0; i < mNumSatelliteList.size(); i++)
            {
                strDataList.add("SATELLITE_" + i + "_AZIMUTH=" + mNumSatelliteList.get(i).getAzimuth());
                strDataList.add("SATELLITE_" + i + "_ELEVATION=" + mNumSatelliteList.get(i).getElevation());
                strDataList.add("SATELLITE_" + i + "_PRN=" + mNumSatelliteList.get(i).getPrn());
                strDataList.add("SATELLITE_" + i + "_SNR=" + mNumSatelliteList.get(i).getSnr());
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add("GPS");
        strHelpList.add("");
        strHelpList.add("This activity brings up the GPS Test");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("ENABLE_GPS - Turns on GPS");
        strHelpList.add("  ");
        strHelpList.add("DISABLE_GPS - Turns off GPS");
        strHelpList.add("  ");
        strHelpList.add("GET_GPS_STATE - Gets the GPS state ON or OFF");
        strHelpList.add("  ");
        strHelpList.add("GET_LOCATION_INFO - Gets the latitude and longitude");
        strHelpList.add("  ");
        strHelpList.add("GET_SATELLITE_INFO - Gets the number of satellites and azimuth, elevation, PRN, and SNR for each satellite found");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "GPS Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "GPS Test:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeUp()
    {
        return true;
    }

    @Override
    public boolean onSwipeDown()
    {
        if (modeCheck("Seq"))
        {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

            return false;
        }
        else
        {
            systemExitWrapper(0);
        }
        return true;
    }
}
