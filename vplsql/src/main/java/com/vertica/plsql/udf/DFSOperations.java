
/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 * Description: run PL/SQL to define procedures, functions and packages.
 *
 */

package com.vertica.plsql.udf;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;

import com.vertica.sdk.*;

public class DFSOperations {
    public static String DFS_PLSQL_PATH = "/plsqlObjects";

    public static void writeFile(ServerInterface srvInterface, String objName, String codePLSQL) {
        try {
            if (objName == null)
                objName = "";
            else
                objName = objName.toUpperCase();

            DFSFile objFile = new DFSFile(srvInterface, DFS_PLSQL_PATH + "/" + objName);
            if (objFile.exists())
                objFile.deleteIt(true);
            objFile.create(DFSFile.DFSScope.NS_GLOBAL, DFSFile.DFSDistribution.HINT_REPLICATE);
            DFSFileWriter objWriter = new DFSFileWriter(objFile);
            objWriter.open();
            ByteBuffer buffer = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(codePLSQL));
            objWriter.write(buffer);
            objWriter.close();
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }

    public static String readFiles(ServerInterface srvInterface, String objName) {
        try {
            if (objName == null)
                objName = "";
            else
                objName = objName.toUpperCase();

            if (objName.length() == 0) {
                StringBuffer content = null;

                DFSFile dir = new DFSFile(srvInterface, DFS_PLSQL_PATH);
                if (dir.exists()) {
                    for (DFSFile objFile : dir.listFiles()) {
                        String strFile = readFile(srvInterface, objFile);
                        if (strFile != null) {
                            if (content == null)
                                content = new StringBuffer();
                            else if (content.length() > 0)
                                content.append("\r\n\r\n");

                            content.append(strFile);
                        }
                    }
                } else {
                    srvInterface.log("WARNING: no object exist!");
                }

                if (content == null)
                    return null;
                else
                    return content.toString();
            } else {
                DFSFile objFile = new DFSFile(srvInterface, DFS_PLSQL_PATH + "/" + objName);
                return readFile(srvInterface, objFile);
            }
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }

    private static String readFile(ServerInterface srvInterface, DFSFile objFile)
            throws DestroyInvocation, CharacterCodingException {
        String content = null;

        if (objFile.exists()) {
            DFSFileReader objReader = new DFSFileReader(objFile);
            objReader.open();
            int size = (int) objReader.size();
            ByteBuffer buffer = ByteBuffer.allocate(size);
            objReader.read(buffer, size);
            buffer.position(0);
            content = Charset.forName("UTF-8").newDecoder().decode(buffer).toString();
            objReader.close();
        } else {
            srvInterface.log("WARNING: object [%s] does not exist!", objFile.getName());
        }

        return content;
    }

    public static boolean removeFile(ServerInterface srvInterface, String objName) {
        try {
            boolean isDeleted = false;

            if (objName == null)
                objName = "";
            else
                objName = objName.toUpperCase();

            DFSFile objFile = new DFSFile(srvInterface, DFS_PLSQL_PATH + "/" + objName);
            if (objFile.exists()) {
                objFile.deleteIt(true);
                isDeleted = true;
            } else {
                srvInterface.log("WARNING: object [%s] does not exist!", objName);
            }

            return isDeleted;
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }
}