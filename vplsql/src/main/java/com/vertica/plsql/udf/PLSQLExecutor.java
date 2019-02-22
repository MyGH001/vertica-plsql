/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import com.vertica.sdk.*;

/**
 * Facade for create and run PL/SQL procedures, functions and packages.
 * 
 * <b>Notice:</b> Vertica UDFs should NOT call HPLSQL functions directly,
 * otherwise it will break the feature of parsed PL/SQL code serializaton.
 */
public interface PLSQLExecutor {
    public static String TRACE = "trace";
    public static String WITHSTDERR = "withStderr";
    public static String DRYRUN = "dryRun";
    public static String WITHCACHE = "withCache";

    public static void setParameterType(ServerInterface srvInterface, SizedColumnTypes parameterTypes) {
        parameterTypes.addBool(PLSQLExecutor.TRACE);
        parameterTypes.addBool(PLSQLExecutor.WITHSTDERR);
        parameterTypes.addBool(PLSQLExecutor.DRYRUN);
        parameterTypes.addBool(PLSQLExecutor.WITHCACHE);
    }

    public static PLSQLExecutor newInstance(ServerInterface srvInterface) {
        PLSQLCache.init(srvInterface);

        try {
            PLSQLExecutor exe = (PLSQLExecutor) Class.forName("com.vertica.plsql.udf.HPLSQLExecutor").newInstance();
            exe.init(srvInterface);
            return exe;
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    public void init(ServerInterface srvInterface);

    public String create(ServerInterface srvInterface, String codePLSQL);

    public String run(String codePLSQL);

}