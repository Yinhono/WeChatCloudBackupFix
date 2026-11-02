#!/usr/bin/env bash
# 不依赖 Gradle 的手动打包脚本，工具链路径按需修改。
# 需要：JDK 17、Android SDK platform android-35、build-tools 35.0.0、Xposed API 82 jar。
set -euo pipefail

TOOLS="${TOOLS:-$HOME/workspace/tools}"
export JAVA_HOME="$TOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$TOOLS/sdk/platforms/android-35/android.jar"
BT="$TOOLS/sdk/build-tools/35.0.0"
XPOSED_JAR="$TOOLS/xposed-api-82.jar"
PROJ="$(cd "$(dirname "$0")" && pwd)"
OUT="$PROJ/build-manual"
MAIN="$PROJ/app/src/main"

rm -rf "$OUT"
mkdir -p "$OUT"

# 1) 资源编译与链接（生成带 manifest、资源和 assets 的基础 APK）
"$BT/aapt2" compile --dir "$MAIN/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$MAIN/AndroidManifest.xml" \
    -A "$MAIN/assets" \
    --min-sdk-version 26 --target-sdk-version 35 \
    --version-code 1 --version-name 1.0 \
    "$OUT/res.zip"

# 2) Java 编译与 dex
mkdir -p "$OUT/classes" "$OUT/dex"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn \
    -cp "$ANDROID_JAR:$XPOSED_JAR" -d "$OUT/classes" \
    $(find "$MAIN/java" -name '*.java')
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class')

# 3) 合并、对齐、签名
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$OUT/debug.keystore" \
        -storepass android -keypass android -alias debug \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android \
    --key-pass pass:android --out "$OUT/WeChatCloudBackupFix.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/WeChatCloudBackupFix.apk" | head -3
echo "built: $OUT/WeChatCloudBackupFix.apk"
