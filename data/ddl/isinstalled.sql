/*****************************
 * Vertica Analytic Database
 *
 * install User Defined Functions for Vertica PL/SQL
 *
 * Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 */

SELECT (COUNT(0) = 1) AS is_installed
FROM user_libraries
WHERE user_libraries.lib_name = 'vertica_plsql_udfs';
