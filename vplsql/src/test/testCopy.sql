/**
* testing copy statement
*/

select PLSQL_CREATE(using parameters content=
$$create or replace procedure p_test_copy()
as
begin
  drop table if exists t_test_copy;
  create table t_test_copy(id IDENTITY, txt varchar(256));

  exec 'copy t_test_copy(txt) from ''/etc/hosts'' direct';
  exec 'select count(*)>0 from t_test_copy';
  
  drop table if exists t_test_copy;
end;$$);

select PLSQL_EXEC($$p_test_copy()$$);

select PLSQL_DROP(using parameters name='p_test_copy');
