#!/bin/sh
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd)

if [ -z "$JAVA_HOME" ]; then
  for c in "$DIR/.toolchain/jdk/jdk-17.0.2" "$DIR/../demo/.toolchain/jdk/jdk-17.0.2" "D:/vibecoding/IME/.toolchain/jdk/jdk-17.0.2"; do
    if [ -x "$c/bin/javac" ]; then
      JAVA_HOME="$c"
      break
    fi
  done
fi
export JAVA_HOME

OUT="$DIR/build/jvmtest"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$OUT" \
  "$DIR"/app/src/main/java/com/meiyan/ime/engine/*.java \
  "$DIR"/tools/jvm/EngineTest.java
cd "$DIR"
exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
  -cp "$OUT" EngineTest