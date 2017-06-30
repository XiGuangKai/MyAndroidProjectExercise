/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import com.motorola.desense.lua.DesenseGlobals;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


public class BPBands {
    private static final String TAG = BPBands.class.getSimpleName();

    private static final String BANDS_XML = "/system/etc/motorola/12m/batch/bands/bands.xml";

    private static final String TCMD_CMD_SET_FTM_MODE = "0FF004290300";

    private static final String TCMD_CMD_QUERY_GSM_CDMA_WCDMA_BANDS = "0FF004265507";

    // RF NV 22131
    private static final String TCMD_CMD_QUERY_RF_NV_WCDMA =
            "0FF0044B132700080000001D000000A11B2F6E762F6974656D5F66696C65732F72666E762F303030323231333100";

    // RF NV 22605
    private static final String TCMD_CMD_QUERY_TD_SCDMA_BANDS =
            "0FF0044B132700080000001D000000A11B2F6E762F6974656D5F66696C65732F72666E762F303030323236303500";

    private static final String TCMD_CMD_QUERY_LTE_BANDS = "0FF00426AC1A";

    private static BPBands sInstance = null;

    // CDMA Bands
    private boolean mCDMABC0 = false;
    private boolean mCDMABC1 = false;
    private boolean mCDMABC10 = false;
    private boolean mCDMABC15 = false;

    // GSM Bands
    private boolean mGSM850 = false;
    private boolean mGSM900 = false;
    private boolean mGSM1800 = false;
    private boolean mGSM1900 = false;

    // WCDMA Bands
    private boolean mWCDMA850 = false; // WCDMA B5
    private boolean mWCDMA900 = false; // WCDMA B8
    private boolean mWCDMA1700 = false; // WCDMA B4
    private boolean mWCDMA1900 = false; // WCDMA B2
    private boolean mWCDMA2100 = false; // WCDMA B1
    private boolean mWCDMA800_Japan = false; // WCDMA B19 Japan 800
    private boolean mWCDMA850_Japan = false; // WCDMA B6 Japan 850

    // TD_SCDMA Bands
    private boolean mTDSCDMAB34 = false;
    private boolean mTDSCDMAB39 = false;

    // LTE Bands
    private boolean mLTEB1 = false;
    private boolean mLTEB2 = false;
    private boolean mLTEB3 = false;
    private boolean mLTEB4 = false;
    private boolean mLTEB5 = false;
    private boolean mLTEB6 = false;
    private boolean mLTEB7 = false;
    private boolean mLTEB8 = false;
    private boolean mLTEB9 = false;
    private boolean mLTEB10 = false;
    private boolean mLTEB11 = false;
    private boolean mLTEB12 = false;
    private boolean mLTEB13 = false;
    private boolean mLTEB14 = false;
    private boolean mLTEB17 = false;
    private boolean mLTEB19 = false;
    private boolean mLTEB20 = false;
    private boolean mLTEB25 = false;
    private boolean mLTEB252 = false;
    private boolean mLTEB255 = false;
    private boolean mLTEB26 = false;
    private boolean mLTEB28 = false;
    private boolean mLTEB29 = false;
    private boolean mLTEB30 = false;
    private boolean mLTEB38 = false;
    private boolean mLTEB39 = false;
    private boolean mLTEB40 = false;
    private boolean mLTEB41 = false;
    private boolean mLTEB66 = false;

    private Context mContext = null;

    private boolean mRFPathControl;

    public static BPBands getInstance(Context context) {
        return getInstance(context, false);
    }

    public static BPBands getInstance(Context context, boolean rfPathControl) {

        if (sInstance == null || (sInstance.isRFPathControl() != rfPathControl)) {
            sInstance = new BPBands(context, rfPathControl);
        }
        return sInstance;
    }

    public boolean isRFPathControl() {
        return mRFPathControl;
    }

    private BPBands(Context context, boolean rfPathControl) {
        mContext = context;
        mRFPathControl = rfPathControl;
        initBandsSupported();
    }

    private boolean parsePathInfo() {
        Map<RFPaths.Tech, Set<Integer>> map;
        try {
            map = RFPaths.getAllRFPathInformation();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            DesenseGlobals.closeSocket();
        }
        if (null != map) {
            for (Map.Entry<RFPaths.Tech, Set<Integer>> e : map.entrySet()) {
                for (int band : e.getValue()) {
                    switch (e.getKey()) {
                        case CDMA:
                            switch (band) {
                                case 0:
                                    mCDMABC0 = true;
                                    break;
                                case 1:
                                    mCDMABC1 = true;
                                    break;
                                case 10:
                                    mCDMABC10 = true;
                                    break;
                                case 15:
                                    mCDMABC15 = true;
                                    break;
                                default:
                                    Log.d(TAG, String.format("Unknown band %d for tech %s",
                                            band, e.getKey().toString()));
                            }
                            break;
                        case WCDMA:
                            switch (band) {
                                case 5:
                                    mWCDMA850 = true; // WCDMA B5
                                    break;
                                case 8:
                                    mWCDMA900 = true; // WCDMA B8
                                    break;
                                case 4:
                                    mWCDMA1700 = true; // WCDMA B4
                                    break;
                                case 2:
                                    mWCDMA1900 = true; // WCDMA B2
                                    break;
                                case 1:
                                    mWCDMA2100 = true; // WCDMA B1
                                    break;
                                case 19:
                                    mWCDMA800_Japan = true; // WCDMA B19 Japan 800
                                    break;
                                case 6:
                                    mWCDMA850_Japan = true; // WCDMA B6 Japan 850
                                    break;
                                default:
                                    Log.d(TAG, String.format("Unknown band %d for tech %s",
                                            band, e.getKey().toString()));
                            }
                            break;
                        case GSM:
                            switch (band) {
                                case 0:
                                    mGSM850 = true;
                                    break;
                                case 1:
                                    mGSM900 = true;
                                    break;
                                case 2:
                                    mGSM1800 = true;
                                    break;
                                case 3:
                                    mGSM1900 = true;
                                    break;
                                default:
                                    Log.d(TAG, String.format("Unknown band %d for tech %s",
                                            band, e.getKey().toString()));
                            }
                            break;
                        case LTE:
                            switch (band) {
                                case 1:
                                    mLTEB1 = true;
                                    break;
                                case 2:
                                    mLTEB2 = true;
                                    break;
                                case 3:
                                    mLTEB3 = true;
                                    break;
                                case 4:
                                    mLTEB4 = true;
                                    break;
                                case 5:
                                    mLTEB5 = true;
                                    break;
                                case 6:
                                    mLTEB6 = true;
                                    break;
                                case 7:
                                    mLTEB7 = true;
                                    break;
                                case 8:
                                    mLTEB8 = true;
                                    break;
                                case 9:
                                    mLTEB9 = true;
                                    break;
                                case 10:
                                    mLTEB10 = true;
                                    break;
                                case 11:
                                    mLTEB11 = true;
                                    break;
                                case 12:
                                    mLTEB12 = true;
                                    break;
                                case 13:
                                    mLTEB13 = true;
                                    break;
                                case 14:
                                    mLTEB14 = true;
                                    break;
                                case 17:
                                    mLTEB17 = true;
                                    break;
                                case 19:
                                    mLTEB19 = true;
                                    break;
                                case 20:
                                    mLTEB20 = true;
                                    break;
                                case 25:
                                    mLTEB25 = true;
                                    break;
                                case 252:
                                    mLTEB252 = true;
                                    break;
                                case 255:
                                    mLTEB255 = true;
                                    break;
                                case 26:
                                    mLTEB26 = true;
                                    break;
                                case 28:
                                    mLTEB28 = true;
                                    break;
                                case 29:
                                    mLTEB29 = true;
                                    break;
                                case 30:
                                    mLTEB30 = true;
                                    break;
                                case 38:
                                    mLTEB38 = true;
                                    break;
                                case 39:
                                    mLTEB39 = true;
                                    break;
                                case 40:
                                    mLTEB40 = true;
                                    break;
                                case 41:
                                    mLTEB41 = true;
                                    break;
                                case 66:
                                    mLTEB66 = true;
                                    break;
                                default:
                                    Log.d(TAG, String.format("Unknown band %d for tech %s",
                                            band, e.getKey().toString()));
                            }
                            break;
                        case TD_SCDMA:
                            switch (band) {
                                case 34:
                                    mTDSCDMAB34 = true;
                                    break;
                                case 39:
                                    mTDSCDMAB39 = true;
                                    break;
                                default:
                                    Log.d(TAG, String.format("Unknown band %d for tech %s",
                                            band, e.getKey().toString()));
                            }
                            break;
                        default:
                            Log.d(TAG, String.format("Unknown technology %s", e.getKey().toString()));
                            return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean loadBandsFromXml() {
        File bandsXml = new File(BANDS_XML);
        if (!bandsXml.exists()) {
            Log.d(TAG, BANDS_XML + " is not present");
            return false;
        }

        String productName = SystemProperties.get("ro.hw.device", "unknown");
        String hwRev = SystemProperties.get("ro.hw.revision", "unknown");
        String radio = SystemProperties.get("ro.hw.radio", "unknown");
        Log.d(TAG, "ro.hw.device=" + productName);
        Log.d(TAG, "ro.hw.revision=" + hwRev);
        Log.d(TAG, "ro.hw.radio=" + radio);

        boolean done = false;
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(bandsXml);

            // Find node "Product" with attribute name=productName
            Element product = null;
            NodeList productList = doc.getElementsByTagName("Product");
            for (int i = 0; i < productList.getLength(); i++) {
                Element element = (Element) productList.item(i);
                if (productName.equalsIgnoreCase(element.getAttribute("name"))) {
                    product = element;
                    break;
                }
            }

            if (product == null) {
                Log.d(TAG, "Could not find product " + productName + " in bands.xml");
                return false;
            }

            // Find node SKU with attribute name=radio and revision=hwRev
            Element sku = null;
            Element defaultRev = null;
            NodeList skuList = product.getElementsByTagName("SKU");
            for (int i = 0; i < skuList.getLength(); i++) {
                Element element = (Element) skuList.item(i);
                if (radio.equalsIgnoreCase(element.getAttribute("name"))) {
                    if (hwRev.equalsIgnoreCase(element.getAttribute("revision"))) {
                        sku = element;
                        break;
                    }
                    if ("default".equalsIgnoreCase(element.getAttribute("revision"))) {
                        defaultRev = element;
                    }
                }
            }

            // If hw revision of testing device could not be found,
            // then use revision="default"
            if (sku == null) {
                Log.d(TAG, "Could not find hw revision " + hwRev + " in bands.xml, load default hw revision");
                sku = defaultRev;
            }

            if (sku == null) {
                Log.d(TAG, "Could not find radio " + radio + " and hw revision " + hwRev + " in bands.xml");
                return false;
            }

            Element cdma = getElementByTagName(sku, "CDMA");
            if (cdma != null) {
                Element bc0 = getElementByTagName(cdma, "BC0");
                if (bc0 != null) {
                    mCDMABC0 = Boolean.parseBoolean(bc0.getAttribute("main"));
                }

                Element bc1 = getElementByTagName(cdma, "BC1");
                if (bc1 != null) {
                    mCDMABC1 = Boolean.parseBoolean(bc1.getAttribute("main"));
                }

                Element bc10 = getElementByTagName(cdma, "BC10");
                if (bc10 != null) {
                    mCDMABC10 = Boolean.parseBoolean(bc10.getAttribute("main"));
                }

                Element bc15 = getElementByTagName(cdma, "BC15");
                if (bc15 != null) {
                    mCDMABC15 = Boolean.parseBoolean(bc15.getAttribute("main"));
                }
            }

            Element gsm = getElementByTagName(sku, "GSM");
            if (gsm != null) {
                Element b2 = getElementByTagName(gsm, "B2");
                if (b2 != null) {
                    mGSM1900 = Boolean.parseBoolean(b2.getAttribute("main"));
                }

                Element b3 = getElementByTagName(gsm, "B3");
                if (b3 != null) {
                    mGSM1800 = Boolean.parseBoolean(b3.getAttribute("main"));
                }

                Element b5 = getElementByTagName(gsm, "B5");
                if (b5 != null) {
                    mGSM850 = Boolean.parseBoolean(b5.getAttribute("main"));
                }

                Element b8 = getElementByTagName(gsm, "B8");
                if (b8 != null) {
                    mGSM900 = Boolean.parseBoolean(b8.getAttribute("main"));
                }
            }

            Element umts = getElementByTagName(sku, "UMTS");
            if (umts != null) {
                Element b1 = getElementByTagName(umts, "B1");
                if (b1 != null) {
                    mWCDMA2100 = Boolean.parseBoolean(b1.getAttribute("main"));
                }

                Element b2 = getElementByTagName(umts, "B2");
                if (b2 != null) {
                    mWCDMA1900 = Boolean.parseBoolean(b2.getAttribute("main"));
                }

                Element b4 = getElementByTagName(umts, "B4");
                if (b4 != null) {
                    mWCDMA1700 = Boolean.parseBoolean(b4.getAttribute("main"));
                }

                Element b5 = getElementByTagName(umts, "B5");
                if (b5 != null) {
                    mWCDMA850 = Boolean.parseBoolean(b5.getAttribute("main"));
                }

                Element b6 = getElementByTagName(umts, "B6");
                if (b6 != null) {
                    mWCDMA850_Japan = Boolean.parseBoolean(b6.getAttribute("main"));
                }

                Element b8 = getElementByTagName(umts, "B8");
                if (b8 != null) {
                    mWCDMA900 = Boolean.parseBoolean(b8.getAttribute("main"));
                }

                Element b19 = getElementByTagName(umts, "B19");
                if (b19 != null) {
                    mWCDMA800_Japan = Boolean.parseBoolean(b19.getAttribute("main"));
                }
            }

            Element tdscdma = getElementByTagName(sku, "TD-SCDMA");
            if (tdscdma != null) {
                Element b34 = getElementByTagName(tdscdma, "B34");
                if (b34 != null) {
                    mTDSCDMAB34 = Boolean.parseBoolean(b34.getAttribute("main"));
                }

                Element b39 = getElementByTagName(tdscdma, "B39");
                if (b39 != null) {
                    mTDSCDMAB39 = Boolean.parseBoolean(b39.getAttribute("main"));
                }
            }

            Element lte = getElementByTagName(sku, "LTE");
            if (lte != null) {
                if (getElementByTagName(lte, "B1") != null) {
                    mLTEB1 = true;
                }

                if (getElementByTagName(lte, "B2") != null) {
                    mLTEB2 = true;
                }

                if (getElementByTagName(lte, "B3") != null) {
                    mLTEB3 = true;
                }

                if (getElementByTagName(lte, "B4") != null) {
                    mLTEB4 = true;
                }

                if (getElementByTagName(lte, "B5") != null) {
                    mLTEB5 = true;
                }

                if (getElementByTagName(lte, "B6") != null) {
                    mLTEB6 = true;
                }

                if (getElementByTagName(lte, "B7") != null) {
                    mLTEB7 = true;
                }

                if (getElementByTagName(lte, "B8") != null) {
                    mLTEB8 = true;
                }

                if (getElementByTagName(lte, "B9") != null) {
                    mLTEB9 = true;
                }

                if (getElementByTagName(lte, "B10") != null) {
                    mLTEB10 = true;
                }

                if (getElementByTagName(lte, "B11") != null) {
                    mLTEB11 = true;
                }

                if (getElementByTagName(lte, "B12") != null) {
                    mLTEB12 = true;
                }

                if (getElementByTagName(lte, "B13") != null) {
                    mLTEB13 = true;
                }

                if (getElementByTagName(lte, "B14") != null) {
                    mLTEB14 = true;
                }

                if (getElementByTagName(lte, "B17") != null) {
                    mLTEB17 = true;
                }

                if (getElementByTagName(lte, "B19") != null) {
                    mLTEB19 = true;
                }

                if (getElementByTagName(lte, "B20") != null) {
                    mLTEB20 = true;
                }

                if (getElementByTagName(lte, "B25") != null) {
                    mLTEB25 = true;
                }

                if (getElementByTagName(lte, "B252") != null) {
                    mLTEB252 = true;
                }

                if (getElementByTagName(lte, "B255") != null) {
                    mLTEB255 = true;
                }

                if (getElementByTagName(lte, "B26") != null) {
                    mLTEB26 = true;
                }

                if (getElementByTagName(lte, "B28") != null) {
                    mLTEB28 = true;
                }

                if (getElementByTagName(lte, "B29") != null) {
                    mLTEB29 = true;
                }

                if (getElementByTagName(lte, "B30") != null) {
                    mLTEB30 = true;
                }

                if (getElementByTagName(lte, "B38") != null) {
                    mLTEB38 = true;
                }

                if (getElementByTagName(lte, "B39") != null) {
                    mLTEB39 = true;
                }

                if (getElementByTagName(lte, "B40") != null) {
                    mLTEB40 = true;
                }

                if (getElementByTagName(lte, "B41") != null) {
                    mLTEB41 = true;
                }

                if (getElementByTagName(lte, "B66") != null) {
                    mLTEB66 = true;
                }
            }

            Log.d(TAG, "Load bands from " + bandsXml);
            logSupportedBands();
            done = true;
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "loadBandsFromXml ParserConfigurationException, " + e);
        } catch (SAXException e) {
            Log.e(TAG, "loadBandsFromXml SAXException, " + e);
        } catch (IOException e) {
            Log.e(TAG, "loadBandsFromXml IOException, " + e);
        }

        return done;
    }

    private Element getElementByTagName(Element element, String name) {
        NodeList list = element.getElementsByTagName(name);
        if (list != null) {
            return (Element) list.item(0);
        } else {
            return null;
        }
    }

    private void initBandsSupported() {
        if (mRFPathControl) {
            if (parsePathInfo()) {
                Log.d(TAG, "parsePathInfo");
                logSupportedBands();
                return;
            }
        }
        try {
            TcmdSocket.TcmdResponseData response = null;
            TcmdSocket tcmd_socket = new TcmdSocket();

            // Set FTM Mode
            tcmd_socket.send(TCMD_CMD_SET_FTM_MODE);
            response = tcmd_socket.receive();

            // parse the response to see if it was a failure
            TcmdSocket.TcmdParsedResponse parsedResponse = TcmdSocket.parseTcmdResponse(response.getData());
            if (parsedResponse.getParsedResponse().startsWith("FAIL")) {
                Log.e(TAG, "Setting the device to FTM Mode failed - "
                        + parsedResponse.getParsedResponse());
                tcmd_socket.close();
                return;
            }

            try {
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, "Exception in sleep - " + e.getMessage()
                        + " " + Log.getStackTraceString(e));
            }

            // Do this after TCMD_CMD_SET_FTM_MODE
            // If /system/etc/motorola/12m/batch/bands/bands.xml is present, load bands from bands.xml
            if (loadBandsFromXml()) {
                tcmd_socket.close();
                return;
            }

            // Query GSM/CDMA/WCDMA Bands
            tcmd_socket.send(TCMD_CMD_QUERY_GSM_CDMA_WCDMA_BANDS);
            response = tcmd_socket.receive();

            parsedResponse = TcmdSocket.parseTcmdResponse(response.getData());
            if (parsedResponse.getParsedResponse().startsWith("FAIL")) {
                Log.e(TAG, "Query GSM/CDMA/WCDMA Bands failed - "
                        + parsedResponse.getParsedResponse());
            } else {
                parseGSMCDMAWCDMAResponse(response.getData());
            }

            // Query WCDMA 2100/1900/1700 in RF NV 22131
            tcmd_socket.send(TCMD_CMD_QUERY_RF_NV_WCDMA);
            response = tcmd_socket.receive();
            parsedResponse = TcmdSocket.parseTcmdResponse(response.getData());
            if (parsedResponse.getParsedResponse().startsWith("FAIL")) {
                Log.e(TAG, "Query WCDMA 2100/1900 in RF NV 22131 failed - "
                        + parsedResponse.getParsedResponse());
            } else {
                parseRF_NV_WCDMAResponse(response.getData());
            }

            // Query TD_SCDMA Bands
            tcmd_socket.send(TCMD_CMD_QUERY_TD_SCDMA_BANDS);
            response = tcmd_socket.receive();

            parsedResponse = TcmdSocket.parseTcmdResponse(response.getData());
            if (parsedResponse.getParsedResponse().startsWith("FAIL")) {
                Log.e(TAG, "Query TD_SCDMA Bands failed - "
                        + parsedResponse.getParsedResponse());
            } else {
                parseTDSCDMAResponse(response.getData());
            }

            // Query LTE Bands
            tcmd_socket.send(TCMD_CMD_QUERY_LTE_BANDS);
            response = tcmd_socket.receive();

            parsedResponse = TcmdSocket.parseTcmdResponse(response.getData());
            if (parsedResponse.getParsedResponse().startsWith("FAIL")) {
                Log.e(TAG, "Query LTE Bands failed - "
                        + parsedResponse.getParsedResponse());
            } else {
                parseLTEResponse(response.getData());
            }
            tcmd_socket.close();

            logSupportedBands();
        } catch (Exception e) {
            Log.e(TAG, "Exception - " + e.getMessage() + " "
                    + Log.getStackTraceString(e));
        }
    }

    private void parseGSMCDMAWCDMAResponse(byte[] data) {
        int data_length = 256 * (data[10] & 0xFF) + (data[11] & 0xFF);
        byte[] response = new byte[8];
        if (data_length >= 11) {
            for (int i = 0, j = 15; j < TcmdSocket.TCMD_HEADER_LENGTH + data_length
                    && i < 8; i++, j++) {
                response[i] = data[j];
            }
            if ((response[0] & 0x03) == 0x03) {
                // CDMA BC0
                mCDMABC0 = true;
            }
            if ((response[0] & 0x04) == 0x04) {
                // CDMA BC1
                mCDMABC1 = true;
            }
            if ((response[0] & 0x80) == 0x80) {
                // GSM 1800
                mGSM1800 = true;
            }
            if ((response[1] & 0x01) == 0x01) {
                // GSM 900
                mGSM900 = true;
            }
            if ((response[1] & 0x40) == 0x40) {
                // CDMA BC10
                mCDMABC10 = true;
            }
            if ((response[2] & 0x08) == 0x08) {
                // GSM 850
                mGSM850 = true;
            }
            if ((response[2] & 0x20) == 0x20) {
                // GSM 1900
                mGSM1900 = true;
            }
            if ((response[2] & 0x40) == 0x40) {
                // WCDMA 2100
                mWCDMA2100 = true;
            }
            if ((response[2] & 0x80) == 0x80) {
                // WCDMA 1900
                mWCDMA1900 = true;
            }
            if ((response[3] & 0x08) == 0x08) {
                // WCDMA 800 Japan
                mWCDMA800_Japan = true;
            }
            if ((response[7] & 0x10) == 0x10) {
                // WCDMA 850 Japan
                mWCDMA850_Japan = true;
            }
            if ((response[3] & 0x04) == 0x04) {
                // WCDMA 850
                mWCDMA850 = true;
            }
            if ((response[3] & 0x02) == 0x02) {
                // WCDMA 1700
                mWCDMA1700 = true;
            }
            if ((response[3] & 0x80) == 0x80) {
                // CDMA BC15
                mCDMABC15 = true;
            }
            if ((response[6] & 0x02) == 0x02) {
                // WCDMA 900
                mWCDMA900 = true;
            }
        }
    }

    // If WCDMA 2100/1900 is not set in BC NV 1877 (parseGSMCDMAWCDMAResponse),
    // query RF NV 22131
    private void parseRF_NV_WCDMAResponse(byte[] data) {
        int data_length = 256 * (data[10] & 0xFF) + (data[11] & 0xFF);
        byte[] response = new byte[8];
        if (data_length >= 15) {
            for (int i = 0, j = 26; j < TcmdSocket.TCMD_HEADER_LENGTH + data_length && i < 8; i++, j++) {
                response[i] = data[j];
            }

            if (mWCDMA2100 == false) {
                mWCDMA2100 = ((response[2] & 0x40) == 0x40);
            }
            if (mWCDMA1900 == false) {
                mWCDMA1900 = ((response[2] & 0x80) == 0x80);
            }
            if (mWCDMA1700 == false) {
                mWCDMA1700 = ((response[3] & 0x02) == 0x02);
            }
        } else {
            Log.e(TAG, "TCMD data response length is not correct length: " + data_length);
            return;
        }
    }

    private void parseTDSCDMAResponse(byte[] data) {
        int data_length = 256 * (data[10] & 0xFF) + (data[11] & 0xFF);
        byte[] response = new byte[8];
        if (data_length >= 15) {
            for (int i = 0, j = 26; j < TcmdSocket.TCMD_HEADER_LENGTH + data_length && i < 8; i++, j++) {
                response[i] = data[j];
            }
            if ((response[0] & 0x01) == 0x01) {
                // TD_SCDMA B34
                mTDSCDMAB34 = true;
            }
            if ((response[0] & 0x20) == 0x20) {
                // TD_SCDMA B39
                mTDSCDMAB39 = true;
            }
        } else {
            Log.e(TAG, "TCMD data response length is not correct length: " + data_length);
            return;
        }
    }

    private void parseLTEResponse(byte[] data) {
        int data_length = 256 * (data[10] & 0xFF) + (data[11] & 0xFF);
        byte[] response = new byte[8];
        if (data_length >= 11) {
            for (int i = 0, j = 15; j < TcmdSocket.TCMD_HEADER_LENGTH + data_length
                    && i < 8; i++, j++) {
                response[i] = data[j];
            }
            if ((response[0] & 0x01) == 0x01) {
                // LTE B1
                mLTEB1 = true;
            }
            if ((response[0] & 0x02) == 0x02) {
                // LTE B2
                mLTEB2 = true;
            }
            if ((response[0] & 0x04) == 0x04) {
                // LTE B3
                mLTEB3 = true;
            }
            if ((response[0] & 0x08) == 0x08) {
                // LTE B4
                mLTEB4 = true;
            }
            if ((response[0] & 0x10) == 0x10) {
                // LTE B5
                mLTEB5 = true;
            }
            if ((response[0] & 0x20) == 0x20) {
                // LTE B6
                mLTEB6 = true;
            }
            if ((response[0] & 0x40) == 0x40) {
                // LTE B7
                mLTEB7 = true;
            }
            if ((response[0] & 0x80) == 0x80) {
                // LTE B8
                mLTEB8 = true;
            }
            if ((response[1] & 0x01) == 0x01) {
                // LTE B9
                mLTEB9 = true;
            }
            if ((response[1] & 0x02) == 0x02) {
                // LTE B10
                mLTEB10 = true;
            }
            if ((response[1] & 0x04) == 0x04) {
                // LTE B11
                mLTEB11 = true;
            }
            if ((response[1] & 0x08) == 0x08) {
                // LTE B12
                mLTEB12 = true;
            }
            if ((response[1] & 0x10) == 0x10) {
                // LTE B13
                mLTEB13 = true;
            }
            if ((response[1] & 0x20) == 0x20) {
                // LTE B14
                mLTEB14 = true;
            }
            if ((response[2] & 0x01) == 0x01) {
                // LTE B17
                mLTEB17 = true;
            }
            if ((response[2] & 0x04) == 0x04) {
                // LTE B19
                mLTEB19 = true;
            }
            if ((response[2] & 0x08) == 0x08) {
                // LTE B20
                mLTEB20 = true;
            }
            if ((response[3] & 0x01) == 0x01) {
                // LTE B25
                mLTEB25 = true;
            }
            if ((response[3] & 0x02) == 0x02) {
                // LTE B26
                mLTEB26 = true;
            }
            if ((response[3] & 0x08) == 0x08) {
                // LTE B28
                mLTEB28 = true;
            }
            if ((response[3] & 0x10) == 0x10) {
                // LTE B29
                mLTEB29 = true;
            }
            if ((response[3] & 0x20) == 0x20) {
                // LTE B30
                mLTEB30 = true;
            }
            if ((response[4] & 0x20) == 0x20) {
                // LTE B38
                mLTEB38 = true;
            }
            if ((response[4] & 0x40) == 0x40) {
                // LTE B39
                mLTEB39 = true;
            }
            if ((response[4] & 0x80) == 0x80) {
                // LTE B40
                mLTEB40 = true;
            }
            if ((response[5] & 0x01) == 0x01) {
                // LTE B41
                mLTEB41 = true;
            }
            if ((response[6] & 0x08) == 0x08) {
                // LTE B66 uses bit 51 as indicator
                mLTEB66 = true;
            }
        }
    }

    private void logSupportedBands() {
        Log.d(TAG, "******************** BANDS SUPPORTED BY DEVICE START ********************");

        Log.d(TAG, "********** CDMA **********");
        Log.d(TAG, "CDMA BC0 - " + mCDMABC0);
        Log.d(TAG, "CDMA BC1 - " + mCDMABC1);
        Log.d(TAG, "CDMA BC10 - " + mCDMABC10);
        Log.d(TAG, "CDMA BC15 - " + mCDMABC15);

        Log.d(TAG, "********** GSM **********");
        Log.d(TAG, "GSM 850 - " + mGSM850);
        Log.d(TAG, "GSM 900 - " + mGSM900);
        Log.d(TAG, "GSM 1800 - " + mGSM1800);
        Log.d(TAG, "GSM 1900 - " + mGSM1900);

        Log.d(TAG, "********** WCDMA **********");
        Log.d(TAG, "WCDMA 800 Japan - " + mWCDMA800_Japan);
        Log.d(TAG, "WCDMA 850 Japan - " + mWCDMA850_Japan);
        Log.d(TAG, "WCDMA 850 - " + mWCDMA850);
        Log.d(TAG, "WCDMA 900 - " + mWCDMA900);
        Log.d(TAG, "WCDMA 1700 - " + mWCDMA1700);
        Log.d(TAG, "WCDMA 1900 - " + mWCDMA1900);
        Log.d(TAG, "WCDMA 2100 - " + mWCDMA2100);

        Log.d(TAG, "********** TD_SCDMA **********");
        Log.d(TAG, "TD_SCDMA B34 - " + mTDSCDMAB34);
        Log.d(TAG, "TD_SCDMA B39 - " + mTDSCDMAB39);

        Log.d(TAG, "********** LTE **********");
        Log.d(TAG, "LTE B1 - " + mLTEB1);
        Log.d(TAG, "LTE B2 - " + mLTEB2);
        Log.d(TAG, "LTE B3 - " + mLTEB3);
        Log.d(TAG, "LTE B4 - " + mLTEB4);
        Log.d(TAG, "LTE B5 - " + mLTEB5);
        Log.d(TAG, "LTE B6 - " + mLTEB6);
        Log.d(TAG, "LTE B7 - " + mLTEB7);
        Log.d(TAG, "LTE B8 - " + mLTEB8);
        Log.d(TAG, "LTE B9 - " + mLTEB9);
        Log.d(TAG, "LTE B10 - " + mLTEB10);
        Log.d(TAG, "LTE B11 - " + mLTEB11);
        Log.d(TAG, "LTE B12 - " + mLTEB12);
        Log.d(TAG, "LTE B13 - " + mLTEB13);
        Log.d(TAG, "LTE B14 - " + mLTEB14);
        Log.d(TAG, "LTE B17 - " + mLTEB17);
        Log.d(TAG, "LTE B19 - " + mLTEB19);
        Log.d(TAG, "LTE B20 - " + mLTEB20);
        Log.d(TAG, "LTE B25 - " + mLTEB25);
        Log.d(TAG, "LTE B252 - " + mLTEB252);
        Log.d(TAG, "LTE B255 - " + mLTEB255);
        Log.d(TAG, "LTE B26 - " + mLTEB26);
        Log.d(TAG, "LTE B28 - " + mLTEB28);
        Log.d(TAG, "LTE B29 - " + mLTEB29);
        Log.d(TAG, "LTE B30 - " + mLTEB30);
        Log.d(TAG, "LTE B38 - " + mLTEB38);
        Log.d(TAG, "LTE B39 - " + mLTEB39);
        Log.d(TAG, "LTE B40 - " + mLTEB40);
        Log.d(TAG, "LTE B41 - " + mLTEB41);
        Log.d(TAG, "LTE B66 - " + mLTEB66);

        Log.i(TAG, "******************** BANDS SUPPORTED BY DEVICE END ********************");
    }

    public boolean isCDMABC0() {
        return mCDMABC0;
    }

    public boolean isCDMABC1() {
        return mCDMABC1;
    }

    public boolean isCDMABC10() {
        return mCDMABC10;
    }

    public boolean isCDMABC15() {
        return mCDMABC15;
    }

    public boolean isGSM850() {
        return mGSM850;
    }

    public boolean isGSM900() {
        return mGSM900;
    }

    public boolean isGSM1800() {
        return mGSM1800;
    }

    public boolean isGSM1900() {
        return mGSM1900;
    }

    public boolean isWCDMA800Japan() {
        return mWCDMA800_Japan;
    }

    public boolean isWCDMA850Japan() {
        return mWCDMA850_Japan;
    }

    public boolean isWCDMA850() {
        return mWCDMA850;
    }

    public boolean isWCDMA900() {
        return mWCDMA900;
    }

    public boolean isWCDMA1700() {
        return mWCDMA1700;
    }

    public boolean isWCDMA1900() {
        return mWCDMA1900;
    }

    public boolean isWCDMA2100() {
        return mWCDMA2100;
    }

    public boolean isTDSCDMAB34() {
        return mTDSCDMAB34;
    }

    public boolean isTDSCDMAB39() {
        return mTDSCDMAB39;
    }

    public boolean isLTEB1() {
        return mLTEB1;
    }

    public boolean isLTEB2() {
        return mLTEB2;
    }

    public boolean isLTEB3() {
        return mLTEB3;
    }

    public boolean isLTEB4() {
        return mLTEB4;
    }

    public boolean isLTEB5() {
        return mLTEB5;
    }

    public boolean isLTEB6() {
        return mLTEB6;
    }

    public boolean isLTEB7() {
        return mLTEB7;
    }

    public boolean isLTEB8() {
        return mLTEB8;
    }

    public boolean isLTEB9() {
        return mLTEB9;
    }

    public boolean isLTEB10() {
        return mLTEB10;
    }

    public boolean isLTEB11() {
        return mLTEB11;
    }

    public boolean isLTEB12() {
        return mLTEB12;
    }

    public boolean isLTEB13() {
        return mLTEB13;
    }

    public boolean isLTEB14() {
        return mLTEB14;
    }

    public boolean isLTEB17() {
        return mLTEB17;
    }

    public boolean isLTEB19() {
        return mLTEB19;
    }

    public boolean isLTEB20() {
        return mLTEB20;
    }

    public boolean isLTEB25() {
        return mLTEB25;
    }

    public boolean isLTEB252() {
        return mLTEB252;
    }

    public boolean isLTEB255() {
        return mLTEB255;
    }

    public boolean isLTEB26() {
        return mLTEB26;
    }

    public boolean isLTEB28() {
        return mLTEB28;
    }

    public boolean isLTEB29() {
        return mLTEB29;
    }

    public boolean isLTEB30() {
        return mLTEB30;
    }

    public boolean isLTEB38() {
        return mLTEB38;
    }

    public boolean isLTEB39() {
        return mLTEB39;
    }

    public boolean isLTEB40() {
        return mLTEB40;
    }

    public boolean isLTEB41() {
        return mLTEB41;
    }

    public boolean isLTEB66() {
        return mLTEB66;
    }
}
