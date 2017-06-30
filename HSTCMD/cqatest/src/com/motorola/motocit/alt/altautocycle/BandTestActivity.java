/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.motorola.desense.ScriptExecutor;
import com.motorola.desense.ScriptExecutor.Callback;
import com.motorola.desense.lua.DesenseGlobals;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.BPBands;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class BandTestActivity extends ALTBaseActivity implements Callback {
    private static final String TAG = BandTestActivity.class.getSimpleName();

    private List<Map<String, String>> mTestStatus;

    private BaseAdapter mAdapter;

    public static final String PROGRESS_SCRIPT = "script";

    public static final String PROGRESS_RESULT = "result";

    private ListView mListView;

    @Override
    void init() {
        setTitle("RF Bands Test");
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.alt_batch_result);
        mTestStatus = new ArrayList<Map<String,String>>();
        mAdapter = new SimpleAdapter(this, mTestStatus, R.layout.alt_batch_script_result,
                        new String[] {PROGRESS_SCRIPT, PROGRESS_RESULT},
                        new int[]{R.id.batch_script_text, R.id.batch_result_text});

        mListView = (ListView) findViewById(R.id.batch_result);
        mListView.setAdapter(mAdapter);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss", Locale.US);
        String timestamp = sdf.format(new Date());

        ScriptExecutor executor = new ScriptExecutor(this, this, timestamp);
        List<String> bands = loadSupportedBands();
        List<String> scripts = new ArrayList<String>();
        for (String band : bands) {
            scripts.addAll(getScriptFiles(band));
        }
        executor.execute(scripts);
    }

    private List<String> getScriptFiles(String band) {
        List<String> scripts = new ArrayList<String>();

        String dirname = band.substring(0, band.indexOf('_')) + "/" + band;
        File dir = new File(DesenseGlobals.SCRIPT_BASE_DIR, dirname);
        if (!dir.exists())
            return scripts;

        for (File f : dir.listFiles()) {
            if (f.getName().startsWith(band)) {
                scripts.add(f.getAbsolutePath());
            }
        }

        return scripts;
    }

    @Override
    void start() {
    }

    @Override
    void release() {
    }

    private List<String> loadSupportedBands() {
        BPBands bpBands = BPBands.getInstance(getApplicationContext(), true);
        List<String> bands = new ArrayList<String>(0);

        if (bpBands.isGSM850())
            bands.add("GSM_850");
        if (bpBands.isGSM900())
            bands.add("GSM_900");
        if (bpBands.isGSM1800())
            bands.add("GSM_1800");
        if (bpBands.isGSM1900())
            bands.add("GSM_1900");

        if (bpBands.isCDMABC0())
            bands.add("CDMA_BC0");
        if (bpBands.isCDMABC1())
            bands.add("CDMA_BC1");
        if (bpBands.isCDMABC10())
            bands.add("CDMA_BC10");
        if (bpBands.isCDMABC15())
            bands.add("CDMA_BC15");

        if (bpBands.isWCDMA2100())
            bands.add("WCDMA_B1");
        if (bpBands.isWCDMA1900())
            bands.add("WCDMA_B2");
        if (bpBands.isWCDMA1700())
            bands.add("WCDMA_B4");
        if (bpBands.isWCDMA850())
            bands.add("WCDMA_B5");
        if (bpBands.isWCDMA900())
            bands.add("WCDMA_B8");
        if (bpBands.isWCDMA850Japan())
            bands.add("WCDMA_B6");
        if (bpBands.isWCDMA800Japan())
            bands.add("WCDMA_B19");

        if (bpBands.isTDSCDMAB34())
            bands.add("TDSCDMA_B34");
        if (bpBands.isTDSCDMAB39())
            bands.add("TDSCDMA_B39");

        if (bpBands.isLTEB1())
            bands.add("LTE_B1");
        if (bpBands.isLTEB2())
            bands.add("LTE_B2");
        if (bpBands.isLTEB3())
            bands.add("LTE_B3");
        if (bpBands.isLTEB4())
            bands.add("LTE_B4");
        if (bpBands.isLTEB5())
            bands.add("LTE_B5");
        if (bpBands.isLTEB6())
            bands.add("LTE_B6");
        if (bpBands.isLTEB7())
            bands.add("LTE_B7");
        if (bpBands.isLTEB8())
            bands.add("LTE_B8");
        if (bpBands.isLTEB9())
            bands.add("LTE_B9");
        if (bpBands.isLTEB10())
            bands.add("LTE_B10");
        if (bpBands.isLTEB11())
            bands.add("LTE_B11");
        if (bpBands.isLTEB12())
            bands.add("LTE_B12");
        if (bpBands.isLTEB13())
            bands.add("LTE_B13");
        if (bpBands.isLTEB14())
            bands.add("LTE_B14");
        if (bpBands.isLTEB17())
            bands.add("LTE_B17");
        if (bpBands.isLTEB19())
            bands.add("LTE_B19");
        if (bpBands.isLTEB20())
            bands.add("LTE_B20");
        if (bpBands.isLTEB25())
            bands.add("LTE_B25");
        if (bpBands.isLTEB26())
            bands.add("LTE_B26");
        if (bpBands.isLTEB28())
            bands.add("LTE_B28");
        if (bpBands.isLTEB29())
            bands.add("LTE_B29");
        if (bpBands.isLTEB30())
            bands.add("LTE_B30");
        if (bpBands.isLTEB38())
            bands.add("LTE_B38");
        if (bpBands.isLTEB39())
            bands.add("LTE_B39");
        if (bpBands.isLTEB40())
            bands.add("LTE_B40");
        if (bpBands.isLTEB41())
            bands.add("LTE_B41");
        if (bpBands.isLTEB66())
            bands.add("LTE_B66");
        if (bpBands.isLTEB252())
            bands.add("LTE_B252");
        if (bpBands.isLTEB255())
            bands.add("LTE_B255");

        return bands;
    }

    @Override
    public void updateProgress(String script, String status, boolean logging) {
        updateProgress(script, status, logging, false);
    }

    private void updateProgress(String script, String status, boolean logging, boolean forceScroll) {
        boolean found = false;
        int i = 0;
        for(Map<String, String> map : mTestStatus){
            if(script.equals(map.get(PROGRESS_SCRIPT))){
                map.put(PROGRESS_RESULT, status);
                mListView.smoothScrollToPosition(i);
                found = true;
                break;
            }
            i++;
        }
        if(!found){
            mTestStatus.add(makeScriptStatus(script, status));
            if(forceScroll){
                mListView.smoothScrollToPosition(i);
            }
        }
        if (logging) {
            logWriter(script, status);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void finishTests() {
        updateProgress("RF BANDS TEST COMPLETED", "", false, true);
        logWriter("************************************************** ALT auto cycle test", "COMPLETED");

        // Wait 5 seconds to reboot device
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                rebootDevice();
            }
        }, 5000);
    }

    private void rebootDevice() {
        TestUtils.dbgLog(TAG, "Reached the last test item, rebooting unit", 'i');
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        pm.reboot(null);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        updateProgress("RF BANDS TEST INTERRUPTED", "", false, true);
        updateProgress("Please remove cqa and desense from recent apps to restart band test", "", false, true);
        return true;
    }

    private Map<String, String> makeScriptStatus(String script, String result){
        HashMap<String, String> status = new HashMap<String, String>();
        status.put(PROGRESS_SCRIPT, script);
        status.put(PROGRESS_RESULT, result);
        return status;
    }
}
