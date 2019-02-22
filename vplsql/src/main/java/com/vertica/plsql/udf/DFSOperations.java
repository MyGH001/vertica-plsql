
/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;

import com.vertica.sdk.*;

/**
 * DFS operations for managing PL/SQL code.
 */
public class DFSOperations {
    public static final String DFS_PLSQL_PATH = "/plsqlObjects";
    public static final String DFS_PLSQL_LASTMODIFIED_PATH = "/plsqlObjects.lastmodified";

    /**
     * Write PL/SQL code to DFS
     */
    public static void writeFile(ServerInterface srvInterface, String objName, String codePLSQL) {
        try {
            // write data to DSF file
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

            /*
             * Notice: can not update same DFS file multiple times in same UDF call, "ERROR
             * 5834: DFSFile: Can not create/ open for write [/plsqlObjects.lastmodified] as
             * another thread in the current statement has requested to create/write the
             * same file".
             * 
             * So, walkaround is call updateLastModified only after a batch of
             * CREATE/writeFile operators.
             */
            // updateLastModified(srvInterface);
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }

    /**
     * Read PL/SQL code from DFS according name pattern.
     * 
     * @param objName name pattern to read, null or empty string means all objects.
     */
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

    /**
     * Read PL/SQL code from one DFS
     */
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

    /**
     * Remove PL/SQL code from DFS with object name
     */
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

                updateLastModified(srvInterface);
            } else {
                srvInterface.log("WARNING: object [%s] does not exist!", objName);
            }

            return isDeleted;
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }

    /**
     * Update lastModified of saved PL/SQL code.
     * 
     * <b>Notice:</b> it should be called when DFS updated.
     */
    public static void updateLastModified(ServerInterface srvInterface) {
        try {
            // update lastModified
            long lastModified = System.currentTimeMillis();
            DFSFile tsFile = new DFSFile(srvInterface, DFS_PLSQL_LASTMODIFIED_PATH);
            if (tsFile.exists())
                tsFile.deleteIt(true);
            tsFile.create(DFSFile.DFSScope.NS_GLOBAL, DFSFile.DFSDistribution.HINT_REPLICATE);
            DFSFileWriter tsWriter = new DFSFileWriter(tsFile);
            tsWriter.open();
            ByteBuffer tsBuffer = ByteBuffer.allocate(Long.BYTES);
            tsBuffer.putLong(lastModified);
            tsBuffer.position(0);
            tsWriter.write(tsBuffer);
            tsWriter.close();

            PLSQLCache.clear();
        } catch (Throwable e) {
            throw new RuntimeException(String.format("ERROR: failed add PL/SQL caused by %s", e.getMessage()));
        }
    }

    /**
     * Get lastModified of saved PL/SQL.
     * 
     * @return the difference, measured in milliseconds, between the current time
     *         and midnight, January 1, 1970 UTC.
     */
    public static long getLastModified(ServerInterface srvInterface)
            throws DestroyInvocation, CharacterCodingException {
        DFSFile tsFile = new DFSFile(srvInterface, DFS_PLSQL_LASTMODIFIED_PATH);
        if (tsFile.exists()) {
            DFSFileReader tsReader = new DFSFileReader(tsFile);
            tsReader.open();
            int size = (int) tsReader.size();
            ByteBuffer buffer = ByteBuffer.allocate(size);
            tsReader.read(buffer, size);
            buffer.position(0);
            tsReader.close();
            return buffer.getLong();
        } else {
            return -1;
        }
    }
}