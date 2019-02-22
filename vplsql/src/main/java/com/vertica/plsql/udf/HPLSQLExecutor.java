/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.ArrayList;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.hive.hplsql.HplsqlLexer;
import org.apache.hive.hplsql.HplsqlParser;

import org.apache.hive.hplsql.Exec4Vertica;

import com.vertica.support.exceptions.InvalidAuthorizationException;
import com.vertica.sdk.*;

/**
 * Implement for reate and run PL/SQL procedures, functions and packages using
 * HPLSQL.
 * 
 * <b>Notice:</b> Vertica UDFs should NOT call this directly, otherwise it will
 * break the feature of parsed PL/SQL code serializaton.
 */
public class HPLSQLExecutor implements PLSQLExecutor {
    private boolean trace = false;
    private boolean withStderr = false;
    private boolean dryRun = false;
    private boolean withCache = true;

    private String databaseName = null;
    private String userName = null;

    public void init(ServerInterface srvInterface) {
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.TRACE))
            this.trace = srvInterface.getParamReader().getBoolean(PLSQLExecutor.TRACE);
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.WITHSTDERR))
            this.withStderr = srvInterface.getParamReader().getBoolean(PLSQLExecutor.WITHSTDERR);
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.DRYRUN))
            this.dryRun = srvInterface.getParamReader().getBoolean(PLSQLExecutor.DRYRUN);
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.WITHCACHE))
            this.withCache = srvInterface.getParamReader().getBoolean(PLSQLExecutor.WITHCACHE);

        this.databaseName = srvInterface.getDatabaseName();
        this.userName = srvInterface.getUserName();

        // read content from DSF.
        try {
            if (!PLSQLCache.isValidate()) {
                String storedPLSQL = DFSOperations.readFiles(srvInterface, null);
                if (storedPLSQL != null && storedPLSQL.length() > 0) {
                    ParseTree treeStoredPLSQL = new HplsqlParser(new CommonTokenStream(new HplsqlLexer(
                            new ANTLRInputStream(new ByteArrayInputStream(storedPLSQL.getBytes("UTF-8")))))).program();
                    PLSQLCache.setData(treeStoredPLSQL, this.withCache);
                }
            }
        } catch (Throwable e) {
            throw new UdfException(0, String.format("ERROR: failed read stored objects caused by %s", e.getMessage()));
        }
    }

    public String create(ServerInterface srvInterface, String codePLSQL) {
        PrintStream stdout = null;
        PrintStream stderr = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream psOut = new PrintStream(out);
            stdout = System.out;
            System.setOut(psOut);
            if (this.withStderr) {
                stderr = System.err;
                System.setErr(psOut);
            }

            // parsing PLSQL code
            Exec4Vertica exec = new Exec4Vertica() {
                @Override
                public Integer visitCreate_procedure_stmt(HplsqlParser.Create_procedure_stmtContext ctx) {
                    // Save procedure
                    DFSOperations.writeFile(srvInterface, ctx.ident(0).getText(), this.getFormattedText(ctx));
                    return super.visitCreate_procedure_stmt(ctx);
                }

                @Override
                public Integer visitCreate_function_stmt(HplsqlParser.Create_function_stmtContext ctx) {
                    // Save procedure
                    DFSOperations.writeFile(srvInterface, ctx.ident().getText(), this.getFormattedText(ctx));
                    return super.visitCreate_function_stmt(ctx);
                }
            };
            String[] args = { "-e", codePLSQL, "--trace", "--offline" };
            exec.run(args);
            DFSOperations.updateLastModified(srvInterface);

            return getHPLSQLOutput(out.toString()).trim();
        } catch (Throwable e) {
            throw new UdfException(0, String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        } finally {
            if (stdout != null)
                System.setOut(stdout);
            if (stderr != null)
                System.setErr(stderr);
        }
    }

    public String run(String codePLSQL) {
        PrintStream stdout = null;
        PrintStream stderr = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream psOut = new PrintStream(out);
            stdout = System.out;
            System.setOut(psOut);
            if (this.withStderr) {
                stderr = System.err;
                System.setErr(psOut);
            }

            // execute PLSQL code
            ArrayList<String> args = new ArrayList<String>();
            args.add("-e");
            args.add(codePLSQL);
            if (this.trace)
                args.add("--trace");
            if (this.dryRun)
                args.add("--offline");

            Exec4Vertica exec = new Exec4Vertica() {
                @Override
                public void initOptions() {
                    super.initOptions();
                    super.getConn().addConnection("verticaconn", String.format(
                            "com.vertica.jdbc.Driver;jdbc:vertica://localhost:5433/%s;%s;", databaseName, userName));
                }

                @Override
                public void includeRcFile() {
                    super.includeRcFile();
                    // include stored PLSQLs
                    try {
                        ParseTree treeStoredPLSQL = (ParseTree) PLSQLCache.getData();
                        if (treeStoredPLSQL != null) {
                            super.visit(treeStoredPLSQL);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public void setSqlCode(Exception e) {
                    // return usefull info when authorization faild
                    if (e instanceof SQLInvalidAuthorizationSpecException
                            || e instanceof InvalidAuthorizationException) {
                        super.criticalExceptionMsg = String.format(
                                String.join("\r\n", "User [%s] login failed from PL/SQL to Vertia.",
                                        "Please check whether you've gaven it privilege as followings:",
                                        "    create authentication v_plsql method 'trust' local;",
                                        "    alter authentication v_plsql priority 9999;",
                                        "    grant authentication v_plsql to %s;", "Caused by:\r\n %s"),
                                userName, userName, e.getMessage());
                    }

                    super.setSqlCode(e);
                }
            };

            exec.run(args.toArray(new String[0]));
            // return usefull info when authorization faild
            if (exec.criticalExceptionMsg != null)
                throw new RuntimeException(exec.criticalExceptionMsg);

            return getHPLSQLOutput(out.toString()).trim();
        } catch (Throwable e) {
            throw new UdfException(0,
                    String.format("ERROR: failed to execute PL/SQL code! Caused by:\r\n %s", e.getMessage()));
        } finally {
            if (stdout != null)
                System.setOut(stdout);
            if (stderr != null)
                System.setErr(stderr);
        }
    }

    /**
     * Get HPLSQL output
     */
    private static String getHPLSQLOutput(String s) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(s));
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("log4j:") && !line.contains("INFO Log4j")) {
                sb.append(line);
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}