#!/bin/sh -e

# Starts a dev console to compile and run lichess.

# Usage:
# ./lila.sh
# Then in the sbt console:
# run

# We use .sbtopts instead
export SBT_OPTS=""

if [ ! -f ".sbtopts" ]; then
  cp .sbtopts.default .sbtopts
fi

if [ ! -f "conf/application.conf" ]; then
  cp conf/application.conf.default conf/application.conf
fi

java_env="-Dreactivemongo.api.bson.document.strict=false"

cat << "BANNER"
  _______   _ _     _                 _                  
     |     | (_)___| |__   ___   __ _(_)  ___  _ __ __ _ 
  ___|___  | | / __| '_ \ / _ \ / _` | | / _ \| '__/ _` |
     |     | | \__ \ | | | (_) | (_| | || (_) | | | (_| |
 ____|____ |_|_|___/_| |_|\___/ \__, |_(_)___/|_|  \__, |
                                |___/              |___/ 
BANNER

version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo Java "$version"

command="sbt $java_env $@"
echo $command
$command
