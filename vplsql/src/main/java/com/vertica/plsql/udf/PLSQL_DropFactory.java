/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import com.vertica.sdk.*;

/**
 * drop specified procedure or function.
 */
public class PLSQL_DropFactory extends ScalarFunctionFactory {
    public static final String NAME = "name";

    public class PLSQL_Drop extends ScalarFunction {
        private boolean isDeleted = false;

        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes) {
            super.setup(srvInterface, argTypes);

            String objName = "";
            if (srvInterface.getParamReader().containsParameter(NAME))
                objName = srvInterface.getParamReader().getString(NAME);
            else
                throw new UdfException(0, String.format("ERROR: paramenter [%s] should be provided", NAME));

            // drop file from DSF.
            try {
                this.isDeleted = DFSOperations.removeFile(srvInterface, objName);
            } catch (Throwable e) {
                throw new UdfException(0,
                        String.format("ERROR: failed export object[%s] caused by %s", objName, e.getMessage()));
            }
        }

        @Override
        public void processBlock(ServerInterface srvInterface, BlockReader argReader, BlockWriter resWriter)
                throws UdfException, DestroyInvocation {
            do {
                resWriter.setBoolean(this.isDeleted);
                resWriter.next();
            } while (argReader.next());
        }
    }

    @Override
    public void getParameterType(ServerInterface srvInterface, SizedColumnTypes parameterTypes) {
        parameterTypes.addVarchar(256, NAME);
    }

    @Override
    public void getPrototype(ServerInterface srvInterface, ColumnTypes argTypes, ColumnTypes returnType) {
        returnType.addBool();
    }

    @Override
    public ScalarFunction createScalarFunction(ServerInterface srvInterface) {
        return new PLSQL_Drop();
    }
}
