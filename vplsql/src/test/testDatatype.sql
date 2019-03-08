/**
* testing data type
*/

select PLSQL_CREATE(using parameters content=
$$create or replace procedure p_test_datatype()
as
  i int;
  f float;
  n numeric(18,6);
  s varchar; 
  d date; 
  dt timestamp; 
begin
  i := 0;
  for j IN 1..10 loop
    i := i+j;
  end loop;
  print('sigma from 1 to 10 = ' || i);

  -- Note: hplsql-0.3.17 只支持从数据库取值的类型CHAR/VARCHAR/INTEGER/BIGINT/SMALLINT/TINYINT/DECIMAL/NUMERIC/FLOAT/DOUBLE, see: org.apache.hive.hplsql.Var.setValue @ 261 line.
  exec 'select
    extract(epoch from ''2019-02-20 12:23:45.678''::datetime)::int
    , extract(epoch from ''2019-02-20 12:23:45.678''::datetime)::float
    , extract(epoch from ''2019-02-20 12:23:45.678''::datetime)::numeric(18,6)
    , ''2019-02-20 12:23:45.678''
    , ''2019-02-20 12:23:45.678''::date
    , ''2019-02-20 12:23:45.678''::datetime
    from dual' into i, f, n, s, d, dt;
  print('SELECT INTO:');
  print('    int = ' || i);
  print('    float = ' || f);
  print('    numeric = ' || n);
  print('    varchar = ' || s);
  print('    date = ' || d);
  print('    datetime = ' || dt);
end;$$);

select PLSQL_EXEC($$p_test_datatype()$$);

select PLSQL_DROP(using parameters name='p_test_datatype');
