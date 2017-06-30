/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.mods;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Toast;

import com.motorola.mod.ModManager;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.motorola.mod.ModBacklight;
import com.motorola.mod.ModContract;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModDisplay;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModProtocol.Protocol;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class HdkTest extends Test_Base
{

    private ModManager mModManager;

    Handler mHandler = null;
    ModsHdkFragment mModsHdkFragment = null;
    ModsTestPatternFragment mModsTestPatternFragment = null;
    FragmentManager mFragmentManager = null;
    private ListPreference mDisplayOutput;
    private SwitchPreference mDisplayOnOff;

    List<ModDevice> mModConnectedList = null;
    private final Lock lockModConnectedList = new ReentrantLock();

    private final static String PREF_DISPLAY_OUTPUT = "pref_display_output";

    private BroadcastReceiver mModBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_ATTACH.equals(action))
            {
                dbgLog(TAG, "Mod attached", 'i');
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run()
                    {
                        mModsHdkFragment.updateStatus(true);
                    }
                }, 200);
            }
            if (ModManager.ACTION_MOD_DETACH.equals(action))
            {
                dbgLog(TAG, "Mod detached", 'i');
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run()
                    {
                        mModsHdkFragment.updateStatus(false);
                    }
                }, 200);
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle)
    {
        TAG = "MotoMods_HDK";
        super.onCreate(bundle);

        mModManager = MotoMods.getModManager();
        dbgLog(TAG, "mModManager=" + mModManager, 'i');

        IntentFilter filter = new IntentFilter();
        filter.addAction(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        registerReceiver(mModBroadcastReceiver, filter);
        mFragmentManager = getFragmentManager();
        mModsHdkFragment = new ModsHdkFragment();
        mModsTestPatternFragment = new ModsTestPatternFragment();
        mFragmentManager.beginTransaction()
                .replace(android.R.id.content, mModsHdkFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mHandler = new Handler();
        mModsHdkFragment.getModConnectedList();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(mModBroadcastReceiver);
        mHandler.removeCallbacksAndMessages(null);
    }

    class ModsHdkFragment extends PreferenceFragment
    {
        Handler mDisplayHandler = null;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            dbgLog(TAG, "ModsHdkFragment: onCreate", 'i');

            addPreferencesFromResource(com.motorola.motocit.R.layout.mods_hdk);
            mDisplayHandler = new Handler();
            getModConnectedList();
            setDisplayBacklight(); // set backlight brightness to 150
            selectOutputSource(); // select DSI or MyDP
            setupDisplayTurnOnOff(); // turn display on or off
            setupShowTestPattern(); // show test pattern
            updateStatus(true); // show hdk mod attach status
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState)
        {
            dbgLog(TAG, "ModsHdkFragment: onCreateView", 'i');
            setNormalScreen();
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        private void getModConnectedList()
        {
            mModConnectedList = null;

            try
            {
                lockModConnectedList.lock();
                int modCheckAttempt = 1;
                long startTime = SystemClock.uptimeMillis();

                do
                {
                    dbgLog(TAG, "Checking for Mod Connection, attempt: " + modCheckAttempt++, 'i');
                    mModConnectedList = mModManager.getModList(true);
                    dbgLog(TAG, "Number of Mods= " + mModConnectedList.size(), 'i');
                    if (mModConnectedList == null || mModConnectedList.isEmpty())
                    {
                        try
                        {
                            Thread.sleep(5);
                        }
                        catch (Exception e)
                        {}
                    }
                }
                while ((mModConnectedList == null || mModConnectedList.isEmpty()) && ((SystemClock.uptimeMillis() - startTime) < 5000));
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Error when getting connected Mods list, " + e.getMessage(), 'e');
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }

        private void selectOutputSource()
        {
            mDisplayOutput = (ListPreference) findPreference(getString(com.motorola.motocit.R.string.pref_title_display_output));
            String[] str = getResources().getStringArray(com.motorola.motocit.R.array.display_output_text);
            mDisplayOutput.setSummary(str[1]);
            String[] values = getResources().getStringArray(com.motorola.motocit.R.array.display_output_values);
            mDisplayOutput.setValue(values[1]);
            selectOutput("MYDP"); // default to MyDP
            mDisplayOutput.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    if (mDisplayOutput.getValue().equals(newValue))
                    {
                        return false;
                    }

                    String selectedOutput = "";
                    int value = Integer.parseInt((String) newValue);
                    // DSI
                    if (value == 0)
                    {
                        selectedOutput = "DSI";
                    }
                    // MyDP
                    else
                    {
                        selectedOutput = "MYDP";
                    }

                    String[] str = getResources().getStringArray(com.motorola.motocit.R.array.display_output_text);
                    if (value < str.length)
                    {
                        mDisplayOutput.setSummary(str[value]);
                    }

                    selectOutput(selectedOutput);

                    // Disable the button temporarily
                    // re-enable it 2 seconds later after display output switch
                    // is done
                    mDisplayOnOff.setEnabled(false);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run()
                        {
                            mDisplayOnOff.setEnabled(isDisplayAvailable());
                        }
                    }, 2000);

                    return true;
                }
            });
            setDisplayOutputButtonState();
        }

        private void selectOutput(String output)
        {
            // Factory Raw Command Format
            // 4 BYTES PID
            // 4 BYTES VID
            // 1 BYTE VERSION
            // 1 BYTE COMMAND ID
            // 1 BYTE PAYLOAD SIZE
            // VARIABLE BYTES PAYLOAD
            byte VERSION = 0x01;
            byte[] HEADER = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, VERSION }; // pid,vid,version

            byte[] bPid = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mModConnectedList.get(0).getProductId()).array();
            byte[] bVid = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mModConnectedList.get(0).getVendorId()).array();

            System.arraycopy(bPid, 0, HEADER, 0, bPid.length);
            System.arraycopy(bVid, 0, HEADER, 4, bVid.length);

            byte payload[] = null;
            byte message[] = null;

            int numberOfReceivedBytes = 0;

            if (output.toString().equals("MYDP"))
            {
                payload = new byte[] { 0x00, 0x01, 0x02 };
                message = concat(HEADER, payload);
            }
            else if (output.toString().equals("DSI"))
            {
                payload = new byte[] { 0x00, 0x01, 0x01 };
                message = concat(HEADER, payload);
            }

            String dataString = "";
            if (message != null)
            {
                for (byte b : message)
                {
                    dataString += String.format("%02x", b);
                }
            }

            dbgLog(TAG, "selected output=" + output, 'i');
            dbgLog(TAG, "SENT_RAW_MESSAGE=" + dataString, 'i');

            byte[] responseData = sendRawProtocolData(message);

            if (responseData == null)
            {
                dbgLog(TAG, "RESPONSE_LENGTH=0", 'i');
            }
            else
            {
                String responseDataString = "";

                numberOfReceivedBytes = 0;
                for (byte b : responseData)
                {
                    numberOfReceivedBytes++;
                    responseDataString += String.format("%02x", b);
                }

                dbgLog(TAG, "RESPONSE_LENGTH=" + numberOfReceivedBytes, 'i');
                dbgLog(TAG, "RESPONSE_DATA=" + responseDataString, 'i');
            }
        }

        private void setDisplayBacklight()
        {
            ModBacklight modBacklight = null;
            if (mModManager != null)
            {
                dbgLog(TAG, "Trying to get ModBacklight Class", 'i');
                modBacklight = (ModBacklight) mModManager.getClassManager(Protocol.LIGHTS);
            }

            if (modBacklight != null)
            {
                dbgLog(TAG, "setting backlight brightness to 150", 'i');
                modBacklight.setModBacklightBrightness((byte) 150);
            }
        }

        // Disable switch option when display device not available,
        // or display state is on
        private void setDisplayOutputButtonState()
        {
            if (isDisplayAvailable() && !getDisplayState())
            {
                mDisplayOutput.setEnabled(true);
            }
            else
            {
                mDisplayOutput.setEnabled(false);
            }
        }

        private void setupDisplayTurnOnOff()
        {

            mDisplayOnOff = (SwitchPreference) findPreference(getString(com.motorola.motocit.R.string.pref_title_display_turn_on_off));
            mDisplayOnOff.setEnabled(isDisplayAvailable());
            mDisplayOnOff.setChecked(getDisplayState());
            mDisplayOnOff.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    Boolean value = (Boolean) newValue;
                    if (value)
                    {
                        setDisplayState(ModDisplay.STATE_ON);
                        mDisplayOutput.setEnabled(false);
                    }
                    else
                    {
                        setDisplayState(ModDisplay.STATE_OFF);
                        mDisplayOutput.setEnabled(true);
                    }
                    return true;
                }
            });
        }

        private byte[] sendRawProtocolData(byte[] rawData)
        {
            byte[] responseData = null;

            List<ModInterfaceDelegation> modInterfaceDelegationList = null;

            if (mModManager != null)
            {
                dbgLog(TAG, "Trying to get Mod Interface Delegation List", 'i');
                try
                {
                    modInterfaceDelegationList = mModManager.getModInterfaceDelegationsByProtocol(mModConnectedList.get(0), Protocol.RAW);
                }
                catch (Exception e)
                {
                    return responseData;
                }
            }

            if (getApplicationContext().checkSelfPermission("com.motorola.mod.permission.RAW_PROTOCOL") != 0)
            {
                this.requestPermissions(new String[] { ModManager.PERMISSION_USE_RAW_PROTOCOL }, 1);
            }

            if (modInterfaceDelegationList != null)
            {
                if (modInterfaceDelegationList.size() >= 1)
                {
                    ModInterfaceDelegation modInterfaceDelegation = modInterfaceDelegationList.get(0);

                    ParcelFileDescriptor pFD = null;
                    try
                    {
                        pFD = mModManager.openModInterface(modInterfaceDelegation, ParcelFileDescriptor.MODE_READ_WRITE);
                    }
                    catch (Exception e)
                    {
                        dbgLog(TAG, "Error when opening mod interface, " + e.getMessage(), 'e');
                        return responseData;
                    }

                    if (pFD != null)
                    {
                        FileDescriptor fd = null;
                        FileInputStream inputStream = null;
                        FileOutputStream outputStream = null;

                        try
                        {
                            fd = pFD.getFileDescriptor();

                            outputStream = new FileOutputStream(fd);
                            inputStream = new FileInputStream(fd);

                            outputStream.write(rawData);

                            StructPollfd[] pollfds = new StructPollfd[1];
                            StructPollfd readRawFd = new StructPollfd();
                            pollfds[0] = readRawFd;
                            readRawFd.fd = pFD.getFileDescriptor();
                            readRawFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);

                            dbgLog(TAG, "Wait for poll", 'i');
                            int ret = Os.poll(pollfds, -1);
                            dbgLog(TAG, "Poll finished " + ret + " rawEvents " + readRawFd.revents, 'i');

                            int MAX_BYTES = 2048;
                            byte[] readBuffer = new byte[MAX_BYTES];
                            int readLength = inputStream.read(readBuffer, 0, MAX_BYTES);

                            responseData = new byte[readLength];

                            for (int byteNumber = 0; byteNumber < readLength; byteNumber++)
                            {
                                responseData[byteNumber] = readBuffer[byteNumber];
                            }

                            // Clear activity from Mod Permission list so
                            // permission is not revoked on attach
                            ClearModPermissionList();
                        }
                        catch (Exception e)
                        {
                            dbgLog(TAG, "Error when opening mod interface, " + e.getMessage(), 'e');
                            return responseData;
                        }
                        finally
                        {
                            if (outputStream != null)
                            {
                                try
                                {
                                    outputStream.close();
                                }
                                catch (Exception e)
                                {
                                    // Do nothing
                                }
                            }

                            if (inputStream != null)
                            {
                                try
                                {
                                    inputStream.close();
                                }
                                catch (Exception e)
                                {
                                    // Do nothing
                                }
                            }

                            if (pFD != null)
                            {
                                try
                                {
                                    pFD.close();
                                    pFD = null;
                                }
                                catch (Exception e)
                                {
                                    // Do nothing
                                }
                            }
                        }
                    }
                    else
                    {
                        return responseData;
                    }
                }
            }
            else
            {
                return responseData;
            }

            return responseData;
        }

        private byte[] concat(byte[] a, byte[] b)
        {
            byte[] c = new byte[a.length + b.length];
            System.arraycopy(a, 0, c, 0, a.length);
            System.arraycopy(b, 0, c, a.length, b.length);
            return c;
        }

        private void ClearModPermissionList()
        {
            // This is required to clear permissions so that mod settings does
            // not re-start our application
            try
            {
                ContentResolver cr = getApplicationContext().getContentResolver();
                int count = cr.delete(ModContract.CONTENT_URI_UID_PERMS,
                        ModContract.COLUMN_PUID + "=?",
                        new String[] { Integer.toString(getApplicationContext().getPackageManager().getApplicationInfo("com.motorola.motocit", 0).uid) });
            }
            catch (Exception e)
            {
                // do nothing
            }
        }

        private void setupShowTestPattern()
        {
            Preference preference = (Preference) findPreference(getString(com.motorola.motocit.R.string.pref_title_show_test_pattern));
            preference.setEnabled(isDisplayAvailable());
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    mFragmentManager.beginTransaction()
                            .replace(android.R.id.content, mModsTestPatternFragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
            });
        }

        public void updateStatus(boolean attached)
        {
            Preference preference = findPreference(getString(com.motorola.motocit.R.string.pref_title_status));
            preference.setSummary((attached) ? getString(com.motorola.motocit.R.string.mod_attach) : getString(com.motorola.motocit.R.string.mod_detach));

            Preference display = findPreference(getString(com.motorola.motocit.R.string.pref_title_display_turn_on_off));
            display.setEnabled(isDisplayAvailable());

            Preference displayOutput = findPreference(getString(com.motorola.motocit.R.string.pref_title_display_output));
            displayOutput.setEnabled(isDisplayAvailable());
        }

        private boolean isDisplayAvailable()
        {
            if (mModManager != null)
            {
                return (mModManager.getClassManager(Protocol.MODS_DISPLAY) != null);
            }
            else
            {
                return false;
            }
        }

        private void setDisplayState(final int state)
        {
            mDisplayHandler.post(new Runnable() {
                public void run()
                {
                    ModDisplay display = null;
                    String result = "failure";
                    if (mModManager != null)
                    {
                        display = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                        if ((display != null) && display.setModDisplayState(state))
                        {
                            result = "success";
                            dbgLog(TAG, "Display state written with success", 'i');
                        }
                        else
                        {
                            dbgLog(TAG, "Display state written with failure", 'i');
                        }
                    }
                    Toast.makeText(HdkTest.this, "Display state changed  " + result, Toast.LENGTH_SHORT).show();
                }
            });
        }

        private boolean getDisplayState()
        {
            ModDisplay display = null;
            if (mModManager != null)
            {
                display = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                if ((display != null) && (display.getModDisplayState() == ModDisplay.STATE_ON))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public class TestPatternView extends View
    {
        protected final Paint mPathLinePaint;

        public TestPatternView(Context c)
        {
            super(c);
            mPathLinePaint = new Paint();
            mPathLinePaint.setAntiAlias(false);
            mPathLinePaint.setARGB(255, 255, 255, 255);
            mPathLinePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            dbgLog(TAG, "HdmiNineBars_View: onDraw", 'i');
            // if activity is ending don't redraw
            if (isActivityEnding())
            {
                return;
            }

            // NOTE: X and Y values are reversed since this is a landscape
            // pattern!
            int originY = 0 + TestUtils.getDisplayXLeftOffset();
            int originX = 0 + TestUtils.getDisplayYTopOffset();
            int width = getWidth() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());

            int VertDivs = 0, HorDivs = 9;

            // if Divs set to 0, use that as flag to divs equal to display
            // resolution
            if (VertDivs == 0)
            {
                VertDivs = height;
            }
            if (HorDivs == 0)
            {
                HorDivs = width;
            }

            float VertRatio = (float) height / VertDivs;
            float HorRatio = (float) width / HorDivs;
            float[] hsv =
            { 0, 0, 0 }; // array to hold HSV values; Hue, Saturation, Value
            Rect r = new Rect();

            // cycle thru grid incrementally changing HSV
            for (int rows = 0; rows < VertDivs; rows++)
            {
                for (int cols = 0; cols < HorDivs; cols++)
                {
                    hsv[0] = (((float) cols / HorDivs) * 360); // Hue 0 to 360
                    // one half of vertical pattern has fixed Value and varies
                    // Sat
                    if (rows < (VertDivs / 2))
                    {
                        hsv[1] = ((2 * (float) rows) / VertDivs); // Saturation
                        hsv[2] = (float) 1.0; // Value fixed to 1.0
                    }
                    // other half of vertical pattern has fixed Sat and varies
                    // Value
                    else
                    {
                        hsv[1] = (float) 1.0; // Saturation fixed to 1.0
                        hsv[2] = (float) 2.0 - ((2 * (float) rows) / VertDivs); // Value
                    }

                    // Define color and draw cell in grid
                    mPathLinePaint.setColor(Color.HSVToColor(255, hsv));
                    r.set((int) (HorRatio * cols) + originX, (int) (VertRatio * rows) + originY, (int) (HorRatio * (cols + 1)) + originX,
                            (int) (VertRatio * (rows + 1)) + originY);
                    canvas.drawRect(r, mPathLinePaint);
                }
            }
        }
    }

    class ModsTestPatternFragment extends Fragment
    {
        View mView = null;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            setFullScreen();

            if (mView == null)
            {
                mView = new TestPatternView(HdkTest.this);
            }
            mView.setVisibility(View.VISIBLE);
            mView.setOnTouchListener(new OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    toggleNavigationBar();
                    return false;
                }

            });
            setContentView(mView);

            dbgLog(TAG, "ModsTestPatternFragment: onCreate", 'i');
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onDestroy()
        {
            super.onDestroy();
            if (mView != null)
            {
                mView.setVisibility(View.GONE);
            }
            dbgLog(TAG, "ModsTestPatternFragment: onDestroy", 'i');
        }
    }

    private void setFullScreen()
    {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ActionBar action = getActionBar();
        action.hide();

        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    private void setNormalScreen()
    {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ActionBar action = getActionBar();
        action.show();

        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    private void toggleNavigationBar()
    {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this,
                        getString(com.motorola.motocit.R.string.mode_notice),
                        Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                dbgLog(TAG, "mFragmentManager.getBackStackEntryCount()=" + mFragmentManager.getBackStackEntryCount(), 'i');
                if (mFragmentManager.getBackStackEntryCount() > 1)
                {
                    mFragmentManager.popBackStackImmediate();
                    mFragmentManager.beginTransaction()
                            .remove(mModsTestPatternFragment)
                            .commit();
                }
                else
                {
                    systemExitWrapper(0);
                }
            }
        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException,
            CmdPassException
    {}

    @Override
    protected void printHelp()
    {}

    @Override
    protected boolean onSwipeRight()
    {
        return false;
    }

    @Override
    protected boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    protected boolean onSwipeDown()
    {
        return false;
    }

    @Override
    protected boolean onSwipeUp()
    {
        return false;
    }
}
