/**
* testing output message with print and dbmd_output 
*/

create or replace procedure p_test_output()
as
begin
  print('Built for Fast.');
  for r IN (select 'Built' as word union all select 'for' union all select 'Freedom.') loop
    dbms_output.put_line(r.word);
  end loop;
end;

begin
  call p_test_output();
end;
