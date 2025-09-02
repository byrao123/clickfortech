#!/bin/bash
# Simple test runner script for ClickForTech TestNG tests

echo "ClickForTech TestNG Test Runner"
echo "================================"

# Create build directories
mkdir -p build/test-classes
mkdir -p build/test-results

echo "Compiling test classes..."

# Compile ControllerDataUtilTest
javac -cp "lib/testng-7.8.0.jar" -d build/test-classes tests/com/click4tech/ControllerDataUtilTest.java
if [ $? -ne 0 ]; then
    echo "Compilation failed for ControllerDataUtilTest!"
    exit 1
fi

# Compile ControllerDataServletTest (no external dependencies needed now)
javac -cp "lib/testng-7.8.0.jar" -d build/test-classes tests/com/click4tech/ControllerDataServletTest.java
if [ $? -ne 0 ]; then
    echo "Compilation failed for ControllerDataServletTest!"
    exit 1
fi

# Compile RunTestTest
# First compile the source RunTest class if needed
mkdir -p build/classes
if [ ! -f "build/classes/test/selenium/test/RunTest.class" ]; then
    echo "Compiling source RunTest class..."
    javac -d build/classes src/java/test/selenium/test/RunTest.java
    if [ $? -ne 0 ]; then
        echo "Compilation failed for RunTest source class!"
        exit 1
    fi
fi

# Add the compiled classes to classpath for test compilation
javac -cp "lib/testng-7.8.0.jar:build/classes" -d build/test-classes tests/test/selenium/test/RunTestTest.java
if [ $? -ne 0 ]; then
    echo "Compilation failed for RunTestTest!"
    exit 1
fi

echo "Running TestNG tests..."
# Update classpath to include all required libraries
FULL_CLASSPATH="lib/testng-7.8.0.jar:lib/jcommander-1.82.jar:lib/slf4j-api-1.7.36.jar:lib/slf4j-simple-1.7.36.jar:build/test-classes:build/classes"
java -cp "$FULL_CLASSPATH" org.testng.TestNG tests/testng.xml -d build/test-results

echo ""
echo "Test results saved to build/test-results/"
echo "Open build/test-results/index.html in a browser to view detailed results."