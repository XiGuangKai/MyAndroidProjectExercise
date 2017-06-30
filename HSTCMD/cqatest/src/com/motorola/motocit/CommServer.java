/*
 * Copyright (c) 2012 - 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *  Date            CR           Author                  Description
 * 2017/02/25  IKSWN-29553 Qiang Guo - guoqiang3   CQATest - Add focaltech to the commserver test
 * 2017/02/24  IKSWN-31702 Marcos Costa - mfreitas    CQATest - Add display bright line test pattern
 * 2017/01/26  IKSWN-21780 Guilherme Deo - guideo91   CQATest - video capture of 3 cameras on Golden Eagle
 * 2016/08/15  IKSWM-55831 Kaili Wang - wangkl3   CQATest: Add the test code about cap sensor
 * 2013/07/25  IKJBXLINE-17654 Rich Hammon - wrh002   Slowed down binary writes to pc
 * 2013/07/23  IKJBXLINE-17483 Bao Sheng Zhu -xjkn86  Creation
 * 2013/07/12  IKJBXLINE-16673 Kris Liu    - e13701   Add Setup Wizard
 * 2013/07/12  IKDVX-489      Rich Hammon  - wrh002   Added Binary Ack for take picture data transfer
 * 2013/06/09  IKJBXLINE-12892 Kris Liu    - e13701   Add GDrive validate test
 * 2013/03/15  IKJB42MAIN-6891 Xiaoli Wu   - e13357   Add personalization test
 * 2013/02/11  IKMAIN-49360   Bo Biggins   - w17013   Add commserver method to display to the screen a configurable color
 * 2012/12/13  IKMAIN-49323   Myron Ma     -ftjm018   Add cosmetic test
 * 2012/11/27  IKMAIN-49307   Rich Hammon  - wrh002   Add binary file transfer capability
 * 2012/10/31  IKMAIN-49299   Min  Dong    - cqd487   Add snap resolutions for internal and external cameras
 * 2012/10/26  IKMAIN-49287   Kurt Walker  - qa3843   Updated so CommServer can work on ODM phones
 * 2012/10/22  IKJBREL1-8767  Kris Liu     - e13701   Add Audio Record and Playback
 * 2012/10/17  IKMAIN-49270   Min Dong     - cqd487   Add support for local socket communication
 * 2012/09/20  IKHSS7-48939   Brandon      - ebw018   Add Touch_Config
 * 2012/08/13  IKHSS7-48337   Kris Liu     - e13701   Add REBOOT command
 * 2012/08/01  IKHSS7-47292   Ken Moy      - wkm039   Add LOG_DEBUG cmd
 * 2012/07/18  IKHSS7-45151   Ken Moy      - wkm039   Update TELL_PARAM_RX_TIMEOUT_MS implementation
 * 2012/07/11  IKHSS7-43391   Ken Moy      - wkm039   Use TAG when getting wakelock
 * 2012/06/25  IKHSS7-40721   Kurt Walker  - qa3843   Create new InstalledApps test
 * 2012/06/20  IKHSS7-39959   Ken Moy      - wkm039   Add BlankTest activity
 * 2012/06/19  IKHSS7-39724   Ken Moy      - wkm039   Clean up incorrectly formatted unsolicited packet data
 * 2012/06/19  IKHSS7-39713   Ken Moy      - wkm039   Increase default TELL_PARAM_RX_TIMEOUT_MS to 5000 ms
 * 2012/06/18  IKHSS7-38742   Ken Moy      - wkm039   Optimizations to improve CommServer performance
 * 2012/06/05  IKHSS7-36811   Ken Moy      - wkm039   Add STOP_ALL cmd, add enhancements to TELL and PING
 * 2012/05/31  IKHSS7-35981   Kurt Walker  - qa3843   Initial coding of ambient temperature test
 * 2012/05/18  IKHSS7-33766   Ken Moy      - wkm039   Add MetaKeyLed activity
 * 2012/04/27  IKHSS7-28539   Ken Moy      - wkm039   Add ability to launch VideoCapture activity
 * 2012/04/24  IKHSS7-27110   Ken Moy      - wkm039   Change ServerCmdType to public
 * 2012/03/27  IKHSS7-18913   Ken Moy      - wkm039   Call startForeground() when starting service
 * 2012/03/21  IKHSS7-17226   Bo           - w17013   Added HdmiInfo
 * 2012/03/20  IKHSS7-16932   Bo           - w17013   Added new test pattern for testing HDMI output
 * 2012/03/19  IKHSS7-15670   Ken Moy      - wkm039   Added ScreenLockUtil Activity
 * 2012/03/17  IKHSS7-15741   ZX Hu        - w18335   Add mic selection for mic loopback
 * 2012/03/14  IKHSS7-15102   Kris Liu     - e13701   Add camera strobe
 * 2012/03/09  IKHSS7-12487   Myron Ma     -ftjm018   Add airplane mode
 * 2012/03/08  IKHSS7-7919    Ken Moy      - wkm039   Add HeadsetInfo and AudioSample activities
 * 2012/03/08  IKHSS7-13667   Ken Moy      - wkm039   Added NFC_Test activity
 * 2012/03/05  IKHSS7-8118    Ken Moy      - wkm039   Add code to make klocwork happier
 * 2012/02/28  IKHSS7-11239   Rich Hammon  -WRH002    Added Camera_Factory activity to findActivity()
 * 2012/02/22  IKHSS7-9900    Kris Liu     - e13701   Add multiple mic loopback
 * 2012/02/15  IKHSS7-7910    Ken Moy      - wkm039   Add wakelock to prevent service from stopping when phone sleeps
 * 2012/02/15  IKHSS7-7907    Ken Moy      - wkm039   Update method to locate blan interface name
 * 2012/02/15  IKHSS7-7901    Ken Moy      - wkm039   Change startActivity intent flags
 * 2012/02/10  IKHSS7-7734    Ken Moy      - wkm039   Add BT/WLAN for use with NexTest
 * 2012/02/08  IKHSS7-7460    Ken Moy      - wkm039   Update findActivity and printHelp
 * 2012/02/06  IKHSS7-7052    Ken Moy      - wkm039   CommServer receive request from BLAN only
 * 2012/01/19  IKHSS7-4869    Kris Liu     - e13701   Port CommServer code to main-dev-ics
 * 2012/01/02  IKHSS7-2305    Ken Moy      - wkm039   Added CommServer functionality
 */

package com.motorola.motocit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.widget.Toast;

import com.motorola.motocit.accel.Accel;
import com.motorola.motocit.accel.AccelSecondary;
import com.motorola.motocit.airplanemode.AirplaneMode;
import com.motorola.motocit.applications.InstalledApps;
import com.motorola.motocit.alt.altautocycle.AltMainActivity;
import com.motorola.motocit.audio.AudioEar;
import com.motorola.motocit.audio.AudioLoopBack;
import com.motorola.motocit.audio.AudioMicAnalyze;
import com.motorola.motocit.audio.AudioPlayBack;
import com.motorola.motocit.audio.AudioPlayMediaBackGround;
import com.motorola.motocit.audio.AudioPlayMediaFile;
import com.motorola.motocit.audio.AudioSample;
import com.motorola.motocit.audio.AudioToneGen;
import com.motorola.motocit.barcode.Barcode;
import com.motorola.motocit.barometer.Barometer;
import com.motorola.motocit.battery.BatteryInfo;
import com.motorola.motocit.bluetooth.BluetoothMac;
import com.motorola.motocit.bluetooth.BluetoothScan;
import com.motorola.motocit.bluetooth.BluetoothUtilityNexTest;
import com.motorola.motocit.camera.CameraFactory;
import com.motorola.motocit.camera.Camera2Factory;
import com.motorola.motocit.camera.ExternalCameraSnapAllResolutions;
import com.motorola.motocit.camera.ExternalViewfinder;
import com.motorola.motocit.camera.InternalCameraSnapAllResolutions;
import com.motorola.motocit.camera.InternalCameraStrobeTest;
import com.motorola.motocit.camera.InternalVideoCapture;
import com.motorola.motocit.camera.InternalViewfinder;
import com.motorola.motocit.camera.InternalViewfinderTorchOn;
import com.motorola.motocit.camera.StrobeTest;
import com.motorola.motocit.camera.VideoCapture;
import com.motorola.motocit.camera.ViewfinderTorchOn;
import com.motorola.motocit.camera2.FrontVideoCapture;
import com.motorola.motocit.camera2.FrontViewfinder;
import com.motorola.motocit.camera2.FrontViewfinderStrobe;
import com.motorola.motocit.camera2.FrontViewfinderTorch;
import com.motorola.motocit.camera2.RearMonoVideoCapture;
import com.motorola.motocit.camera2.RearMonoViewfinder;
import com.motorola.motocit.camera2.RearMonoViewfinderStrobe;
import com.motorola.motocit.camera2.RearRgbVideoCapture;
import com.motorola.motocit.camera2.RearRgbViewfinder;
import com.motorola.motocit.camera2.RearRgbViewfinderStrobe;
import com.motorola.motocit.camera2.RearRgbViewfinderTorch;
import com.motorola.motocit.cosmetic.CosmeticTest;
import com.motorola.motocit.database.Database;
import com.motorola.motocit.display.Display_RedLineCapture;
import com.motorola.motocit.display.Display_VideoPlayback;
import com.motorola.motocit.display.testPatterns.BlackPattern;
import com.motorola.motocit.display.testPatterns.BlackReverseOrientation;
import com.motorola.motocit.display.testPatterns.BlackWhiteBorderReverseOrientation;
import com.motorola.motocit.display.testPatterns.BlackWithWhiteBorder;
import com.motorola.motocit.display.testPatterns.Blueblocks;
import com.motorola.motocit.display.testPatterns.Bright;
import com.motorola.motocit.display.testPatterns.BrightLine;
import com.motorola.motocit.display.testPatterns.Dark;
import com.motorola.motocit.display.testPatterns.DisplayFile;
import com.motorola.motocit.display.testPatterns.DrawablePattern;
import com.motorola.motocit.display.testPatterns.Focus;
import com.motorola.motocit.display.testPatterns.GenericColorFullScreenPattern;
import com.motorola.motocit.display.testPatterns.Grayblocks;
import com.motorola.motocit.display.testPatterns.Grayscale;
import com.motorola.motocit.display.testPatterns.Greenblocks;
import com.motorola.motocit.display.testPatterns.HdmiNineBars;
import com.motorola.motocit.display.testPatterns.Macbeth;
import com.motorola.motocit.display.testPatterns.Medium;
import com.motorola.motocit.display.testPatterns.Redblocks;
import com.motorola.motocit.display.testPatterns.WhiteBlackBorderReverseOrientation;
import com.motorola.motocit.display.testPatterns.WhiteWithBlackBorder;
import com.motorola.motocit.display.testPatterns.Xtalk;
import com.motorola.motocit.display.testPatterns.XtalkInterferingHorizontal;
import com.motorola.motocit.display.testPatterns.XtalkInterferingVertical;
import com.motorola.motocit.display.testPatterns.XtalkR;
import com.motorola.motocit.flip.FlipTest;
import com.motorola.motocit.fingerprint.FingerPrint;
import com.motorola.motocit.fingerprint.FingerPrintTest;
import com.motorola.motocit.gdrive.GDriveValidate;
import com.motorola.motocit.gps.GPS;
import com.motorola.motocit.gyroscope.Gyroscope;
import com.motorola.motocit.hdmi.HdmiInfo;
import com.motorola.motocit.headset.HeadsetInfo;
import com.motorola.motocit.heartratesensor.HeartRateSensor;
import com.motorola.motocit.hostmode.Hostmode;
import com.motorola.motocit.irsensor.IRGuesture;
import com.motorola.motocit.key.KeyTest;
import com.motorola.motocit.led.Led;
import com.motorola.motocit.led.MetaKeyLed;
import com.motorola.motocit.light.FrontLight;
import com.motorola.motocit.mag.Magnetometer;
import com.motorola.motocit.mmc.MMC;
import com.motorola.motocit.mods.MotoMods;
import com.motorola.motocit.nfc.NFCTest;
import com.motorola.motocit.personalization.Personalization;
import com.motorola.motocit.proximity.Proximity;
import com.motorola.motocit.proximity.ProximitySecondary;
import com.motorola.motocit.proximity.ProximityThird;
import com.motorola.motocit.capsense.Capsense;
import com.motorola.motocit.ringtone.Ringtone;
import com.motorola.motocit.screenlock.ScreenLockUtil;
import com.motorola.motocit.serviceinfo.ServiceInfo;
import com.motorola.motocit.settings.GetSettingsInfo;
import com.motorola.motocit.setupwizard.SetupWizard;
import com.motorola.motocit.simcard.SIMCard;
import com.motorola.motocit.systemtime.SystemTime;
import com.motorola.motocit.temperature.AmbientTemperature;
import com.motorola.motocit.temperature.CpuTemperature;
import com.motorola.motocit.touchscreen.Touch_Config;
import com.motorola.motocit.touchscreen.Touch_Diagonal;
import com.motorola.motocit.touchscreen.Touch_MXT1386;
import com.motorola.motocit.touchscreen.Touch_MXT1386_Short;
import com.motorola.motocit.touchscreen.Touch_MXT224;
import com.motorola.motocit.touchscreen.Touch_FT;
import com.motorola.motocit.version.Version;
import com.motorola.motocit.vibrator.VibratorTest;
import com.motorola.motocit.wlan.ScanNetwork;
import com.motorola.motocit.wlan.WlanUtilityNexTest;


public class CommServer extends Service
{
    private static final String TAG = "MotoTest_CommServer";

    // Sockets used for communication. Client is re-created on each connection
    // (obviously), Server is persistent with this object
    private Socket client = null;
    private ServerSocket server = null;

    // Input and output streams.
    // private Scanner socketIn;
    protected BufferedReader socketIn;
    protected PrintWriter socketOut;

    // Input and Output Streams for binary transfer;
    protected OutputStream socketBinaryOut = null;
    protected InputStream socketBinaryIn = null;

    // data array for binary transfer
    private static byte[] mBinaryData = null;
    private static boolean mPcReadyForBinary = false;
    private static boolean mBinaryDataSent = false;
    private static final Lock LockPcReadyStatus = new ReentrantLock();
    private static final Lock LockBinaryDataSent = new ReentrantLock();

    //For local socket
    public static final int LOCAL_SOCKET_CONNECTION = 0;
    public static final int EXTERNAL_SOCKET_CONNECTION = 1;
    public static final int INVALID_SOCKET_CONNECTION = -1;
    public static int socketConnectedType = INVALID_SOCKET_CONNECTION;


    // *******************************************************************************
    // Vectors for storing the communication data
    // vectors are used because they are inherently synchronized, which then orders
    // (and syncs) the input/output data.
    // *******************************************************************************
    // stringDataToPcOutputQueue should remain minimally small, as we treat it as a FIFO Queue,
    // popping the element off as it's written
    private static Vector<String> stringDataToPcOutputQueue = new Vector<String>();

    // outputCmdLog size will be capped by OUTPUT_CMD_LOG_SIZE variable.
    // Oldest element will be dropped when size limit is reached
    public static final Vector<String> outputCmdLog = new Vector<String>();
    public static final int OUTPUT_CMD_LOG_SIZE = 100;

    // inputCmdLog size will be capped by INPUT_CMD_LOG_SIZE variable.
    // Oldest element will be dropped when size limit is reached
    public static final Vector<String> inputCmdLog = new Vector<String>();
    public static final int INPUT_CMD_LOG_SIZE = 100;

    private static Vector<CommServerDataPacket> dataPacketOutputQueue = new Vector<CommServerDataPacket>();

    // Keep track of time when last time ServerInput thread received a packet
    public Long lastServerInputPacketTime = System.currentTimeMillis();
    private final Lock lockLastServerInputPacketTime = new ReentrantLock();


    // connected variable defines if the connection is currently alive. Lock is
    // provided to allow synchronised access.
    public static boolean connected = false;
    protected static final Lock lockConnStatus = new ReentrantLock();

    // Lock provided to allow synchronised access to the data being sent.
    private final Lock lockSendData = new ReentrantLock();
    // Lock provided to allow synchronised access to the data being sent.
    // private final Lock lockReceiveData = new ReentrantLock();

    // Notification manager allows the CommServer icon to show up in the
    // notification window.
    protected NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    protected int NOTIFICATION = R.string.local_service_started;

    // all escape sequences start with this char sequence.
    // using this to try to optimize execution time.
    private static final String ESCAPE_SEQUENCE_MARKER = "&#";

    // replace line feed and carriage feed in string data send back to phone
    private static final String LF_ESCAPE_SEQUENCE = "&#10;";
    private static final String CR_ESCAPE_SEQUENCE = "&#13;";

    // replace double quotes and single quotes in middle of strings with escape seq
    private static final String DOUBLE_QUOTE_ESCAPE_SEQUENCE = "&#14;";
    private static final String SINGLE_QUOTE_ESCAPE_SEQUENCE = "&#15;";

    // seq tag for server initiated packets
    private static int nNextUnsolictedSeqTag = 0x80000000;
    private static final Lock lockNextUnsolictedSeqTag = new ReentrantLock();

    // variables for heart beat test that checks if socket is still active
    private static int HEART_BEAT_INTERVAL_MS = 1000;
    public Long lastHeartBeatPacketTime = System.currentTimeMillis();

    // port number which the CommServer is listening to
    private static final int SERVER_PORT_NUMBER = 2631;

    protected PowerManager pm;
    protected PowerManager.WakeLock wl;

    // path to determine BLAN interface name
    private static final String SYS_NET_PATH = "/sys/class/net";
    private static final String BLAN_DESC = "Motorola BLAN Interface";

    private Handler mHandler = new Handler();

    // default delay time to reboot device
    private long REBOOT_DELAY_TIME = 2500; //ms

    // List of supported CommServer commands
    public enum ServerCmdType
    {
        PING, STOP, STOP_ALL, START, ACK, TELL, LOG_DEBUG, REBOOT, HELP, BINARY_ACK;

        @Override
        public String toString()
        {
            return name();
        }
    }

    // List of valid response packet types sent back to PC
    protected enum ServerResponseType
    {
        PASS, FAIL, INFO, BINARYTRANSFER, UNSOLICITED, UNKNOWN;

        @Override
        public String toString()
        {
            return name();
        }
    }

    // List of Unsolicited response types
    protected enum UnsolicitedResponseType
    {
        PING, ERROR;

        @Override
        public String toString()
        {
            return name();
        }
    }

    // String formats for each type of response packet.
    // Used by builtPacket()
    private enum responsePacketFormatType
    {
        ACK         ("0x%08X ACK %s %s %s"),
        NACK        ("0x%08X NACK %s %s %s"),
        INFO        ("0x%08X INFO %s %s %s"),
        BINARYTRANSFER ("0x%08X BINARYTRANSFER %s %s %s"),
        UNSOLICITED ("0x%08X UNSOLICITED %s %s %s"),
        UNKNOWN     ("0x%08X UNKNOWN %s %s %s");

        private final String format;

        private responsePacketFormatType(String format)
        {
            this.format = format;
        }

        public String getFormat()
        {
            return format;
        }
    }

    // Constant for checking to see if there is atleast 1 field after input packet is split by whitespace
    private static int ONE_FIELD_IN_INPUT_PACKET = 1;

    // minimal number of fields after input packet is split by whitespace
    private static int MIN_NUM_FIELDS_INPUT_PACKET = 2;

    // hash tables to store activity name to class mapping and vice versa
    private static HashMap<String, Class<?>> activityNameToClass = new HashMap<String, Class<?>>();
    private static HashMap<String, String> activityClassToName = new HashMap<String, String>();

    // apk version code
    protected int apkVersionCode;

    // Hash to track if activity received broadcast
    private static ConcurrentHashMap<Integer, Boolean> activityTellRxedMap = new ConcurrentHashMap<Integer, Boolean>();

    // default timeout for activity to receive TELL intent
    private static final int ACTIVITY_RXED_DEFAULT_TIMEOUT = 5000;

    // default timeout for STOP_ALL cmd to wait for all activities to stop
    private static final int STOP_ALL_ACTIVITIES_DEFAULT_TIMEOUT = 30000;

    // action used by STOP_ALL cmd to signal all activities to call finish()
    public static final String ACTION_TEST_BASE_FINISH = "motocit.commserver.intent.action.test_base_finish";

    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        CommServer getService()
        {
            dbgLog(TAG, "Retrieving binding service", 'i');
            return CommServer.this;
        }
    }

    /**
     * @return
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        dbgLog(TAG, "Binding to CommServer", 'i');
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate()
    {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        dbgLog(TAG, "OnCreate of CommServer called", 'i');

        // setup activity lookup hashes
        setupActivityClassLookup();

        // get apk version code
        try
        {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),0);
            apkVersionCode = info.versionCode;
        }
        catch (NameNotFoundException e)
        {
            apkVersionCode = -1;
        }


        // Start service in foreground and display a notification about us starting.
        startForegroundAndShowNotification();


        // Create a new server at port 2631
        try
        {
            server = new ServerSocket(SERVER_PORT_NUMBER);
            server.setSoTimeout(0);// infinite timeout
            dbgLog(TAG, "Server started on port " + SERVER_PORT_NUMBER, 'i');
        }
        catch (Exception ex)
        {
            dbgLog(TAG, "Exception found in onCreate method of CommServer.  Message follows", 'e');
            dbgLog(TAG, "" + ex.getMessage(), 'e');
            ex.printStackTrace();
            String strErrMsg = String.format("MESSAGE=%s(%d): %s() Exception found in onCreate method of CommServer. %s",
                    ex.getStackTrace()[0].getFileName(), ex.getStackTrace()[0].getLineNumber(), ex.getStackTrace()[0].getMethodName(),
                    ex.getMessage());

            // Create unsolicited packet back to CommServer reporting an error
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(strErrMsg);
            CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
            sendUnsolicitedPacketToCommServer(unsolicitedPacket);
        }
    }

    // Function to disconnect the CommServer. by setting connected to false, the
    // Server Runnable will disconnect the client
    public static void disconnect()
    {
        try
        {
            lockConnStatus.lock();
            connected = false;
        }
        finally
        {
            lockConnStatus.unlock();
        }
    }



    // This is the "connection" thread of the commServer. Basically this thread
    // continues to ensure that the server remains running and that if there is
    // no client connected it waits for a single connection
    private Runnable commInterfaceRunnable = new Thread()
    {
        @Override
        public void run()
        {
            String connectionStatus;

            dbgLog(TAG, "Starting Comm Server Thread", 'i');
            do
            {
                dbgLog(TAG, "Comm Server Thread waiting for client", 'i');

                try
                {
                    // attempt to accept a connection, .accept() locks for an
                    // incoming connection.
                    client = server.accept();

                    if(connected == true )
                    {
                        dbgLog(TAG, "a client already connected ", 'i');

                        // close client connection
                        client.close();
                        client = null;

                        continue;
                    }

                    // Check to make sure client is allowed to connect
                    if (isSocketAllowed() == false)
                    {
                        // close client connection
                        client.close();
                        client = null;

                        continue;
                    }

                    // set socket options
                    client.setTcpNoDelay(true);  // turn off Nagle algorithm
                    dbgLog(TAG, "turn off nagle algorithm ", 'i');

                    ServerOutput serverOutputThread = new ServerOutput();
                    ServerInput serverInputThread = new ServerInput();
                    BuildOutputPacket buildOutputPacketThread = new BuildOutputPacket();

                    // A client is connecting, create input and output streams.
                    socketBinaryOut = client.getOutputStream();
                    socketBinaryIn = client.getInputStream();
                    socketIn = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    socketOut = new PrintWriter(client.getOutputStream(), true);


                    if (client != null)
                    {
                        try
                        {
                            lockConnStatus.lock();
                            connected = true;
                            socketConnectedType = EXTERNAL_SOCKET_CONNECTION;
                            dbgLog(TAG, "Connected socket type: EXTERNAL_SOCKET_CONNECTION", 'i');
                        }
                        finally
                        {
                            lockConnStatus.unlock();
                        }
                        // print out connection success
                        connectionStatus = "Connection was successful!";
                        dbgLog(TAG, "" + connectionStatus, 'i');

                        // Start the servers, each of these calls start a new
                        // thread.
                        serverOutputThread.start();
                        serverInputThread.start();
                        buildOutputPacketThread.start();

                        // The next section waits for the two preceding threads
                        // both finish. This
                        // ensures that only one client-server connection is
                        // active at a time.
                        try
                        {
                            serverOutputThread.join();
                            serverInputThread.join();
                            buildOutputPacketThread.join();

                            dbgLog(TAG, "Threads JOINED " + this.getId(), 'i');

                            socketIn.close();
                            socketOut.close();

                            socketIn = null;
                            socketOut = null;

                            client.close();
                            client = null;
                            socketConnectedType = INVALID_SOCKET_CONNECTION;

                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }

                    }
                    // Catch blocks for any exception possible above. If
                    // un-handled exceptions happen the
                    // code should be fixed.


                }
                catch (SocketTimeoutException e)
                {
                    // print out TIMEOUT
                    connectionStatus = "Connection has timed out! Please try again";
                    dbgLog(TAG, "" + connectionStatus, 'e');
                }
                catch (IOException e)
                {
                    dbgLog(TAG, "IOException: " + e.getMessage(), 'e');
                }
                catch (Exception e)
                {
                    connectionStatus = "Unknown Exception type caught in commInterfaceRunnable run method.  Message Follows:";
                    dbgLog(TAG, "" + connectionStatus, 'e');
                    dbgLog(TAG, "" + e.getMessage(), 'e');
                    e.printStackTrace();

                    String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                            e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

                    // Create unsolicited packet back to CommServer reporting an error
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(strErrMsg);
                    CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                    sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                }
            }
            while (true);
        }

        // Verify that client connected to socket is allowed to connect
        private boolean isSocketAllowed()
        {
            if (TestUtils.isMotDevice())
            {
                // Connection must meet following criteria for Motorola Devices
                //  1 - connected over blan interface (USB)
                //  2 - IP address of client does not match IP address of phone

                // Get blan interface (should start with usb)
                String blanInterfaceName = findBlanInterface(); // SystemProperties.get("ro.blan.interface", "unknown");

                // Get client (PC) IP Address
                InetAddress clientInetAddress =  client.getInetAddress();

                // make klocwork happy
                if (null == clientInetAddress)
                {
                    return false;
                }

                // get phone's (server's) IP address
                InetAddress serverInetAddress = client.getLocalAddress();

                // make klocwork happy
                if (null == serverInetAddress)
                {
                    return false;
                }

                // Get interface name that server through
                NetworkInterface serverNetworkInterface = null;
                try
                {
                    serverNetworkInterface = NetworkInterface.getByInetAddress(serverInetAddress);
                }
                catch (SocketException e)
                {
                    dbgLog(TAG, "SocketException: " + e.getMessage(), 'e');
                    return false;
                }

                if (serverNetworkInterface == null)
                {
                    dbgLog(TAG, "NetworkInterface.getByInetAddress() failed on serverInetAddress", 'e');
                    return false;
                }

                String serverInterfaceName = serverNetworkInterface.getName();

                // make sure socket connection is over BLAN interface

                if (!blanInterfaceName.equals(serverInterfaceName))
                {
                    dbgLog(TAG, "Interface " + serverInterfaceName + " is not allowed", 'e');
                    return false;
                }

                // Make sure server IP address does not match client IP address
                if (clientInetAddress.equals(serverInetAddress))
                {
                    dbgLog(TAG, "Host address ("     + serverInetAddress.getHostAddress()
                            + ") and peer address (" + clientInetAddress.getHostAddress()
                            + ") the same, not allowed",
                            'e');

                    return false;
                }

                return true;
            }
            else if (TestUtils.isOdmDevice())
            {
                // Connection must meet following criteria for ODM Devices
                // 1 - connected over local IP Address 127.0.0.1
                // 2 - IP address of client must match IP address of phone

                // Get client (PC) IP Address
                InetAddress clientInetAddress = client.getInetAddress();

                // make klocwork happy
                if (null == clientInetAddress)
                {
                    return false;
                }

                // get phone's (server's) IP address
                InetAddress serverInetAddress = client.getLocalAddress();

                // make klocwork happy
                if (null == serverInetAddress)
                {
                    return false;
                }

                // Make sure server IP Address is loopback IP (127.0.0.1)
                if (!serverInetAddress.getHostAddress().equals("127.0.0.1"))
                {
                    dbgLog(TAG, "Host address (" + serverInetAddress.getHostAddress() + ") is not equal to 127.0.0.1", 'e');

                    return false;
                }

                // Make sure server IP address matches client IP address
                if (!clientInetAddress.equals(serverInetAddress))
                {
                    dbgLog(TAG, "Host address (" + serverInetAddress.getHostAddress() + ") and peer address (" + clientInetAddress.getHostAddress()
                            + ") not the same", 'e');

                    return false;
                }

                return true;
            }

            return false;
        }

    };

    // locate the blan interface name
    private String findBlanInterface()
    {
        String blanInterfaceName = null;
        Pattern blanDescRegEx = Pattern.compile(".*"+BLAN_DESC+".*", Pattern.CASE_INSENSITIVE);

        // search through /sys/class/net for interface that has "Motorola BLAN Interface"
        File sysNetDir = new File(SYS_NET_PATH);

        if ((null != sysNetDir) && sysNetDir.isDirectory())
        {
            String[] netDevices = sysNetDir.list();

            if (null != netDevices)
            {
                for (int i = 0; i < netDevices.length; i++)
                {
                    // skip . and ..
                    if (netDevices[i].charAt(0) == '.')
                    {
                        continue;
                    }

                    String netDevicePath = String.format("%s/%s", SYS_NET_PATH, netDevices[i]);

                    File netDeviceDir = new File(netDevicePath);

                    // if netDeviceDir is null or not  directory skip
                    if ((null == netDeviceDir) || (sysNetDir.isDirectory() == false))
                    {
                        continue;
                    }

                    // read netDeviceDir contents
                    String[] deviceInfo = netDeviceDir.list();

                    // if null then skip
                    if (null == deviceInfo)
                    {
                        continue;
                    }

                    // check each file for BLAN_DESC
                    for (int j=0; j < deviceInfo.length; j++)
                    {
                        // skip . and ..
                        if (deviceInfo[j].charAt(0) == '.')
                        {
                            continue;
                        }

                        String deviceInfoPath = String.format("%s/%s/%s", SYS_NET_PATH, netDevices[i], deviceInfo[j]);

                        File deviceInfoFile = new File(deviceInfoPath);

                        // skip if null or not a file
                        if ((null == deviceInfoFile) || (deviceInfoFile.isFile() == false))
                        {
                            continue;
                        }

                        BufferedReader input = null;
                        try
                        {
                            // read file
                            input = new BufferedReader(new FileReader(deviceInfoFile));

                            String tempData;
                            StringBuffer res = new StringBuffer();
                            while ((tempData = input.readLine()) != null)
                            {
                                res.append(tempData);
                            }

                            Matcher matcher = blanDescRegEx.matcher(res.toString());

                            // if found then set blanInterfaceName and kick out
                            if (matcher.find())
                            {
                                blanInterfaceName = netDevices[i];
                                break;
                            }
                        }
                        catch (Exception e)
                        {
                            // do nothing
                        }
                        finally
                        {
                            if (null != input)
                            {
                                try
                                {
                                    input.close();
                                }
                                catch (IOException ignore)
                                {

                                }
                            }
                        }

                    } // end for (int j=0; j < deviceInfo.length; j++)

                    // if found device then kick out loop
                    if (null != blanInterfaceName)
                    {
                        break;
                    }
                } // end for (int i = 0; i < netDevices.length; i++)
            }
        }

        // only try to read sys prop if all else fails
        if (null == blanInterfaceName)
        {
            blanInterfaceName = SystemProperties.get("ro.blan.interface", "unknown");
        }

        return blanInterfaceName;
    }

    // Output thread for sending data from phone to PC. This thread remains
    // active as long as the connection is active. Further, it ensures that the
    // connection is active by sending PING to the client (expecting ACK back).
    protected class ServerOutput extends Thread
    {
        @Override
        public void run()
        {
            lastServerInputPacketTime = System.currentTimeMillis();
            dbgLog(TAG, "Starting Phone to PC Link", 'i');

            try
            {
                while (connected == true)
                {
                    if (socketConnectedType == EXTERNAL_SOCKET_CONNECTION)
                    {
                        if (!client.isConnected())
                        {
                            break;
                        }
                    }

                    Thread.sleep(5);

                    if (true != stringDataToPcOutputQueue.isEmpty())
                    {
                        String strData = removeStringFromOutBuffer();

                        if (null == strData)
                        {
                            continue;
                        }

                        sendData(strData, mBinaryData);

                        if (mBinaryDataSent)
                        {
                            mBinaryData = null;

                            System.gc();
                            LockBinaryDataSent.lock();
                            mBinaryDataSent = false;
                            LockBinaryDataSent.unlock();
                        }
                        // Keep a history of all the responses sent to the PC
                        if (outputCmdLog.size() > OUTPUT_CMD_LOG_SIZE)
                        {
                            outputCmdLog.remove(0);
                        }
                        outputCmdLog.add(strData);
                    }

                    // Heart beat PING unsolicited commands sent to PC.
                    // This way we can detect if the socket connection has died
                    // and take appropriate actions.

                    if ((((System.currentTimeMillis() - lastServerInputPacketTime) > HEART_BEAT_INTERVAL_MS)
                            && ((System.currentTimeMillis() - lastHeartBeatPacketTime) > HEART_BEAT_INTERVAL_MS)) || ((System.currentTimeMillis() - lastHeartBeatPacketTime) < 0))
                    {
                        dbgLog(TAG, "Sending heartbeat packet to verify comm link is alive", 'v');

                        lastHeartBeatPacketTime = System.currentTimeMillis();

                        // build unsolicited packet
                        String strCmdData = "MESSAGE=HEY PC YOU THERE?";

                        // Create unsolicited packet back to CommServer reporting an error
                        List<String> strDataList = new ArrayList<String>();
                        strDataList.add(strCmdData);
                        CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.PING.toString(), TAG, strDataList);
                        sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                    }
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unknown Exception type caught in ServerOutput run method.  Message Follows:", 'e');
                dbgLog(TAG, "" + e.getMessage(), 'e');
                e.printStackTrace();

                // Build Packet
                String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                        e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

                // Create unsolicited packet back to CommServer reporting an
                // error
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                sendUnsolicitedPacketToCommServer(unsolicitedPacket);

                // output writer thread died.. signal that connection is dead
                disconnect();

            }
            finally
            {
                // Connection is effectively closed, clear all communication
                // logs
                // Done in finally to ensure that if there is an exception these
                // logs are still cleared
                synchronized (stringDataToPcOutputQueue)
                {
                    stringDataToPcOutputQueue.clear();
                }

                outputCmdLog.clear();
                inputCmdLog.clear();
            }
            dbgLog(TAG, "Server Connection Closed", 'i');
            try
            {
                // Close the client connection
                if (client != null)
                {
                    client.close();
                }

            }
            catch (IOException ec)
            {
                dbgLog(TAG, "Cannot close client socket" + ec, 'e');
            }

        } // end of ServerOutput Thread

        // Function for sending data from the device to the PC. This function
        // implements synchronised communication, so println should NEVER be
        // used on it's own
        private void sendData(String data, byte[] binaryData)
        {
            try
            {
                // acquire the lock to send data
                lockSendData.lock();

                // send the data
                dbgLog(TAG, "Sending:" + data, 'v');
                socketOut.println(data);

                if(binaryData != null && data.contains("FILE_SIZE"))
                {
                    // check to see if PC ready for Binary Data
                    dbgLog(TAG, "Checking To See If PC Ready For Binary Data", 'v');
                    long startTimeForPCReady = System.currentTimeMillis();
                    long maxTimeToWaitForPC = 2000;
                    while(!mPcReadyForBinary && Test_Base.BinaryWaitForAck)
                    {
                        Thread.sleep(1);
                        if((System.currentTimeMillis() - startTimeForPCReady) > maxTimeToWaitForPC)
                        {

                            dbgLog(TAG, "Max Wait Time For PC Binary_Ack Exceeded", 'v');
                            break;
                        }
                    }

                    if(mPcReadyForBinary || !Test_Base.BinaryWaitForAck)
                    {
                        // send the data
                        // need to slow down data to pc due to TCP packet failures
                        dbgLog(TAG, "Sending: Binary Data", 'v');
                        try
                        {
                            int bytesWritten = 0;
                            int chunkSize = 4000;
                            while((bytesWritten + chunkSize) < binaryData.length)
                            {
                                socketBinaryOut.write(binaryData, bytesWritten, chunkSize);
                                bytesWritten = bytesWritten + chunkSize;
                                Thread.sleep(1);
                            }
                            socketBinaryOut.write(binaryData, bytesWritten, binaryData.length - bytesWritten);
                            socketBinaryOut.flush();
                            LockBinaryDataSent.lock();
                            mBinaryDataSent = true;
                            LockBinaryDataSent.unlock();

                        }
                        catch (IOException e)
                        {
                            dbgLog(TAG, "SeverOutput thread failed while sending binary data. Signalling link disconnected", 'v');
                            e.printStackTrace();
                            disconnect();
                        }
                    }

                }

                if (socketOut.checkError())
                {
                    dbgLog(TAG, "ServerOutput thread yacked while sending data.  Signalling link disconnected", 'v');
                    disconnect();
                }

            }
            catch (InterruptedException e)
            {
                dbgLog(TAG, "ServerOutput thread yacked when trying to sleep",'v');
                e.printStackTrace();
            }
            finally
            {
                // unlock the data
                lockSendData.unlock();
                LockPcReadyStatus.lock();
                mPcReadyForBinary = false;
                LockPcReadyStatus.unlock();
            }
        }
    };

    // Thread that continuously loops looking for incoming data while the
    // connection is active
    // Because this is the only place we get data, the single loop ensures that
    // we process it FIFO
    protected class ServerInput extends Thread
    {
        @Override
        public void run()
        {

            String strThreadId = " " + this.getId() + " " + this.getName();

            dbgLog(TAG, "Starting PC to Phone Link" + strThreadId, 'i');

            try
            {
                // loop while connected
                while (connected == true)
                {
                    if (socketConnectedType == EXTERNAL_SOCKET_CONNECTION)
                    {
                        if (!client.isConnected())
                        {
                            break;
                        }
                    }

                    // The following line only hits when data is received by the
                    // input socket
                    Thread.sleep(5);
                    // receive the data

                    String strInputPacket;

                    if ((strInputPacket = socketIn.readLine()) != null)
                    {
                        inputCmdLog.add(strInputPacket);

                        // Log that we received a packet and clear out any
                        // heartbeat packet sent

                        lockLastServerInputPacketTime.lock();
                        lastServerInputPacketTime = System.currentTimeMillis();
                        lockLastServerInputPacketTime.unlock();

                        // *******************************************
                        // Parse out input packet.
                        // The following are the field definitions.
                        //   [0] = sequence tag
                        //   [1] = command
                        //   [2+] = command data (optional)
                        //
                        // All packets must contain at least 2 fields
                        // which are the 'sequence tag' and the command.
                        // *******************************************
                        List<String> strSplitInputList = splitPacket(strInputPacket);

                        // go through split list and unescape any characters
                        // try to save execution time by checking if element has any escape sequences
                        if (strInputPacket.contains(ESCAPE_SEQUENCE_MARKER) == true)
                        {
                            for (int i = 0; i < strSplitInputList.size(); i++)
                            {
                                // unescape newline and carriage return
                                strSplitInputList.set(i, strSplitInputList.get(i).replaceAll(LF_ESCAPE_SEQUENCE, "\n"));
                                strSplitInputList.set(i, strSplitInputList.get(i).replaceAll(CR_ESCAPE_SEQUENCE, "\r"));

                                // unescape double and single quotes
                                strSplitInputList.set(i, strSplitInputList.get(i).replaceAll(DOUBLE_QUOTE_ESCAPE_SEQUENCE, "\""));
                                strSplitInputList.set(i, strSplitInputList.get(i).replaceAll(SINGLE_QUOTE_ESCAPE_SEQUENCE, "'"));
                            }
                        }

                        // Get Sequence Tag
                        // if not sequence tag then an empty (blank) packet
                        if (strSplitInputList.size() < ONE_FIELD_IN_INPUT_PACKET)
                        {
                            // no data in packet.. ignore packet
                            dbgLog(TAG, String.format("Skipping empty packet: '%s'", strInputPacket), 'v');
                            continue;
                        }

                        String strSeqTag = strSplitInputList.get(0).toLowerCase();

                        // Convert sequence tag to integer value
                        int nSeqTag = -1;
                        try
                        {
                            if (strSeqTag.startsWith("0x"))
                            {
                                nSeqTag = (int) Long.parseLong(strSeqTag.replaceFirst("^0x", ""), 16);
                            }
                            else
                            {
                                nSeqTag = Integer.parseInt(strSeqTag);
                            }
                        }
                        catch (Exception e)
                        {
                            dbgLog(TAG, String.format("Packet does have valid seq tag %s", strInputPacket), 'v');

                            // Build Packet
                            String strErrMsg = String.format("MESSAGE=Received packet '%s' does not have valid seq tag", strInputPacket);

                            // Create unsolicited packet back to CommServer
                            // reporting an error
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(strErrMsg);
                            CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                            sendUnsolicitedPacketToCommServer(unsolicitedPacket);

                            continue;
                        }

                        // valid packets have atleast two fields!
                        // - first field is the sequence tag.
                        // - second field is the command.
                        if (strSplitInputList.size() < MIN_NUM_FIELDS_INPUT_PACKET)
                        {
                            dbgLog(TAG, String.format("Input packet does not have atleast two field %s", strInputPacket), 'v');

                            // Create packet object and send it to
                            // sendCmdFailToCommServer
                            String strErrMsg = String.format("Received packet '%s' does not have at least two fields", strInputPacket);
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(strErrMsg);
                            CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, "ERROR", TAG, strErrMsgList);
                            sendCmdFailToCommServer(failPacket);

                            continue;

                        }
                        String strCmd = strSplitInputList.get(1).toUpperCase();

                        // retrieve command data (if present)
                        // command data will be in field third and following fields
                        List<String> strCmdDataList = new ArrayList<String>();

                        if (strSplitInputList.size() > MIN_NUM_FIELDS_INPUT_PACKET)
                        {
                            strCmdDataList.addAll(strSplitInputList.subList(MIN_NUM_FIELDS_INPUT_PACKET, strSplitInputList.size()));
                        }

                        // ##########################################
                        // Examine command and take correct actions
                        // ##########################################

                        // respond to PING
                        if (strCmd.equalsIgnoreCase(ServerCmdType.PING.toString()))
                        {
                            CommServerRespondToPing(nSeqTag, strCmd);
                        }
                        // PC responded to heart beat PING
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.ACK.toString()))
                        {
                            dbgLog(TAG, "Connection Verified ", 'v');
                        }
                        // get CommServer HELP
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.HELP.toString()))
                        {
                            CommServerRespondToHelp(nSeqTag, strCmd);
                        }
                        // start activity
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.START.toString()))
                        {
                            CommServerStartActivity(nSeqTag, strCmd, strCmdDataList);
                        }
                        // stop activity
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.STOP.toString()))
                        {
                            CommServerStopActivity(nSeqTag, strCmd, strCmdDataList);
                        }
                        // stop all activities
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.STOP_ALL.toString()))
                        {
                            new CommServerStopAllThread(nSeqTag, strCmd, strCmdDataList).start();
                        }
                        // tell activity to do something
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.TELL.toString()))
                        {
                            new CommServerTellThread(nSeqTag, strCmd, strCmdDataList).start();
                        }
                        // enable/disable debug logging
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.LOG_DEBUG.toString()))
                        {
                            CommServerLogDebug(nSeqTag, strCmd, strCmdDataList);
                        }
                        // reboot device
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.REBOOT.toString()))
                        {
                            CommServerRespondToReboot(nSeqTag, strCmd, strCmdDataList);
                        }
                        else if (strCmd.equalsIgnoreCase(ServerCmdType.BINARY_ACK.toString()))
                        {
                            LockPcReadyStatus.lock();
                            mPcReadyForBinary = true;
                            LockPcReadyStatus.unlock();
                        }
                        // Unknown command .. return NACK to PC
                        else
                        {
                            CommServerRespondToUnknownCmd(nSeqTag, strCmd);
                        }

                        // the inputCmdLog variable is only used for logging purposes,
                        while (inputCmdLog.size() > INPUT_CMD_LOG_SIZE)
                        {
                            inputCmdLog.remove(0);
                        }

                    }
                }
            }
            catch (SocketException e)
            {
                dbgLog(TAG, "Input socket threw SocketException.  Assuming connection was ended by client.", 'i');
                disconnect();

                inputCmdLog.clear();

            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unknown Exception type caught in ServerInput run method.  Message Follows:" + strThreadId, 'e');
                dbgLog(TAG, "" + e.getMessage(), 'e');
                e.printStackTrace();

                String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                        e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

                // Create unsolicited packet back to CommServer reporting an error
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                sendUnsolicitedPacketToCommServer(unsolicitedPacket);

                // input reader thread died.. signal that connection is dead
                disconnect();

                inputCmdLog.clear();
            }

            dbgLog(TAG, "Client Connection Closed" + strThreadId, 'i');
        }

        // function to send response packet from PING command
        private void CommServerRespondToPing(int nSeqTag, String strCmd)
        {
            dbgLog(TAG, "PING RECEIVED ", 'v');

            // Build Pass Packet
            List<String> strReturnDataList = new ArrayList<String>();

            // piggyback PING to send versionCode.
            // Normally you should send an info packet but that would
            // slow down the PING command and the PING command needs
            // to be as fast as possible.
            // Note the NexTest driver will not parse this keyValue
            // pair and I wrote custom code support this very
            // specific use case in the calling app.
            strReturnDataList.add("VERSION_CODE="+apkVersionCode);

            CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);

            sendCmdPassToCommServer(passPacket);

            return;
        }

        // function to respond to HELP command
        private void CommServerRespondToHelp(int nSeqTag, String strCmd)
        {
            printHelp(nSeqTag, strCmd);

            // Build Pass Packet
            List<String> strReturnDataList = new ArrayList<String>();
            CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);
            sendCmdPassToCommServer(passPacket);

            return;
        }

        // function to reboot device
        private void CommServerRespondToReboot(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {

            // see if user passed in timeout
            for (String keyValuePair : strCmdDataList)
            {
                String splitResult[] = keyValuePair.split("=");

                if (splitResult.length != 2)
                {
                    // Create packet object and send it to
                    // sendCmdFailToCommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalidly formatted key-value pair '%s'", keyValuePair));
                    CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                    sendCmdFailToCommServer(failPacket);
                    return;
                }

                String keyName = splitResult[0];
                String value = splitResult[1];

                if (keyName.equalsIgnoreCase("REBOOT_DELAY_TIME"))
                {
                    int userRebootDelay = Integer.parseInt(value);
                    if (userRebootDelay >= 0)
                    {
                        REBOOT_DELAY_TIME = userRebootDelay;
                    }
                }
            }

            dbgLog(TAG, "REBOOT RECEIVED ", 'v');
            reboot();

            // Build Pass Packet
            List<String> strReturnDataList = new ArrayList<String>();
            CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);
            sendCmdPassToCommServer(passPacket);

            return;
        }

        // function to start activity
        private void CommServerStartActivity(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {
            // make sure activity name is defined
            if (strCmdDataList.size() < 1)
            {
                dbgLog(TAG, strCmd + " command missing activity name ", 'v');

                // Build Packet
                String strErrMsg = String.format("'%s' cmd missing activity name", strCmd);

                // Create packet object and send it to sendCmdFailToCommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);

                return;
            }

            String strActivityName = strCmdDataList.get(0).toLowerCase();

            dbgLog(TAG, "start activity " + strActivityName, 'v');
            Intent myActivity = findActivity(strActivityName);

            // This if should be redundant, but ensures safety
            if (myActivity != null)
            {
                // Try turning off all animations, and then
                // start activity (MotoBlur breaks the animation
                // commands).
                myActivity.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );


                // add nSeqTag, strCmd and strCmdDataList to
                // intent
                myActivity.putExtra("seq_tag", nSeqTag);
                myActivity.putExtra("cmd", ServerCmdType.START.toString());
                myActivity.putStringArrayListExtra("cmd_data", (ArrayList<String>) strCmdDataList);

                startActivity(myActivity);

                // activity will generate ACK is successfully
            }
            else
            {
                dbgLog(TAG, "ERROR No activity " + strActivityName, 'v');
                // Create packet object and send it to sendCmdFailToCommServer
                String strErrMsg = String.format("Activity called '%s' does not exist", strActivityName);
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);
            }

            return;
        }


        // Create thread to stop all activities so input thread can respond to other
        // incoming commands and not get bogged down waiting for activities to stop
        private class CommServerStopAllThread extends Thread
        {
            private int nSeqTag;
            private String strCmd;
            private List<String> strCmdDataList;

            public CommServerStopAllThread(int nSeqTag, String strCmd, List<String> strCmdDataList)
            {
                this.nSeqTag = nSeqTag;
                this.strCmd = strCmd;
                this.strCmdDataList = strCmdDataList;
            }

            @Override
            public void run()
            {
                dbgLog(TAG, "CommServerStopAllThread:run", 'v');
                CommServerStopAllActivities(nSeqTag, strCmd, strCmdDataList);
            }
        }

        // function to stop all activities
        private void CommServerStopAllActivities(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {
            int stopAllTimeoutMs = STOP_ALL_ACTIVITIES_DEFAULT_TIMEOUT;

            // see if user passed in timeout
            for(String keyValuePair : strCmdDataList)
            {
                String splitResult[] = keyValuePair.split("=");

                if (splitResult.length != 2)
                {
                    // Create packet object and send it to sendCmdFailToCommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalidly formatted key-value pair '%s'", keyValuePair));
                    CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                    sendCmdFailToCommServer(failPacket);
                    return;
                }

                String keyName = splitResult[0];
                String value = splitResult[1];

                if (keyName.equalsIgnoreCase("TIMEOUT_MS"))
                {
                    int userTimeoutMs =  Integer.parseInt(value);
                    if (userTimeoutMs > 0)
                    {
                        stopAllTimeoutMs = userTimeoutMs;
                    }
                }
            }

            // create broadcast to all activities based off Test_Base to call finish
            Intent testBaseChildrenIntent = new Intent(ACTION_TEST_BASE_FINISH);
            sendBroadcast(testBaseChildrenIntent);

            // create broadcast to ALT base activities
            TestUtils.dbgLog(TAG, "Stop test - Sending broadcast to stop alt test", 'i');
            Intent altTestBaseIntent = new Intent(com.motorola.motocit.alt.altautocycle.ALTBaseActivity.ACTION_ALT_TEST_BASE_FINISH);
            sendBroadcast(altTestBaseIntent);

            // now loop until all activities are finished
            ActivityManager mActivityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.RunningTaskInfo info;
            List<String> stillRunningActivities = new ArrayList<String>();

            long startTime = System.currentTimeMillis();
            boolean stillRunning = false;

            while (true)
            {
                dbgLog(TAG, "CommServerStopAllActivities loop top", 'v');

                stillRunningActivities.clear();
                stillRunning = false;

                List <ActivityManager.RunningTaskInfo> runningTasks = mActivityManager.getRunningTasks(9999);

                // keep klocwork happy
                if (runningTasks == null)
                {
                    break;
                }

                Iterator <ActivityManager.RunningTaskInfo> i = runningTasks.iterator();

                while(i.hasNext())
                {
                    info=i.next();

                    // only check task in our package
                    if (getPackageName().equals(info.topActivity.getPackageName()))
                    {
                        String activityName = getActivityNameFromClassName(info.topActivity.getClassName());
                        dbgLog(TAG, "CommServerStopAllActivities still running = " + activityName, 'v');
                        if (activityName != null)
                        {
                            stillRunningActivities.add(activityName);
                            stillRunning = true;
                        }
                    }
                }

                if (stillRunning == false)
                {
                    break;
                }

                if ((System.currentTimeMillis() - startTime) > stopAllTimeoutMs)
                {
                    dbgLog(TAG, "CommServerStopAllActivities failed to stop = " + stillRunningActivities.toString(), 'v');

                    // Create packet object and send it to sendCmdFailToCommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("Failed to stop all activites within " + stopAllTimeoutMs +" ms.  The following are still running: " + TextUtils.join(",", stillRunningActivities.toArray()));
                    CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                    sendCmdFailToCommServer(failPacket);
                    return;
                }

                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException ignore)
                {
                }

                // resend broadcast because some activities could have been "destroyed" to save memory
                // when the last broadcast was send out.
                sendBroadcast(testBaseChildrenIntent);
            }

            // Build Pass Packet
            List<String> strReturnDataList = new ArrayList<String>();
            CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);
            sendCmdPassToCommServer(passPacket);
        }

        // function to stop activity
        private void CommServerStopActivity(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {
            // make sure activity name is defined
            if (strCmdDataList.size() < 1)
            {
                dbgLog(TAG, strCmd + " command missing activity name ", 'v');

                // Build Packet
                String strErrMsg = String.format("'%s' cmd missing activity name", strCmd);
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);

                return;
            }

            String strActivityName = strCmdDataList.get(0).toLowerCase();

            dbgLog(TAG, "stop activity " + strActivityName, 'v');

            Intent myActivity = new Intent(strActivityName);
            myActivity.putExtra(strActivityName, ServerCmdType.STOP.toString());
            myActivity.putExtra("seq_tag", nSeqTag);
            myActivity.putExtra("cmd", ServerCmdType.STOP.toString());
            myActivity.putStringArrayListExtra("cmd_data", (ArrayList<String>) strCmdDataList);

            sendBroadcast(myActivity);

            // activity will generate the ACK/NACK packet
            return;
        }

        // Create thread to process TELL so input thread can respond to other
        // incoming commands and not get bogged down waiting for activity to
        // receive TELL command.
        private class CommServerTellThread extends Thread
        {
            private int nSeqTag;
            private String strCmd;
            private List<String> strCmdDataList;

            public CommServerTellThread(int nSeqTag, String strCmd, List<String> strCmdDataList)
            {
                this.nSeqTag = nSeqTag;
                this.strCmd = strCmd;
                this.strCmdDataList = strCmdDataList;
            }

            @Override
            public void run()
            {
                dbgLog(TAG, "CommServerTellThread:run", 'v');
                CommServerTellActivity(nSeqTag, strCmd, strCmdDataList);
            }
        }

        // function to tell activity to do something
        private void CommServerTellActivity(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {
            int activityRxedTimeout = ACTIVITY_RXED_DEFAULT_TIMEOUT;

            // pick out TELL command parameters
            // These parameters should start with TELL_PARAM
            for(Iterator<String> it = strCmdDataList.iterator(); it.hasNext();)
            {
                String keyValuePair = it.next();

                String splitResult[] = keyValuePair.split("=");

                // if not a key-value pair just skip
                if (splitResult.length != 2)
                {
                    continue;
                }

                String keyName = splitResult[0];
                String value = splitResult[1];

                if (keyName.equalsIgnoreCase("TELL_PARAM_RX_TIMEOUT_MS"))
                {
                    int userTimeoutMs =  Integer.parseInt(value);
                    if (userTimeoutMs > 0)
                    {
                        activityRxedTimeout = userTimeoutMs;
                    }

                    it.remove();
                }
            }

            // make sure activity name is defined
            if (strCmdDataList.size() < 2)
            {
                dbgLog(TAG, strCmd + " needs at least two arguments", 'v');

                // Build Packet
                String strErrMsg = String.format("'%s' cmd expects at least two arguments", strCmd);

                // Create packet object and send it to sendCmdFailToCommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);

                return;
            }

            String strActivityName = strCmdDataList.get(0).toLowerCase();

            String strTellCmd = strCmdDataList.get(1).toUpperCase();

            // retrieve activity specific tell command data (if present)
            List<String> strTellDataList = new ArrayList<String>();
            if (strCmdDataList.size() > 2)
            {
                strTellDataList.addAll(strCmdDataList.subList(2, strCmdDataList.size()));
            }

            dbgLog(TAG, "tell " + strActivityName + " cmd " + strTellCmd, 'v');

            Intent myActivity = new Intent(strActivityName);
            myActivity.putExtra(strActivityName, strTellCmd);
            myActivity.putExtra("seq_tag", nSeqTag);
            myActivity.putExtra("cmd", strTellCmd);
            myActivity.putStringArrayListExtra("cmd_data", (ArrayList<String>) strTellDataList);

            // remove seq tag out of tell received map if for some odd reason it's already in there
            clearActivityReceivedCmd(nSeqTag);

            sendBroadcast(myActivity);

            dbgLog(TAG, "starting tell rx check", 'v');

            // see if we can detect if activity received broadcast
            long startTime = System.currentTimeMillis();

            while (true)
            {
                dbgLog(TAG, "checking if tell was rxed", 'v');

                if (didActivityReceiveCmd(nSeqTag) )
                {
                    dbgLog(TAG, "activity rxed tell cmd", 'v');
                    clearActivityReceivedCmd(nSeqTag);
                    break;
                }

                if ((System.currentTimeMillis() - startTime) > activityRxedTimeout)
                {
                    dbgLog(TAG, "tell failed to be rxed within " + activityRxedTimeout + " ms", 'e');

                    // Build Packet
                    String strErrMsg = String.format("CommServer did not detect that activity '%s' received TELL cmd within %d ms", strActivityName, activityRxedTimeout);

                    // Create packet object and send it to sendCmdFailToCommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(strErrMsg);
                    CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                    sendCmdFailToCommServer(failPacket);
                    return;
                }

                try
                {
                    Thread.sleep(10);
                }
                catch (InterruptedException ignore)
                {
                    dbgLog(TAG, "InterruptedException rxed", 'e');
                }
            }

            // remove seq tag from map to release memory
            activityTellRxedMap.remove(nSeqTag);

            dbgLog(TAG, String.format("activity took %d ms to rx cmd", System.currentTimeMillis() - startTime), 'v');

            // activity will generate the ACK/NACK packet
            return;
        }


        // function to enable or disable debug logging
        private void CommServerLogDebug(int nSeqTag, String strCmd, List<String> strCmdDataList)
        {
            // make at least one key-value pair is supplied
            if (strCmdDataList.size() < 1)
            {
                // Create packet object and send it to sendCmdFailToCommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("No key=value pairs sent for %s cmd", strCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'v');
                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);

                return;
            }

            // retrieve key=value pairs
            for (String keyValuePair : strCmdDataList)
            {
                String splitResult[] = keyValuePair.split("=");
                String key = splitResult[0];
                String value = (splitResult.length > 1) ? splitResult[1] : null;

                // enable/disable debug
                if (key.equalsIgnoreCase("ENABLE"))
                {
                    if (value.equalsIgnoreCase("TRUE"))
                    {
                        TestUtils.cqaLogDebugEnable(true);
                    }
                    else if (value.equalsIgnoreCase("FALSE"))
                    {
                        TestUtils.cqaLogDebugEnable(false);
                    }
                    else
                    {
                        // Generate an exception to send FAIL result and mesg back to CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Invalid value for key %s. Expected TRUE or FALSE. You supplied %s", key, value));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                        sendCmdFailToCommServer(failPacket);

                        return;
                    }
                }
                // get enable status
                else if (key.equalsIgnoreCase("GET_ENABLE_STATUS"))
                {
                    List<String> strDataList = new ArrayList<String>();
                    strDataList.add("STATUS="+TestUtils.getCqaLogDebugState());
                    CommServerDataPacket infoPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strDataList);
                    sendInfoPacketToCommServer(infoPacket);
                }
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);
            sendCmdPassToCommServer(passPacket);

            return;
        }

        // function to send response packet tell PC it send an unknown command
        private void CommServerRespondToUnknownCmd(int nSeqTag, String strCmd)
        {
            String strErrMsg = String.format("Unknown command '%s'", strCmd);

            // Create packet object and send it to
            // sendCmdFailToCommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(strErrMsg);
            CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
            sendCmdFailToCommServer(failPacket);

            return;
        }

    };

    // Thread that will continuously run looking for Output packet to build and
    // place built packet on Output vector
    protected class BuildOutputPacket extends Thread
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "BuildOutputPacket thread start", 'i');

            // always run regardless if server is connected since we don't want
            // to miss any packets
            while (connected == true)
            {
                if (socketConnectedType == EXTERNAL_SOCKET_CONNECTION)
                {
                    if (!client.isConnected())
                    {
                        break;
                    }
                }

                try
                {
                    Thread.sleep(5);

                    // check data packet queue for packet
                    if (dataPacketOutputQueue.isEmpty())
                    {
                        continue;
                    }

                    // remove first element and process it
                    CommServerDataPacket dataPacket = removePacketFromOutputQueue();

                    if (null == dataPacket)
                    {
                        continue;
                    }

                    dbgLog(TAG,
                            "BuildOutputPacket thread received data packet from " + dataPacket.strSenderTag + "for seq tag " + dataPacket.nSeqTag + " result " + dataPacket.packetResponseType.toString(),
                            'i');

                    // build dataPacket into a string to send back to PC
                    String builtDataPacket = buildPacket(dataPacket);

                    // Add string to output buffer to send to PC
                    addStringToOutBuffer(builtDataPacket);


                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Unknown Exception type caught in BuildOutputPacket thread run method.  Message Follows:", 'e');
                    dbgLog(TAG, "" + e.getMessage(), 'e');
                    e.printStackTrace();

                    String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                            e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

                    // Create unsolicited packet back to CommServer reporting an error
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(strErrMsg);
                    CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                    sendUnsolicitedPacketToCommServer(unsolicitedPacket);

                    // packet builder thread died.. signal that connection is dead
                    disconnect();
                }
            }

            dbgLog(TAG, "BuildOutputPacket thread exit", 'i');

        }

    };

    /**
     * Start service in foreground and show a notification while this service is running.
     */
    protected void startForegroundAndShowNotification()
    {
        // Setup the text from the string resource
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and time stamp
        Notification notification = new Notification(R.drawable.comm_server_icon, text, System.currentTimeMillis());

        notification.flags |= Notification.FLAG_NO_CLEAR;

        // The PendingIntent to launch our activity if the user selects this notification
        // Right now, this launches the Test_Main activity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, com.motorola.motocit.AppMainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label), text, contentIntent);

        // start in foreground to hopefully ask android not to kill this service when running low on memory
        startForeground(NOTIFICATION, notification);
    }

    @Override
    // The below function is called every time the user starts the CommServer
    // service. We override it to launch the
    // commInterfaceRunnable thread, ensuring that thread is always running.
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        dbgLog(TAG, "Received start id " + startId + ": " + intent, 'i');

        // Acquire wakelock to prevent service from stopping if phone
        // tries to go to sleep
        dbgLog(TAG, "Setting partial wake lock", 'i');
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        new Thread(commInterfaceRunnable).start();


        return START_STICKY;
    }

    @Override
    // Stop the CommServer
    public void onDestroy()
    {

        dbgLog(TAG, "onDestroy() Called", 'i');

        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // release wake lock
        if(wl != null)
        {
            wl.release();
        }

        if (null != server)
        {
            try
            {
                server.close();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }


        // Tell the user we stopped.
        try
        {
            Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
        }
        catch (Resources.NotFoundException e)
        {
            dbgLog(TAG, "Exception in onDestroy Method, Bad Variables for toast", 'e');
            e.printStackTrace();
        }
    }

    // Externally visible wrapper function to send FAIL packet back to PC
    public static void sendCmdFailToCommServer(CommServerDataPacket failPacket)
    {
        sendPacketToCommServer(ServerResponseType .FAIL, failPacket);
    }

    // Externally visible wrapper function to send PASS packet back to PC
    public static void sendCmdPassToCommServer(CommServerDataPacket passPacket)
    {
        sendPacketToCommServer(ServerResponseType .PASS, passPacket);
    }

    // Externally visible wrapper function to send INFO packet back to PC
    public static void sendInfoPacketToCommServer(CommServerDataPacket infoPacket)
    {
        sendPacketToCommServer(ServerResponseType .INFO, infoPacket);
    }

    // Externally visible wrapper function to send BINARY packet back to PC
    public static void sendBinaryPacketToCommServer(CommServerBinaryPacket binaryPacket)
    {
        // only can handle one BinaryPacket in the queue at any given time.
        // This limitation should not be a problem sense calling apk tell command will wait until complete before sending another tell command.
        mBinaryData = binaryPacket.binaryData;
        CommServerDataPacket infoPacket = new CommServerDataPacket(binaryPacket.nSeqTag,binaryPacket.strCmd, binaryPacket.strSenderTag, binaryPacket.strInfoDataList);
        sendPacketToCommServer(ServerResponseType.BINARYTRANSFER, infoPacket);
        binaryPacket.binaryData = null;
    }

    // Externally visible wrapper function to send UNSOLICITED packet back to PC
    public static void sendUnsolicitedPacketToCommServer(CommServerDataPacket infoPacket)
    {
        sendPacketToCommServer(ServerResponseType .UNSOLICITED, infoPacket);
    }

    // Private generic function that builds output packet and places it on output queue
    private static void sendPacketToCommServer(ServerResponseType packetResponseType, CommServerDataPacket dataPacket)
    {
        dataPacket.packetResponseType = packetResponseType;
        addPacketToOutputQueue(dataPacket);
    }

    // Takes CommServerDataPacket and builds the response string that will be sent back to the PC
    private static String buildPacket(CommServerDataPacket dataPacket)
    {
        // for dataPacket.strInfoDataList add double quotes to any entries
        // that have spaces and are not already double quotes
        for (int i = 0; i < dataPacket.strInfoDataList.size(); i++)
        {
            // Need to escape linefeed and carriage-returns
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceAll("\n", LF_ESCAPE_SEQUENCE));
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceAll("\r", CR_ESCAPE_SEQUENCE));

            // strip off any " at begining and end of string
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceFirst("^\\s*\"", ""));
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceFirst("\"\\s*$", ""));

            // escape any double quotes and single quotes
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceAll("\"", DOUBLE_QUOTE_ESCAPE_SEQUENCE));
            dataPacket.strInfoDataList.set(i, dataPacket.strInfoDataList.get(i).replaceAll("'", SINGLE_QUOTE_ESCAPE_SEQUENCE));

            // check if any spaces in string
            if (dataPacket.strInfoDataList.get(i).contains(" "))
            {
                dataPacket.strInfoDataList.set(i, "\"" + dataPacket.strInfoDataList.get(i) + "\"");
            }
        }

        String builtPacket;
        switch(dataPacket.packetResponseType)
        {
        case PASS:
            builtPacket = String.format(responsePacketFormatType.ACK.getFormat(),
                    dataPacket.nSeqTag, dataPacket.strCmd,dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));

            break;

        case FAIL:
            builtPacket = String.format(responsePacketFormatType.NACK.getFormat(),
                    dataPacket.nSeqTag, dataPacket.strCmd,dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));

            break;

        case INFO:
            builtPacket = String.format(responsePacketFormatType.INFO.getFormat(),
                    dataPacket.nSeqTag, dataPacket.strCmd,dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));
            break;

        case BINARYTRANSFER:
            builtPacket = String.format(responsePacketFormatType.BINARYTRANSFER.getFormat(),
                    dataPacket.nSeqTag, dataPacket.strCmd, dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));
            break;


        case UNSOLICITED:

            int nUnsolSeqTag;
            // SeqTag is generated based on strCmd
            if (dataPacket.strCmd.equalsIgnoreCase(UnsolicitedResponseType.ERROR.toString()))
            {
                nUnsolSeqTag = 0xFFFFFFFF; // special seq tag for errors
            }
            else
            {
                try
                {
                    lockNextUnsolictedSeqTag.lock();
                    nUnsolSeqTag = nNextUnsolictedSeqTag;
                    nNextUnsolictedSeqTag++;
                }
                finally
                {
                    lockNextUnsolictedSeqTag.unlock();
                }
            }

            builtPacket = String.format(responsePacketFormatType.UNSOLICITED.getFormat(),
                    nUnsolSeqTag, dataPacket.strCmd, dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));
            break;

        default: //unknown type
            builtPacket = String.format(responsePacketFormatType.UNKNOWN.getFormat(),
                    dataPacket.nSeqTag, dataPacket.strCmd,dataPacket.strSenderTag,
                    TextUtils.join(" ", dataPacket.strInfoDataList));
            break;
        }

        return builtPacket;
    }

    // Add built string data packet to output queue that will
    // be sent back to PC.  Used by ServerOutput() thread.
    private static void addStringToOutBuffer(String strData)
    {
        if (strData != null)
        {
            synchronized (stringDataToPcOutputQueue)
            {
                stringDataToPcOutputQueue.add(strData);
            }
        }
        return;
    }

    // Remove built string data packet from output queue.
    // Used by ServerOutput() thread.
    private static String removeStringFromOutBuffer()
    {
        synchronized (stringDataToPcOutputQueue)
        {
            if (stringDataToPcOutputQueue.size() > 0)
            {
                return stringDataToPcOutputQueue.remove(0);
            }
            else
            {
                return null;
            }
        }
    }

    // Add data packet to queue for BuildOutputPacket() thread to read.
    public static boolean addPacketToOutputQueue(CommServerDataPacket OutputPacket)
    {
        // Could do basic error checking here if necessary
        synchronized (dataPacketOutputQueue)
        {
            dataPacketOutputQueue.add(OutputPacket);
        }

        return true;
    }

    // Removes the oldest data packet from the queue.
    // Used by the BuildOutputPacket() thread.
    private static CommServerDataPacket removePacketFromOutputQueue()
    {
        CommServerDataPacket dataPacket = null;

        synchronized (dataPacketOutputQueue)
        {

            if (dataPacketOutputQueue.size() > 0)
            {
                dataPacket = dataPacketOutputQueue.remove(0);
            }
        }

        return dataPacket;
    }

    // Returns that tag for the CommServer
    public static String getCommServerTag()
    {
        return TAG;
    }

    // regex pattern used to split packet
    private static Pattern splitPacketRegex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");

    // Function to split the input string by whitespace and returns
    // returns in a List of String.
    //  - Keeps quoted words as one output string.
    //    if input string = one two "three four"
    //    the resulting List would be:
    //      matchList[0] = one
    //      matchList[1] = two
    //      matchList[2] = three four
    //
    private static List<String> splitPacket(String strPacket)
    {
        List<String> matchList = new ArrayList<String>();
        Matcher regexMatcher = splitPacketRegex.matcher(strPacket);
        while (regexMatcher.find())
        {
            // if stripe enclosing quotes if any
            String element = regexMatcher.group();

            if ((element.matches("^\".+\"$")) || (element.matches("^\'.+\'$")))
            {
                element = element.substring(1, element.length() - 1);
            }

            matchList.add(element);
        }

        return matchList;
    }

    // Helper function for looking up an activity. This list should be updated
    // whenever an activity is added
    // to MotoTest
    private Intent findActivity(String ActivityToStart)
    {
        Intent myActivity = new Intent();
        try
        {
            Class<?> activityClass = getActivityClassFromName(ActivityToStart);

            if (activityClass != null)
            {
                myActivity.setClass(this, activityClass);
            }
            else
            {
                dbgLog(TAG, ActivityToStart + " activity not found", 'e');
                return null;
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Unknown Exception type caught in findActivity run method.  Message Follows:", 'e');
            dbgLog(TAG, "" + e.getMessage(), 'e');
            e.printStackTrace();

            String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                    e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

            // Create unsolicited packet back to CommServer reporting an error
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(strErrMsg);
            CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
            sendUnsolicitedPacketToCommServer(unsolicitedPacket);
        }
        return myActivity;
    }


    private void printHelp(int nSeqTag, String strCmd)
    {
        String release = VERSION.RELEASE;
        String sdk = VERSION.SDK;
        String mVersion = "";
        PackageManager pkm = getPackageManager();
        try
        {
            // ---get the package info---
            PackageInfo pi = pkm.getPackageInfo("com.motorola.motocit", 0);
            // Save the version
            mVersion = pi.versionName;

        }
        catch (NameNotFoundException e)
        {
            e.printStackTrace();
        }

        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add("CQATest APK: " + mVersion + "    version-code  "+ apkVersionCode + "    Android " + release + "    Phone SDK " + sdk);
        strHelpList.add("");
        strHelpList.add("Command Format");
        strHelpList.add("  Command ActivityName(Optional) Data(Optional)");
        strHelpList.add("");
        strHelpList.add("Universal Commands");
        strHelpList.add("  help\t-Shows this help screen");
        strHelpList.add("  ping\t-Pings device to verify connection");
        strHelpList.add("  reboot\t-Reboot device");
        strHelpList.add("     optional: REBOOT_DELAY_TIME=<TIMEOUT> - time to wait before reboot");
        strHelpList.add("  start ActivityName\t-Starts the activity");
        strHelpList.add("  stop ActivityName\t-Stops the activity");
        strHelpList.add("  stop_all\t-Stops all previously started activities");
        strHelpList.add("     optional: TIMEOUT_MS=<TIMEOUT> - time to wait for all activities to stop");
        strHelpList.add("  tell ActivityName Data\t-Sends Data to activity");
        strHelpList.add("     optional: TELL_PARAM_RX_TIMEOUT_MS=<TIMEOUT> - time to wait for activity to receive TELL cmd");
        strHelpList.add("  log_debug parameters\t- enable or disable debug logging");
        strHelpList.add("     parameters:");
        strHelpList.add("       ENABLE=<TRUE|FALSE> - TRUE to enable, FALSE to disable logging");
        strHelpList.add("       GET_ENABLE_STATUS   - Returns TRUE or FALSE whether logging is enable");
        strHelpList.add("");
        strHelpList.add("Valid Activities:");
        strHelpList.add("  Mic_Loopback\t-Loopback audio from default mic or camcorder or selected mic to earpiece");
        strHelpList.add("  Airplane_Mode\t-Get/set airplane mode");
        strHelpList.add("  AltMainActivity\t-ALT related test items");
        strHelpList.add("  Audio_EarSpeaker\t-Test earpiece by running buzz and speech pattern test");
        strHelpList.add("  Audio_MicAnalyze\t-Analyze microphone freq and amplitude");
        strHelpList.add("  Audio_Playback\t-Record audio from specified input source and playback through loudspeaker or earpiece");
        strHelpList.add("  Audio_Playmediabackground\t-Play media file in background");
        strHelpList.add("  Audio_Sample\t-Samples audio data from specified input source");
        strHelpList.add("  Audio_Loudspeaker\t-Play specific media file via loudspeaker");
        strHelpList.add("  Audio_ToneGen\t-Generates tone and plays back through earpiece or loudspeaker");
        strHelpList.add("  Barcode\t-Generate barcode img for device track id, imei/meid, model number and phone ICCID");
        strHelpList.add("  BatteryInfo\t-Get Battery Info");
        strHelpList.add("  BlankTest\t-Displays a blank screen");
        strHelpList.add("  Bluetooth_MacAddress\t-Reads Bluetooth MAC Address");
        strHelpList.add("  Bluetooth_Scan\t-Scan for BT devices");
        strHelpList.add("  Bluetooth_Utility_NexTest\t-BT test interface for testing via NexTest");
        strHelpList.add("  Camera_External_Viewfinder\t-Launch External Camera Viewfinder");
        strHelpList.add("  Camera_External_VideoCapture\t-Starts Video Capture activity");
        strHelpList.add("  Camera_Internal_VideoCapture\t-Starts Internal Video Capture activity");
        strHelpList.add("  Camera_Internal_Viewfinder\t-Launch Internal Camera Viewfinder");
        strHelpList.add("  Camera_Internal_ViewfinderTorchOn\t-Launch Internal Camera Viewfinder with the torch on");
        strHelpList.add("  Camera_InternalCameraSnapAllResolutions\t-Capture internal camera images at all resolutions");
        strHelpList.add("  Camera_InternalCameraStrobeTest\t-Test the front camera flash");
        strHelpList.add("  Camera_ExternalCameraSnapAllResolutions\t-Capture external camera images at all resolutions");
        strHelpList.add("  Camera_Strobe\t-Control the camera strobe");
        strHelpList.add("  Camera_ViewfinderTorchOn\t-Launch Camera Viewfinder with the torch on");
        strHelpList.add("  Camera_Factory\t-Factory Camera Control");
        strHelpList.add("  Camera2_Factory\t-Factory Camera2 Control");
        strHelpList.add("  Camera2_FrontVideoCapture\t- Launch front camera video capture");
        strHelpList.add("  Camera2_FrontViewfinder\t- Launch front camera viewfinder");
        strHelpList.add("  Camera2_FrontViewfinderStrobe\t- Test the front camera flash");
        strHelpList.add("  Camera2_FrontViewfinderTorch\t- Test the front camera with touch on");
        strHelpList.add("  Camera2_RearMonoVideoCapture\t- Launch rear mono camera video capture");
        strHelpList.add("  Camera2_RearMonoViewfinder\t- Launch rear mono camera viewfinder");
        strHelpList.add("  Camera2_RearMonoViewfinderStrobe\t- Take picture with rear mono camera");
        strHelpList.add("  Camera2_RearRgbVideoCapture\t- Launch rear rgb camera video capture");
        strHelpList.add("  Camera2_RearRgbViewfinder\t- Launch rear rgb camera viewfinder");
        strHelpList.add("  Camera2_RearRgbViewfinderStrobe\t- Test rear rgb camera flash");
        strHelpList.add("  Camera2_RearRgbViewfinderTorch\t- Test rear rgb camera with touch on");
        strHelpList.add("  CosmeticTest\t-Displays a prompt screen for user to do cosmetic inspection");
        strHelpList.add("  CPU_Temperature\t-Read CPU Temperature");
        strHelpList.add("  Database\t-Start Database activity");
        strHelpList.add("  Display_VideoPlayback\t-Video Playback (plays a white background from a video file");
        strHelpList.add("  Display_RedLineCapture\t-Redline capture (a corner case where android would not render floating views");
        strHelpList.add("  FlipTest\t-Reads state of flip or slider");
        strHelpList.add("  GetSettingsInfo\t-Get application in Settings status");
        strHelpList.add("  GDriveValidate\t-Validate GDrive status");
        strHelpList.add("  GPS\t-Start GPS activity");
        strHelpList.add("  HdmiInfo\t-Start HDMI Cable and EDID activity");
        strHelpList.add("  HeadsetInfo\t-Start HeadsetInfo activity");
        strHelpList.add("  Installed_Apps\t-Provides installed application info");
        strHelpList.add("  KeyTest\t-Reads pressed and released keys");
        strHelpList.add("  NotificationLED\t-Turn on message LED");
        strHelpList.add("  MetaKeyLed\t-Turn on meta key LEDs");
        strHelpList.add("  MotoMods\t-Test Motorola Mods");
        strHelpList.add("  MMC\t-Reads mount state of MMC card");
        strHelpList.add("  NFC_Test\t-Read/Write to NFC card");
        strHelpList.add("  Personalization\t-Test Personalization");
        strHelpList.add("  Ringtone\t-Test Ringtone source");
        strHelpList.add("  ScreenLockUtil\t-Enable and disable screen lock");
        strHelpList.add("  Sensor_Accelerometer\t-Displays and reads accelerometer data");
        strHelpList.add("  Sensor_AccelerometerSecondary\t-Displays and reads the 2nd accelerometer data");
        strHelpList.add("  Sensor_Ambient_Temperature\t-Displays and reads ambient temperature data");
        strHelpList.add("  Sensor_Barometer\t-Displays and reads barometer data");
        strHelpList.add("  Sensor_Gyroscope\t-Displays and reads gyroscope data");
        strHelpList.add("  Sensor_FingerPrint\t-Test FingerPrint sensor");
        strHelpList.add("  Sensor_FingerPrintTest\t-Test FingerPrint sensor");
        strHelpList.add("  Sensor_FrontLight\t-Displays and reads front light sensor data");
        strHelpList.add("  Sensor_Magnetometer\t-Displays and reads front magnetometer data");
        strHelpList.add("  Sensor_Proximity\t-Displays and reads proximity sensor data");
        strHelpList.add("  Sensor_ProximitySecondary\t-Displays and reads 2nd proximity sensor data");
        strHelpList.add("  Sensor_ProximityThird\t-Displays and reads 3rd proximity sensor data");
        strHelpList.add("  Sensor_Capsense\t-Displays and reads Cap sensor data");
        strHelpList.add("  Sensor_HeartRate\t-Displays and reads Heart Rate sensor data");
        strHelpList.add("  Sensor_IRGuesture\t-Displays and reads IR sensor data");
        strHelpList.add("  ServiceInfo\t-Get all running service info");
        strHelpList.add("  SetupWizard\t-Verify setup wizard animation");
        strHelpList.add("  SIM_Card\t-Test SIM Card");
        strHelpList.add("  SystemTime\t-Get current system time");
        strHelpList.add("  TestPattern_Black\t-Black display test pattern");
        strHelpList.add("  TestPattern_BlackReverseOrientation\t-Black display test pattern");
        strHelpList.add("  TestPattern_BlackWithWhiteBorder\t-Displays a BlackWithWhiteBorder rendered image on the screen");
        strHelpList.add("  TestPattern_BlackWithWhiteBorderReverseOrientation\t-Displays a BlackWithWhiteBorder rendered image on the screen");
        strHelpList.add("  TestPattern_Blueblocks\t-Blue Block Test Pattern");
        strHelpList.add("  TestPattern_Color_Bright\t-Color display test pattern with bright backlights");
        strHelpList.add("  TestPattern_Color_BrightLine\t-Color display test pattern line with bright backlights");
        strHelpList.add("  TestPattern_Color_Medium\t-Color display test pattern with medium backlights");
        strHelpList.add("  TestPattern_Color_Dark\t-Color display test pattern with dark backlights");
        strHelpList.add("  TestPattern_DisplayFile\t-Display pattern from image file");
        strHelpList.add("  TestPattern_Drawable\t-Drawable display test pattern");
        strHelpList.add("  TestPattern_Focus\t-Focus display test pattern");
        strHelpList.add("  TestPattern_GenericColorFullScreenPattern\t-Configurable Generic Color display test pattern");
        strHelpList.add("  TestPattern_Grayblocks\t-Gray Block display test pattern");
        strHelpList.add("  TestPattern_Grayscale\t-Grayscale display test pattern");
        strHelpList.add("  TestPattern_Greenblocks\t-Green Block Test pattern");
        strHelpList.add("  TestPattern_HdmiNineBars\t-HDMI display test pattern");
        strHelpList.add("  TestPattern_Macbeth\t-Macbeth display test pattern");
        strHelpList.add("  TestPattern_Redblocks\t-Red Block Test pattern");
        strHelpList.add("  TestPattern_WhiteWithBlackBorder\t-Displays a BlackWithWhiteBorder rendered image on the screen");
        strHelpList.add("  TestPattern_WhiteWithBlackBorderReverseOrientation\t-Displays a BlackWithWhiteBorder rendered image on the screen");
        strHelpList.add("  TestPattern_Xtalk\t-Xtalk display test pattern");
        strHelpList.add("  TestPattern_Xtalk_Interfering_Horizontal\t-Xtalk Interfering horizontal display test pattern");
        strHelpList.add("  TestPattern_Xtalk_Interfering_Vertical\t-Xtalk Interfering vertical display test pattern");
        strHelpList.add("  TestPattern_Xtalk_Reference\t-Xtalk Reference display test pattern");
        strHelpList.add("  Touch_MXT1386\t-Touchscreen Tablet");
        strHelpList.add("  Touch_MXT1386_Short\t-Shorter Version of Touchscreen Tablet test");
        strHelpList.add("  Touch_MXT224\t-Touchscreen Phone");
        strHelpList.add("  Touch_Config\t-Touchscreen Phone");
        strHelpList.add("  Touch_Diagonal\t-Touchscreen Phone");
        strHelpList.add("  Touch_FT\t-Touchscreen Phone");
        strHelpList.add("  USB_Hostmode\t-Check usb host mode");
        strHelpList.add("  Version\t-Reads SW Version from phone");
        strHelpList.add("  VibratorTest\t-Turns on and off vibrator");
        strHelpList.add("  WLAN_ScanNetwork\t-Scan for WLAN networks");
        strHelpList.add("  WLAN_Utility_NexTest\t-WLAN test interface for testing via NexTest");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }


    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }

    // get activity class from activity name
    private Class<?> getActivityClassFromName(String activityName)
    {
        if (activityNameToClass.containsKey(activityName.toLowerCase()))
        {
            return activityNameToClass.get(activityName.toLowerCase());
        }

        return null;
    }

    // get activity name from activity class name
    private String getActivityNameFromClassName(String activityClassName)
    {
        if (activityClassToName.containsKey(activityClassName))
        {
            return activityClassToName.get(activityClassName);
        }

        return null;
    }

    // Function to setup hash table to map activity name to class
    // and map activity class 'name' to activity name.
    // This function should be updated everytime an activity is added.
    protected void setupActivityClassLookup()
    {
        activityNameToClass.clear();

        activityNameToClass.put("Airplane_Mode".toLowerCase(), AirplaneMode.class);
        activityNameToClass.put("AltMainActivity".toLowerCase(), AltMainActivity.class);
        activityNameToClass.put("Audio_EarSpeaker".toLowerCase(), AudioEar.class);
        activityNameToClass.put("Audio_Playback".toLowerCase(), AudioPlayBack.class);
        activityNameToClass.put("Audio_Playmediabackground".toLowerCase(), AudioPlayMediaBackGround.class);
        activityNameToClass.put("Audio_Sample".toLowerCase(), AudioSample.class);
        activityNameToClass.put("Audio_ToneGen".toLowerCase(), AudioToneGen.class);
        activityNameToClass.put("Audio_Loudspeaker".toLowerCase(), AudioPlayMediaFile.class);
        activityNameToClass.put("Audio_MicAnalyze".toLowerCase(), AudioMicAnalyze.class);
        activityNameToClass.put("Mic_Loopback".toLowerCase(), AudioLoopBack.class);
        activityNameToClass.put("Barcode".toLowerCase(), Barcode.class);
        activityNameToClass.put("BatteryInfo".toLowerCase(), BatteryInfo.class);
        activityNameToClass.put("BlankTest".toLowerCase(), BlankTest.class);
        activityNameToClass.put("Bluetooth_MacAddress".toLowerCase(), BluetoothMac.class);
        activityNameToClass.put("Bluetooth_Scan".toLowerCase(), BluetoothScan.class);
        activityNameToClass.put("Bluetooth_Utility_NexTest".toLowerCase(), BluetoothUtilityNexTest.class);
        activityNameToClass.put("Camera_Internal_Viewfinder".toLowerCase(), InternalViewfinder.class);
        activityNameToClass.put("Camera_Internal_ViewfinderTorchOn".toLowerCase(), InternalViewfinderTorchOn.class);
        activityNameToClass.put("Camera_Internal_VideoCapture".toLowerCase(), InternalVideoCapture.class);
        activityNameToClass.put("Camera_External_Viewfinder".toLowerCase(), ExternalViewfinder.class);
        activityNameToClass.put("Camera_External_VideoCapture".toLowerCase(), VideoCapture.class);
        activityNameToClass.put("Camera_Factory".toLowerCase(), CameraFactory.class);
        activityNameToClass.put("Camera2_Factory".toLowerCase(), Camera2Factory.class);
        activityNameToClass.put("Camera_ViewfinderTorchOn".toLowerCase(), ViewfinderTorchOn.class);
        activityNameToClass.put("Camera_Strobe".toLowerCase(), StrobeTest.class);
        activityNameToClass.put("Camera_InternalCameraSnapAllResolutions".toLowerCase(), InternalCameraSnapAllResolutions.class);
        activityNameToClass.put("Camera_InternalCameraStrobeTest".toLowerCase(), InternalCameraStrobeTest.class);
        activityNameToClass.put("Camera_ExternalCameraSnapAllResolutions".toLowerCase(), ExternalCameraSnapAllResolutions.class);
        activityNameToClass.put("Camera2_FrontVideoCapture".toLowerCase(), FrontVideoCapture.class);
        activityNameToClass.put("Camera2_FrontViewfinder".toLowerCase(), FrontViewfinder.class);
        activityNameToClass.put("Camera2_FrontViewfinderStrobe".toLowerCase(), FrontViewfinderStrobe.class);
        activityNameToClass.put("Camera2_FrontViewfinderTorch".toLowerCase(), FrontViewfinderTorch.class);
        activityNameToClass.put("Camera2_RearMonoVideoCapture".toLowerCase(), RearMonoVideoCapture.class);
        activityNameToClass.put("Camera2_RearMonoViewfinder".toLowerCase(), RearMonoViewfinder.class);
        activityNameToClass.put("Camera2_RearMonoViewfinderStrobe".toLowerCase(), RearMonoViewfinderStrobe.class);
        activityNameToClass.put("Camera2_RearRgbVideoCapture".toLowerCase(), RearRgbVideoCapture.class);
        activityNameToClass.put("Camera2_RearRgbViewfinder".toLowerCase(), RearRgbViewfinder.class);
        activityNameToClass.put("Camera2_RearRgbViewfinderStrobe".toLowerCase(), RearRgbViewfinderStrobe.class);
        activityNameToClass.put("Camera2_RearRgbViewfinderTorch".toLowerCase(), RearRgbViewfinderTorch.class);
        activityNameToClass.put("CosmeticTest".toLowerCase(), CosmeticTest.class);
        activityNameToClass.put("CPU_Temperature".toLowerCase(), CpuTemperature.class);
        activityNameToClass.put("Database".toLowerCase(), Database.class);
        activityNameToClass.put("Display_VideoPlayback".toLowerCase(), Display_VideoPlayback.class);
        activityNameToClass.put("Display_RedLineCapture".toLowerCase(), Display_RedLineCapture.class);
        activityNameToClass.put("FlipTest".toLowerCase(), FlipTest.class);
        activityNameToClass.put("GetSettingsInfo".toLowerCase (), GetSettingsInfo.class);
        activityNameToClass.put("GDriveValidate".toLowerCase(), GDriveValidate.class);
        activityNameToClass.put("GPS".toLowerCase(), GPS.class);
        activityNameToClass.put("HdmiInfo".toLowerCase(), HdmiInfo.class);
        activityNameToClass.put("HeadsetInfo".toLowerCase(), HeadsetInfo.class);
        activityNameToClass.put("Installed_Apps".toLowerCase(), InstalledApps.class);
        activityNameToClass.put("KeyTest".toLowerCase(), KeyTest.class);
        activityNameToClass.put("Notification_LED".toLowerCase(), Led.class);
        activityNameToClass.put("MotoMods".toLowerCase(), MotoMods.class);
        activityNameToClass.put("MetaKeyLed".toLowerCase(), MetaKeyLed.class);
        activityNameToClass.put("MMC".toLowerCase(), MMC.class);
        activityNameToClass.put("NFC_Test".toLowerCase(), NFCTest.class);
        activityNameToClass.put("Personalization".toLowerCase(), Personalization.class);
        activityNameToClass.put("Ringtone".toLowerCase(), Ringtone.class);
        activityNameToClass.put("ScreenLockUtil".toLowerCase(), ScreenLockUtil.class);
        activityNameToClass.put("Sensor_Accelerometer".toLowerCase(), Accel.class);
        activityNameToClass.put("Sensor_AccelerometerSecondary".toLowerCase(), AccelSecondary.class);
        activityNameToClass.put("Sensor_Ambient_Temperature".toLowerCase(), AmbientTemperature.class);
        activityNameToClass.put("Sensor_Barometer".toLowerCase(), Barometer.class);
        activityNameToClass.put("Sensor_Gyroscope".toLowerCase(), Gyroscope.class);
        activityNameToClass.put("Sensor_FingerPrint".toLowerCase(), FingerPrint.class);
        activityNameToClass.put("Sensor_FingerPrintTest".toLowerCase(), FingerPrintTest.class);
        activityNameToClass.put("Sensor_FrontLight".toLowerCase(), FrontLight.class);
        activityNameToClass.put("Sensor_Magnetometer".toLowerCase(), Magnetometer.class);
        activityNameToClass.put("Sensor_Proximity".toLowerCase(), Proximity.class);
        activityNameToClass.put("Sensor_ProximitySecondary".toLowerCase(), ProximitySecondary.class);
        activityNameToClass.put("Sensor_ProximityThird".toLowerCase(), ProximityThird.class);
        activityNameToClass.put("Sensor_Capsense".toLowerCase(), Capsense.class);
        activityNameToClass.put("Sensor_HeartRate".toLowerCase(), HeartRateSensor.class);
        activityNameToClass.put("Sensor_IRGuesture".toLowerCase(), IRGuesture.class);
        activityNameToClass.put("ServiceInfo".toLowerCase (), ServiceInfo.class);
        activityNameToClass.put("SetupWizard".toLowerCase(), SetupWizard.class);
        activityNameToClass.put("SIM_Card".toLowerCase(), SIMCard.class);
        activityNameToClass.put("SystemTime".toLowerCase(), SystemTime.class);
        activityNameToClass.put("TestPattern_BlackWithWhiteBorder".toLowerCase(), BlackWithWhiteBorder.class);
        activityNameToClass.put("TestPattern_BlackWithWhiteBorderReverseOrientation".toLowerCase(), BlackWhiteBorderReverseOrientation.class);
        activityNameToClass.put("TestPattern_DisplayFile".toLowerCase(), DisplayFile.class);
        activityNameToClass.put("TestPattern_WhiteWithBlackBorder".toLowerCase(), WhiteWithBlackBorder.class);
        activityNameToClass.put("TestPattern_WhiteWithBlackBorderReverseOrientation".toLowerCase(), WhiteBlackBorderReverseOrientation.class);
        activityNameToClass.put("TestPattern_Xtalk".toLowerCase(), Xtalk.class);
        activityNameToClass.put("TestPattern_Xtalk_Interfering_Horizontal".toLowerCase(), XtalkInterferingHorizontal.class);
        activityNameToClass.put("TestPattern_Xtalk_Interfering_Vertical".toLowerCase(), XtalkInterferingVertical.class);
        activityNameToClass.put("TestPattern_Xtalk_Reference".toLowerCase(), XtalkR.class);
        activityNameToClass.put("TestPattern_Black".toLowerCase(), BlackPattern.class);
        activityNameToClass.put("TestPattern_BlackReverseOrientation".toLowerCase(), BlackReverseOrientation.class);
        activityNameToClass.put("TestPattern_GenericColorFullScreenPattern".toLowerCase(), GenericColorFullScreenPattern.class);
        activityNameToClass.put("TestPattern_Grayscale".toLowerCase(), Grayscale.class);
        activityNameToClass.put("TestPattern_Color_Bright".toLowerCase(), Bright.class);
        activityNameToClass.put("TestPattern_Color_BrightLine".toLowerCase(), BrightLine.class);
        activityNameToClass.put("TestPattern_Color_Medium".toLowerCase(), Medium.class);
        activityNameToClass.put("TestPattern_Color_Dark".toLowerCase(), Dark.class);
        activityNameToClass.put("TestPattern_Drawable".toLowerCase(), DrawablePattern.class);
        activityNameToClass.put("TestPattern_Focus".toLowerCase(), Focus.class);
        activityNameToClass.put("TestPattern_Macbeth".toLowerCase(), Macbeth.class);
        activityNameToClass.put("TestPattern_Grayblocks".toLowerCase(), Grayblocks.class);
        activityNameToClass.put("TestPattern_Redblocks".toLowerCase(), Redblocks.class);
        activityNameToClass.put("TestPattern_Greenblocks".toLowerCase(), Greenblocks.class);
        activityNameToClass.put("TestPattern_Blueblocks".toLowerCase(), Blueblocks.class);
        activityNameToClass.put("TestPattern_HdmiNineBars".toLowerCase(), HdmiNineBars.class);
        activityNameToClass.put("Touch_MXT1386".toLowerCase(), Touch_MXT1386.class);
        activityNameToClass.put("Touch_MXT1386_Short".toLowerCase(), Touch_MXT1386_Short.class);
        activityNameToClass.put("Touch_MXT224".toLowerCase(), Touch_MXT224.class);
        activityNameToClass.put("Touch_Config".toLowerCase(), Touch_Config.class);
        activityNameToClass.put("Touch_Diagonal".toLowerCase(), Touch_Diagonal.class);
        activityNameToClass.put("Touch_FT".toLowerCase(), Touch_FT.class);
        activityNameToClass.put("USB_hostmode".toLowerCase(), Hostmode.class);
        activityNameToClass.put("Version".toLowerCase(), Version.class);
        activityNameToClass.put("VibratorTest".toLowerCase(), VibratorTest.class);
        activityNameToClass.put("WLAN_ScanNetwork".toLowerCase(), ScanNetwork.class);
        activityNameToClass.put("WLAN_Utility_NexTest".toLowerCase(), WlanUtilityNexTest.class);


        // now setup class to name look up
        activityClassToName.clear();

        for(String key : activityNameToClass.keySet())
        {
            // Keep klocwork happy
            Class<?> activityClass = activityNameToClass.get(key);
            if (activityClass == null)
            {
                continue;
            }

            activityClassToName.put(activityClass.getName(),key);
        }

    }

    public static void setActivityReceivedCmd(int seqTag)
    {
        activityTellRxedMap.put(seqTag, true);
    }

    private void clearActivityReceivedCmd(int seqTag)
    {
        activityTellRxedMap.remove(seqTag);
    }

    private boolean didActivityReceiveCmd(int seqTag)
    {
        if (activityTellRxedMap.containsKey(seqTag) == true)
        {
            return activityTellRxedMap.get(seqTag);
        }

        return false;
    }

    private void reboot()
    {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                if (pm != null)
                {
                    pm.reboot(null);
                }
            }
        }, REBOOT_DELAY_TIME);
    }
}
