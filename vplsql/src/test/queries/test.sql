
select PLSQL_CREATE(using parameters content=$$

create or replace procedure p_hello(msg varchar)
as
begin
  print('Hello, ' || msg || '!');
end;

create or replace procedure p_basic()
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

  -- Note: hplsql-0.3.17 只支持从数据库取值的类型CHAR/VARCHAR/INTEGER/BIGINT/SMALLINT/TINYINT/DECIMAL/NUMERIC/FLOAT/DOUBLE, see: org.apache.hive.hplsql.Var.setValue @ 261 line.
  select sysdate(), sysdate()::varchar into dt, ds from dual;
  print('SELECT INTO:');
  print('    timestamp = ' || dt);
  print('    string = ' || ds);
end;

create or replace procedure p_copy()
as
begin
  drop table if exists procdure_test;
  create table procdure_test(id IDENTITY, txt varchar(256));
  exec 'copy procdure_test(txt) from ''/etc/hosts'' direct';
  select count(*) from procdure_test;
  select txt from procdure_test order by id;
  drop table if exists procdure_test;
end;

create or replace procedure p_output()
as
begin
  dbms_output.put_line('Tables:')
  for t IN (select table_schema, table_name from tables order by 1,2) loop
    dbms_output.put_line('    schemaName = ' || t.table_schema || ', tableName = ' || t.table_name);
  end loop;
end;

create function f_add2ints(i1 int, i2 int)
returns int
as
begin
  return i1+i2;
end;

create function f_fordrop(i1 int, i2 int) 
returns varchar
as
begin
  return 'just for testing drop';
end;

$$);


select PLSQL_EXPORT(using parameters name='p_hello');

select PLSQL_EXPORT(using parameters name='p_basic');

select PLSQL_EXPORT(using parameters name='p_copy');

select PLSQL_EXPORT(using parameters name='p_output');

select PLSQL_EXPORT(using parameters name='f_add2ints');

select PLSQL_EXPORT(using parameters name='f_fordrop');

select PLSQL_DROP(using parameters name='f_fordrop');

select PLSQL_EXPORT(using parameters name='f_fordrop');

select PLSQL_EXPORT();

/*
begin
  call p_hello('Vertica');
  call p_basic();
  call p_copy();
  call p_output();
  print('1+2 = ' || f_add2ints(1, 2));
end
*/

select PLSQL_EXEC($$p_hello('world')$$);

select PLSQL_EXEC($$
begin
  call p_hello('Vertica');
  call p_basic();
  call p_copy();
  call p_output();
  print('1+2 = ' || f_add2ints(1, 2));
end
$$ using parameters trace=true, dryRun=true, withStderr=true);
