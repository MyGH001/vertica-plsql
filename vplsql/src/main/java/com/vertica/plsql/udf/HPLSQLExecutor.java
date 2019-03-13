/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
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
    private static final int CONCURRENCY = (Runtime.getRuntime().availableProcessors() + 1) / 2;

    private boolean trace = false;
    private boolean withStderr = false;
    private boolean dryRun = false;

    private String databaseName = null;
    private String userName = null;

    public void init(ServerInterface srvInterface) {
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.TRACE))
            this.trace = srvInterface.getParamReader().getBoolean(PLSQLExecutor.TRACE);
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.WITHSTDERR))
            this.withStderr = srvInterface.getParamReader().getBoolean(PLSQLExecutor.WITHSTDERR);
        if (srvInterface.getParamReader().containsParameter(PLSQLExecutor.DRYRUN))
            this.dryRun = srvInterface.getParamReader().getBoolean(PLSQLExecutor.DRYRUN);

        this.databaseName = srvInterface.getDatabaseName();
        this.userName = srvInterface.getUserName();
    }

    public void initCache(ServerInterface srvInterface) {
        PLSQLCache.init(srvInterface);
        try {
            if (!PLSQLCache.isValidate()) {
                Map<String, Throwable> parsingExceptions = new ConcurrentHashMap<String, Throwable>();
                // read stored PL/SQL objects from DSF.
                Map<String, Object[]> filesContent = DFSOperations.readFiles(srvInterface, null);
                if (filesContent != null) {
                    // maybe need deserialization, with mulitple thread for better performance
                    ExecutorService exServer = Executors.newFixedThreadPool(CONCURRENCY);
                    for (Map.Entry<String, Object[]> cnt : filesContent.entrySet()) {
                        String codePLSQL = (String) cnt.getValue()[0];
                        Object value = cnt.getValue()[1];
                        if (codePLSQL != null && codePLSQL.length() > 0) {
                            if (value != null && value instanceof byte[]) {
                                byte[] baObject = (byte[]) value;
                                exServer.execute(new Runnable() {
                                    public void run() {
                                        try {
                                            ByteArrayInputStream baiosObject = new ByteArrayInputStream(baObject);
                                            ObjectInputStream ois = new ObjectInputStream(
                                                    new GZIPInputStream(baiosObject));
                                            cnt.getValue()[1] = ois.readObject();
                                            ois.close();
                                        } catch (Throwable e) {
                                            // failed on deserialization, reparse later.
                                            cnt.getValue()[1] = null;
                                        }
                                    }
                                });
                            }
                        }
                    }
                    exServer.shutdown();
                    exServer.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

                    // maybe need parsing, with mulitple thread for better performance
                    exServer = Executors.newFixedThreadPool(CONCURRENCY);
                    for (Map.Entry<String, Object[]> cnt : filesContent.entrySet()) {
                        String codePLSQL = (String) cnt.getValue()[0];
                        Object value = cnt.getValue()[1];
                        if (codePLSQL != null && codePLSQL.length() > 0) {
                            if (value == null) {
                                // reparse when parsing tree library upgraded.
                                exServer.execute(new Runnable() {
                                    public void run() {
                                        try {
                                            ParseTree tree = (ParseTree) new HplsqlParser(
                                                    new CommonTokenStream(new HplsqlLexer(new ANTLRInputStream(
                                                            new ByteArrayInputStream(codePLSQL.getBytes("UTF-8"))))))
                                                                    .program();
                                            cnt.getValue()[1] = tree;
                                            DFSOperations.writeFile(srvInterface, cnt.getKey(), codePLSQL, tree);
                                        } catch (Throwable e) {
                                            parsingExceptions.put(cnt.getKey(), e);
                                        }
                                    }
                                });
                            }
                        }
                    }
                    exServer.shutdown();
                    exServer.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                }

                if (parsingExceptions.size() == 0) {
                    PLSQLCache.setData(filesContent);
                } else {
                    StringBuffer sb = new StringBuffer();
                    for (Map.Entry<String, Throwable> ent : parsingExceptions.entrySet()) {
                        sb.append("\n    Parsing [").append(ent.getKey()).append("]").append(" failed. Reason: ")
                                .append(ent.getValue().getMessage());
                    }
                }
            }
        } catch (Throwable e) {
            throw new UdfException(0, String.format("ERROR: failed read stored objects caused by %s", e.getMessage()));
        }
    }

    public String create(ServerInterface srvInterface, String codePLSQL) {
        PrintStream stdout = null;
        PrintStream stderr = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = null;
        try {
            PrintStream psOut = new PrintStream(out);
            stdout = System.out;
            System.setOut(psOut);
            stderr = System.err;
            if (this.withStderr) {
                System.setErr(psOut);
            } else {
                err = new ByteArrayOutputStream();
                System.setErr(new PrintStream(err));
            }

            // parsing PLSQL code
            Exec4Vertica exec = new Exec4Vertica() {
                @Override
                public Integer visitCreate_procedure_stmt(HplsqlParser.Create_procedure_stmtContext ctx) {
                    // Save procedure
                    DFSOperations.writeFile(srvInterface, ctx.ident(0).getText(), this.getFormattedText(ctx), ctx);
                    return super.visitCreate_procedure_stmt(ctx);
                }

                @Override
                public Integer visitCreate_function_stmt(HplsqlParser.Create_function_stmtContext ctx) {
                    // Save procedure
                    DFSOperations.writeFile(srvInterface, ctx.ident().getText(), this.getFormattedText(ctx), ctx);
                    return super.visitCreate_function_stmt(ctx);
                }
            };
            String[] args = { "-e", codePLSQL, "--trace", "--offline" };
            exec.run(args);

            return getHPLSQLOutput(out.toString()).trim();
        } catch (Throwable e) {
            if (e.getCause() instanceof NotSerializableException) {
                return String.format("PL/SQL syntax error! Details:\n%s%s", getHPLSQLOutput(out.toString()).trim(),
                        (err != null) ? getHPLSQLOutput(err.toString()).trim() : "");
            } else {
                throw new UdfException(0,
                        String.format("ERROR: failed add PL/SQL caused by %s.\nDetails:\n%s%s", e.getMessage(),
                                getHPLSQLOutput(out.toString()).trim(),
                                (err != null) ? getHPLSQLOutput(err.toString()).trim() : ""));
            }
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = null;
        try {
            PrintStream psOut = new PrintStream(out);
            stdout = System.out;
            System.setOut(psOut);
            stderr = System.err;
            if (this.withStderr) {
                System.setErr(psOut);
            } else {
                err = new ByteArrayOutputStream();
                System.setErr(new PrintStream(err));
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
                private boolean inIncluding = false;

                @Override
                public void initOptions() {
                    super.initOptions();
                    super.getConn().addConnection("verticaconn", String.format(
                            "com.vertica.jdbc.Driver;jdbc:vertica://localhost:5433/%s;%s;", databaseName, userName));
                }

                @Override
                public void includeRcFile() {
                    this.inIncluding = true;

                    super.includeRcFile();
                    // include stored PLSQLs
                    try {
                        Map<String, Object[]> storedPLSQLTrees = PLSQLCache.getData();
                        if (storedPLSQLTrees != null) {
                            for (Map.Entry<String, Object[]> tree : storedPLSQLTrees.entrySet()) {
                                super.visit((ParseTree) tree.getValue()[1]);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    this.inIncluding = false;
                }

                public void setSqlCode(Exception e) {
                    // return usefull info when authorization faild
                    if (e instanceof SQLInvalidAuthorizationSpecException
                            || e instanceof InvalidAuthorizationException) {
                        super.criticalExceptionMsg = String.format(
                                String.join("\n", "User [%s] login failed from PL/SQL to Vertia.",
                                        "Please check whether you've gaven it privilege as followings:",
                                        "    create authentication v_plsql method 'trust' local;",
                                        "    alter authentication v_plsql priority 9999;",
                                        "    grant authentication v_plsql to %s;", "Caused by:\n %s"),
                                userName, userName, e.getMessage());
                    }

                    super.setSqlCode(e);
                }

                public void trace(ParserRuleContext ctx, String message) {
                    // turn off trace info when including
                    if (!this.inIncluding)
                        super.trace(ctx, message);
                }
            };

            exec.run(args.toArray(new String[0]));
            // return usefull info when authorization faild
            if (exec.criticalExceptionMsg != null)
                throw new RuntimeException(exec.criticalExceptionMsg);

            return getHPLSQLOutput(out.toString()).trim();
        } catch (Throwable e) {
            throw new UdfException(0,
                    String.format("ERROR: failed to execute PL/SQL code! Caused by:\n %s.\nDetails:\n%s%s",
                            e.getMessage(), getHPLSQLOutput(out.toString()).trim(),
                            (err != null) ? getHPLSQLOutput(err.toString()).trim() : ""));
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
    private static String getHPLSQLOutput(String s) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(s));
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("log4j:") && !line.contains("INFO Log4j")
                        && !line.startsWith("Configuration file:")) {
                    sb.append(line);
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
        }
        return sb.toString();
    }
}