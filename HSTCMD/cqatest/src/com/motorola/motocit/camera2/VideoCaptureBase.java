/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *    Date           CR                  Author                                  Description
 * 2017/01/26    IKSWN-21780    Guilherme Deo - guideo91       CQATest - video capture of 3 cameras on Golden Eagle
 */
package com.motorola.motocit.camera2;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

import java.util.ArrayList;
import java.util.List;

public class VideoCaptureBase extends Test_Base {

    protected String[] mHelpMessage;
    protected CameraActivityFragment fragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(com.motorola.motocit.R.layout.activity_camera);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(com.motorola.motocit.R.id.container, fragment)
                    .commit();
        }

        mHelpMessage = new String[]{TAG,
                "This function will start video capture"};
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS")) {

        } else if (strRxCmd.equalsIgnoreCase("help")) {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        } else {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp() {
        List<String> strHelpList = new ArrayList<String>();
        strHelpList.add(mHelpMessage[0]);
        strHelpList.add("");
        strHelpList.add(mHelpMessage[1]);
        strHelpList.add("");
        strHelpList.addAll(getBaseHelp());
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        for(int i = 2; i < mHelpMessage.length; i++) {
            strHelpList.add(mHelpMessage[i]);
        }
        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    protected boolean onSwipeRight() { return true; }

    @Override
    protected boolean onSwipeLeft() { return true; }

    @Override
    protected boolean onSwipeDown() { return true; }

    @Override
    protected boolean onSwipeUp() { return true; }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS")) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            contentRecord("testresult.txt", "Camera - " + TAG + ":  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);
            try {
                Thread.sleep(1000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            contentRecord("testresult.txt", "Camera - " + TAG + ":  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);
            try {
                Thread.sleep(1000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            fragment.stopRecordingOrPlayback();
            if (modeCheck("Seq")) {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();
                return false;
            } else {
                systemExitWrapper(0);
            }
        }

        return true;
    }

}

