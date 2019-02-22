/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */


package com.vertica.plsql.udf;

import com.vertica.sdk.*;

/**
 * export PL/SQL code of specified procedure or function.
 */
public class PLSQL_ExportFactory extends ScalarFunctionFactory {
    public static final String NAME = "name";

    public class PLSQL_Export extends ScalarFunction
    {
        private String content = null;
        
        @Override
        public void setup(ServerInterface srvInterface, SizedColumnTypes argTypes ) {
            super.setup(srvInterface, argTypes);

            String objName = "";
            if (srvInterface.getParamReader().containsParameter(NAME))
                objName = srvInterface.getParamReader().getString(NAME);

            //read content from DSF.
            try {
                this.content = DFSOperations.readFiles(srvInterface, objName);
            } catch (Throwable e) {
                throw new UdfException(0, String.format("ERROR: failed export object[%s] caused by %s", objName, e.getMessage()));
            }
        }

        @Override
        public void processBlock(ServerInterface srvInterface, BlockReader argReader, 
                                 BlockWriter resWriter) throws UdfException, DestroyInvocation {
            do {
                if(this.content != null)
                    resWriter.setString(this.content);
                else
                    resWriter.setStringNull();

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
        returnType.addVarchar();
    }

    @Override
    public void getReturnType(ServerInterface srvInterface, SizedColumnTypes argTypes, SizedColumnTypes returnType) {
        returnType.addVarchar(65000, "content");
    }

    @Override
	public ScalarFunction createScalarFunction(ServerInterface srvInterface) {
        return new PLSQL_Export();
    }
}
