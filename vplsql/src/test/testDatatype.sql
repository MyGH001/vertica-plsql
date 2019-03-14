/**
* testing data type
*/

select PLSQL_CREATE(using parameters content=
$$create or replace procedure p_test_datatype()
as
begin
  declare i int;
  i := 0;
  for j in 1..10 loop
    i := i+j;
  end loop;
  print('sigma from 1 to 10 = ' || i);

  declare v_int as int;
  declare c_int as int = 20190220122345;
  select v into v_int from table (values c_int) as tb(v) where v = c_int;
  print('int = ' || v_int);
  
  declare v_float as float;
  declare c_float as float = 20190220122345.678;
  select v into v_float from table (values c_float) as tb(v) where v = c_float;
  print('float = ' || v_float);
  
  declare v_numeric as numeric(18,6);
  declare c_numeric as numeric(18,6) = 20190220122345.678;
  select v into v_numeric from table (values c_numeric) as tb(v) where v = c_numeric;
  print('numeric = ' || v_numeric);
  
  declare v_varchar as varchar;
  declare c_varchar as varchar = '2019-02-20 12:23:45.678';
  select v into v_varchar from table (values c_varchar) as tb(v) where v = c_varchar;
  print('varchar = ' || v_varchar);
  
  declare v_date as date;
  declare c_date as date = date '2019-02-20 12:23:45.678';
  select v into v_date from table (values c_date) as tb(v) where v = c_date;
  print('date = ' || v_date);

  declare v_datetime as timestamp(17, 3);
  declare c_datetime as timestamp(17, 3) = timestamp '2019-02-20 12:23:45.678';
  select v into v_datetime from table (values c_datetime) as tb(v) where v = c_datetime;
  print('datetime = ' || v_datetime);
end;$$);

select PLSQL_EXEC($$p_test_datatype()$$);

select PLSQL_DROP(using parameters name='p_test_datatype');
