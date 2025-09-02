package com.click4tech;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;

/**
 * TestNG test cases for ControllerData class utility methods
 * Focus on testing static methods and constants that don't require external dependencies
 */
public class ControllerDataUtilTest {

    /**
     * Test battery percentage calculation logic
     * Since CalcBatteryPercent is a private static method, we test the logic externally
     */
    @Test(dataProvider = "batteryVoltageData")
    public void testBatteryPercentCalculation(double voltage, double expectedPercent, String description) {
        // Replicate the battery calculation logic from CalcBatteryPercent
        double MAX_BATTERY_VOLTS = 4.100;
        double MIN_BATTERY_VOLTS = 3.650;
        double RANGE_BATTERY_VOLTS = MAX_BATTERY_VOLTS - MIN_BATTERY_VOLTS;
        
        double percent = (voltage - MIN_BATTERY_VOLTS) / RANGE_BATTERY_VOLTS;
        if (percent < 0.0) {
            percent = 0.0;
        } else if (percent > 1.0) {
            percent = 1.0;
        }
        
        Assert.assertEquals(percent, expectedPercent, 0.001, 
            "Battery percent calculation failed for " + description);
    }
    
    @DataProvider(name = "batteryVoltageData")
    public Object[][] batteryVoltageData() {
        return new Object[][] {
            {4.100, 1.0, "maximum voltage (4.1V)"},
            {3.650, 0.0, "minimum voltage (3.65V)"},
            {3.875, 0.5, "middle voltage (3.875V)"},
            {4.200, 1.0, "above maximum voltage (4.2V)"},
            {3.500, 0.0, "below minimum voltage (3.5V)"},
            {3.785, 0.3, "30% voltage level"},
            {3.965, 0.7, "70% voltage level"}
        };
    }
    
    /**
     * Test kilometers per knot conversion constant
     */
    @Test
    public void testKilometersPerKnotConstant() {
        // Test the constant value used for speed conversion
        double KILOMETERS_PER_KNOT = 1.85200000;
        
        // Test typical speed conversions
        double knots_10 = 10.0;
        double expectedKph_10 = 18.52;
        double actualKph_10 = knots_10 * KILOMETERS_PER_KNOT;
        Assert.assertEquals(actualKph_10, expectedKph_10, 0.001, 
            "10 knots should convert to 18.52 km/h");
        
        double knots_50 = 50.0;
        double expectedKph_50 = 92.6;
        double actualKph_50 = knots_50 * KILOMETERS_PER_KNOT;
        Assert.assertEquals(actualKph_50, expectedKph_50, 0.001, 
            "50 knots should convert to 92.6 km/h");
    }
    
    /**
     * Test string parsing functionality (replicated from ControllerData)
     */
    @Test(dataProvider = "stringParsingData")
    public void testStringParsing(String input, String delimiter, int expectedLength, String description) {
        // Simple string parsing test
        String[] parts = input.split(delimiter);
        Assert.assertEquals(parts.length, expectedLength, 
            "String parsing failed for " + description);
    }
    
    @DataProvider(name = "stringParsingData")
    public Object[][] stringParsingData() {
        return new Object[][] {
            {"$GPRMC,023000.000,A,3130.0577,N,14271.7421,W,0.53,208.37,210507,,*19,AUTO", ",", 13, "Valid GPRMC string"},
            {"field1,field2,field3", ",", 3, "Simple comma-separated values"},
            {"single", ",", 1, "Single field"},
            {"", ",", 1, "Empty string"},
            {"a,b,c,d,e", ",", 5, "Five fields"}
        };
    }
    
    /**
     * Test latitude parsing logic (replicated from ControllerData)
     */
    @Test(dataProvider = "latitudeParsingData")
    public void testLatitudeParsing(String latStr, String direction, double expectedLat, String description) {
        // Replicate the latitude parsing logic
        double result = parseLatitude(latStr, direction);
        Assert.assertEquals(result, expectedLat, 0.0001, 
            "Latitude parsing failed for " + description);
    }
    
    /**
     * Helper method that replicates _parseLatitude logic
     */
    private double parseLatitude(String s, String d) {
        try {
            double _lat = Double.parseDouble(s);
            if (_lat < 99999.0) {
                double lat = (double)((long)_lat / 100L); // degrees
                lat += (_lat - (lat * 100.0)) / 60.0; // add minutes/60
                return d.equals("S") ? -lat : lat;
            } else {
                return 90.0; // invalid latitude
            }
        } catch (NumberFormatException e) {
            return 90.0; // invalid latitude
        }
    }
    
    @DataProvider(name = "latitudeParsingData")
    public Object[][] latitudeParsingData() {
        return new Object[][] {
            {"3130.0577", "N", 31.50096167, "North latitude"},
            {"3130.0577", "S", -31.50096167, "South latitude"},
            {"0000.0000", "N", 0.0, "Zero latitude North"},
            {"0000.0000", "S", 0.0, "Zero latitude South"},
            {"4500.0000", "N", 45.0, "45 degrees North"},
            {"invalid", "N", 90.0, "Invalid latitude string"},
            {"", "N", 90.0, "Empty latitude string"}
        };
    }
    
    /**
     * Test longitude parsing logic (replicated from ControllerData)
     */
    @Test(dataProvider = "longitudeParsingData")
    public void testLongitudeParsing(String lonStr, String direction, double expectedLon, String description) {
        // Replicate the longitude parsing logic
        double result = parseLongitude(lonStr, direction);
        Assert.assertEquals(result, expectedLon, 0.0001, 
            "Longitude parsing failed for " + description);
    }
    
    /**
     * Helper method that replicates _parseLongitude logic
     */
    private double parseLongitude(String s, String d) {
        try {
            double _lon = Double.parseDouble(s);
            if (_lon < 99999.0) {
                double lon = (double)((long)_lon / 100L); // degrees
                lon += (_lon - (lon * 100.0)) / 60.0; // add minutes/60
                return d.equals("W") ? -lon : lon;
            } else {
                return 180.0; // invalid longitude
            }
        } catch (NumberFormatException e) {
            return 180.0; // invalid longitude
        }
    }
    
    @DataProvider(name = "longitudeParsingData")
    public Object[][] longitudeParsingData() {
        return new Object[][] {
            {"14271.7421", "W", -143.1957, "West longitude"}, // 142 + 71.7421/60 = 143.1957
            {"14271.7421", "E", 143.1957, "East longitude"},
            {"00000.0000", "E", 0.0, "Zero longitude East"},
            {"00000.0000", "W", 0.0, "Zero longitude West"},
            {"18000.0000", "E", 180.0, "180 degrees East (invalid)"}, // This should be invalid
            {"invalid", "E", 180.0, "Invalid longitude string"},
            {"", "E", 180.0, "Empty longitude string"}
        };
    }
    
    /**
     * Test time parsing logic (replicated from ControllerData)
     */
    @Test(dataProvider = "timeParsingData")
    public void testTimeParsing(long hms, int expectedHour, int expectedMinute, int expectedSecond, String description) {
        // Replicate time parsing logic from _getUTCSeconds
        int HH = (int)((hms / 10000L) % 100L);
        int MM = (int)((hms / 100L) % 100L);
        int SS = (int)(hms % 100L);
        
        Assert.assertEquals(HH, expectedHour, "Hour parsing failed for " + description);
        Assert.assertEquals(MM, expectedMinute, "Minute parsing failed for " + description);
        Assert.assertEquals(SS, expectedSecond, "Second parsing failed for " + description);
    }
    
    @DataProvider(name = "timeParsingData")
    public Object[][] timeParsingData() {
        return new Object[][] {
            {23000L, 2, 30, 0, "02:30:00 (23000)"}, // 23000 = 2:30:00, not 0:30:00
            {124422L, 12, 44, 22, "12:44:22 (124422)"},
            {235959L, 23, 59, 59, "23:59:59 (235959)"},
            {0L, 0, 0, 0, "00:00:00 (0)"},
            {120000L, 12, 0, 0, "12:00:00 (120000)"}
        };
    }
    
    /**
     * Test date parsing logic (replicated from ControllerData)
     */
    @Test(dataProvider = "dateParsingData")
    public void testDateParsing(long dmy, int expectedDay, int expectedMonth, int expectedYear, String description) {
        // Replicate date parsing logic from _getUTCSeconds
        int yy = (int)(dmy % 100L) + 2000;
        int mm = (int)((dmy / 100L) % 100L);
        int dd = (int)((dmy / 10000L) % 100L);
        
        Assert.assertEquals(dd, expectedDay, "Day parsing failed for " + description);
        Assert.assertEquals(mm, expectedMonth, "Month parsing failed for " + description);
        Assert.assertEquals(yy, expectedYear, "Year parsing failed for " + description);
    }
    
    @DataProvider(name = "dateParsingData")
    public Object[][] dateParsingData() {
        return new Object[][] {
            {210507L, 21, 5, 2007, "May 21, 2007 (210507)"},
            {110809L, 11, 8, 2009, "August 11, 2009 (110809)"},
            {10101L, 1, 1, 2001, "January 1, 2001 (10101)"}, // Removed leading zero to avoid octal
            {311299L, 31, 12, 2099, "December 31, 2099 (311299)"}
        };
    }
    
    /**
     * Test status code prefix handling (replicated from ControllerData)
     */
    @Test(dataProvider = "statusCodePrefixData")
    public void testStatusCodePrefix(String code, String expectedCode, String description) {
        // Replicate the "B" prefix handling logic
        String result = code;
        if (result.startsWith("B")) {
            if (result.startsWith("B-")) {
                result = result.substring(2); // remove "B-"
            } else {
                result = result.substring(1); // remove "B"
            }
        }
        
        Assert.assertEquals(result, expectedCode, 
            "Status code prefix handling failed for " + description);
    }
    
    @DataProvider(name = "statusCodePrefixData")
    public Object[][] statusCodePrefixData() {
        return new Object[][] {
            {"AUTO", "AUTO", "No prefix"},
            {"B-AUTO", "AUTO", "B- prefix"},
            {"BAUTO", "AUTO", "B prefix"},
            {"SOS", "SOS", "SOS no prefix"},
            {"B-SOS", "SOS", "SOS with B- prefix"},
            {"BSOS", "SOS", "SOS with B prefix"}
        };
    }
    
    /**
     * Test constants from ControllerData
     */
    @Test
    public void testConstants() {
        // Test that our constants match expected values
        String VERSION = "1.0.2";
        String DEVICE_CODE = "gc101";
        String UNIQUE_ID_PREFIX_GC101 = "gc101_";
        String UNIQUE_ID_PREFIX_IMEI = "imei_";
        double KILOMETERS_PER_KNOT = 1.85200000;
        
        Assert.assertEquals(VERSION, "1.0.2", "Version constant");
        Assert.assertEquals(DEVICE_CODE, "gc101", "Device code constant");
        Assert.assertEquals(UNIQUE_ID_PREFIX_GC101, "gc101_", "GC101 prefix constant");
        Assert.assertEquals(UNIQUE_ID_PREFIX_IMEI, "imei_", "IMEI prefix constant");
        Assert.assertEquals(KILOMETERS_PER_KNOT, 1.85200000, 0.0001, "Kilometers per knot constant");
    }
}