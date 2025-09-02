# ClickForTech TestNG Tests

This directory contains TestNG test cases for the ClickForTech project.

## Test Classes

### ControllerDataUtilTest
Comprehensive TestNG tests for the ControllerData class utility methods, including:

- **Battery Percentage Calculation Tests**: Tests the voltage-to-percentage conversion logic
- **GPS Coordinate Parsing Tests**: Tests latitude and longitude parsing from GPS strings
- **Date/Time Parsing Tests**: Tests conversion of GPS date/time formats
- **String Parsing Tests**: Tests general string parsing functionality  
- **Status Code Prefix Handling Tests**: Tests handling of buffered status code prefixes
- **Constants Tests**: Validates important constant values

## Running Tests

### Using the Test Runner Script
```bash
./run-tests.sh
```

### Manual Execution
```bash
# Compile tests
javac -cp "lib/testng-7.8.0.jar" -d build/test-classes tests/com/click4tech/ControllerDataUtilTest.java

# Run tests
java -cp "lib/testng-7.8.0.jar:lib/jcommander-1.82.jar:lib/slf4j-api-1.7.36.jar:lib/slf4j-simple-1.7.36.jar:build/test-classes" org.testng.TestNG tests/testng.xml -d build/test-results
```

## Test Results

Test results are generated in `build/test-results/`:
- `index.html` - Main test report
- `emailable-report.html` - Detailed test report
- `testng-results.xml` - XML test results

## Dependencies

The following JAR files are required and included in the `lib/` directory:
- `testng-7.8.0.jar` - TestNG framework
- `jcommander-1.82.jar` - Command line parsing for TestNG
- `slf4j-api-1.7.36.jar` - Logging API
- `slf4j-simple-1.7.36.jar` - Simple logging implementation

## Test Coverage

Current test coverage includes:
- 43 test methods across 9 test scenarios
- Data-driven tests using TestNG @DataProvider
- Edge case testing for invalid inputs
- Precision testing for mathematical calculations

## Notes

The tests focus on utility methods and parsing logic from the ControllerData class that can be tested independently without requiring the full OpenGTS framework dependencies.