/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.Display;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class Test_MenuMode extends ListActivity
{
    private String mCQAversion;
    private static final String TAG = "CQATest:Menu_Mode";

    private View.OnTouchListener mGestureListener;
    private GestureDetector mGestureDetector;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        VERSION version = new Build.VERSION();
        String release = version.RELEASE;
        String sdk = VERSION.SDK;

        PackageManager pkm = getPackageManager();

        TestUtils.setSequenceFileInUse(TestUtils.getfactoryOrAltConfigFileInUse());

        try
        {
            // ---get the package info---
            PackageInfo pi = pkm.getPackageInfo("com.motorola.motocit", 0);
            // Save the version
            mCQAversion = pi.versionName;
            dbgLog(TAG, "CQATest Version: " + mCQAversion + "    Android: " + release + "    Phone SDK " + sdk, 'd');
        }
        catch (NameNotFoundException e)
        {

            e.printStackTrace();
        }

        Intent intent = getIntent();
        String path = intent.getStringExtra("com.motorola.motocit.Path");

        if (path == null)
        {
            path = "";
            this.setTitle("CQA: " + mCQAversion + " Android: " + release + " SDK: " + sdk);
        }
        else
        {
            this.setTitle("CQA Test/" + path);
        }

        if (TestUtils.getDisplayHideTitle())
        {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        if (TestUtils.getDisplayHideNotification())
        {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        if (!(TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
        {
            if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)
            {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                dbgLog(TAG, "ORIENTATION SWITCHED TO REVERSE PORTRAIT", 'i');
            }
            else if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                dbgLog(TAG, "ORIENTATION SWITCHED TO LANDSCAPE", 'i');
            }
            else if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
            {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                dbgLog(TAG, "ORIENTATION SWITCHED TO REVERSE LANDSCAPE", 'i');
            }
        }

        setListAdapter(new SimpleAdapter(this, getData(path), android.R.layout.simple_list_item_1, new String[] { "title" },
                new int[] { android.R.id.text1 }));

        getListView().setTextFilterEnabled(true);

        if ((TestUtils.getDisplayXLeftOffset() > 0) || (TestUtils.getDisplayXRightOffset() > 0) || (TestUtils.getDisplayYTopOffset() > 0)
                || (TestUtils.getDisplayYBottomOffset() > 0))
        {
            int XOffset = 0;
            int YOffset = 0;

            int requestedOrientation = getRequestedOrientation();

            int CQASettingDisplayOrientation = TestUtils.getDisplayOrientation();

            // check if requested orientation is landscape
            switch (CQASettingDisplayOrientation)
            {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // Only reverse for portrait
                    dbgLog(TAG, "Portrait Detected", 'i');
                    XOffset = TestUtils.getDisplayXLeftOffset();
                    YOffset = TestUtils.getDisplayYTopOffset();
                    break;
                default:
                    // if Portrait, reverse X and Y
                    dbgLog(TAG, "Landscape Detected", 'i');
                    XOffset = TestUtils.getDisplayYTopOffset();
                    YOffset = TestUtils.getDisplayXLeftOffset();
                    break;
                }
                break;
            default:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // if Landscape, reverse X and Y
                    dbgLog(TAG, "Landscape Detected", 'i');
                    XOffset = TestUtils.getDisplayYTopOffset();
                    YOffset = TestUtils.getDisplayXLeftOffset();
                    break;
                default:
                    // Only reverse for landscape
                    dbgLog(TAG, "Portrait Detected", 'i');
                    XOffset = TestUtils.getDisplayXLeftOffset();
                    YOffset = TestUtils.getDisplayYTopOffset();
                    break;
                }
                break;
            }

            getListView().setX(XOffset);
            getListView().setY(YOffset);

            LayoutParams layoutParams = getListView().getLayoutParams();
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;

            // check if requested orientation is landscape
            switch (CQASettingDisplayOrientation)
            {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // Only reverse for portrait
                    layoutParams.width = width - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                    layoutParams.height = height - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                    break;
                default:
                    // if portrait, reverse X and Y Offsets
                    layoutParams.width = width - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                    layoutParams.height = height - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                    break;
                }
                break;
            default:
                // check if requested orientation is landscape
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // if Landscape, reverse X and Y Offsets
                    layoutParams.width = width - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                    layoutParams.height = height - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                    break;
                default:
                    // Only reverse for landscape
                    layoutParams.width = width - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                    layoutParams.height = height - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                    break;
                }
            }

            dbgLog(TAG, "setContent offsets added Width: " + layoutParams.width + " Height: " + layoutParams.height, 'i');
        }

        this.getListView().setLongClickable(true);
        this.getListView().setOnItemLongClickListener(new OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id)
            {
                onBackPressed();
                return true;
            }
        });

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    protected List getData(String prefix)
    {

        PackageManager pm = getPackageManager();
        List<Map> myData = new ArrayList<Map>();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory("com.motorola.motocit.TestMenuMode.MENU_ITEM");

        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

        if (null == list)
        {
            return myData;
        }

        String[] prefixPath;

        if ((prefix != null) && (prefix.length() == 0))
        {
            prefixPath = null;
        }
        else
        {
            prefixPath = prefix.split("/");
        }

        int len = list.size();

        Set<String> entries = new HashSet<>();

        for (int i = 0; i < len; i++)
        {
            ResolveInfo info = list.get(i);
            CharSequence labelSeq = info.loadLabel(pm);
            String label = labelSeq != null ? labelSeq.toString() : info.activityInfo.name;

            if ((prefix.length() == 0) || label.startsWith(prefix))
            {

                String[] labelPath = label.split("/");

                String nextLabel = prefixPath == null ? labelPath[0] : labelPath[prefixPath.length];

                if (!TestUtils.activityFilter(info.activityInfo.name))
                {
                    if ((prefixPath != null ? prefixPath.length : 0) == (labelPath.length - 1))
                    {
                        addItem(myData, nextLabel, activityIntent(info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
                    }
                    else
                    {
                        if (!entries.contains(nextLabel))
                        {
                            addItem(myData, nextLabel, browseIntent(((prefix != null) && (prefix.length() == 0)) ? nextLabel : prefix + "/" + nextLabel));
                            entries.add(nextLabel);
                        }
                    }
                }
            }
        }

        Collections.sort(myData, sDisplayNameComparator);

        return myData;
    }

    protected Intent activityIntent(String pkg, String componentName)
    {
        Intent result = new Intent();
        result.setClassName(pkg, componentName);
        return result;
    }

    protected Intent browseIntent(String path)
    {
        Intent result = new Intent();
        result.setClass(this, Test_MenuMode.class);
        result.putExtra("com.motorola.motocit.Path", path);
        return result;
    }

    protected void addItem(List<Map> data, String name, Intent intent)
    {
        Map<String, Object> temp = new HashMap<String, Object>();
        temp.put("title", name);
        temp.put("intent", intent);
        data.add(temp);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Map map = (Map) l.getItemAtPosition(position);

        // keep klocwork happy
        if (null != map)
        {
            Intent intent = (Intent) map.get("intent");
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        }
    }

    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }

    private final static Comparator<Map> sDisplayNameComparator = new Comparator<Map>()
            {
        private final Collator collator = Collator.getInstance();

        @Override
        public int compare(Map map1, Map map2)
        {
            return collator.compare(map1.get("title"), map2.get("title"));
        }
            };

}
