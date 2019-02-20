/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 * Description: run PL/SQL to define procedures, functions and packages.
 *
 */

package com.vertica.plsql.udf;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.ArrayList;

import org.apache.hive.hplsql.Exec4Vertica;

import com.vertica.support.exceptions.InvalidAuthorizationException;
import com.vertica.sdk.*;

public class PLSQL_ExecFactory extends ScalarFunctionFactory {
    public static String TRACE = "trace";
    public static String WITHSTDERR = "withStderr";
    public static String DRYRUN = "dryRun";

    public class PLSQL_Exe extends ScalarFunction {
        private Boolean trace = false;
        private Boolean withStderr = false;
        private Boolean dryRun = false;

        private String storedPLSQL = "";

        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes) {
            super.setup(srvInterface, argTypes);

            if (srvInterface.getParamReader().containsParameter(TRACE))
                this.trace = srvInterface.getParamReader().getBoolean(TRACE);
            if (srvInterface.getParamReader().containsParameter(WITHSTDERR))
                this.withStderr = srvInterface.getParamReader().getBoolean(WITHSTDERR);
            if (srvInterface.getParamReader().containsParameter(DRYRUN))
                this.dryRun = srvInterface.getParamReader().getBoolean(DRYRUN);

            // read content from DSF.
            try {
                this.storedPLSQL = DFSOperations.readFiles(srvInterface, null);
            } catch (Throwable e) {
                throw new UdfException(0,
                        String.format("ERROR: failed read stored objects caused by %s", e.getMessage()));
            }
        }

        /**
         * Get HPLSQL output
         */
        String getHPLSQLOutput(String s) throws Exception {
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

        @Override
        public void processBlock(ServerInterface srvInterface, BlockReader argReader, BlockWriter resWriter)
                throws UdfException, DestroyInvocation {
            String databaseName = srvInterface.getDatabaseName();
            String userName = srvInterface.getUserName();
            do {
                if (argReader.getNumCols() < 1)
                    throw new UdfException(0, "The 1 argument should be provided for executing PLSQL code!");

                String codePLSQL = argReader.getString(0);
                if (codePLSQL != null && codePLSQL.length() > 0) {
                    resWriter.setString(executePLSQLCode(srvInterface, codePLSQL, databaseName, userName));
                } else {
                    resWriter.setStringNull();
                }

                resWriter.next();
            } while (argReader.next());
        }

        private String executePLSQLCode(ServerInterface srvInterface, String codePLSQL, String databaseName,
                String userName) {
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
                        super.getConn().addConnection("verticaconn",
                                String.format("com.vertica.jdbc.Driver;jdbc:vertica://localhost:5433/%s;%s;",
                                        databaseName, userName));
                    }

                    @Override
                    public void includeRcFile() {
                        super.includeRcFile();
                        // include storedPLSQL
                        try {
                            super.include(storedPLSQL);
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
    }

    @Override
    public void getParameterType(ServerInterface srvInterface, SizedColumnTypes parameterTypes) {
        parameterTypes.addBool(TRACE);
        parameterTypes.addBool(WITHSTDERR);
        parameterTypes.addBool(DRYRUN);
    }

    @Override
    public void getPrototype(ServerInterface srvInterface, ColumnTypes argTypes, ColumnTypes returnType) {
        argTypes.addVarchar();
        returnType.addVarchar();
    }

    @Override
    public void getReturnType(ServerInterface srvInterface, SizedColumnTypes argTypes, SizedColumnTypes returnType) {
        argTypes.addVarchar(65000, "content");
        returnType.addVarchar(65000, "result");
    }

    @Override
    public ScalarFunction createScalarFunction(ServerInterface srvInterface) {
        return new PLSQL_Exe();
    }
}
