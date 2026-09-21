#!/bin/zsh
# Akademik Takvim - gelistirme ortami aktivasyonu (macOS)
# Kullanim:  source ./ortam.sh
BREW_ROOT="/opt/homebrew"
export JAVA_HOME="$BREW_ROOT/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$BREW_ROOT/opt/postgresql@17/bin:$BREW_ROOT/bin:$PATH"
export PGPASSWORD="admin123"
echo "Ortam hazir:"
java -version
mvn -v | head -n 1
