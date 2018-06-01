#!/bin/bash

# A script file to continuously run tests and move the target directories to the 
# configured directory for later analysis.  Run it and get back to work on
# something else.
#
# If you want to put the output somewhere other than $HOME run it like
# OUTPUT=~/Desktop/emissary-test/ ./contrib/continuous-test.sh
# Assuming that directory is already created
#
#
# Here is an example of how it can be used
# OUTPUT=~/Desktop/emissary-test/ MVN_CMD="mvn clean verify" ./contrib/continuous-test.sh
# Then in $OUTPUT directory see the failures by running
# grep -r -A6 'ERROR[]] Failures:' */mvn_test.out

OUTPUT="${OUTPUT:=$HOME}"
if [ ! -e $OUTPUT ]; then
  echo $OUTPUT does not exist
  exit 1
fi
OUTFILE="mvn_test.out"
RUNNUM=0
SUCCESSES=0
ERRORS=0
MVN_CMD=${MVN_CMD:-"mvn clean test"}

rm_outfile() {
  test -e $OUTFILE && rm $OUTFILE
}

mvn_test() {
  RUNNUM=$((RUNNUM + 1))
  echo ---------
  echo Starting run $RUNNUM, tail -F $OUTFILE to follow 
  ${MVN_CMD} 2>&1 > $OUTFILE
}

output_stats() {
  local TTIME=$(grep "Total time: \d\d:\d\d min" $OUTFILE | sed 's/.*Total /total /')
  rm_outfile
  echo Run $RUNNUM complete, $SUCCESSES passed and $ERRORS failed, $TTIME
}

after_success() {
  echo PASSED
  SUCCESSES=$((SUCCESSES + 1))
  output_stats
}

after_error() {
  # Ctrl-C is an error.  Do not move anything
  if [ "$INTERRUPTED" == "true" ]; then
    exit 0
  fi
  echo FAILED
  ERRORS=$((ERRORS + 1))
  # move results
  local CURRENT="$(dirname $OUTPUT)/$(basename $OUTPUT)/$(date +'%Y%m%d%H%M%S')"
  mkdir -p $CURRENT
  echo Moving result data to $CURRENT
  cp $OUTFILE $CURRENT/
  for d in `find . -name target -type d`; do f=${d#*\/}; mkdir -p $CURRENT/$f && cp -r $d $CURRENT/${f%/target*}; done
  output_stats
}

interrupted() {
  INTERRUPTED=true
  echo -e "\nbreaking....\n"
  rm_outfile
}

trap interrupted SIGHUP SIGINT SIGTERM

rm_outfile
echo Dumping failures to ${OUTPUT}
echo Use Ctrl-C to break
while true; do
  mvn_test && after_success || after_error
done

