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
    // HTTP Request Processing Methods
    // ------------------------------------------------------------------------
    
    /**
     * HTTP POST request handler for GPS device communication.
     * GC-101 devices typically use POST requests to send GPS data and receive commands.
     * 
     * @param request  HttpServletRequest containing GPS data parameters
     * @param response HttpServletResponse for sending acknowledgment back to device
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException      If there's an I/O error during request/response handling
     */
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
        // Delegate to common processing method with POST flag
        this._doWork(true, request, response);
    }

    /**
     * HTTP GET request handler for GPS device communication.
     * Some GPS devices or testing scenarios may use GET requests instead of POST.
     * 
     * @param request  HttpServletRequest containing GPS data parameters  
     * @param response HttpServletResponse for sending acknowledgment back to device
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException      If there's an I/O error during request/response handling
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        // Delegate to common processing method with GET flag
        this._doWork(false, request, response);
    }

    /**
     * Common request processing method for both GET and POST requests.
     * Extracts GPS data parameters, handles special commands, and processes location events.
     * 
     * <p>Supported Parameters:</p>
     * <ul>
     *   <li><strong>imei/id:</strong> Device IMEI identifier</li>
     *   <li><strong>rmc/gprmc:</strong> GPRMC format GPS data string</li>
     *   <li><strong>code/sc:</strong> Status/event code (AUTO, SOS, ALARM1, etc.)</li>
     *   <li><strong>cmd:</strong> Special commands (version, mobileid)</li>
     * </ul>
     * 
     * <p>Special Commands:</p>
     * <ul>
     *   <li><strong>version:</strong> Returns servlet version information</li>
     *   <li><strong>mobileid:</strong> Validates device registration and returns ACK/NAK</li>
     * </ul>
     * 
     * @param isPost   True if this is a POST request, false for GET request
     * @param request  HttpServletRequest containing GPS data parameters
     * @param response HttpServletResponse for sending acknowledgment back to device
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException      If there's an I/O error during request/response handling
     */
    private void _doWork(boolean isPost, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        // Extract client information and request parameters
        String ipAddr  = request.getRemoteAddr();
        String imei    = AttributeTools.getRequestString(request, PARM_IMEI   , "");
        String gprmc   = AttributeTools.getRequestString(request, PARM_RMC    , "");
        String code    = AttributeTools.getRequestString(request, PARM_CODE   , "");

        /** Log incoming request for debugging and monitoring */
        StringBuffer reqURL = request.getRequestURL();
        String queryStr = StringTools.blankDefault(request.getQueryString(),"(n/a)");
        
        if (isPost) {
            // For POST requests, query string may not contain all parameters
            // Build parameter string from POST body for complete logging
            StringBuffer postSB = new StringBuffer();
            for (java.util.Enumeration ae = request.getParameterNames(); ae.hasMoreElements();) {
                if (postSB.length() > 0) { postSB.append("&"); }
                String ak = (String)ae.nextElement();
                String av = request.getParameter(ak);
                postSB.append(ak + "=" + av);
            }
            Print.logInfo("[" + ipAddr + "] POST: " + reqURL + " " + queryStr + " [" + postSB + "]");
        } else {
            // For GET requests, all parameters are in query string
            Print.logInfo("[" + ipAddr + "] GET: "  + reqURL + " " + queryStr);
        }

        /** Handle special command requests (only for POST requests) */
        if (isPost) {
            String cmd = AttributeTools.getRequestString(request, PARM_COMMAND, "");
            
            // Version inquiry command
            if (cmd.equalsIgnoreCase("version") || 
                cmd.equalsIgnoreCase("ver")       ) {
                String vers = DEVICE_CODE+"-"+VERSION;
                this.plainTextResponse(response, "OK:version:"+vers);
                return;
            } 
            // Device registration validation command
            else if (cmd.equalsIgnoreCase("mobileid") ||
                     cmd.equalsIgnoreCase("id")         ) {
                String vers = DEVICE_CODE+"-"+VERSION;
                Device device = this.loadDevice(imei, ipAddr);
                if (device != null) {
                    // Device found and validated
                    this.plainTextResponse(response, "OK:ack:"+vers);
                } else {
                    // Device not found or invalid
                    this.plainTextResponse(response, "ERROR:nak:"+vers);
                }
                return;
            }
        }

        /** Process GPS location data and generate response */
        String resp = "";
        try {
            resp = this.parseInsertEvent(ipAddr, imei, gprmc, code);
        } catch (Throwable t) {
            Print.logException("Unexpected Exception", t);
        }

        /** Send response back to device */
        this.plainTextResponse(response, resp);
       
        // Forward to JSP for additional processing/logging (if needed)
        request.getRequestDispatcher("TrackTech.jsp").forward(request, response);
        
    }

    // ------------------------------------------------------------------------
    // GPS Event Processing Methods
    // ------------------------------------------------------------------------

    /**
     * Parses incoming GPS data and inserts events into the database.
     * This is the core method that processes GPRMC format GPS data from tracking devices.
     * 
     * <p>Processing Steps:</p>
     * <ol>
     *   <li>Load and validate device record from IMEI</li>
     *   <li>Parse GPRMC GPS data string</li>
     *   <li>Create EventData record with location and status information</li>
     *   <li>Generate geofence events if enabled and device crosses boundaries</li>
     *   <li>Insert event(s) into database</li>
     *   <li>Update device metadata</li>
     * </ol>
     * 
     * @param ipAddr Source IP address of the GPS device
     * @param imei   Device IMEI identifier (15-digit number)
     * @param gprmc  GPRMC format GPS data string ($GPRMC,time,status,lat,latDir,lon,lonDir,speed,course,date,...)
     * @param code   Status/event code (AUTO, SOS, ALARM1, etc.)
     * @return       Response string to send back to device (OK or ERROR)
     */
    private String parseInsertEvent(String ipAddr, String imei, String gprmc, String code)
    {

        /** Load and validate device record */
        Device device = this.loadDevice(imei, ipAddr);
        if (device == null) {
            // Device not found or validation failed - errors already logged
            return RESPONSE_ERROR;
        }

        /** Check if any location data was provided */
        if (StringTools.isBlank(gprmc) && 
            StringTools.isBlank(code)    ) {
            // No GPS data or status code provided, but device validation was successful
            return RESPONSE_OK;
        }

        /** Parse GPS data and create event record */
        String accountID = device.getAccountID();
        String deviceID  = device.getDeviceID();
        EventData evdb   = this.parseGPRMC(device, gprmc, code);
        
        if (evdb != null) {
            // Successfully parsed GPS data
            GeoPoint geoPoint = evdb.getGeoPoint();
            boolean  validGPS = evdb.isValidGeoPoint();
            int      statCode = evdb.getStatusCode();

            /** Process geofence transitions if enabled */
            int geozoneEvents = 0;
            if (SIMEVENT_GEOZONES && validGPS) {
                long timestamp = evdb.getTimestamp();
                
                // Check for geofence boundary crossings
                java.util.List<Device.GeozoneTransition> zone = device.checkGeozoneTransitions(timestamp, geoPoint);
                if (zone != null) {
                    // Device crossed one or more geofence boundaries
                    // Extract event data for geofence events
                    double hdop      = evdb.getHDOP();
                    int    satCnt    = evdb.getSatelliteCount();
                    double speedKPH  = evdb.getSpeedKPH();
                    double heading   = evdb.getHeading();
                    double odomKM    = evdb.getOdometerKM();
                    double battPct   = evdb.getBatteryLevel();
                    long   gpioInp   = evdb.getInputMask();
                    
                    // Create and insert geofence events
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

            /** Insert the original GPS event */
            // Insert original event unless we generated geofence events and this is just a location update
            if ((geozoneEvents <= 0) || (statCode != StatusCodes.STATUS_LOCATION)) {
                boolean didInsert = device.insertEventData(evdb);
                if (didInsert) {
                    Print.logInfo("Event: " + accountID + "/" + deviceID + " - " + geoPoint);
                }
            }

        }

        /** Save any changes to device record */
        try {
            // Update device fields that may have changed during event processing
            device.updateChangedEventFields();
        } catch (DBException dbe) {
            Print.logException("Unable to update Device: " + 
                device.getAccountID() + "/" + device.getDeviceID(), dbe);
        }
        
        // Return success response to device
        return RESPONSE_OK;

    }
        
    // ------------------------------------------------------------------------
    // Status Code Processing Methods
    // ------------------------------------------------------------------------

    /**
     * Parses and maps device status codes to OpenGTS internal status code constants.
     * GC-101 devices send various status codes that indicate different types of events
     * (location updates, alarms, ignition changes, etc.).
     * 
     * <p>Supported Status Codes:</p>
     * <ul>
     *   <li><strong>AUTO:</strong> Periodic location update</li>
     *   <li><strong>SOS:</strong> Panic button pressed</li>
     *   <li><strong>MOVE:</strong> Device started moving</li>
     *   <li><strong>POLL:</strong> Response to "Locate Now" command</li>
     *   <li><strong>GFIN:</strong> Geofence entry event</li>
     *   <li><strong>GFOUT/GOUT:</strong> Geofence exit event</li>
     *   <li><strong>PARK/UNPARK:</strong> Vehicle parked/unparked</li>
     *   <li><strong>ACCON/ACCOFF:</strong> Ignition on/off</li>
     *   <li><strong>ALARM1-ALARM6:</strong> Various alarm inputs</li>
     *   <li><strong>LP:</strong> Low battery warning</li>
     *   <li><strong>OVERSPEED:</strong> Speed limit exceeded</li>
     *   <li><strong>0xNNNN:</strong> Explicit hex status code</li>
     * </ul>
     * 
     * <p>Special Prefixes:</p>
     * <ul>
     *   <li><strong>B or B-:</strong> Event was stored in device flash memory</li>
     * </ul>
     * 
     * @param evCode  Raw status code string from GPS device
     * @param dftCode Default status code to use if no match is found
     * @return        OpenGTS status code constant corresponding to the device status
     */
    private int parseStatusCode(String evCode, int dftCode)
    {
        // Normalize the status code string
        String code = StringTools.trim(evCode).toUpperCase();

        /** Handle flash storage prefix */
        // "B" prefix indicates event was stored in device flash memory before transmission
        if (code.startsWith("B")) {
            if (code.startsWith("B-")) {
                code = code.substring(2); // Remove "B-" prefix
            } else {
                code = code.substring(1); // Remove "B" prefix
            }
        }
        int codeLen = code.length();

        /** Map status codes to OpenGTS constants */
        int statusCode = dftCode;
        
        if (codeLen == 0) {
            // Empty code - use default
            statusCode = dftCode;
        } else
        if (code.startsWith("0X")) {
            // Explicit hexadecimal status code (0xNNNN format)
            statusCode = StringTools.parseInt(code, dftCode);
        } else
        if (code.equalsIgnoreCase("AUTO")) {
            // Periodic location update (normal tracking)
            statusCode = StatusCodes.STATUS_LOCATION;
        } else 
        if (code.equalsIgnoreCase("SOS")) {
            // Emergency/panic button pressed
            statusCode = StatusCodes.STATUS_WAYMARK_0;
        } else
        if (code.equalsIgnoreCase("MOVE")) {
            // Device started moving (motion detection)
          statusCode = StatusCodes.STATUS_MOTION_MOVING;
        } else 
        if (code.equalsIgnoreCase("POLL")) {
            // Response to server "Locate Now" request
            statusCode = StatusCodes.STATUS_QUERY;
        } else
        if (code.equalsIgnoreCase("GFIN")) {
            // Geofence entry (device entered defined area)
            statusCode = StatusCodes.STATUS_GEOFENCE_ARRIVE;
        } else
        if (code.equals("GFOUT") || code.equals("GOUT")) {
            // Geofence exit (device left defined area)
            statusCode = StatusCodes.STATUS_GEOFENCE_DEPART;
        } else
        if (code.equalsIgnoreCase("PARK")) {
            // Vehicle parked (stopped and ignition off)
            statusCode = StatusCodes.STATUS_PARKED;
        } else
        if (code.equals("UNPARK") || code.equals("UNPA")) {
            // Vehicle unparked (ignition on, ready to move)
            statusCode = StatusCodes.STATUS_UNPARKED;
        } else
        if (code.equals("START")) {
            // Device startup/initialization
            statusCode = StatusCodes.STATUS_LOCATION;
        } else
        if (code.equals("ACCON")) {
            // Accessory/ignition turned on
            statusCode = StatusCodes.STATUS_IGNITION_ON;
        } else
        if (code.equals("ACCOFF")) {
            // Accessory/ignition turned off
            statusCode = StatusCodes.STATUS_IGNITION_OFF;
        } else
        if (code.equalsIgnoreCase("LP")) {
            // Low power/battery warning
            statusCode = StatusCodes.STATUS_LOW_BATTERY;
        } else
        if (code.equals("DC")) {
            // Power disconnected/lost
            statusCode = StatusCodes.STATUS_POWER_FAILURE;
        } else
        if (code.equals("CH")) {
            // Battery charging detected
            statusCode = StatusCodes.STATUS_POWER_RESTORED;
        } else
        if (code.equals("OPEN")) { 
            // Digital input opened (normally open switch activated)
            statusCode = StatusCodes.InputStatusCodes_ON[0];
        } else
        if (code.equals("CLOSE")) { 
            // Digital input closed (normally open switch deactivated)
            statusCode = StatusCodes.InputStatusCodes_OFF[0];
        } else
        if (code.startsWith("ALARM") && (codeLen >= 6)) { 
            // Alarm input triggered (ALARM1 through ALARM6)
            int ndx = (code.charAt(5) - '0'); // Extract alarm number (1-6)
            if ((ndx >= 0) && (ndx <= 9) && (ndx < StatusCodes.InputStatusCodes_ON.length)) {
                // Valid alarm number - use specific input status code
                statusCode = StatusCodes.InputStatusCodes_ON[ndx];
            } else {
                // Invalid alarm number - use generic input status
                statusCode = StatusCodes.STATUS_INPUT_ON;
            }
        } else
        if (code.equals("STATIONARY")) {
            // Device is not moving (motion sensor inactive)
            statusCode = StatusCodes.STATUS_MOTION_DORMANT;
        } else
        if (code.equals("VIBRATION")) {
            // Vibration/shock detected
            statusCode = StatusCodes.STATUS_LOCATION;
        } else 
        if (code.equals("OVERSPEED")) {
            // Speed limit exceeded
            statusCode = StatusCodes.STATUS_MOTION_EXCESS_SPEED;
        } else 
        {
            // Unknown status code (could be barcode data for GS-818 devices)
            statusCode = dftCode;
        }
        
        return statusCode;

    }

    // ------------------------------------------------------------------------
    // GPRMC Data Parsing Methods
    // ------------------------------------------------------------------------

    /**
     * Parses GPRMC (Global Positioning Recommended Minimum) GPS data format.
     * GPRMC is a standard NMEA sentence that contains essential GPS information.
     * 
     * <p>GPRMC Format:</p>
     * <code>$GPRMC,time,status,latitude,lat_dir,longitude,lon_dir,speed,course,date,mag_var,var_dir*checksum</code>
     * 
     * <p>Field Breakdown:</p>
     * <ul>
     *   <li><strong>time:</strong> UTC time in HHMMSS.SSS format</li>
     *   <li><strong>status:</strong> A=Active (valid), V=Void (invalid)</li>
     *   <li><strong>latitude:</strong> DDMM.MMMM format</li>
     *   <li><strong>lat_dir:</strong> N=North, S=South</li>
     *   <li><strong>longitude:</strong> DDDMM.MMMM format</li>
     *   <li><strong>lon_dir:</strong> E=East, W=West</li>
     *   <li><strong>speed:</strong> Speed over ground in knots</li>
     *   <li><strong>course:</strong> Course over ground in degrees</li>
     *   <li><strong>date:</strong> Date in DDMMYY format</li>
     * </ul>
     * 
     * <p>Extended GC-101 Format may include:</p>
     * <ul>
     *   <li>Battery voltage in mV</li>
     *   <li>GPIO input status</li>
     *   <li>HDOP (Horizontal Dilution of Precision)</li>
     *   <li>Satellite count</li>
     * </ul>
     * 
     * @param dev   Device record for context and configuration
     * @param data  Raw GPRMC data string from GPS device
     * @param code  Status/event code associated with this GPS fix
     * @return      EventData record ready for database insertion, or null if parsing fails
     */
    private EventData parseGPRMC(Device dev, String data, String code)
    {
        // Split GPRMC string into comma-separated fields
        String fld[] = StringTools.parseString(data, ',');

        /** Validate GPRMC format */
        if ((fld == null) || (fld.length < 1) || !fld[0].equals("$GPRMC")) {
            Print.logWarn("Not a $GPRMC record: " + data);
            return null;
        } else
        if (fld.length < 10) {
            Print.logWarn("Invalid number of $GPRMC fields: " + data);
            return null;
        }

        /** Parse basic GPRMC fields */
        long    hms        = StringTools.parseLong(fld[1], 0L);       // Time (HHMMSS)
        long    dmy        = StringTools.parseLong(fld[9], 0L);       // Date (DDMMYY)
        long    fixtime    = this._getUTCSeconds(dmy, hms);           // Convert to UTC timestamp
        boolean isValid    = fld[2].equals("A");                      // GPS fix validity (A=Active, V=Void)
        double  latitude   = isValid? this._parseLatitude (fld[3], fld[4]) : 0.0;  // Latitude in decimal degrees
        double  longitude  = isValid? this._parseLongitude(fld[5], fld[6]) : 0.0;  // Longitude in decimal degrees
        double  knots      = isValid? StringTools.parseDouble(fld[7], 0.0) : 0.0;  // Speed in knots
        double  heading    = isValid? StringTools.parseDouble(fld[8], 0.0) : 0.0;  // Course in degrees
        double  speedKPH   = (knots > 0.0)? (knots * KILOMETERS_PER_KNOT)  : 0.0;  // Convert to km/h
        int     statusCode = this.parseStatusCode(code, StatusCodes.STATUS_LOCATION); // Map device status
        
        // Initialize extended data fields
        double  batteryV   = 0.0;   // Battery voltage in volts
        long    gpioInput  = -1L;   // GPIO input state bitmask
        double  HDOP       = 0.0;   // Horizontal dilution of precision
        int     numSats    = 0;     // Number of satellites used

        /** Validate GPS coordinates */
        if (!GeoPoint.isValid(latitude,longitude)) {
            Print.logWarn("Invalid GPRMC lat/lon: " + latitude + "/" + longitude);
            latitude  = 0.0;
            longitude = 0.0;
            isValid   = false;
        }
        GeoPoint geoPoint = new GeoPoint(latitude, longitude);

        /** Parse extended GC-101 specific data fields */
        // Determine where extended data starts (after checksum field)
        int exd = ((fld.length > 11) && (fld[11].indexOf('*') >= 0))? 12 : 
                  ((fld.length > 12) && (fld[12].indexOf('*') >= 0))? 13 : 12;
        
        String extra0 = (fld.length > (exd+0))? fld[exd+0] : "";  // First extended field
        String extra1 = (fld.length > (exd+1))? fld[exd+1] : "";  // Second extended field  
        String extra2 = (fld.length > (exd+2))? fld[exd+2] : "";  // Third extended field
        String extra3 = (fld.length > (exd+3))? fld[exd+3] : "";  // Fourth extended field
        
        // Parse extended data based on format variations:
        // Format 1: "3722mV,VIBRATION,..." (voltage first, then status)
        // Format 2: "AUTO-3893mv" (status with embedded voltage)
        if ((extra0.length() > 0) && Character.isDigit(extra0.charAt(0))) {
            // Format 1: Voltage value comes first
            batteryV    = StringTools.parseDouble(extra0,0.0) / 1000.0; // Convert mV to V
            statusCode  = this.parseStatusCode(extra1, statusCode);     // Status in second field
        } else {
            // Format 2: Status code with optional embedded voltage
            int ep = extra0.indexOf('-');
            String stat = (ep >= 0)? extra0.substring(0,ep) : extra0;  // Status part
            String batt = (ep >= 0)? extra0.substring(ep+1) : null;    // Voltage part (if present)
            statusCode  = this.parseStatusCode(stat, statusCode);
            batteryV    = StringTools.parseDouble(batt,0.0) / 1000.0;  // Convert mV to V
            
            // Additional extended fields
            gpioInput   = StringTools.parseHexLong(extra1, -1L);       // GPIO state as hex
            HDOP        = StringTools.parseDouble( extra2,0.0);        // Horizontal DOP
            numSats     = StringTools.parseInt(    extra3,  0);        // Satellite count
        }

        /** Apply status code filters */
        if (statusCode == StatusCodes.STATUS_IGNORE) {
            // Event marked for ignoring
            return null;
        } else
        if (statusCode == StatusCodes.STATUS_NONE) {
            // No event to process
            return null;
        } else
        if ((statusCode == StatusCodes.STATUS_LOCATION) && !isValid) {
            // Location event with invalid GPS fix - ignore
            Print.logWarn("Ignoring event with invalid GPS fix");
            return null;
        }

        /** Calculate battery level percentage */
        double batteryLvl = CalcBatteryPercent(batteryV);

        /** Apply minimum speed filter */
        // Filter out GPS noise when stationary
        if (speedKPH < MinimumReqSpeedKPH) {
            speedKPH = 0.0;  // Treat as stopped
            heading  = 0.0;  // No meaningful heading when stopped
        }

        /** Calculate odometer value */
        // Use GPS-based estimation if enabled, otherwise use last known value
        double odomKM = (ESTIMATE_ODOMETER && isValid)? 
            dev.getNextOdometerKM(geoPoint) :     // Calculate distance from last position
            dev.getLastOdometerKM();              // Use existing odometer value

        /** Create and populate EventData record */
        String acctID = dev.getAccountID();
        String devID  = dev.getDeviceID();
        EventData.Key evKey = new EventData.Key(acctID, devID, fixtime, statusCode);
        EventData evdb = evKey.getDBRecord();
        
        // Set location and GPS quality data
        evdb.setGeoPoint(geoPoint);
        evdb.setHDOP(HDOP);
        evdb.setSatelliteCount(numSats);
        
        // Set motion data
        evdb.setSpeedKPH(speedKPH);
        evdb.setHeading(heading);
        evdb.setOdometerKM(odomKM);
        
        // Set power and I/O data
        evdb.setBatteryLevel(batteryLvl);
        if (gpioInput >= 0L) {
            evdb.setInputMask(gpioInput);
        }
        
        return evdb;

    }
    // ------------------------------------------------------------------------
    // Date/Time and Coordinate Utility Methods
    // ------------------------------------------------------------------------
    
    /**
     * Converts GPRMC date and time fields to UTC seconds since epoch.
     * Handles both complete date/time and time-only scenarios.
     * 
     * @param dmy Date in DDMMYY format (or 0 if not available)
     * @param hms Time in HHMMSS format
     * @return UTC timestamp in seconds since Unix epoch (January 1, 1970)
     */
    private long _getUTCSeconds(long dmy, long hms)
    {
    
        /** Extract time components (always available) */
        int    HH  = (int)((hms / 10000L) % 100L);  // Hours (00-23)
        int    MM  = (int)((hms / 100L) % 100L);    // Minutes (00-59)
        int    SS  = (int)(hms % 100L);             // Seconds (00-59)
        long   TOD = (HH * 3600L) + (MM * 60L) + SS; // Time of day in seconds
    
        /** Calculate date component */
        long DAY;
        if (dmy > 0L) {
            // Date provided in GPRMC - parse DDMMYY format
            int    yy  = (int)(dmy % 100L) + 2000;        // Year (add 2000 for Y2K)
            int    mm  = (int)((dmy / 100L) % 100L);      // Month (01-12)
            int    dd  = (int)((dmy / 10000L) % 100L);    // Day (01-31)
            
            // Calculate days since epoch using astronomical formula
            long   yr  = ((long)yy * 1000L) + (long)(((mm - 3) * 1000) / 12);
            DAY        = ((367L * yr + 625L) / 1000L) - (2L * (yr / 1000L))
                         + (yr / 4000L) - (yr / 100000L) + (yr / 400000L)
                         + (long)dd - 719469L;
        } else {
            // No date provided - estimate based on current time
            // This handles cases where GPS device only sends time updates
            long   utc = DateTime.getCurrentTimeSec();
            long   tod = utc % DateTime.DaySeconds(1);      // Current time of day
            DAY        = utc / DateTime.DaySeconds(1);      // Current day number
            long   dif = (tod >= TOD)? (tod - TOD) : (TOD - tod); // Time difference
            
            if (dif > DateTime.HourSeconds(12)) { 
                // Large time difference suggests day boundary crossing
                if (tod > TOD) {
                    // Current time > GPS time suggests GPS time is tomorrow
                    DAY++;
                } else {
                    // Current time < GPS time suggests GPS time was yesterday
                    DAY--;
                }
            }
        }
        
        /** Combine date and time components */
        long sec = DateTime.DaySeconds(DAY) + TOD;
        return sec;
        
    }

    /**
     * Parses GPRMC latitude field and converts to decimal degrees.
     * GPRMC latitude format: DDMM.MMMM (degrees and decimal minutes)
     * 
     * @param s Latitude string in DDMM.MMMM format
     * @param d Direction character ('N' for North, 'S' for South)
     * @return Latitude in decimal degrees (positive for North, negative for South)
     */
    private double _parseLatitude(String s, String d)
    {
        double _lat = StringTools.parseDouble(s, 99999.0);
        if (_lat < 99999.0) {
            // Convert from DDMM.MMMM to decimal degrees
            double lat = (double)((long)_lat / 100L); // Extract degrees part
            lat += (_lat - (lat * 100.0)) / 60.0;     // Add minutes converted to degrees
            return d.equals("S")? -lat : lat;         // Apply hemisphere sign
        } else {
            return 90.0; // Return invalid latitude indicator
        }
    }
    
    /**
     * Parses GPRMC longitude field and converts to decimal degrees.
     * GPRMC longitude format: DDDMM.MMMM (degrees and decimal minutes)
     * 
     * @param s Longitude string in DDDMM.MMMM format  
     * @param d Direction character ('E' for East, 'W' for West)
     * @return Longitude in decimal degrees (positive for East, negative for West)
     */
    private double _parseLongitude(String s, String d)
    {
        double _lon = StringTools.parseDouble(s, 99999.0);
        if (_lon < 99999.0) {
            // Convert from DDDMM.MMMM to decimal degrees
            double lon = (double)((long)_lon / 100L); // Extract degrees part
            lon += (_lon - (lon * 100.0)) / 60.0;     // Add minutes converted to degrees
            return d.equals("W")? -lon : lon;         // Apply hemisphere sign
        } else {
            return 180.0; // Return invalid longitude indicator
        }
    }

    // ------------------------------------------------------------------------
    // End of ControllerData Class
    // ------------------------------------------------------------------------

}
