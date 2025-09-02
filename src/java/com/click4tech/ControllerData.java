/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.click4tech;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;
import org.opengts.db.tables.*;
import org.opengts.db.*;

import org.opengts.war.tools.*;

/**
 * GPS Tracking Device Controller Data Servlet
 * 
 * This servlet handles HTTP requests from GC-101 GPS tracking devices and processes
 * GPS location data in GPRMC format. It's designed to work with the OpenGTS 
 * (Open GPS Tracking System) framework.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Processes HTTP GET/POST requests from GPS devices</li>
 *   <li>Parses GPRMC (Global Positioning Recommended Minimum) data format</li>
 *   <li>Handles device identification via IMEI numbers</li>
 *   <li>Manages various GPS event status codes (location, alarms, geofencing)</li>
 *   <li>Calculates battery levels and performs coordinate transformations</li>
 *   <li>Supports geofence transition detection and simulation</li>
 *   <li>Integrates with OpenGTS database for event storage</li>
 * </ul>
 * 
 * <p>Supported URL format:</p>
 * <code>http://server:port/gc101/Data?imei=DEVICE_IMEI&rmc=GPRMC_DATA&code=STATUS_CODE</code>
 * 
 * <p>Example request:</p>
 * <code>http://localhost:8080/gc101/Data?imei=471923002250245&rmc=$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210507,,*19,AUTO</code>
 * 
 * @author Click4Tech Support Team
 * @version 1.0.2
 * @since 1.0.0
 */
public class ControllerData 
    extends CommonServlet
{

    // ------------------------------------------------------------------------
    // Application Constants and Configuration
    // ------------------------------------------------------------------------
    
    /** Current version of the ControllerData servlet */
    public  static final String     VERSION                     = "1.0.2";
    
    /** Device code identifier for GC-101 GPS tracking devices */
    public  static final String     DEVICE_CODE                 = "gc101";

    /** Unique ID prefix for GC-101 devices (format: gc101_IMEI) */
    public  static final String     UNIQUE_ID_PREFIX_GC101      = "gc101_";
    
    /** Alternative unique ID prefix using IMEI directly (format: imei_IMEI) */
    public  static final String     UNIQUE_ID_PREFIX_IMEI       = "imei_";
    
    /** 
     * Flag to enable/disable IMEI-only lookup as fallback device identification.
     * When true, allows lookup by raw IMEI number if prefixed versions fail.
     */
    private static final boolean    ALSO_CHECK_IMEI             = false;

    /** HTTP parameter names for command requests (case insensitive) */
    private static final String     PARM_COMMAND[]              = { "cmd"             };
    
    /** HTTP parameter names for device IMEI identification (case insensitive) */
    private static final String     PARM_IMEI[]                 = { "imei"  , "id"    };
    
    /** HTTP parameter names for GPRMC GPS data (case insensitive) */
    private static final String     PARM_RMC[]                  = { "rmc"   , "gprmc" };
    
    /** HTTP parameter names for status/event codes (case insensitive) */
    private static final String     PARM_CODE[]                 = { "code"  , "sc"    };
    
    /** Standard success response sent back to GPS device */
    private static final String     RESPONSE_OK                 = "OK";
    
    /** Standard error response sent back to GPS device */
    private static final String     RESPONSE_ERROR              = "";

    // ------------------------------------------------------------------------
    // Runtime Configuration Property Keys
    // ------------------------------------------------------------------------

    /** Configuration key for minimum speed threshold in KPH below which device is considered stopped */
    public  static final String CONFIG_MIN_SPEED            = DEVICE_CODE + ".minimumSpeedKPH";
    
    /** Configuration key to enable GPS-based odometer estimation for distance calculations */
    public  static final String CONFIG_ESTIMATE_ODOMETER    = DEVICE_CODE + ".estimateOdometer";
    
    /** Configuration key to enable automatic geofence arrive/depart event simulation */
    public  static final String CONFIG_SIMEVENT_GEOZONES    = DEVICE_CODE + ".simulateGeozones";

    // ------------------------------------------------------------------------
    // Physical Constants and Conversion Factors
    // ------------------------------------------------------------------------

    /** Conversion factor from nautical miles (knots) to kilometers */
    public static final  double  KILOMETERS_PER_KNOT        = 1.85200000;

    // ------------------------------------------------------------------------
    // Runtime Configuration Variables (loaded from config files)
    // ------------------------------------------------------------------------

    /** 
     * Flag indicating whether to calculate GPS-based odometer values.
     * When enabled, estimates distance traveled based on GPS coordinate changes.
     */
    public  static       boolean ESTIMATE_ODOMETER          = false;

    /** 
     * Flag indicating whether to automatically simulate geofence events.
     * When enabled, generates arrive/depart events when device crosses geofence boundaries.
     */
    public  static       boolean SIMEVENT_GEOZONES          = false;

    /** 
     * Minimum acceptable speed threshold in kilometers per hour.
     * GPS readings below this value are treated as stopped (speed = 0).
     * Helps filter out GPS noise when device is stationary.
     */
    public  static       double  MinimumReqSpeedKPH         = 4.0;

    // ------------------------------------------------------------------------
    // Battery Level Calculation Constants and Methods
    // ------------------------------------------------------------------------
    
    /** Maximum battery voltage for GC-101 device (4.1V for 1100 mAh battery) */
    private static       double  MAX_BATTERY_VOLTS          = 4.100; // 1100 maH
    
    /** Minimum operational battery voltage for GC-101 device */
    private static       double  MIN_BATTERY_VOLTS          = 3.650;
    
    /** 
     * Battery voltage range used for percentage calculation.
     * Represents the difference between max and min operational voltages.
     */
    private static       double  RANGE_BATTERY_VOLTS        = MAX_BATTERY_VOLTS - MIN_BATTERY_VOLTS; // 0.45
    
    /**
     * Calculates battery level as a percentage based on current voltage.
     * Uses linear interpolation between minimum and maximum voltage thresholds.
     * Formula provided by Sanav (device manufacturer).
     * 
     * @param voltage Current battery voltage in volts
     * @return Battery level as percentage (0.0 to 1.0), where 1.0 = 100%
     */
    private static double CalcBatteryPercent(double voltage)
    {
        // Linear interpolation: (current - min) / (max - min)
        double percent = (voltage - MIN_BATTERY_VOLTS) / RANGE_BATTERY_VOLTS;
        
        // Clamp to valid range [0.0, 1.0]
        if (percent < 0.0) {
            return 0.0;  // Battery critically low or voltage reading error
        } else
        if (percent > 1.0) {
            return 1.0;  // Battery full or voltage reading above maximum
        } else {
            return percent;  // Normal battery level
        }
    }

    // ------------------------------------------------------------------------
    // Static Initialization Block
    // ------------------------------------------------------------------------

    /** 
     * Static initializer - executed once when class is first loaded.
     * Initializes database configuration and loads runtime settings from config files.
     */
    static {

        /** Initialize OpenGTS database factories and configuration */
        // Note: Should already have been called by 'RTConfigContextListener' during webapp startup
        DBConfig.servletInit(null);

        /** Log current servlet version for debugging and monitoring */
        Print.logInfo("Version: v" + VERSION);

        /** Load minimum speed threshold from configuration file */
        MinimumReqSpeedKPH = RTConfig.getDouble(CONFIG_MIN_SPEED, MinimumReqSpeedKPH);
        Print.logInfo("Minimum speed: " + MinimumReqSpeedKPH + " kph");

        /** Load GPS-based odometer estimation setting from configuration */
        ESTIMATE_ODOMETER = RTConfig.getBoolean(CONFIG_ESTIMATE_ODOMETER, ESTIMATE_ODOMETER);
        Print.logInfo("Estimating Odometer: " + ESTIMATE_ODOMETER);

        /** Load geofence simulation setting from configuration */
        SIMEVENT_GEOZONES = RTConfig.getBoolean(CONFIG_SIMEVENT_GEOZONES, SIMEVENT_GEOZONES);
        Print.logInfo("Simulating Geozone: " + SIMEVENT_GEOZONES);

    };

    // ------------------------------------------------------------------------
    // HTTP Response Utility Methods  
    // ------------------------------------------------------------------------

    /**
     * Sends a plain text response back to the requesting GPS device.
     * Sets the appropriate MIME type for plain text and writes the message to the response.
     * 
     * @param response HttpServletResponse object to write the response to
     * @param errMsg   The text message to send back to the device
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException      If there's an I/O error writing the response
     */
    private void plainTextResponse(HttpServletResponse response, String errMsg)
        throws ServletException, IOException
    {
        // Set response content type to plain text
        CommonServlet.setResponseContentType(response, HTMLTools.MIME_PLAIN());
        
        // Get output stream and send response
        PrintWriter out = response.getWriter();
        out.println(errMsg);
    }

    // ------------------------------------------------------------------------
    // Device Management Methods
    // ------------------------------------------------------------------------

    /**
     * Loads a Device record from the database using the device's IMEI number.
     * Attempts multiple lookup strategies in order of preference:
     * 1. Standard GC-101 prefixed ID (gc101_IMEI)
     * 2. Alternative IMEI prefixed ID (imei_IMEI) 
     * 3. Raw IMEI number (if ALSO_CHECK_IMEI is enabled)
     * 
     * Also validates the requesting IP address against the device's allowed IP list
     * and updates device connection metadata.
     * 
     * @param imei    The device's IMEI number (15-digit identifier)
     * @param ipAddr  The IP address of the requesting device (for validation)
     * @return        Device object if found and valid, null if not found or invalid IP
     */
    private Device loadDevice(String imei, String ipAddr)
    {

        /** Validate IMEI parameter */
        if (StringTools.isBlank(imei)) {
            Print.logWarn("Ignoring packet with blank IMEI#");
            return null;
        }

        /** Initialize device lookup variables */
        Device        device    = null;
        DataTransport dataXPort = null;
        String        mobileID  = null;
        
        try {
            // Strategy 1: Try standard GC-101 prefixed unique ID
            String gc101ID = UNIQUE_ID_PREFIX_GC101 + imei;
            device = Transport.loadDeviceByUniqueID(gc101ID);
            if (device != null) {
                mobileID = gc101ID; // Found device with gc101_ prefix
            } else {
                // Strategy 2: Try alternative IMEI prefixed unique ID
                String imeiID = UNIQUE_ID_PREFIX_IMEI + imei;
                device = Transport.loadDeviceByUniqueID(imeiID);
                if (device != null) {
                    mobileID = imeiID; // Found device with imei_ prefix
                } else {
                    // Strategy 3: Try raw IMEI number (fallback option)
                    if (ALSO_CHECK_IMEI && (imei.length() >= 15)) { // IMEI numbers are 15 digits long
                        device = Transport.loadDeviceByUniqueID(imei);
                        if (device != null) {
                            mobileID = imei; // Found device with raw IMEI
                        }
                    }
                }
            }
            
            // Final validation - ensure we found a device record
            if (device == null) {
                Print.logWarn("GC-101 ID not found!: " + gc101ID); // Display primary lookup key
                return null;
            }
            
            // Get the device's data transport configuration
            dataXPort = device.getDataTransport();
            
        } catch (DBException dbe) {
            Print.logError("Exception getting Device: " + mobileID + " [" + dbe + "]");
            return null;
        }

        /** Validate source IP address against device's allowed IP list */
        if ((ipAddr != null) && !dataXPort.isValidIPAddress(ipAddr)) {
            Print.logError("Invalid IP Address for device: " + ipAddr + 
                " [expecting " + dataXPort.getIpAddressValid() + "]");
            return null;
        }

        /** Update device connection metadata */
        dataXPort.setIpAddressCurrent(ipAddr);      // Record current IP address
        dataXPort.setDeviceCode(DEVICE_CODE);       // Set device type identifier  
        device.setLastTotalConnectTime(DateTime.getCurrentTimeSec()); // Update last connection time

        /** Return successfully loaded and validated device */
        return device;

    }

    // ------------------------------------------------------------------------
    // Configure the GC-101 to send to a URL similar to:
    //  http://track.example.com/gc101/Data
    // Returned data format:
    //  ?imei=471923002250245&rmc=$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210507,,*19,AUTO
    // Example:
    //  http://localhost:8080/gc101/Data?imei=471923002250245&rmc=$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210507,,*19,AUTO
    //  http://localhost:8080/gc101/Data?imei=352024025553342&rmc=$GPRMC,124422.000,A,3135.5867,S,14245.3128,W,0.16,100.00,110809,,,A*71,alarm1
    //  http://localhost:8080/gc101/Data?imei=00000&rmc=$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210511,,*19,AUTO

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        this._doWork(true, request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        this._doWork(false, request, response);
    }

    private void _doWork(boolean isPost, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String ipAddr  = request.getRemoteAddr();
        String imei    = AttributeTools.getRequestString(request, PARM_IMEI   , "");
        String gprmc   = AttributeTools.getRequestString(request, PARM_RMC    , "");
        String code    = AttributeTools.getRequestString(request, PARM_CODE   , "");

        /* URL */
        StringBuffer reqURL = request.getRequestURL();
        String queryStr = StringTools.blankDefault(request.getQueryString(),"(n/a)");
        if (isPost) {
            // 'queryStr' is likely not available
            StringBuffer postSB = new StringBuffer();
            for (java.util.Enumeration ae = request.getParameterNames(); ae.hasMoreElements();) {
                if (postSB.length() > 0) { postSB.append("&"); }
                String ak = (String)ae.nextElement();
                String av = request.getParameter(ak);
                postSB.append(ak + "=" + av);
            }
            Print.logInfo("[" + ipAddr + "] POST: " + reqURL + " " + queryStr + " [" + postSB + "]");
        } else {
            Print.logInfo("[" + ipAddr + "] GET: "  + reqURL + " " + queryStr);
        }

        /* "&cmd=version"? */
        if (isPost) {
            String cmd = AttributeTools.getRequestString(request, PARM_COMMAND, "");
            if (cmd.equalsIgnoreCase("version") || 
                cmd.equalsIgnoreCase("ver")       ) {
                String vers = DEVICE_CODE+"-"+VERSION;
                this.plainTextResponse(response, "OK:version:"+vers);
                return;
            } else
            if (cmd.equalsIgnoreCase("mobileid") ||
                cmd.equalsIgnoreCase("id")         ) {
                String vers = DEVICE_CODE+"-"+VERSION;
                Device device = this.loadDevice(imei, ipAddr);
                if (device != null) {
                    this.plainTextResponse(response, "OK:ack:"+vers);
                } else {
                    this.plainTextResponse(response, "ERROR:nak:"+vers);
                }
                return;
            }
        }

        /* parse/insert event */
        String resp = "";
        try {
            resp = this.parseInsertEvent(ipAddr, imei, gprmc, code);
        } catch (Throwable t) {
            Print.logException("Unexpected Exception", t);
        }

        /* write response */
        this.plainTextResponse(response, resp);
       
        request.getRequestDispatcher("TrackTech.jsp").forward(request, response);
        
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* parse and insert event */
    private String parseInsertEvent(String ipAddr, String imei, String gprmc, String code)
    {

        /* load device */
        Device device = this.loadDevice(imei, ipAddr);
        if (device == null) {
            // errors already displayed
            return RESPONSE_ERROR;
        }

        /* no location data specified? */
        if (StringTools.isBlank(gprmc) && 
            StringTools.isBlank(code)    ) {
            // no error was generated, bu also GPS location was processed
            return RESPONSE_OK;
        }

        /* parse event */
        String accountID = device.getAccountID();
        String deviceID  = device.getDeviceID();
        EventData evdb   = this.parseGPRMC(device, gprmc, code);
        if (evdb != null) {
            GeoPoint geoPoint = evdb.getGeoPoint();
            boolean  validGPS = evdb.isValidGeoPoint();
            int      statCode = evdb.getStatusCode();

            /* simulate geozones */
            int geozoneEvents = 0;
            if (SIMEVENT_GEOZONES && validGPS) {
                long timestamp = evdb.getTimestamp();
                java.util.List<Device.GeozoneTransition> zone = device.checkGeozoneTransitions(timestamp, geoPoint);
                if (zone != null) {
                    double hdop      = evdb.getHDOP();
                    int    satCnt    = evdb.getSatelliteCount();
                    double speedKPH  = evdb.getSpeedKPH();
                    double heading   = evdb.getHeading();
                    double odomKM    = evdb.getOdometerKM();
                    double battPct   = evdb.getBatteryLevel();
                    long   gpioInp   = evdb.getInputMask();
                    for (Device.GeozoneTransition z : zone) {
                        EventData.Key zoneKey = new EventData.Key(accountID, deviceID, z.getTimestamp(), z.getStatusCode());
                        EventData zoneEv = zoneKey.getDBRecord();
                        zoneEv.setGeozone(z.getGeozone());
                        zoneEv.setGeoPoint(geoPoint);
                        zoneEv.setHDOP(hdop);
                        zoneEv.setSatelliteCount(satCnt);
                        zoneEv.setSpeedKPH(speedKPH);
                        zoneEv.setHeading(heading);
                        zoneEv.setOdometerKM(odomKM);
                        zoneEv.setBatteryLevel(battPct);
                        zoneEv.setInputMask(gpioInp);
                        if (device.insertEventData(zoneEv)) {
                            Print.logInfo("Geozone    : " + z);
                            geozoneEvents++;
                        }
                    }
                }
            }

            /* insert original event */
            if ((geozoneEvents <= 0) || (statCode != StatusCodes.STATUS_LOCATION)) {
                boolean didInsert = device.insertEventData(evdb);
                if (didInsert) {
                    Print.logInfo("Event: " + accountID + "/" + deviceID + " - " + geoPoint);
                }
            }

        }

        /* save device changes */
        try {
            // TODO: check "this.device" vs "this.dataXPort"
            device.updateChangedEventFields();
        } catch (DBException dbe) {
            Print.logException("Unable to update Device: " + 
                device.getAccountID() + "/" + device.getDeviceID(), dbe);
        }
        
        return RESPONSE_OK;

    }
        
    // ------------------------------------------------------------------------

    /* parse status code */
    private int parseStatusCode(String evCode, int dftCode)
    {
        String code = StringTools.trim(evCode).toUpperCase();

        /* prefixing "B" means that the event was stored in flash */
        if (code.startsWith("B")) {
            if (code.startsWith("B-")) {
                code = code.substring(2); // remove "B-"
            } else {
                code = code.substring(1); // remove "B"
            }
        }
        int codeLen = code.length();

        /* find code match */
        int statusCode = dftCode;
        if (codeLen == 0) {
            statusCode = dftCode;
        } else
        if (code.startsWith("0X")) {
            // explicit hex status code definition
            statusCode = StringTools.parseInt(code, dftCode);
        } else
        if (code.equalsIgnoreCase("AUTO")) {
            // periodic event
            statusCode = StatusCodes.STATUS_LOCATION;
        } else 
        if (code.equalsIgnoreCase("SOS")) {
            // panic button
            statusCode = StatusCodes.STATUS_WAYMARK_0;
        } else
        if (code.equalsIgnoreCase("MOVE")) {
            // device is moving?
          statusCode = StatusCodes.STATUS_MOTION_MOVING;
        } else 
        if (code.equalsIgnoreCase("POLL")) {
            // response to "Locate Now"
            statusCode = StatusCodes.STATUS_QUERY;
        } else
        if (code.equalsIgnoreCase("GFIN")) {
            // Geofence arrive
            statusCode = StatusCodes.STATUS_GEOFENCE_ARRIVE;
        } else
        if (code.equals("GFOUT") || code.equals("GOUT")) {
            // Geofence depart
            statusCode = StatusCodes.STATUS_GEOFENCE_DEPART;
        } else
        if (code.equalsIgnoreCase("PARK")) {
            // parked
            statusCode = StatusCodes.STATUS_PARKED;
        } else
        if (code.equals("UNPARK") || code.equals("UNPA")) {
            // unparked
            statusCode = StatusCodes.STATUS_UNPARKED;
        } else
        if (code.equals("START")) {
            // start?
            statusCode = StatusCodes.STATUS_LOCATION;
        } else
        if (code.equals("ACCON")) {
            // accessory on (assume ignition)
            statusCode = StatusCodes.STATUS_IGNITION_ON;
        } else
        if (code.equals("ACCOFF")) {
            // accessory off (assume ignition)
            statusCode = StatusCodes.STATUS_IGNITION_OFF;
        } else
        if (code.equalsIgnoreCase("LP")) {
            // Low power
            statusCode = StatusCodes.STATUS_LOW_BATTERY;
        } else
        if (code.equals("DC")) {
            // lost power ??
            statusCode = StatusCodes.STATUS_POWER_FAILURE; // ???
        } else
        if (code.equals("CH")) {
            // charging?
            statusCode = StatusCodes.STATUS_POWER_RESTORED; // charging?
        } else
        if (code.equals("OPEN")) { 
            // on normally "open" switch (provided by Sanav), this is alarm "ON"
            statusCode = StatusCodes.InputStatusCodes_ON[0];
        } else
        if (code.equals("CLOSE")) { 
            // on normally "open" switch (provided by Sanav), this is alarm "OFF"
            statusCode = StatusCodes.InputStatusCodes_OFF[0];
        } else
        if (code.startsWith("ALARM") && (codeLen >= 6)) { // "ALARM1" .. "ALARM6"
            // "ALARM1" ==> StatusCodes.STATUS_INPUT_ON_01
            int ndx = (code.charAt(5) - '0'); // will be 1..6 ('0' not used here)
            if ((ndx >= 0) && (ndx <= 9) && (ndx < StatusCodes.InputStatusCodes_ON.length)) {
                statusCode = StatusCodes.InputStatusCodes_ON[ndx];
            } else {
                statusCode = StatusCodes.STATUS_INPUT_ON;
            }
        } else
        if (code.equals("STATIONARY")) {
            // not moving
            statusCode = StatusCodes.STATUS_MOTION_DORMANT; // not moving
        } else
        if (code.equals("VIBRATION")) {
            // device was 'shaken'
            statusCode = StatusCodes.STATUS_LOCATION;
        } else 
        if (code.equals("OVERSPEED")) {
            // over speed
            statusCode = StatusCodes.STATUS_MOTION_EXCESS_SPEED;
        } else 
        {
            // GS-818: "code" could contain barcode data
            statusCode = dftCode;
        }
        return statusCode;

    }

    /* parse GPRMC record */
    private EventData parseGPRMC(Device dev, String data, String code)
    {
        String fld[] = StringTools.parseString(data, ',');

        /* invalid record? */
        if ((fld == null) || (fld.length < 1) || !fld[0].equals("$GPRMC")) {
            Print.logWarn("Not a $GPRMC record: " + data);
            return null;
        } else
        if (fld.length < 10) {
            Print.logWarn("Invalid number of $GPRMC fields: " + data);
            return null;
        }

        /* parse */
        long    hms        = StringTools.parseLong(fld[1], 0L);
        long    dmy        = StringTools.parseLong(fld[9], 0L);
        long    fixtime    = this._getUTCSeconds(dmy, hms);
        boolean isValid    = fld[2].equals("A");
        double  latitude   = isValid? this._parseLatitude (fld[3], fld[4]) : 0.0;
        double  longitude  = isValid? this._parseLongitude(fld[5], fld[6]) : 0.0;
        double  knots      = isValid? StringTools.parseDouble(fld[7], 0.0) : 0.0;
        double  heading    = isValid? StringTools.parseDouble(fld[8], 0.0) : 0.0;
        double  speedKPH   = (knots > 0.0)? (knots * KILOMETERS_PER_KNOT)  : 0.0;
        int     statusCode = this.parseStatusCode(code, StatusCodes.STATUS_LOCATION);
        double  batteryV   = 0.0;
        long    gpioInput  = -1L;
        double  HDOP       = 0.0;
        int     numSats    = 0;

        /* valid lat/lon? */
        if (!GeoPoint.isValid(latitude,longitude)) {
            Print.logWarn("Invalid GPRMC lat/lon: " + latitude + "/" + longitude);
            latitude  = 0.0;
            longitude = 0.0;
            isValid   = false;
        }
        GeoPoint geoPoint = new GeoPoint(latitude, longitude);

        /* status code, extra data */
        int exd = ((fld.length > 11) && (fld[11].indexOf('*') >= 0))? 12 : ((fld.length > 12) && (fld[12].indexOf('*') >= 0))? 13 : 12;
        String extra0 = (fld.length > (exd+0))? fld[exd+0] : "";
        String extra1 = (fld.length > (exd+1))? fld[exd+1] : "";
        String extra2 = (fld.length > (exd+2))? fld[exd+2] : "";
        String extra3 = (fld.length > (exd+3))? fld[exd+3] : "";
        // CODE[-XXXXmv]
        // "3722mV,VIBRATION,..."
        // "AUTO-3893mv"
        if ((extra0.length() > 0) && Character.isDigit(extra0.charAt(0))) {
            // "3893mV,VIBRATION"
            batteryV    = StringTools.parseDouble(extra0,0.0) / 1000.0;
            statusCode  = this.parseStatusCode(extra1, statusCode);
        } else {
            // "AUTO" or "AUTO-3893mv"
            int ep = extra0.indexOf('-');
            String stat = (ep >= 0)? extra0.substring(0,ep) : extra0;
            String batt = (ep >= 0)? extra0.substring(ep+1) : null;
            statusCode  = this.parseStatusCode(stat, statusCode);
            batteryV    = StringTools.parseDouble(batt,0.0) / 1000.0;
            gpioInput   = StringTools.parseHexLong(extra1, -1L); 
            HDOP        = StringTools.parseDouble( extra2,0.0); 
            numSats     = StringTools.parseInt(    extra3,  0);
        }

        /* ignore event based on status code? */
        if (statusCode == StatusCodes.STATUS_IGNORE) {
            return null;
        } else
        if (statusCode == StatusCodes.STATUS_NONE) {
            return null;
        } else
        if ((statusCode == StatusCodes.STATUS_LOCATION) && !isValid) {
            Print.logWarn("Ignoring event with invalid GPS fix");
            return null;
        }

        /* battery level (percent) */
        double batteryLvl = CalcBatteryPercent(batteryV);

        /* minimum speed */
        if (speedKPH < MinimumReqSpeedKPH) {
            speedKPH = 0.0;
            heading  = 0.0;
        }

        /* estimate GPS-based odometer */
        double odomKM = (ESTIMATE_ODOMETER && isValid)? 
            dev.getNextOdometerKM(geoPoint) : 
            dev.getLastOdometerKM();

        /* create/return EventData record */
        String acctID = dev.getAccountID();
        String devID  = dev.getDeviceID();
        EventData.Key evKey = new EventData.Key(acctID, devID, fixtime, statusCode);
        EventData evdb = evKey.getDBRecord();
        evdb.setGeoPoint(geoPoint);
        evdb.setHDOP(HDOP);
        evdb.setSatelliteCount(numSats);
        evdb.setSpeedKPH(speedKPH);
        evdb.setHeading(heading);
        evdb.setOdometerKM(odomKM);
        evdb.setBatteryLevel(batteryLvl);
        if (gpioInput >= 0L) {
            evdb.setInputMask(gpioInput);
        }
        return evdb;

    }
    
    private long _getUTCSeconds(long dmy, long hms)
    {
    
        /* time of day [TOD] */
        int    HH  = (int)((hms / 10000L) % 100L);
        int    MM  = (int)((hms / 100L) % 100L);
        int    SS  = (int)(hms % 100L);
        long   TOD = (HH * 3600L) + (MM * 60L) + SS;
    
        /* current UTC day */
        long DAY;
        if (dmy > 0L) {
            int    yy  = (int)(dmy % 100L) + 2000;
            int    mm  = (int)((dmy / 100L) % 100L);
            int    dd  = (int)((dmy / 10000L) % 100L);
            long   yr  = ((long)yy * 1000L) + (long)(((mm - 3) * 1000) / 12);
            DAY        = ((367L * yr + 625L) / 1000L) - (2L * (yr / 1000L))
                         + (yr / 4000L) - (yr / 100000L) + (yr / 400000L)
                         + (long)dd - 719469L;
        } else {
            // we don't have the day, so we need to figure out as close as we can what it should be.
            long   utc = DateTime.getCurrentTimeSec();
            long   tod = utc % DateTime.DaySeconds(1);
            DAY        = utc / DateTime.DaySeconds(1);
            long   dif = (tod >= TOD)? (tod - TOD) : (TOD - tod); // difference should be small (ie. < 1 hour)
            if (dif > DateTime.HourSeconds(12)) { // 12 to 18 hours
                // > 12 hour difference, assume we've crossed a day boundary
                if (tod > TOD) {
                    // tod > TOD likely represents the next day
                    DAY++;
                } else {
                    // tod < TOD likely represents the previous day
                    DAY--;
                }
            }
        }
        
        /* return UTC seconds */
        long sec = DateTime.DaySeconds(DAY) + TOD;
        return sec;
        
    }

    private double _parseLatitude(String s, String d)
    {
        double _lat = StringTools.parseDouble(s, 99999.0);
        if (_lat < 99999.0) {
            double lat = (double)((long)_lat / 100L); // _lat is always positive here
            lat += (_lat - (lat * 100.0)) / 60.0;
            return d.equals("S")? -lat : lat;
        } else {
            return 90.0; // invalid latitude
        }
    }
    
    private double _parseLongitude(String s, String d)
    {
        double _lon = StringTools.parseDouble(s, 99999.0);
        if (_lon < 99999.0) {
            double lon = (double)((long)_lon / 100L); // _lon is always positive here
            lon += (_lon - (lon * 100.0)) / 60.0;
            return d.equals("W")? -lon : lon;
        } else {
            return 180.0; // invalid longitude
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

}
