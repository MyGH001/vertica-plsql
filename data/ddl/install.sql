/*****************************
 * Vertica Analytic Database
 *
 * install User Defined Functions for Vertica PL/SQL
 *
 * Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 */

\set jarfile `ls lib/vertica-plsql-udfs-*.jar`

\set libname vertica_plsql_udfs

\set language '''JAVA'''

--\set isfenced 'not fenced'
\set isfenced 'fenced'


\set strlibfile ''''`pwd`'/':jarfile''''
\set strdepends ''''`pwd`'/lib/'''
\set strlibname '''':libname''''

CREATE OR REPLACE LIBRARY :libname AS :strlibfile DEPENDS :strdepends LANGUAGE :language;


\set tmpfile '/tmp/:libname.sql'
\! cat /dev/null > :tmpfile

\t
\o :tmpfile

select 'CREATE FUNCTION public.'||lower(replace(split_part(obj_name, '.', 1+regexp_count(obj_name, '\.')), 'Factory', ''))||' AS LANGUAGE '''||:language||''' NAME '''||obj_name||''' LIBRARY :libname :isfenced ;' from user_library_manifest where lib_name=:strlibname  and obj_type='Scalar Function';

select 'GRANT EXECUTE ON FUNCTION '||lower(replace(split_part(obj_name, '.', 1+regexp_count(obj_name, '\.')), 'Factory', ''))||' (' || arg_types || ') to PUBLIC;' from user_library_manifest where lib_name=:strlibname and obj_type='Scalar Function';


select 'CREATE TRANSFORM FUNCTION public.'||lower(replace(split_part(obj_name, '.', 1+regexp_count(obj_name, '\.')), 'Factory', ''))||' AS LANGUAGE '''||:language||''' NAME '''||obj_name||''' LIBRARY :libname :isfenced ;' from user_library_manifest where lib_name=:strlibname and obj_type='Transform Function';

select 'GRANT EXECUTE ON TRANSFORM FUNCTION '||lower(replace(split_part(obj_name, '.', 1+regexp_count(obj_name, '\.')), 'Factory', ''))||' (' || arg_types || ') to PUBLIC;' from user_library_manifest where lib_name=:strlibname and obj_type='Transform Function';

\o
\t

\i :tmpfile
\! rm -f :tmpfile
