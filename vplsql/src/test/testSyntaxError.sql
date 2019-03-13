/**
* testing error message with incorrect syntax PL/SQL.
*/

select PLSQL_CREATE(using parameters content=
$$create or replace procedure p_test_syntax_error()
as
end;$$);

select PLSQL_EXEC(
$$create or replace procedure p_test_syntax_error()
as
end;$$ using parameters withStderr=true);
