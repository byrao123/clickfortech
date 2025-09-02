#!/bin/bash
# Simple test runner script for ClickForTech TestNG tests

echo "ClickForTech TestNG Test Runner"
echo "================================"

# Set up classpath
CLASSPATH="lib/testng-7.8.0.jar:lib/jcommander-1.82.jar:lib/slf4j-api-1.7.36.jar:lib/slf4j-simple-1.7.36.jar:build/test-classes"

# Create build directories
mkdir -p build/test-classes
mkdir -p build/test-results

echo "Compiling test classes..."
javac -cp "lib/testng-7.8.0.jar" -d build/test-classes tests/com/click4tech/ControllerDataUtilTest.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Running TestNG tests..."
java -cp "$CLASSPATH" org.testng.TestNG tests/testng.xml -d build/test-results

echo ""
echo "Test results saved to build/test-results/"
echo "Open build/test-results/index.html in a browser to view detailed results."