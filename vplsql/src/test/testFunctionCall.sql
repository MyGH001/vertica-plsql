/**
* testing create simple function, and get function
*/

select PLSQL_CREATE(using parameters content=
$$create function f_test_add2ints(i1 int, i2 int)
returns int
as
begin
  return i1+i2;
end;

create or replace procedure p_test_hello(msg varchar)
as
begin
  print('Hello, ' || msg || '!');
  print('1+2 = ' || f_test_add2ints(1, 2));
end;$$);

select PLSQL_EXEC($$p_test_hello('world')$$);

select PLSQL_DROP(using parameters name='p_test_hello');
select PLSQL_DROP(using parameters name='f_test_add2ints'); 
