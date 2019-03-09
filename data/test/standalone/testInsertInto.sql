/**
* testing insert into statement
*/

create or replace procedure p_test_insertinto()
as
begin
  drop table if exists t_test_insertinto;
  create table t_test_insertinto(id int, name varchar(256));

  insert into t_test_insertinto values(1, 'one');
  insert into t_test_insertinto values(2, 'two');
  select * from t_test_insertinto;
  
  drop table if exists t_test_insertinto;
end;

begin
  call p_test_insertinto();
end
