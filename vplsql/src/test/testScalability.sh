#!/bin/bash

#parameters

SIZE=100
if [ ! -z "$1" ] ; then
  SIZE="$1"
fi

CONCURRENCY=2
if [ ! -z "$2" ] ; then
  CONCURRENCY="$2"
fi

if [ -z "$VSQL" ] ; then
  echo "Please set VSQL environment parameter, eg. export VSQL='/opt/vertica/bin/vsql [-h verticaHost] [-u username] [-w password] [databaseName]'"
  exit 1
fi

curDir=$(pwd)
scriptDir=$(cd "$(dirname $0)"; pwd)

BATCH=10
for (( i=0; i<SIZE/BATCH/CONCURRENCY; i++ )) ; do
for (( j=0; j<CONCURRENCY; j++ )) ; do

$VSQL -i <<- EOF 2>&1 &
  select PLSQL_CREATE(using parameters content=\$\$
$(
	for (( n=0; n<BATCH; n++ )) ; do
		cat <<-EOFSQL

create or replace procedure p_test_scalalability_$((i*CONCURRENCY*BATCH+j*BATCH+n))()
as
  i int;
  dt timestamp; 
  ds varchar; 
begin
  i := 0;
  print('i = ' || i);

  i := i+1;
  print('i = ' || i);

  select count(*) into i from tables;
  print('tables count = ' || i);

  select count(*) into i from projections;
  print('projections count = ' || i);

  select sysdate(), sysdate()::varchar into dt, ds from dual;
  print('SELECT INTO:');
  print('    timestamp = ' || dt);
  print('    string = ' || ds);
  
  drop table if exists procdure_test;
  create table procdure_test(id IDENTITY, txt varchar(256));
  exec 'copy procdure_test(txt) from ''/etc/hosts'' direct';
  select count(*) from procdure_test;
  select txt from procdure_test order by id;
  drop table if exists procdure_test;

  dbms_output.put_line('Tables:')
  for t IN (select table_schema, table_name from tables order by 1,2) loop
    dbms_output.put_line('    schemaName = ' || t.table_schema || ', tableName = ' || t.table_name);
  end loop;

end;

		EOFSQL
	done
)
\$\$);
EOF

done
wait
done


$VSQL -i <<- 'EOF' 2>&1
  select PLSQL_CREATE(using parameters content=$$
    create function f_test_scalalability_add2ints(i1 int, i2 int)
    returns int
    as
    begin
      return i1+i2;
    end;

    create or replace procedure p_test_scalalability_hello(msg varchar)
    as
    begin
      print('Hello, ' || msg || '!');
      print('1+2 = ' || f_test_scalalability_add2ints(1, 2));
    end;
    $$);
EOF

$VSQL -i <<- 'EOF' 2>&1
  select PLSQL_EXEC($$p_test_scalalability_hello('world')$$);

  select PLSQL_EXPORT(using parameters name='f_test_scalalability_add2ints');
EOF

$VSQL -i <<- 'EOF' 2>&1
  select PLSQL_DROP(using parameters name='f_test_scalalability_add2ints');
EOF

$VSQL -i <<- 'EOF' 2>&1
  select PLSQL_DROP(using parameters name='p_test_scalalability_hello');
EOF
