/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import com.vertica.sdk.*;

/**
 * parse PL/SQL code to define procedures, functions and packages.
 */
public class PLSQL_CreateFactory extends ScalarFunctionFactory {
    public static String CONTENT = "content";

    public class PLSQL_Create extends ScalarFunction {
        private String result = null;
        private PLSQLExecutor executor = null;

        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes) {
            super.setup(srvInterface, argTypes);

            this.executor = PLSQLExecutor.newInstance(srvInterface);

            String codePLSQL = "";
            if (srvInterface.getParamReader().containsParameter(CONTENT))
                codePLSQL = srvInterface.getParamReader().getString(CONTENT);
            else
                throw new UdfException(0, String.format("ERROR: paramenter [%s] should be provided", CONTENT));

            // create PL/SQL objects when parsing PLSQL code
            this.result = this.executor.create(srvInterface, codePLSQL);
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
        PLSQLExecutor.setParameterType(srvInterface, parameterTypes);
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
        return new PLSQL_Create();
    }
}
