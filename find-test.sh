#!/bin/bash

SCRIPT=`basename "$0"`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR

if [ "$#" -ne 1 ]; then
    >&2 echo "Usage: $SCRIPT {a1,a2,a3,a4,a5}/<test>"
    exit 1
fi

TEST=./test/marmoset/$1
RESULT=

if [ -d "$TEST" -o -f "$TEST" ]; then
  RESULT=$(find "$TEST" -iname '*.java' | xargs -I {} echo '"{}",')
else
  if [ -f "$TEST.java" ]; then
    RESULT=$(find "$TEST.java" -iname '*.java' | xargs -I {} echo '"{}",')
  else
    >&2 echo "ERROR: Test not found"
    exit 1
  fi
fi

echo $RESULT | sed -e 's|,$||' | perl -p -e 's/,\s/,\n/g'
