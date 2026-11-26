#!/bin/sh
set -e
DIR=$(cd "$(dirname "$0")" && pwd)

if [ -z "$JAVA_HOME" ]; then
  if [ -x "$DIR/.toolchain/jdk/jdk-17.0.2/bin/java" ]; then
    JAVA_HOME="$DIR/.toolchain/jdk/jdk-17.0.2"
  elif [ -x "$DIR/../demo/.toolchain/jdk/jdk-17.0.2/bin/java" ]; then
    JAVA_HOME="$DIR/../demo/.toolchain/jdk/jdk-17.0.2"
  fi
  export JAVA_HOME
fi

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$DIR/.toolchain/sdk/platforms/android-34" ]; then
    ANDROID_HOME="$DIR/.toolchain/sdk"
  elif [ -d "$DIR/../demo/.toolchain/sdk/platforms/android-34" ]; then
    ANDROID_HOME="$DIR/../demo/.toolchain/sdk"
  fi
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

GRADLE_BIN="$DIR/.toolchain/gradle/gradle-8.7/bin/gradle"
if [ ! -f "$GRADLE_BIN" ]; then
  GRADLE_BIN="$DIR/../demo/.toolchain/gradle/gradle-8.7/bin/gradle"
fi
if [ -f "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "未找到 Gradle 8.7。请设置 JAVA_HOME/ANDROID_HOME，或安装 Gradle 8.7 后重试。" >&2
exit 1