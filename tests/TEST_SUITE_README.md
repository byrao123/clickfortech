# ClickForTech Test Suite

This directory contains comprehensive TestNG test cases for the ClickForTech GPS tracking system.

## Test Classes

### 1. ControllerDataUtilTest.java
Tests for utility methods and parsing logic in ControllerData class:
- Battery percentage calculations
- GPS coordinate parsing and validation
- GPRMC record validation
- Speed calculations and minimum speed handling
- Status code parsing patterns
- Time and date parsing
- String parsing functionality
- Latitude/longitude conversion
- IMEI validation patterns
- Constants verification

**Test Count:** 77 tests with extensive data-driven testing

### 2. ControllerDataServletTest.java
Tests for servlet functionality patterns in ControllerData class:
- HTTP request/response patterns
- Command parsing (version, mobile ID)
- Parameter extraction and validation
- URL parameter encoding
- IP address validation
- Request logging formats
- Response constants
- HTTP method handling
- Unique ID generation

**Test Count:** 65 tests covering servlet interaction patterns

### 3. RunTestTest.java
Tests for the RunTest selenium test class:
- Class instantiation and structure
- Java conventions compliance
- Object equality and hash code behavior
- Package structure validation
- Multiple instantiation handling

**Test Count:** 7 tests for basic class functionality

## Running Tests

### Using the Test Script
```bash
./run-tests.sh
```

### Using Ant
```bash
ant test
```

### Manual Execution
```bash
# Compile tests
javac -cp "lib/testng-7.8.0.jar" -d build/test-classes tests/com/click4tech/*.java

# Run tests
java -cp "lib/testng-7.8.0.jar:lib/jcommander-1.82.jar:build/test-classes" org.testng.TestNG tests/testng.xml
```

## Test Results

- **Total Tests:** 149
- **Passing:** 149 
- **Failing:** 0
- **Skipped:** 0

Results are saved to `build/test-results/` directory with detailed HTML reports.

## Test Coverage

The test suite provides comprehensive coverage for:
- ✅ Utility method logic and calculations
- ✅ GPS data parsing and validation
- ✅ HTTP servlet request/response handling patterns
- ✅ Parameter validation and extraction
- ✅ Error handling and edge cases
- ✅ Data format conversions
- ✅ String parsing and validation
- ✅ Class structure and instantiation

## Dependencies

- TestNG 7.8.0 (testing framework)
- JCommander 1.82 (TestNG dependency)
- SLF4J 1.7.36 (logging)
- Java 17+ (runtime)

## Test Structure

Tests follow TestNG conventions with:
- `@Test` annotations for test methods
- `@DataProvider` for parameterized testing
- `@BeforeMethod/@AfterMethod` for setup/cleanup
- Descriptive test names and assertions
- Comprehensive edge case coverage