/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.storage.IMountService;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public class TestUtils
{
    private static final String TAG = "TestUtils";
    private static boolean CQA_LOG_DEBUG = false;
    private static boolean CQA_LOG_ON_CS = true;
    private static IMountService mMntSvc = null;
    private static final String COMMSERVER_SERVICE_NAME = "com.motorola.motocit.CommServer";
    private static final String MOTO_SETTING_IMPORT_NAME = "com.motorola.android.provider.MotorolaSettings";
    private static final String[] odmModelList = { "XT303", "XT389", "XT390", "XT914", "XT915", "XT916", "XT918",
                                                   "XT919", "hawk", "XT920", "FIH-DBU", "FIH-TLE-FIH",
                            /* Petanque */         "XT1750", "XT1754", "XT1755", "XT1757", "XT1756",
                            /* Lacrosse */         "XT1721", "XT1723", "XT1726", "XT1724", "XT1725",
                            /* Andy ROW */         "XT1760", "XT1761", "XT1762", "XT1763", "XT1764", "XT1769",
                            /* George ROW */       "XT1770", "XT1771", "XT1772", "XT1773" };

    private static boolean determinedSupportedDevices = false;
    private static boolean odmDevice = false;
    private static boolean motDevice = false;

    private static boolean loadedCQASettings = false;
    private static final Lock mLoadCQASettingsLock = new ReentrantLock();

    private static String sequenceFileInUse = "cqatest_cfg";
    private static String factoryOrAltConfigFileInUse = "cqatest_cfg";

    private static class DisplaySettings
    {
        private static int displayXLeftOffset;
        private static int displayXRightOffset;
        private static int displayYTopOffset;
        private static int displayYBottomOffset;
        private static int displayOrientation;
        private static boolean hideTitle;
        private static boolean hideNotification;

        static
        {
            displayXLeftOffset = 0;
            displayXRightOffset = 0;
            displayYTopOffset = 0;
            displayYBottomOffset = 0;
            displayOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            hideTitle = false;
            hideNotification = false;
        }
    }

    private static class CQASettings
    {
        private static String PassFailMethod;
        private static String StartCQAMenuMode;
        private static String StartCQAMenuModeFactory;
        private static String StartCommServer;

        static
        {
            PassFailMethod = "VOLUME_KEYS";
            StartCQAMenuMode = "NO";
            StartCQAMenuModeFactory = "NO";
            StartCommServer = "NO";
        }
    }

    public static boolean isUserdebugEngBuild()
    {
        boolean result = false;
        String buildType;

        buildType = SystemProperties.get("ro.build.type", "RO_BUILD_TYPE_UNKNOWN");
        if (buildType.equals("userdebug") || buildType.equals("eng"))
        {
            result = true;
        }

        return result;
    }

    public static boolean isFactoryCableBoot()
    {
        boolean result = false;

        try
        {
            BufferedReader breader = new BufferedReader(new FileReader("/proc/bootinfo"));
            String line = "";

            while ((line = breader.readLine()) != null)
            {
                if (line.contains("POWERUPREASON") == false)
                {
                    continue;
                }

                String[] tokens = line.split(":");
                int i = 0;
                for (String token : tokens)
                {
                    dbgLog(TAG, "token " + Integer.toString(i) + "|" + token + "|", 'd');
                    i++;
                }
                tokens[0] = tokens[0].trim();
                String temp = tokens[1].trim();
                int reason = Integer.parseInt(temp.substring(2), 16);

                /*
                 * BIT 4 reserved for PU_REASON_USB_CABLE, BIT 5 reserved for
                 * PU_REASON_FACTORY_CABLE
                 */
                if (tokens[0].equals("POWERUPREASON") && ((reason & 0x20) != 0))
                {
                    result = true;
                    break;
                }
            }
            breader.close();
        }
        catch (Exception e)
        {
            dbgLog(TAG, "!!! Some exception in TestUtils isFactoryCableBoot", 'd');
        }

        if (result == false)
        {
            String bootmode = SystemProperties.get("ro.bootmode", "normal");

            if (bootmode.toLowerCase(Locale.US).equals("factory") || bootmode.toLowerCase(Locale.US).equals("mot-factory"))
            {
                result = true;
            }

        }

        return result;
    }

    /**
     * This function is to filter the test items base on product. The items
     * won't be displayed if not supported by product
     */
    public static boolean activityFilter(String activityname)
    {
        boolean rtn = false;
        boolean isInLocal12m = false;
        boolean isInSystem12m = false;
        boolean isInSdcard = false;

        File file_local_12m = new File("/data/local/12m/" + TestUtils.getSequenceFileInUse());
        File file_system_12m = new File("/system/etc/motorola/12m/" + TestUtils.getSequenceFileInUse());
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + TestUtils.getSequenceFileInUse());

        if (file_local_12m.exists())
        {
            isInLocal12m = true;
        }

        if (file_system_12m.exists())
        {
            isInSystem12m = true;
        }

        if (file_system_sdcard.exists())
        {
            isInSdcard = true;
        }

        if (isInLocal12m && isInSystem12m)
        {
            if (searchStringInFile(file_local_12m, activityname))
            {
                rtn = true;
            }
        }
        else if ((isInLocal12m == false) && isInSystem12m)
        {
            if (searchStringInFile(file_system_12m, activityname))
            {
                rtn = true;
            }
        }
        else if (isInLocal12m && (isInSystem12m == false))
        {
            if (searchStringInFile(file_local_12m, activityname))
            {
                rtn = true;
            }
        }
        else if (isInSdcard)
        {
            if (searchStringInFile(file_system_sdcard, activityname))
            {
                rtn = true;
            }
        }
        else
        {
            rtn = false;
        }

        return rtn;
    }

    /**
     * Function searchStringInFile is used to search certain string in specific
     * file. filename: filename with absolute path string: string want to be
     * searched in file return true if string is found, else return false
     */

    public static boolean searchStringInFile(File filename, String string)
    {
        boolean rtn = false;

        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            String line = "";

            while ((line = reader.readLine()) != null)
            {
                if (line.compareTo(string) == 0)
                {
                    rtn = true;
                }

            }
            reader.close();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return rtn;
    }

    public static void dbgLog(String msgTag, String msg, char type)
    {
        String longMsg = msgTag + "-" + msg;

        if ((isUserdebugEngBuild()) && CQA_LOG_ON_CS)
        {
            CQA_LOG_DEBUG = true;
        }

        if (CQA_LOG_DEBUG)
        {
            switch (type)
            {
            case 'v':
                Log.v("CQATest", longMsg);
                break;
            case 'd':
                Log.d("CQATest", longMsg);
                break;
            case 'i':
                Log.i("CQATest", longMsg);
                break;
            case 'e':
                Log.e("CQATest", longMsg);
                break;
            case 'w':
                Log.w("CQATest", longMsg);
                break;
            default:
                break;
            }
        }
    }

    public static void dbgLog(String msgTag, String msg, Throwable tr, char type)
    {
        String longMsg = msgTag + "-" + msg;

        if ((isUserdebugEngBuild()) && CQA_LOG_ON_CS)
        {
            CQA_LOG_DEBUG = true;
        }

        if (CQA_LOG_DEBUG)
        {
            switch (type)
            {
            case 'v':
                Log.v("CQATest", longMsg, tr);
                break;
            case 'd':
                Log.d("CQATest", longMsg, tr);
                break;
            case 'i':
                Log.i("CQATest", longMsg, tr);
                break;
            case 'e':
                Log.e("CQATest", longMsg, tr);
                break;
            case 'w':
                Log.w("CQATest", longMsg, tr);
                break;
            default:
                break;
            }
        }
    }

    public static boolean isTestBuild(String versionName)
    {
        boolean rtn = false;
        if (versionName.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
        {
            rtn = true;
        }

        return rtn;
    }

    public static String getExternalStorageState(String mountPoint)
    {
        try
        {
            if (mMntSvc == null)
            {
                mMntSvc = IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            }

            // make klocwork happy
            if (null == mMntSvc)
            {
                Log.e(TAG, "Error in getExternalStorageState()");
                return Environment.MEDIA_REMOVED;
            }

            return mMntSvc.getVolumeState(mountPoint);
        }
        catch (Exception rex)
        {
            Log.e(TAG, "Error in getExternalStorageState()");
            return Environment.MEDIA_REMOVED;
        }
    }

    public static boolean isCommServerRunning(Context context)
    {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        // keep klocwork happy
        if (null == manager)
        {
            dbgLog(TAG, "isCommServerRunning() Could not retrieve ActivityManager", 'e');
            return false;
        }

        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        if (null == runningServices)
        {
            dbgLog(TAG, "isCommServerRunning() Could not retrieve list of running services", 'e');
            return false;
        }

        for (RunningServiceInfo service : runningServices)
        {
            // keep klockwork happy
            if (null == service)
            {
                continue;
            }

            if (COMMSERVER_SERVICE_NAME.equals(service.service.getClassName()))
            {
                dbgLog(TAG, "isCommServerRunning() returned true", 'i');
                return true;
            }
        }
        dbgLog(TAG, "isCommServerRunning() returned false", 'i');
        return false;
    }

    public static double round(double value, int precisionRightOfDecimal)
    {
        double precision = 1.0 * Math.pow(10, precisionRightOfDecimal);

        return Math.round(value * precision) / precision;
    }

    public static int getMotoSettingInt(ContentResolver cr, String name, int def) throws MotoSettingsNotFoundException
    {
        try
        {
            return (Integer) Class.forName(MOTO_SETTING_IMPORT_NAME).getDeclaredMethod("getInt", ContentResolver.class, String.class, int.class)
                    .invoke(null, cr, name, def);
        }
        catch (Exception e)
        {
            throw new MotoSettingsNotFoundException(e.toString());
        }
    }

    public static boolean putMotoSettingInt(ContentResolver cr, String name, int value) throws MotoSettingsNotFoundException
    {
        try
        {
            return (Boolean) Class.forName(MOTO_SETTING_IMPORT_NAME).getDeclaredMethod("putInt", ContentResolver.class, String.class, int.class)
                    .invoke(null, cr, name, value);
        }
        catch (Exception e)
        {
            throw new MotoSettingsNotFoundException(e.toString());

        }
    }

    public static void cqaLogDebugEnable(boolean enable)
    {
        if (enable)
        {
            CQA_LOG_ON_CS = true;
            CQA_LOG_DEBUG = true;
        }
        else
        {
            CQA_LOG_ON_CS = false;
            CQA_LOG_DEBUG = false;
        }
    }

    public static boolean getCqaLogDebugState()
    {
        return CQA_LOG_DEBUG;
    }

    public static void setSupportedDevices()
    {
        File socketfile = new File("/dev/socket/local_tcmd");

        if (socketfile.exists())
        {
            dbgLog(TAG, "Confirmed that this is a Mot device.", 'i');
            motDevice = true;
        }
        else
        {
            String sku = SystemProperties.get("ro.boot.hardware.sku", null);
            for (int i = 0; i < odmModelList.length; i++)
            {
                dbgLog(TAG, "Model_ " + i + "= " + odmModelList[i], 'i');
                if (odmModelList[i].equalsIgnoreCase(Build.MODEL))
                {
                    dbgLog(TAG, "Confirmed that this is ODM device but in Model List. Device Model= " + Build.MODEL, 'i');
                    odmDevice = true;
                    break;
                }
                if (odmModelList[i].equalsIgnoreCase(sku))
                {
                    dbgLog(TAG, "Confirmed that this is ODM device but in Model List. Device SKU= " + sku, 'i');
                    odmDevice = true;
                    break;
                }
                
            }
            if (odmDevice == false)
            {
                if (Build.MANUFACTURER.equalsIgnoreCase("motorola") || Build.MANUFACTURER.equalsIgnoreCase("lenovo"))
                {
                    dbgLog(TAG, "Manufacturer=" + Build.MANUFACTURER, 'i');
                    dbgLog(TAG, "Confirmed that this is Motorola/Lenovo ODM device. Device Model=" + Build.MODEL, 'i');
                    odmDevice = true;
                }
            }
        }
        if(!motDevice && !odmDevice){
            dbgLog(TAG, "Unsupported device", 'i');
        }

        determinedSupportedDevices = true;
    }

    public static boolean isOdmDevice()
    {
        if (determinedSupportedDevices == false)
        {
            setSupportedDevices();
        }
        return odmDevice;
    }

    public static boolean isMotDevice()
    {
        if (determinedSupportedDevices == false)
        {
            setSupportedDevices();
        }
        return motDevice;
    }

    public static void setDisplayOffsets(int xLeftOffset, int xRightOffset, int yTopOffset, int yBottomOffset)
    {
        DisplaySettings.displayXLeftOffset = xLeftOffset;
        DisplaySettings.displayXRightOffset = xRightOffset;
        DisplaySettings.displayYTopOffset = yTopOffset;
        DisplaySettings.displayYBottomOffset = yBottomOffset;
    }

    public static void setDisplayOrientation(String orientation)
    {
        if (orientation.equalsIgnoreCase("SCREEN_ORIENTATION_PORTRAIT"))
        {
            DisplaySettings.displayOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        else if (orientation.equalsIgnoreCase("SCREEN_ORIENTATION_REVERSE_PORTRAIT"))
        {
            DisplaySettings.displayOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        }
        else if (orientation.equalsIgnoreCase("SCREEN_ORIENTATION_LANDSCAPE"))
        {
            DisplaySettings.displayOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        else if (orientation.equalsIgnoreCase("SCREEN_ORIENTATION_REVERSE_LANDSCAPE"))
        {
            DisplaySettings.displayOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }

    }

    public static void setDisplayHideTitle(boolean setting)
    {
        DisplaySettings.hideTitle = setting;
    }

    public static void setDisplayHideNotification(boolean setting)
    {
        DisplaySettings.hideNotification = setting;
    }

    public static int getDisplayXLeftOffset()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.displayXLeftOffset;
    }

    public static int getDisplayXRightOffset()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.displayXRightOffset;
    }

    public static int getDisplayYTopOffset()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.displayYTopOffset;
    }

    public static int getDisplayYBottomOffset()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.displayYBottomOffset;
    }

    public static int getDisplayOrientation()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.displayOrientation;
    }

    public static boolean getDisplayHideTitle()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.hideTitle;
    }

    public static boolean getDisplayHideNotification()
    {
        setCQASettingsFromConfig();
        return DisplaySettings.hideNotification;
    }

    public static void setSequenceFileInUse(String filePassedIn)
    {

        loadedCQASettings = false;
        sequenceFileInUse = filePassedIn;
    }

    public static String getSequenceFileInUse()
    {
        return sequenceFileInUse;
    }

    public static void setfactoryOrAltConfigFileInUse(String filePassedIn)
    {

        loadedCQASettings = false;
        factoryOrAltConfigFileInUse = filePassedIn;
    }

    public static String getfactoryOrAltConfigFileInUse()
    {
        return factoryOrAltConfigFileInUse;
    }

    public static boolean setPassFailMethods(String passFailMethod)
    {
        boolean result = false;
        if (passFailMethod.equalsIgnoreCase("VOLUME_KEYS") || passFailMethod.equalsIgnoreCase("TOUCHSCREEN"))
        {
            CQASettings.PassFailMethod = passFailMethod;
            result = true;
        }
        return result;
    }

    public static String getPassFailMethods()
    {
        setCQASettingsFromConfig();
        return CQASettings.PassFailMethod;
    }

    public static void setAutoCQAStart(String autoCQAStart)
    {
        if (autoCQAStart.toUpperCase(Locale.US).contains("YES"))
        {
            CQASettings.StartCQAMenuMode = "YES";
        }
        else
        {
            CQASettings.StartCQAMenuMode = "NO";
        }
        return;
    }

    public static String getAutoCQAStart()
    {
        setCQASettingsFromConfig();
        return CQASettings.StartCQAMenuMode;
    }

    public static void setAutoStartCommServer(String autoStartCommServer)
    {
        if (autoStartCommServer.toUpperCase(Locale.US).contains("YES"))
        {
            CQASettings.StartCommServer = "YES";
        }
        else
        {
            CQASettings.StartCommServer = "NO";
        }
        return;
    }

    public static String getAutoStartCommServer()
    {
        setCQASettingsFromConfig();
        return CQASettings.StartCommServer;
    }

    public static void setAutoCQAStartFactory(String autoCQAStartFactory)
    {
        if (autoCQAStartFactory.toUpperCase(Locale.US).contains("YES"))
        {
            CQASettings.StartCQAMenuModeFactory = "YES";
        }
        else
        {
            CQASettings.StartCQAMenuModeFactory = "NO";
        }
        return;
    }

    public static String getAutoCQAStartFactory()
    {
        setCQASettingsFromConfig();
        return CQASettings.StartCQAMenuModeFactory;
    }

    public static void setCQASettingsFromConfig()
    {
        try
        {
            mLoadCQASettingsLock.lock();
            if (loadedCQASettings == true)
            {
                dbgLog(TAG, "CQA Settings already loaded: Skipping", 'd');
                return;
            }
            else
            {
                dbgLog(TAG, "Loading CQA Settings", 'd');
            }


            String SequenceFileInUse = TestUtils.getSequenceFileInUse();
            if (isDaliSportHW() && !loadedCQASettings)
            {
                if (!SequenceFileInUse.contains("DaliSport"))
                {
                    SequenceFileInUse = SequenceFileInUse + "_DaliSport";
                    TestUtils.setSequenceFileInUse(SequenceFileInUse);
                }
                else
                {
                    dbgLog(TAG, "setCQASettingsFromConfig, config already set for Dali, file_name=" + SequenceFileInUse, 'i');
                    return;
                }
            }

            dbgLog(TAG, "setCQASettingsFromConfig, config_file_name:" + SequenceFileInUse, 'i');

            loadedCQASettings = true;
            File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
            File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
            File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
            String config_file = null;

            int display_x_left_offset = 0;
            int display_x_right_offset = 0;
            int display_y_top_offset = 0;
            int display_y_bottom_offset = 0;

            if (file_local_12m.exists())
            {
                config_file = file_local_12m.toString();
            }
            else if (file_system_12m.exists())
            {
                config_file = file_system_12m.toString();
            }
            else if (file_system_sdcard.exists())
            {
                config_file = file_system_sdcard.toString();
            }
            else
            {
                dbgLog(TAG, "!! CAN'T FIND CONFIG FILE", 'd');
            }

            if ((config_file != null) && (SequenceFileInUse != null))
            {
                try
                {
                    BufferedReader breader = new BufferedReader(new FileReader(config_file));
                    String line = "";

                    while ((line = breader.readLine()) != null)
                    {
                        if (line.contains("<DISPLAY SETTINGS>") == true)
                        {
                            dbgLog(TAG, "Display Settings: " + line, 'd');
                            String[] fields = line.split(",");
                            for (String field : fields)
                            {
                                String[] tokens = field.split("=");

                                if (tokens[0].equalsIgnoreCase("DISPLAY_X_LEFT_OFFSET"))
                                {
                                    display_x_left_offset = Integer.parseInt(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("DISPLAY_X_RIGHT_OFFSET"))
                                {
                                    display_x_right_offset = Integer.parseInt(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("DISPLAY_Y_TOP_OFFSET"))
                                {
                                    display_y_top_offset = Integer.parseInt(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("DISPLAY_Y_BOTTOM_OFFSET"))
                                {
                                    display_y_bottom_offset = Integer.parseInt(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("DISPLAY_ORIENTATION"))
                                {
                                    setDisplayOrientation(tokens[1].toUpperCase(Locale.US));
                                }
                                if (tokens[0].equalsIgnoreCase("HIDE_TITLE"))
                                {
                                    if (tokens[1].equalsIgnoreCase("TRUE"))
                                    {
                                        setDisplayHideTitle(true);
                                    }
                                    else
                                    {
                                        setDisplayHideTitle(false);
                                    }
                                }
                                if (tokens[0].equalsIgnoreCase("HIDE_NOTIFICATION"))
                                {
                                    if (tokens[1].equalsIgnoreCase("TRUE"))
                                    {
                                        setDisplayHideNotification(true);
                                    }
                                    else
                                    {
                                        setDisplayHideNotification(false);
                                    }
                                }
                            }

                            setDisplayOffsets(display_x_left_offset, display_x_right_offset, display_y_top_offset, display_y_bottom_offset);

                            dbgLog(TAG, "Parsed: DISPLAY_X_LEFT_OFFSET=" + display_x_left_offset + ", DISPLAY_X_RIGHT_OFFSET="
                                    + display_x_right_offset + ", DISPLAY_Y_TOP_OFFSET=" + display_y_top_offset + ", DISPLAY_Y_BOTTOM_OFFSET="
                                    + display_y_bottom_offset + "HIDE_TITLE=" + getDisplayHideTitle() + "HIDE_NOTIFICATION="
                                    + getDisplayHideNotification(), 'd');
                        }
                        if (line.contains("<PASS FAIL SETTINGS>") == true)
                        {
                            dbgLog(TAG, "Pass Fail Settings: " + line, 'd');
                            String[] fields = line.split(",");
                            for (String field : fields)
                            {
                                String[] tokens = field.split("=");

                                if (tokens[0].equalsIgnoreCase("PASS_FAIL_METHOD"))
                                {
                                    boolean setPassFailMethod = setPassFailMethods(tokens[1]);

                                    if (setPassFailMethod == false)
                                    {
                                        dbgLog(TAG, "!!! INVALID PASS_FAIL_METHOD: " + field, 'd');
                                    }
                                }
                            }
                        }

                        if (line.contains("<AUTO CQA START>") == true)
                        {
                            dbgLog(TAG, "Auto CQA Start Settings: " + line, 'd');
                            String[] fields = line.split(",");
                            for (String field : fields)
                            {
                                String[] tokens = field.split("=");

                                if (tokens[0].equalsIgnoreCase("AUTO_START_CQA_MENU_MODE"))
                                {
                                    setAutoCQAStart(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("AUTO_START_COMMSERVER"))
                                {
                                    setAutoStartCommServer(tokens[1]);
                                }
                                if (tokens[0].equalsIgnoreCase("FACTORY_AUTO_START_CQA_MENU_MODE"))
                                {
                                    setAutoCQAStartFactory(tokens[1]);
                                }
                            }
                        }
                    }

                    breader.close();
                    dbgLog(TAG, "CQA Settings are loaded: ", 'd');
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "!!! Some exception in parsing CQA settings", 'd');
                    dbgLog(TAG, "Exception: " + e.toString(), 'd');
                }
            }
        }
        finally
        {
            mLoadCQASettingsLock.unlock();
        }
    }

    private static Bitmap CreateTwoDCode(String content) throws WriterException
    {
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                if (matrix.get(x, y))
                {
                    pixels[(y * width) + x] = 0xff000000;
                }
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public static Bitmap CreateOneDCode(String content) throws WriterException
    {
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, 500, 100);

        int width = matrix.getWidth();

        int height = matrix.getHeight();

        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++)
        {

            for (int x = 0; x < width; x++)
            {

                if (matrix.get(x, y))
                {

                    pixels[(y * width) + x] = 0xff000000;

                }

            }

        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;

    }

    public static Bitmap GenerateImage(String str, String method)
    {
        Bitmap bmp = null;
        try
        {
            if (method.contentEquals("2d"))
            {
                bmp = CreateTwoDCode(str);
            }
            else if (method.contentEquals("1d"))
            {
                bmp = CreateOneDCode(str);
            }
        }
        catch (WriterException e)
        {
            e.printStackTrace();
        }

        return bmp;
    }

    public static boolean isConfigFileExist(String FileName)
    {
        boolean rtn = false;
        File file_local_12m = new File("/data/local/12m/" + FileName);
        File file_system_12m = new File("/system/etc/motorola/12m/" + FileName);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + FileName);

        if (file_local_12m.exists() || file_system_12m.exists() || file_system_sdcard.exists())
        {
            rtn = true;
        }

        return rtn;
    }

    public static String join(Iterable<?> elements, String delimiter)
    {
        StringBuilder sb = new StringBuilder();
        for (Object e : elements)
        {
            if (sb.length() > 0)
            {
                sb.append(delimiter);
            }
            sb.append(e);
        }
        return sb.toString();
    }

    public static ArrayList<String> getIgnoredMicFromConfig()
    {
        String SequenceFileInUse = TestUtils.getSequenceFileInUse();
        File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
        String config_file = null;
        ArrayList<String> tempMics = new ArrayList<String>();

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else if (file_system_sdcard.exists())
        {
            config_file = file_system_sdcard.toString();
        }
        else
        {
            dbgLog(TAG, "!! CAN'T FIND CONFIG FILE", 'd');
        }

        if ((config_file != null) && (SequenceFileInUse != null))
        {
            try
            {
                BufferedReader breader = new BufferedReader(new FileReader(config_file));
                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    if (line.contains("<IGNORED MICS>") == true)
                    {
                        dbgLog(TAG, "Ignored Mics: " + line, 'd');
                        String[] fields = line.split(",");
                        int i = 0;
                        for (String field : fields)
                        {
                            dbgLog(TAG, "Ignored mics added in config. " + field.toString(), 'i');

                            if (field.equalsIgnoreCase("DEFAULT_MIC"))
                            {
                                tempMics.add("DEFAULT MIC");
                            }
                            else if (field.equalsIgnoreCase("CAMCORDER_MIC"))
                            {
                                tempMics.add("CAMCORDER MIC");
                            }
                            else if (field.equalsIgnoreCase("PRIMARY_MIC"))
                            {
                                tempMics.add("PRIMARY MIC");
                            }
                            else if (field.equalsIgnoreCase("SECONDARY_MIC"))
                            {
                                tempMics.add("SECONDARY MIC");
                            }
                            else if (field.equalsIgnoreCase("TERTIARY_MIC"))
                            {
                                tempMics.add("TERTIARY MIC");
                            }
                            else if (field.equalsIgnoreCase("HEADSET_MIC"))
                            {
                                tempMics.add("HEADSET MIC");
                            }
                            else if (field.equalsIgnoreCase("PRIMARY_MIC_LOW_PWR"))
                            {
                                tempMics.add("PRIMARY MIC LOW PWR");
                            }
                            else if (field.equalsIgnoreCase("SECONDARY_MIC_LOW_PWR"))
                            {
                                tempMics.add("SECONDARY MIC LOW PWR");
                            }
                            else if (field.equalsIgnoreCase("TERTIARY_MIC_LOW_PWR"))
                            {
                                tempMics.add("TERTIARY MIC LOW PWR");
                            }
                            else if (field.equalsIgnoreCase("HEADSET_MIC_LOW_PWR"))
                            {
                                tempMics.add("HEADSET MIC LOW PWR");
                            }
                            else if (field.equalsIgnoreCase("MIC_4"))
                            {
                                tempMics.add("MIC 4");
                            }
                            else if (field.equalsIgnoreCase("MIC_4_LOW_PWR"))
                            {
                                tempMics.add("MIC 4 LOW PWR");
                            }
                            else if (field.equalsIgnoreCase("MIC_5"))
                            {
                                tempMics.add("MIC 5");
                            }
                            else if (field.equalsIgnoreCase("MIC_5_LOW_PWR"))
                            {
                                tempMics.add("MIC 5 LOW PWR");
                            }
                        }
                    }
                }

                breader.close();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception in parsing CQA Mic selection", 'd');
                dbgLog(TAG, "Exception: " + e.toString(), 'd');
            }
        }

        return tempMics;
    }

    public static boolean isDaliSportHW()
    {
        boolean rtn = false;
        String hwID;
        // special requirement from Dali sport.
        // HW ID: 0x70A and 0x90A
        // CR - IKSWL-27346
        hwID = SystemProperties.get("ro.boot.hwrev", "unknown");
        dbgLog(TAG, "hw id=" + hwID, 'i');

        if (hwID.equalsIgnoreCase("0x70A") || hwID.equalsIgnoreCase("0x90A") || hwID.equalsIgnoreCase("0x90C"))
        {
            rtn = true;
        }

        return rtn;
    }

    public static boolean hasFingerprintSensor()
    {
        boolean isSupported = false;

        String propertyFPS = "false";
        propertyFPS = SystemProperties.get("ro.hw.fps", "false");
        dbgLog(TAG, "ro.hw.fps=" + propertyFPS + " device_name=" + Build.DEVICE, 'i');
        if (propertyFPS.equalsIgnoreCase("true")
            || Build.DEVICE.toLowerCase().contains("griffin")
            || Build.DEVICE.toLowerCase().contains("nash"))
        {
            isSupported = true;
        }

        return isSupported;
    }

    public static int hasMagnetometerSensor()
    {
        int isSupported = -1;
        String propertyMag = "false";
        propertyMag= SystemProperties.get("ro.hw.ecompass");
        dbgLog(TAG, "ro.hw.ecompass=" + propertyMag + " device_name=" + Build.DEVICE, 'i');
        if (propertyMag.equals("null"))
        {
            isSupported = -1;
        }
        else if(propertyMag.equals("true"))
        {
            isSupported = 1;
        }
        else if(propertyMag.equals("false"))
        {
            isSupported = 0;
        }
        return isSupported;
    }
}
