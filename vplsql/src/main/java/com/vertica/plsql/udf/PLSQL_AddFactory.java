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

import org.apache.hive.hplsql.Exec4Vertica;
import org.apache.hive.hplsql.HplsqlParser;

import com.vertica.sdk.*;

public class PLSQL_AddFactory extends ScalarFunctionFactory {
    public static String CONTENT = "content";

    public class PLSQL_Add extends ScalarFunction {
        private String result = null;

        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes) {
            super.setup(srvInterface, argTypes);

            String codePLSQL = "";
            if (srvInterface.getParamReader().containsParameter(CONTENT))
                codePLSQL = srvInterface.getParamReader().getString(CONTENT);
            else
                throw new UdfException(0, String.format("ERROR: paramenter [%s] should be provided", CONTENT));

            try {
                // parsing PLSQL code
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                System.setOut(new PrintStream(out));
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
                this.result = getHPLSQLOutput(out.toString()).trim();
                System.setOut(null);
            } catch (Throwable e) {
                throw new UdfException(0, String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
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
            do {
                if (this.result != null)
                    resWriter.setString(this.result);
                else
                    resWriter.setStringNull();

                resWriter.next();
            } while (argReader.next());
        }
    }

    @Override
    public void getParameterType(ServerInterface srvInterface, SizedColumnTypes parameterTypes) {
        parameterTypes.addVarchar(65000, CONTENT);
    }

    @Override
    public void getPrototype(ServerInterface srvInterface, ColumnTypes argTypes, ColumnTypes returnType) {
        returnType.addVarchar();
    }

    @Override
    public void getReturnType(ServerInterface srvInterface, SizedColumnTypes argTypes, SizedColumnTypes returnType) {
        returnType.addVarchar(65000, "result");
    }

    @Override
    public ScalarFunction createScalarFunction(ServerInterface srvInterface) {
        return new PLSQL_Add();
    }
}
