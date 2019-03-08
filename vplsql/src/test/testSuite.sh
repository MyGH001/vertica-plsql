#!/bin/bash

showUsage ()
{
cat <<-EOF >&2
Test suite for PL/SQL in Vertica
Usage: $(basename ${0}) [options] [caseName1] [caseName2] [...]
       empty parameter means test all cases.
Options:
    -h show this usage info.

Note: Please set VSQL environment parameter before run this tool, eg. export VSQL='/opt/vertica/bin/vsql [-h verticaHost] [-u username] [-w password] [databaseName]'
EOF
}

#options and parameters
while getopts ":h:" opt; do
  case $opt in
    h | ?)
      showUsage
      exit 1
      ;;
  esac
done
shift $(($OPTIND -1))


if [ -z "${VSQL}" ] ; then
  showUsage
  exit 1
fi
VSQL="$(sed '
    s/ *-e */ /g
    s/ *-a */ /g
    s/ *-E */ /g
    s/ *-o *.*/ /g
    s/ *-s */ /g
    s/ *-S */ /g
    s/ *-H */ /g
    s/ *-T *.*/ /g
    s/ *-x */ /g
    s/ *-Q */ /g
    s/ *-F *.*/ /g
    s/ *-R *.*/ /g
    s/ *-i */ /g
  ' <<< "${VSQL}")"

curDir=$(pwd)
scriptDir=$(cd "$(dirname $0)"; pwd)


runCase ()
{
  caseName="${1}"
  sqlFile="${2}"
  outputFile="${3}"
  echo -n "    testing case [${caseName}] ... "
  result="$( diff "${outputFile}" <(${VSQL} -XAqt -f "${sqlFile}") )"
  if [ $? -eq 0 ] ; then
    echo "passed."
  else
    echo "failed! The difference between expectation and result:"
    sed -e ':a' -e 'N' -e '$!ba' -e "s/\n/\\$(echo -e '\n\r        ')/g" <<< "        ${result}"
    return 1
  fi
}

echo "Begin testing ..."
passed=0
failed=0
if [ $# -gt 0 ] ; then
  for caseName in "$@" ; do
    sqlFile="${scriptDir}/test${caseName}.sql"
    outputFile="${scriptDir}/test${caseName}.out"
    if [ "${caseName}" != "-" -a "${caseName}" != "--" -a -f "${sqlFile}" ] ; then
      runCase "${caseName}" "${sqlFile}" "${outputFile}"
      if [ $? -eq 0 ] ; then
        passed=$((passed+1))
      else
        failed=$((failed+1))
      fi
    else
      echo -n "    case [${caseName}] ... invalid!"
    fi
  done
else
  for sqlFile in "${scriptDir}"/test?*.sql ; do
    caseName="$(sed -e 's/^test//g' -e 's/\.sql$//g' <<< "$(basename ${sqlFile})")"
    outputFile="${scriptDir}/test${caseName}.out"
    runCase "${caseName}" "${sqlFile}" "${outputFile}"
    if [ $? -eq 0 ] ; then
      passed=$((passed+1))
    else
      failed=$((failed+1))
    fi
  done
fi

echo ""
echo -n "Summary: tested $((passed+failed)) cases totally, "
if [ $failed -eq 0 ] ; then
  echo "all cases passed."
else
  echo "${failed} cases failed!"
fi
