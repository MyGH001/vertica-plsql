/**
* testing output message with print and dbmd_output 
*/

select PLSQL_CREATE(using parameters content=
$$create or replace procedure p_test_output()
as
begin
  print('Built for Fast.');
  for r IN (select 'Built' as word union all select 'for' union all select 'Freedom.') loop
    dbms_output.put_line(r.word);
  end loop;
end;$$);

select PLSQL_EXEC($$p_test_output()$$);

select PLSQL_DROP(using parameters name='p_test_output');
