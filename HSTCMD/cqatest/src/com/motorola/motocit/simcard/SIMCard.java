/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 *
 */

package com.motorola.motocit.simcard;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyProperties;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class SIMCard extends Test_Base
{

    TelephonyManager telephonyManager;
    SubscriptionManager subsriptionManager;
    TextView simStateTextView;
    TextView simOperatorTextView;
    TextView simOperatorNameTextView;
    TextView simCountryTextView;
    TextView simSerialNumberTextView;
    TextView simGid1TextView;

    TextView simICCIDTextView;
    TextView phoneICCIDTextView;
    TextView compareICCIDtextView;

    TextView sim2StateTextView;
    TextView sim2OperatorTextView;
    TextView sim2OperatorNameTextView;
    TextView sim2CountryTextView;
    TextView sim2SerialNumberTextView;

    private String mSIMStateText;
    private String mSIMOperator;
    private String mSIMOperatorName;
    private String mSIMCountry;
    private String mSIMSerialNumber;
    private String mSIMGid1;

    private String mPhoneICCID;
    private String mSIMICCID;

    private String mSIM2StateText;
    private String mSIM2Operator;
    private String mSIM2OperatorName;
    private String mSIM2Country;
    private String mSIM2SerialNumber;

    private static Method methodMultiSimState = null;
    private static Method methodMultiSimStateMSimTelphonyManager = null;
    private static Method methodMultiSimOperator = null;
    private static Method methodMultiSimOperatorMSimTelphonyManager = null;
    private static Method methodMultiSimOperatorName = null;
    private static Method methodMultiSimOperatorNameMSimTelphonyManager = null;
    private static Method methodMultiSimSerialNumber = null;
    private static Method methodMultiSimSerialNumberMSimTelphonyManager = null;
    private static Method methodMultiSimCountryIso = null;
    private static Method methodMultiSimCountryIsoMSimTelphonyManager = null;
    private static Method sMethodGetDefault;

    private static Method methodSubscriptionManagerGetSubId = null;
    private static Method methodTelephonyManagerGetSimOperator = null;
    private static Method methodTelephonyManagerGetSimCountryIso = null;
    private static Method methodTelephonyManagerGetSimSerialNumber = null;
    private static Method methodTelephonyManagerGetSimOperatorNameForSubscription = null;

    private static int simCount;
    private Class<?> MSimClass = null;
    private static Object sObject;
    private byte[] dataToRil = null;
    private ITelephony mITelephony = null;
    private static final String PLMN_FILE_PATH_OVERLAY = "/system/etc/motorola/preferred_networks/plmn_text_table.bin";

    private boolean isPermissionAllowed = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "SIM_Card";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.simcard);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        simStateTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_card_state);
        simOperatorTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_operator);
        simOperatorNameTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_operator_name);
        simSerialNumberTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_serial_number);
        simGid1TextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_gid1);
        simCountryTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_country);

        simICCIDTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_iccid);
        phoneICCIDTextView = (TextView) findViewById(com.motorola.motocit.R.id.phone_iccid);
        compareICCIDtextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_iccid_compare);

        sim2StateTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_card_state_2);
        sim2OperatorTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_operator_2);
        sim2OperatorNameTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_operator_name_2);
        sim2SerialNumberTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_serial_number_2);
        sim2CountryTextView = (TextView) findViewById(com.motorola.motocit.R.id.sim_country_2);

    }

    private BroadcastReceiver simStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (null == action)
            {
                return;
            }

            dbgLog(TAG, "simStateReceiver Intent Action: " + action, 'i');

            dbgLog(TAG, "simStateReceiver Intent: " + intent.toString(), 'i');

            if (action.equals("android.intent.action.SIM_STATE_CHANGED"))
            {
                if (Build.VERSION.SDK_INT >= 21)
                {
                    updateSimDataL();
                }
                else
                {
                    updateSimData();
                }
                printSimData();
            }

        }
    };

    private void configureMultiSimMethods()
    {
        // Some phones support Dual SIM Cards
        // ODM Phones implement the following methods in TelephonyManager:
        // public int getSimStateGemini(int simId)
        // public String getSimOperatorGemini(int simId)
        // public String getSimOperatorNameGemini(int simId)
        // public String getSimSerialNumberGemini(int simId)
        // public String getSimCountryIsoGemini(int simId)
        //
        // Moto internal phones implement following methods in
        // MSimTelephonyManager
        // public int getSimState(int slotId)
        // public String getSimOperator(int subscription)
        // public String getSimOperatorName(int subscription)
        // public String getSimCountryIso(int subscription)
        // public String getSimSerialNumber(int subscription)

        dbgLog(TAG, "configure multi sim", 'i');

        String multisimString = "";
        try
        {
            multisimString = SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, "unknown");
            dbgLog(TAG, "multisim property name=" + TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, 'i');
            dbgLog(TAG, "multisim property value=" + multisimString, 'i');

            if (multisimString.contentEquals("unknown"))
            {
                // trying to get from persist.radio.multisim.config
                multisimString = SystemProperties.get("persist.radio.multisim.config", "unknown");
                dbgLog(TAG, "try to read from persist.radio.multisim.config", 'i');
                dbgLog(TAG, "multisim property value=" + multisimString, 'i');
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }

        if (multisimString.contentEquals("dsds") || multisimString.contentEquals("dsda"))
        {
            try
            {
                MSimClass = Class.forName("android.telephony.MSimTelephonyManager");
                sMethodGetDefault = MSimClass.getDeclaredMethod("getDefault");
                sObject = sMethodGetDefault.invoke(null);
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }

        if (methodMultiSimState == null)
        {
            try
            {
                methodMultiSimState = TelephonyManager.class.getMethod("getSimStateGemini", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimState = null;
            }
        }

        if (methodMultiSimState == null)
        {
            try
            {
                methodMultiSimStateMSimTelphonyManager = MSimClass.getDeclaredMethod("getSimState", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimStateMSimTelphonyManager = null;
            }
        }

        if (methodMultiSimOperator == null)
        {
            try
            {
                methodMultiSimOperator = TelephonyManager.class.getMethod("getSimOperatorGemini", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimOperator = null;
            }
        }

        if (methodMultiSimOperator == null)
        {
            try
            {
                methodMultiSimOperatorMSimTelphonyManager = MSimClass.getDeclaredMethod("getSimOperator", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimOperatorMSimTelphonyManager = null;
            }
        }

        if (methodMultiSimOperatorName == null)
        {
            try
            {
                methodMultiSimOperatorName = TelephonyManager.class.getMethod("getSimOperatorNameGemini", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimOperatorName = null;
            }
        }

        if (methodMultiSimOperatorName == null)
        {
            try
            {
                methodMultiSimOperatorNameMSimTelphonyManager = MSimClass.getDeclaredMethod("getSimOperatorName", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimOperatorNameMSimTelphonyManager = null;
            }
        }

        if (methodMultiSimSerialNumber == null)
        {
            try
            {
                methodMultiSimSerialNumber = TelephonyManager.class.getMethod("getSimSerialNumberGemini", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimSerialNumber = null;
            }
        }

        if (methodMultiSimSerialNumber == null)
        {
            try
            {
                methodMultiSimSerialNumberMSimTelphonyManager = MSimClass.getDeclaredMethod("getSimSerialNumber", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimSerialNumberMSimTelphonyManager = null;
            }
        }

        if (methodMultiSimCountryIso == null)
        {
            try
            {
                methodMultiSimCountryIso = TelephonyManager.class.getMethod("getSimCountryIsoGemini", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimCountryIso = null;
            }
        }

        if (methodMultiSimCountryIso == null)
        {
            try
            {
                methodMultiSimCountryIsoMSimTelphonyManager = MSimClass.getDeclaredMethod("getSimCountryIso", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                methodMultiSimCountryIsoMSimTelphonyManager = null;
            }
        }
    }

    private void configureLollipopMultiSimMethods()
    {
        if (methodSubscriptionManagerGetSubId == null)
        {
            try
            {
                methodSubscriptionManagerGetSubId = SubscriptionManager.class.getMethod("getSubId", new Class[] { int.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                dbgLog(TAG, "failed to get method getSubId", 'i');
                methodSubscriptionManagerGetSubId = null;
            }
        }

        if (methodTelephonyManagerGetSimOperator == null)
        {
            try
            {
                if (Build.VERSION.SDK_INT > 21)
                {
                    methodTelephonyManagerGetSimOperator = TelephonyManager.class.getMethod("getSimOperator", new Class[] { int.class });
                }
                else
                {
                    methodTelephonyManagerGetSimOperator = TelephonyManager.class.getMethod("getSimOperator", new Class[] { long.class });
                }
            }
            catch (Exception ex)
            {
                // method does not exist
                dbgLog(TAG, "failed to get method getSimOperator", 'i');
                methodTelephonyManagerGetSimOperator = null;
            }
        }

        if (methodTelephonyManagerGetSimCountryIso == null)
        {
            try
            {
                if (Build.VERSION.SDK_INT > 21)
                {
                    methodTelephonyManagerGetSimCountryIso = TelephonyManager.class.getMethod("getSimCountryIso", new Class[] { int.class });
                }
                else
                {
                    methodTelephonyManagerGetSimCountryIso = TelephonyManager.class.getMethod("getSimCountryIso", new Class[] { long.class });
                }
            }
            catch (Exception ex)
            {
                // method does not exist
                dbgLog(TAG, "failed to get method getSimCountryIso", 'i');
                methodTelephonyManagerGetSimCountryIso = null;
            }
        }

        if (methodTelephonyManagerGetSimSerialNumber == null)
        {
            try
            {
                if (Build.VERSION.SDK_INT > 21)
                {
                    methodTelephonyManagerGetSimSerialNumber = TelephonyManager.class.getMethod("getSimSerialNumber", new Class[] { int.class });
                }
                else
                {
                    methodTelephonyManagerGetSimSerialNumber = TelephonyManager.class.getMethod("getSimSerialNumber", new Class[] { long.class });
                }
            }
            catch (Exception ex)
            {
                // method does not exist
                dbgLog(TAG, "failed to get method getSimSerialNumber", 'i');
                methodTelephonyManagerGetSimSerialNumber = null;
            }
        }

        if (methodTelephonyManagerGetSimOperatorNameForSubscription == null)
        {
            try
            {
                if (Build.VERSION.SDK_INT > 23)
                {
                    methodTelephonyManagerGetSimOperatorNameForSubscription = TelephonyManager.class.getMethod("getSimOperatorName", new Class[] { int.class });
                }
                else if (Build.VERSION.SDK_INT > 21)
                {
                    methodTelephonyManagerGetSimOperatorNameForSubscription = TelephonyManager.class.getMethod("getSimOperatorNameForSubscription", new Class[] { int.class });
                }
                else
                {
                    methodTelephonyManagerGetSimOperatorNameForSubscription = TelephonyManager.class.getMethod("getSimOperatorName", new Class[] { long.class });
                }
            }
            catch (Exception ex)
            {
                // method does not exist
                dbgLog(TAG, "failed to get method getSimOperatorNameForSubscription", 'i');
                methodTelephonyManagerGetSimOperatorNameForSubscription = null;
            }
        }
    }

    PhoneStateListener listener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState)
        {
            dbgLog(TAG, "service state changed", 'i');
            if (Build.VERSION.SDK_INT >= 21)
            {
                updateSimDataL();
            }
            else
            {
                updateSimData();
            }
            printSimData();
        }
    };

    protected void printSimData()
    {
        simStateTextView.setText("");
        simOperatorTextView.setText("");
        simOperatorNameTextView.setText("");
        simSerialNumberTextView.setText("");
        simGid1TextView.setText("");
        simCountryTextView.setText("");

        phoneICCIDTextView.setText("");
        simICCIDTextView.setText("");
        compareICCIDtextView.setText("");

        sim2StateTextView.setText("");
        sim2OperatorTextView.setText("");
        sim2OperatorNameTextView.setText("");
        sim2SerialNumberTextView.setText("");
        sim2CountryTextView.setText("");

        simStateTextView.setText("SIM STATE: " + mSIMStateText);
        simOperatorTextView.setText("SIM OPERATOR: " + mSIMOperator);
        simOperatorNameTextView.setText("SIM OPERATOR NAME: " + mSIMOperatorName);
        simCountryTextView.setText("SIM COUNTRY: " + mSIMCountry);
        simSerialNumberTextView.setText("SIM SERIAL NUMBER: " + mSIMSerialNumber);
        simGid1TextView.setText("SIM GID1: " + mSIMGid1);

        phoneICCIDTextView.setText("PHONE ICCID: " + mPhoneICCID);
        simICCIDTextView.setText("SIM ICCID: " + mSIMICCID);

        // Compare phone ICCID with Sim ICCID
        if (compareICCID())
        {
            compareICCIDtextView.setTextColor(Color.GREEN);
            compareICCIDtextView.setText("Phone ICCID Compare with SIM ICCID: MATCH");
        }
        else
        {
            compareICCIDtextView.setTextColor(Color.RED);
            compareICCIDtextView.setText("Phone ICCID Compare with SIM ICCID: ERROR");
        }

        if (Build.VERSION.SDK_INT >= 21)
        {
            if (simCount == 2)
            {
                sim2StateTextView.setText("SIM 2 STATE: " + mSIM2StateText);
                sim2OperatorTextView.setText("SIM 2 OPERATOR: " + mSIM2Operator);
                sim2OperatorNameTextView.setText("SIM 2 OPERATOR NAME: " + mSIM2OperatorName);
                sim2CountryTextView.setText("SIM 2 COUNTRY: " + mSIM2Country);
                sim2SerialNumberTextView.setText("SIM 2 SERIAL NUMBER: " + mSIM2SerialNumber);
            }
        }
        else if ("UNKNOWN".equalsIgnoreCase(mSIM2StateText) == false)
        {
            // If device supports multi sim, print sim2 data, even its status is
            // Unknown or absent
            sim2StateTextView.setText("SIM 2 STATE: " + mSIM2StateText);
            sim2OperatorTextView.setText("SIM 2 OPERATOR: " + mSIM2Operator);
            sim2OperatorNameTextView.setText("SIM 2 OPERATOR NAME: " + mSIM2OperatorName);
            sim2CountryTextView.setText("SIM 2 COUNTRY: " + mSIM2Country);
            sim2SerialNumberTextView.setText("SIM 2 SERIAL NUMBER: " + mSIM2SerialNumber);
        }
    }

    protected void updateSimData()
    {
        dbgLog(TAG, "update sim data", 'i');

        int[] simState = { -1, -1 };
        mSIMOperator = "";
        mSIMCountry = "";
        mSIMSerialNumber = "";
        mSIMGid1 = "";

        if (methodMultiSimState != null)
        {
            try
            {
                simState[0] = Integer.parseInt((methodMultiSimState.invoke(telephonyManager, 0)).toString());
                simState[1] = Integer.parseInt((methodMultiSimState.invoke(telephonyManager, 1)).toString());
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
        else if (methodMultiSimStateMSimTelphonyManager != null)
        {
            try
            {
                simState[0] = (Integer) methodMultiSimStateMSimTelphonyManager.invoke(sObject, 0);
                simState[1] = (Integer) methodMultiSimStateMSimTelphonyManager.invoke(sObject, 1);

            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            simState[0] = telephonyManager.getSimState();
        }

        dbgLog(TAG, "sim1=" + simState[0] + " sim2=" + simState[1], 'i');

        for (int i = 0; i < simCount; i++)
        {
            String simStateTextTemp;
            switch (simState[i])
            {
                case TelephonyManager.SIM_STATE_ABSENT:
                    simStateTextTemp = "ABSENT";
                    break;
                case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                    simStateTextTemp = "LOCKED";
                    break;
                case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                    simStateTextTemp = "PIN_REQUIRED";
                    break;
                case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                    simStateTextTemp = "PUK_REQUIRED";
                    break;
                case TelephonyManager.SIM_STATE_READY:
                    simStateTextTemp = "READY";
                    break;
                case TelephonyManager.SIM_STATE_UNKNOWN:
                    simStateTextTemp = "UNKNOWN";
                    break;
                case -1:
                    simStateTextTemp = "UNKNOWN";
                    break;
                default:
                    simStateTextTemp = "UNDEFINED_" + simState[i];
                    break;
            }
            if (i == 0)
            {
                mSIMStateText = simStateTextTemp;
            }
            else
            {
                mSIM2StateText = simStateTextTemp;
            }
        }

        if (methodMultiSimOperator != null)
        {
            try
            {
                mSIMOperator = (methodMultiSimOperator.invoke(telephonyManager, 0)).toString();
                mSIM2Operator = (methodMultiSimOperator.invoke(telephonyManager, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else if (methodMultiSimOperatorMSimTelphonyManager != null)
        {
            try
            {
                mSIMOperator = (methodMultiSimOperatorMSimTelphonyManager.invoke(sObject, 0)).toString();
                mSIM2Operator = (methodMultiSimOperatorMSimTelphonyManager.invoke(sObject, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else
        {
            mSIMOperator = telephonyManager.getSimOperator();
        }

        try
        {
            mSIMGid1 = telephonyManager.getGroupIdLevel1();
            dbgLog(TAG, "SIM GID = " + mSIMGid1, 'i');
        }
        catch (Exception e)
        {
            dbgLog(TAG, e.toString(), 'e');
        }

        if (methodMultiSimOperatorName != null)
        {
            try
            {
                mSIMOperatorName = (methodMultiSimOperatorName.invoke(telephonyManager, 0)).toString();
                mSIM2OperatorName = (methodMultiSimOperatorName.invoke(telephonyManager, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else if (methodMultiSimOperatorNameMSimTelphonyManager != null)
        {
            try
            {
                mSIMOperatorName = (methodMultiSimOperatorNameMSimTelphonyManager.invoke(sObject, 0)).toString();
                mSIM2OperatorName = (methodMultiSimOperatorNameMSimTelphonyManager.invoke(sObject, 1)).toString();
            }
            catch (Exception e)
            {}

            if (TextUtils.isEmpty(mSIMOperatorName))
            {
                if (TestUtils.isMotDevice())
                {
                    dbgLog(TAG, "Failed to get operator name for dual sim 1, try to get from modem", 'i');
                    mSIMOperatorName = convertOperator(mSIMOperator);
                }
                else
                {
                    // set sim operator name to null
                    dbgLog(TAG, "Can not get sim 1 operator name from telephonyManager, set name to null", 'i');
                    mSIMOperatorName = "null";
                }
            }

            if (TextUtils.isEmpty(mSIM2OperatorName))
            {
                if (TestUtils.isMotDevice())
                {
                    dbgLog(TAG, "Failed to get operator name for dual sim 2, try to get from modem", 'i');
                    mSIM2OperatorName = convertOperator(mSIM2Operator);
                }
                else
                {
                    // set sim operator name to null
                    dbgLog(TAG, "Can not get sim 2 operator name from telephonyManager, set name to null", 'i');
                    mSIM2OperatorName = "null";
                }
            }
        }
        else
        {
            mSIMOperatorName = telephonyManager.getSimOperatorName();

            // if SIM operator name is empty, try to get it from modem
            if (TextUtils.isEmpty(mSIMOperatorName))
            {
                if (TestUtils.isMotDevice())
                {
                    dbgLog(TAG, "Try to get sim operator name from modem", 'i');
                    mSIMOperatorName = convertOperator(mSIMOperator);
                }
                else
                {
                    // set sim operator name to null
                    dbgLog(TAG, "Can not get sim operator name from telephonyManager, set name to null", 'i');
                    mSIMOperatorName = "null";
                }
            }

            // if it's still empty, try to get it from plmn table bin
            if (TextUtils.isEmpty(mSIMOperatorName) && isBinaryExist())
            {
                mSIMOperatorName = PlmnTable.getNetworkName(getApplicationContext(), mSIMOperator);
            }

        }

        if (methodMultiSimSerialNumber != null)
        {
            try
            {
                mSIMSerialNumber = (methodMultiSimSerialNumber.invoke(telephonyManager, 0)).toString();
                mSIM2SerialNumber = (methodMultiSimSerialNumber.invoke(telephonyManager, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else if (methodMultiSimSerialNumberMSimTelphonyManager != null)
        {
            try
            {
                mSIMSerialNumber = (methodMultiSimSerialNumberMSimTelphonyManager.invoke(sObject, 0)).toString();
                mSIM2SerialNumber = (methodMultiSimSerialNumberMSimTelphonyManager.invoke(sObject, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else
        {
            mSIMSerialNumber = telephonyManager.getSimSerialNumber();
            mSIMICCID = mSIMSerialNumber;
        }

        if (methodMultiSimCountryIso != null)
        {
            try
            {
                mSIMCountry = (methodMultiSimCountryIso.invoke(telephonyManager, 0)).toString();
                mSIM2Country = (methodMultiSimCountryIso.invoke(telephonyManager, 1)).toString();
            }
            catch (Exception e)
            {}
        }
        else if (methodMultiSimCountryIsoMSimTelphonyManager != null)
        {
            try
            {
                mSIMCountry = (methodMultiSimCountryIsoMSimTelphonyManager.invoke(sObject, 0)).toString();
                mSIM2Country = (methodMultiSimCountryIsoMSimTelphonyManager.invoke(sObject, 1)).toString();

            }
            catch (Exception e)
            {}
        }
        else
        {
            mSIMCountry = telephonyManager.getSimCountryIso();
        }
    }

    protected void updateSimDataL()
    {
        dbgLog(TAG, "update sim data", 'i');

        int[] simState = { -1, -1 };

        mSIMOperator = "";
        mSIMCountry = "";
        mSIMSerialNumber = "";
        mSIMGid1 = "";

        simCount = telephonyManager.getSimCount();

        if (simCount == 1)
        {
            simState[0] = telephonyManager.getSimState();
            mSIMOperator = telephonyManager.getSimOperator();
            mSIMOperatorName = telephonyManager.getSimOperatorName();
            mSIMCountry = telephonyManager.getSimCountryIso();
            mSIMSerialNumber = telephonyManager.getSimSerialNumber();
            mSIMICCID = mSIMSerialNumber;
        }
        else if (simCount == 2)
        {
            try
            {
                // Get SIM States
                simState[0] = telephonyManager.getSimState(0);
                simState[1] = telephonyManager.getSimState(1);

                if (Build.VERSION.SDK_INT >= 22)
                {
                    // Use Ints for subId
                    int[] intSubId1 = (int[]) methodSubscriptionManagerGetSubId.invoke(subsriptionManager, 0);
                    int[] intSubId2 = (int[]) methodSubscriptionManagerGetSubId.invoke(subsriptionManager, 1);
                    dbgLog(TAG, "intSubId1=" + intSubId1[0] + " intSubId2=" + intSubId2[0], 'i');

                    mSIMOperator = (String) (methodTelephonyManagerGetSimOperator.invoke(telephonyManager, intSubId1[0]));
                    mSIMCountry = (String) (methodTelephonyManagerGetSimCountryIso.invoke(telephonyManager, intSubId1[0]));
                    mSIMSerialNumber = (String) (methodTelephonyManagerGetSimSerialNumber.invoke(telephonyManager, intSubId1[0]));
                    mSIMOperatorName = (String) (methodTelephonyManagerGetSimOperatorNameForSubscription.invoke(telephonyManager, intSubId1[0]));

                    mSIM2Operator = (String) (methodTelephonyManagerGetSimOperator.invoke(telephonyManager, intSubId2[0]));
                    mSIM2Country = (String) (methodTelephonyManagerGetSimCountryIso.invoke(telephonyManager, intSubId2[0]));
                    mSIM2SerialNumber = (String) (methodTelephonyManagerGetSimSerialNumber.invoke(telephonyManager, intSubId2[0]));
                    mSIM2OperatorName = (String) (methodTelephonyManagerGetSimOperatorNameForSubscription.invoke(telephonyManager, intSubId2[0]));
                }
                else
                {
                    // Use Longs for subId
                    long[] longSubId1 = (long[]) methodSubscriptionManagerGetSubId.invoke(subsriptionManager, 0);
                    long[] longSubId2 = (long[]) methodSubscriptionManagerGetSubId.invoke(subsriptionManager, 1);
                    dbgLog(TAG, "longSubId1=" + longSubId1[0] + " longSubId2=" + longSubId2[0], 'i');

                    mSIMOperator = (String) (methodTelephonyManagerGetSimOperator.invoke(telephonyManager, longSubId1[0]));
                    mSIMCountry = (String) (methodTelephonyManagerGetSimCountryIso.invoke(telephonyManager, longSubId1[0]));
                    mSIMSerialNumber = (String) (methodTelephonyManagerGetSimSerialNumber.invoke(telephonyManager, longSubId1[0]));
                    mSIMOperatorName = (String) (methodTelephonyManagerGetSimOperatorNameForSubscription.invoke(telephonyManager, longSubId1[0]));

                    mSIM2Operator = (String) (methodTelephonyManagerGetSimOperator.invoke(telephonyManager, longSubId2[0]));
                    mSIM2Country = (String) (methodTelephonyManagerGetSimCountryIso.invoke(telephonyManager, longSubId2[0]));
                    mSIM2SerialNumber = (String) (methodTelephonyManagerGetSimSerialNumber.invoke(telephonyManager, longSubId2[0]));
                    mSIM2OperatorName = (String) (methodTelephonyManagerGetSimOperatorNameForSubscription.invoke(telephonyManager, longSubId2[0]));
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, e.toString(), 'e');
            }
        }

        dbgLog(TAG, "sim1=" + simState[0] + " sim2=" + simState[1], 'i');

        for (int i = 0; i <= 1; i++)
        {
            String simStateTextTemp;
            switch (simState[i])
            {
                case TelephonyManager.SIM_STATE_ABSENT:
                    simStateTextTemp = "ABSENT";
                    break;
                case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                    simStateTextTemp = "LOCKED";
                    break;
                case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                    simStateTextTemp = "PIN_REQUIRED";
                    break;
                case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                    simStateTextTemp = "PUK_REQUIRED";
                    break;
                case TelephonyManager.SIM_STATE_READY:
                    simStateTextTemp = "READY";
                    break;
                case TelephonyManager.SIM_STATE_UNKNOWN:
                    simStateTextTemp = "UNKNOWN";
                    break;
                case -1:
                    simStateTextTemp = "UNKNOWN";
                    break;
                default:
                    simStateTextTemp = "UNDEFINED_" + simState[i];
                    break;
            }
            if (i == 0)
            {
                mSIMStateText = simStateTextTemp;
            }
            else
            {
                mSIM2StateText = simStateTextTemp;
            }
        }

        if (TextUtils.isEmpty(mSIMOperatorName))
        {
            if (TestUtils.isMotDevice())
            {
                dbgLog(TAG, "Failed to get operator name for dual sim 1, try to get from modem", 'i');
                mSIMOperatorName = convertOperator(mSIMOperator);
            }
            else
            {
                // set sim operator name to null
                dbgLog(TAG, "Can not get sim 1 operator name from telephonyManager, set name to null", 'i');
                mSIMOperatorName = "null";
            }
        }

        if (TextUtils.isEmpty(mSIM2OperatorName))
        {
            if (TestUtils.isMotDevice())
            {
                dbgLog(TAG, "Failed to get operator name for dual sim 2, try to get from modem", 'i');
                mSIM2OperatorName = convertOperator(mSIM2Operator);
            }
            else
            {
                // set sim operator name to null
                dbgLog(TAG, "Can not get sim 2 operator name from telephonyManager, set name to null", 'i');
                mSIM2OperatorName = "null";
            }
        }

        try
        {
            mSIMGid1 = telephonyManager.getGroupIdLevel1();
            dbgLog(TAG, "SIM GID = " + mSIMGid1, 'i');
        }
        catch (Exception e)
        {
            dbgLog(TAG, e.toString(), 'e');
        }
    }

    protected boolean compareICCID()
    {
        boolean returnValue = false;

        if ((mSIMICCID != null) && (mPhoneICCID != null))
        {
            if (mSIMICCID.contentEquals(mPhoneICCID))
            {
                returnValue = true;
            }
        }

        return returnValue;
    }

    protected String convertOperator(String operatorNumber)
    {
        String return_operator_name = "";
        if (!TextUtils.isEmpty(operatorNumber))
        {
            String mcc = null;
            String mnc = null;

            // the length of mcc is 3
            // the length of mnc is 2 or 3
            if (operatorNumber.length() == 5)
            {
                mcc = operatorNumber.substring(0, 3);
                mnc = operatorNumber.substring(3, 5);
            }
            else if (operatorNumber.length() == 6)
            {
                mcc = operatorNumber.substring(0, 3);
                mnc = operatorNumber.substring(3, 6);
            }
            else
            {
                dbgLog(TAG, "Invalid sim operator length. len=" + operatorNumber.length(), 'e');
                return return_operator_name;
            }

            dbgLog(TAG, "mcc=" + mcc + " mnc=" + mnc, 'i');

            int mccDec = Integer.parseInt(mcc);
            int mncDec = Integer.parseInt(mnc);
            String mccHexStr = Integer.toHexString(mccDec);
            String mncHexStr = Integer.toHexString(mncDec);
            dbgLog(TAG, "mccHexStr=" + mccHexStr + " mncHexStr=" + mncHexStr, 'i');

            byte[] mccHex2DecByte = null;
            byte[] mncHex2DecByte = null;

            if ((mccHexStr.length() % 2) == 0)
            {
                mccHex2DecByte = hexStringToByteArray(mccHexStr);
            }
            else
            {
                mccHex2DecByte = hexStringToByteArray("0" + mccHexStr);
            }

            for (int i = 0; i < mccHex2DecByte.length; i++)
            {
                dbgLog(TAG, "mccHexByte_" + i + "=" + mccHex2DecByte[i], 'i');
            }

            if ((mncHexStr.length() % 2) == 0)
            {
                mncHex2DecByte = hexStringToByteArray(mncHexStr);
            }
            else
            {
                mncHex2DecByte = hexStringToByteArray("0" + mncHexStr);
            }

            for (int i = 0; i < mncHex2DecByte.length; i++)
            {
                dbgLog(TAG, "mncHexByte_" + i + "=" + mncHex2DecByte[i], 'i');
            }

            /**
             * the format of dataToRil
             * 
             * Get the PLMN name from static table.
             * 
             * "data" is
             * Byte 0 : Category : 0
             * Byte 1-3 : Msg Id : 0A 00 06
             * Byte 4-7 : Length : 00 00 00 04
             * Byte 8 onwards is data:
             * 
             * byte[8] and byte[9] will be MCC. byte[8] - LSB, byte[9] - MSB
             * byte[10] and byte[11] will be MNC.byte[10] - LSB, byte[11] - MSB
             * 
             * "response" is char array of PLMN name
             * 
             */

            dataToRil = new byte[12];

            dataToRil[0] = 1;
            dataToRil[1] = 10;
            dataToRil[2] = 0;
            dataToRil[3] = 6;
            dataToRil[4] = 0;
            dataToRil[5] = 0;
            dataToRil[6] = 0;
            dataToRil[7] = 4;
            if (mccHex2DecByte.length == 1)
            {
                dataToRil[8] = mccHex2DecByte[0];
                dataToRil[9] = 0;
            }
            else
            {
                dataToRil[8] = mccHex2DecByte[1];
                dataToRil[9] = mccHex2DecByte[0];
            }

            if (mncHex2DecByte.length == 1)
            {
                dataToRil[10] = mncHex2DecByte[0];
                dataToRil[11] = 0;
            }
            else
            {
                dataToRil[10] = mncHex2DecByte[1];
                dataToRil[11] = mncHex2DecByte[0];
            }

            for (int i = 0; i < 12; i++)
            {
                dbgLog(TAG, "dataToRil_" + i + "=" + dataToRil[i], 'i');
            }

            ITelephony telephony = getITelephony();

            try
            {
                dbgLog(TAG, "Trying to send data to ril...", 'i');
                int retVal = -1;
                byte[] response = new byte[2048];
                retVal = telephony.invokeOemRilRequestRaw(dataToRil, response);
                dbgLog(TAG, "retVal=" + retVal, 'i');

                if (retVal > 1)
                {
                    byte[] validResponseBytes = null;
                    validResponseBytes = new byte[retVal];
                    System.arraycopy(response, 0, validResponseBytes, 0, retVal);
                    for (int i = 0; i < retVal; i++)
                    {
                        dbgLog(TAG, "validResponseBytes" + i + "=" + validResponseBytes[i], 'i');
                    }

                    dbgLog(TAG, "toString=" + bytetoString(validResponseBytes), 'i');
                    return_operator_name = bytetoString(validResponseBytes);
                }
                else if (retVal == 1)
                {
                    // If the return len from modem is 1, it means no plmn name
                    // found.
                    // Just return empty string for operator name.
                    return_operator_name = "";
                    dbgLog(TAG, "No operator name found by modem", 'i');
                }
                else
                {
                    dbgLog(TAG, "Error retVal=" + retVal, 'e');
                }

            }
            catch (Exception e)
            {
                dbgLog(TAG, e.toString(), 'i');
            }
        }
        else
        {
            dbgLog(TAG, "Error operatorNumber=" + operatorNumber, 'e');
        }

        return return_operator_name;
    }

    private ITelephony getITelephony()
    {
        if (mITelephony == null)
        {
            mITelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        }

        return mITelephony;
    }

    private boolean isBinaryExist()
    {
        boolean rtn = false;
        File file_plmn_binary = new File(PLMN_FILE_PATH_OVERLAY);

        if (file_plmn_binary.exists())
        {
            rtn = true;
            dbgLog(TAG, "plmn exists", 'i');
        }
        else
        {
            dbgLog(TAG, "plmn doesn't exist", 'e');
        }

        return rtn;
    }

    public static String bytetoString(byte[] bytearray)
    {
        String result = "";
        char temp;

        int length = bytearray.length;
        for (int i = 0; i < length; i++)
        {
            temp = (char) bytearray[i];
            result += temp;
        }
        return result;
    }

    public static byte[] hexStringToByteArray(String s)
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
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

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, 1001);
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            if (Build.VERSION.SDK_INT < 21)
            {
                configureMultiSimMethods();
            }
            else
            {
                configureLollipopMultiSimMethods();
            }

            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            subsriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            // Retrieve phone iccid from 'ro.mot.iccid'
            mPhoneICCID = SystemProperties.get("ro.mot.iccid", "PHONE_ICCID_UNKNOWN");

            if (Build.VERSION.SDK_INT >= 21)
            {
                updateSimDataL();
            }
            else
            {
                updateSimData();
            }
            printSimData();

            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE);

            IntentFilter simStateFilter = new IntentFilter("android.intent.action.SIM_STATE_CHANGED");
            registerReceiver(simStateReceiver, simStateFilter);

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Sim test");
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

            contentRecord("testresult.txt", "SIM Card Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "SIM Card Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_SIM_CARD_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (Build.VERSION.SDK_INT >= 21)
            {
                updateSimDataL();
            }
            else
            {
                updateSimData();
            }

            strDataList.add(String.format("SIM_STATE=" + mSIMStateText));
            strDataList.add(String.format("SIM_OPERATOR=" + mSIMOperator));
            strDataList.add(String.format("SIM_OPERATOR_NAME=" + mSIMOperatorName));
            strDataList.add(String.format("SIM_COUNTRY=" + mSIMCountry));
            strDataList.add(String.format("SIM_SERIAL_NUMBER=" + mSIMSerialNumber));

            strDataList.add(String.format("SIM_2_STATE=" + mSIM2StateText));
            strDataList.add(String.format("SIM_2_OPERATOR=" + mSIM2Operator));
            strDataList.add(String.format("SIM_2_OPERATOR_NAME=" + mSIM2OperatorName));
            strDataList.add(String.format("SIM_2_COUNTRY=" + mSIM2Country));
            strDataList.add(String.format("SIM_2_SERIAL_NUMBER=" + mSIM2SerialNumber));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_ICCID_COMP_RESULT"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (Build.VERSION.SDK_INT >= 21)
            {
                updateSimDataL();
            }
            else
            {
                updateSimData();
            }

            if (compareICCID())
            {
                strDataList.add(String.format("ICCID_COMPARISION=MATCH"));
            }
            else
            {
                strDataList.add(String.format("ICCID_COMPARISION=ERROR"));
            }

            strDataList.add(String.format("SIM_ICCID=" + mSIMICCID));
            strDataList.add(String.format("PHONE_ICCID=" + mPhoneICCID));

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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will verify the SIM Card status");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_SIM_CARD_STATUS   - Returns state of SIM Card");
        strHelpList.add("GET_ICCID_COMP_RESULT - Returns comparision result of iccid");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "SIM Card Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "SIM Card Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
