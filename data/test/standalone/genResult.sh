#!/bin/bash

showUsage ()
{
cat <<-EOF >&2
Generate result of test cases for PL/SQL PL/SQL standalone on Vertica
Usage: $(basename ${0}) [options] [caseName1] [caseName2] [...]
       empty parameter means test all cases.
Options:
    -h show this usage info.

Note: Please point environment variable HPLSQL_HOME to vertica-plsql release directory, this tool need runnable [\${HPLSQL_HOME}/bin/hplsql].
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


curDir=$(pwd)
scriptDir=$(cd "$(dirname $0)"; pwd)

if [ -z "${HPLSQL_HOME}" ] ; then
  showUsage
  exit 1
fi
HPLSQL="${HPLSQL_HOME}/bin/hplsql"
if [ ! -x "${HPLSQL}" ] ; then
  showUsage
  exit 1
fi

runCase ()
{
  caseName="${1}"
  sqlFile="${2}"
  outputFile="${3}"
  echo -n "    generating result for case [${caseName}] ... "
  
  result="$( ${HPLSQL} -f "${sqlFile}" 3>&2 2>&1 1>&3 >"${outputFile}" )"
  if [ $? -eq 0 ] ; then
    echo "successful."
  elif [ -z "${result}" ] ; then
    echo "failed!"
    return 1
  else
    echo "failed! Error message:"
    sed -e ':a' -e 'N' -e '$!ba' -e "s/\n/\\$(echo -e '\n\r        ')/g" <<< "        ${result}"
    return 1
  fi
}

echo "Begin generating ..."
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
echo -n "Summary: generated $((passed+failed)) cases totally, "
if [ $failed -eq 0 ] ; then
  echo "all cases successful."
else
  echo "${failed} cases failed!"
fi
