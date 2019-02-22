/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import com.vertica.sdk.*;

/**
 * run specified procedure, function, or dynamic PL/SQL code.
 */
public class PLSQL_ExecFactory extends ScalarFunctionFactory {

    public class PLSQL_Exec extends ScalarFunction {
        private PLSQLExecutor executor = null;

        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes) {
            super.setup(srvInterface, argTypes);

            this.executor = PLSQLExecutor.newInstance(srvInterface);
        }

        @Override
        public void processBlock(ServerInterface srvInterface, BlockReader argReader, BlockWriter resWriter)
                throws UdfException, DestroyInvocation {
            do {
                if (argReader.getNumCols() < 1)
                    throw new UdfException(0, "The 1 argument should be provided for executing PLSQL code!");

                String codePLSQL = argReader.getString(0);
                if (codePLSQL != null && codePLSQL.length() > 0) {
                    resWriter.setString(this.executor.run(codePLSQL));
                } else {
                    resWriter.setStringNull();
                }

                resWriter.next();
            } while (argReader.next());
        }
    }

    @Override
    public void getParameterType(ServerInterface srvInterface, SizedColumnTypes parameterTypes) {
        PLSQLExecutor.setParameterType(srvInterface, parameterTypes);
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
        return new PLSQL_Exec();
    }
}
