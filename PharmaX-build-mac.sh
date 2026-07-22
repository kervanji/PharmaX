#!/bin/bash
# PharmaX Distribution Build Script for macOS
# Creates a complete distributable package with a custom Java runtime (JRE) using jlink
# Output: ./distribution-mac/

set -e # Exit on error

# -----------------------------
# Configuration
# -----------------------------
APP_NAME="PharmaX"
APP_VERSION="1.3.3"
JAVAFX_VERSION="17.0.10"
MAIN_CLASS="com.pharmax.MainApp"

# -----------------------------
# Directories
# -----------------------------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
DIST_DIR="$PROJECT_DIR/distribution-mac"
RUNTIME_DIR="$DIST_DIR/runtime"
JAVAFX_JMODS_ROOT="$PROJECT_DIR/javafx-jmods-$JAVAFX_VERSION"

echo -e "\033[36m========================================\033[0m"
echo -e "\033[36mBuilding $APP_NAME Distribution Package (macOS)\033[0m"
echo -e "\033[36m========================================\033[0m"

# -----------------------------
# Step 1: Clean previous builds
# -----------------------------
echo -e "\n\033[33m[1/6] Cleaning previous builds...\033[0m"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# -----------------------------
# Step 2: Build the application with Maven
# -----------------------------
echo -e "\n\033[33m[2/6] Building application with Maven...\033[0m"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "\033[31mMaven build failed!\033[0m"
    exit 1
fi

# -----------------------------
# Step 3: Locate JavaFX jmods folder
# -----------------------------
echo -e "\n\033[33m[3/6] Checking JavaFX jmods...\033[0m"
if [ ! -d "$JAVAFX_JMODS_ROOT" ]; then
    echo -e "\033[31mJavaFX jmods folder not found!\033[0m"
    echo -e "\033[33mExpected folder: $JAVAFX_JMODS_ROOT\033[0m"
    exit 1
fi

# Find a directory that contains *.jmod
JMOD_FILE=$(find "$JAVAFX_JMODS_ROOT" -name "*.jmod" | head -n 1)
if [ -z "$JMOD_FILE" ]; then
    echo -e "\033[31mCould not find any *.jmod files under: $JAVAFX_JMODS_ROOT\033[0m"
    exit 1
fi
JAVAFX_JMODS_DIR=$(dirname "$JMOD_FILE")
echo -e "\033[32mJavaFX jmods detected at: $JAVAFX_JMODS_DIR\033[0m"

# -----------------------------
# Step 4: Create custom runtime with jlink
# -----------------------------
echo -e "\n\033[33m[4/6] Creating custom Java runtime...\033[0m"

if [ -z "$JAVA_HOME" ]; then
    # Try to find default java home on mac
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
    if [ -z "$JAVA_HOME" ]; then
        echo -e "\033[31mJAVA_HOME is not set and JDK 17 not found.\033[0m"
        exit 1
    fi
    echo "Found JAVA_HOME: $JAVA_HOME"
fi

JLINK="$JAVA_HOME/bin/jlink"
if [ ! -f "$JLINK" ]; then
    echo -e "\033[31mjlink not found under JAVA_HOME. Expected: $JLINK\033[0m"
    exit 1
fi

MODULE_PATH="$JAVA_HOME/jmods:$JAVAFX_JMODS_DIR"
MODULES="java.base,java.desktop,java.sql,java.naming,java.xml,java.logging,java.management,java.instrument,java.prefs,java.net.http,java.security.jgss,jdk.crypto.ec,jdk.unsupported,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing"

"$JLINK" --module-path "$MODULE_PATH" \
         --add-modules "$MODULES" \
         --output "$RUNTIME_DIR" \
         --strip-debug \
         --no-header-files \
         --no-man-pages \
         --compress=2

if [ $? -ne 0 ]; then
    echo -e "\033[31mjlink failed!\033[0m"
    exit 1
fi

# -----------------------------
# Step 5: Copy application files
# -----------------------------
echo -e "\n\033[33m[5/6] Copying application files...\033[0m"

# Prefer shaded jar
JAR_TO_COPY=$(ls -t "$TARGET_DIR"/inventory-management-*-shaded.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_TO_COPY" ]; then
    JAR_TO_COPY=$(ls -t "$TARGET_DIR"/inventory-management-*.jar 2>/dev/null | grep -v "shaded.jar" | head -n 1)
fi

if [ -n "$JAR_TO_COPY" ]; then
    cp "$JAR_TO_COPY" "$DIST_DIR/$APP_NAME.jar"
else
    echo -e "\033[31mApp jar not found in target!\033[0m"
    exit 1
fi

# Optional DB file
if [ -f "$PROJECT_DIR/pharmax.db" ]; then
    cp "$PROJECT_DIR/pharmax.db" "$DIST_DIR/"
fi

# -----------------------------
# Step 6: Create macOS launcher script
# -----------------------------
echo -e "\n\033[33m[6/6] Creating launcher...\033[0m"

LAUNCHER_SCRIPT="$DIST_DIR/$APP_NAME.command"
cat > "$LAUNCHER_SCRIPT" << EOF
#!/bin/bash
cd "\$(dirname "\$0")"
./runtime/bin/java -cp "$APP_NAME.jar" $MAIN_CLASS
EOF
chmod +x "$LAUNCHER_SCRIPT"

# App bundle wrapper (Optional, but nice for Mac)
APP_BUNDLE="$DIST_DIR/${APP_NAME}.app"
mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources"

cat > "$APP_BUNDLE/Contents/MacOS/$APP_NAME" << EOF
#!/bin/bash
DIR="\$(cd "\$(dirname "\$0")/../../.." && pwd)"
cd "\$DIR"
./runtime/bin/java -cp "$APP_NAME.jar" $MAIN_CLASS
EOF
chmod +x "$APP_BUNDLE/Contents/MacOS/$APP_NAME"

# Provide a simpler unbundled mac runner script
MAC_RUNNER="$DIST_DIR/$APP_NAME-mac.sh"
cat > "$MAC_RUNNER" << EOF
#!/bin/bash
cd "\$(dirname "\$0")"
./runtime/bin/java -cp "$APP_NAME.jar" $MAIN_CLASS
EOF
chmod +x "$MAC_RUNNER"

# README
cat > "$DIST_DIR/README.txt" << EOF
# $APP_NAME - نظام إدارة المخازن والمبيعات للماك

## التشغيل
- لتشغيل البرنامج على نظام الماك، انقر نقراً مزدوجاً على ملف "$APP_NAME.command".
- (أو قم بتشغيل سكربت "$APP_NAME-mac.sh" من الطرفية Terminal).

## المتطلبات
لا يوجد! البرنامج يحتوي على Runtime Java + JavaFX مدمج خاص بالماك.

الإصدار: $APP_VERSION
EOF

echo -e "\n\033[32m========================================\033[0m"
echo -e "\033[32mBuild Complete for macOS!\033[0m"
echo -e "\033[32m========================================\033[0m"
echo -e "\nDistribution package created at:"
echo -e "$DIST_DIR"

SIZE=$(du -sh "$DIST_DIR" | cut -f1)
echo -e "\nPackage size: $SIZE"

echo -e "\nTo distribute:"
echo -e "1) Compress the 'distribution-mac' folder to ZIP"
echo -e "2) Send to macOS customers"
echo -e "3) They extract and run $APP_NAME.command"
