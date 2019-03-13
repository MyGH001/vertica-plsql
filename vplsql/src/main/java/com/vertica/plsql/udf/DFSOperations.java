
/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import com.vertica.sdk.*;

/**
 * DFS operations for managing PL/SQL code.
 */
public class DFSOperations {
    public static final String DFS_PLSQL_PATH = "/plsqlObjects";

    /**
     * Write PL/SQL code to DFS
     */
    public static synchronized void writeFile(ServerInterface srvInterface, String objName, String codePLSQL,
            Object tree) {
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

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(codePLSQL);

            ByteArrayOutputStream baosObject = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(baosObject));
            oos.writeObject(tree);
            oos.close();
            byte[] baObject = baosObject.toByteArray();
            dos.writeInt(baObject.length);
            dos.write(baObject);

            dos.close();
            ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
            objWriter.write(buffer);
            objWriter.close();

            PLSQLCache.put(objName, codePLSQL, tree);
        } catch (NotSerializableException e) {
            throw new RuntimeException("PL/SQL syntax error", e);
        } catch (Throwable e) {
            throw new RuntimeException(
                    String.format("ERROR: failed writing PL/SQL to database, caused by %s", e.getMessage()));
        }
    }

    /**
     * Read PL/SQL code from DFS according name pattern.
     * 
     * @param objName name pattern to read, null or empty string means all objects.
     * @return Map of {name: Object[codePLSQL, GZIPInputStream(ObjectInputStream)]}
     */
    public static synchronized Map<String, Object[]> readFiles(ServerInterface srvInterface, String objName) {
        try {
            Serialization.init(srvInterface);

            if (objName == null)
                objName = "";
            else
                objName = objName.toUpperCase();

            if (objName.length() == 0) {
                Map<String, Object[]> filesContent = null;

                DFSFile dir = new DFSFile(srvInterface, DFS_PLSQL_PATH);
                if (dir.exists()) {
                    for (DFSFile objFile : dir.listFiles()) {
                        Object[] content = readFile(srvInterface, objFile);
                        if (content != null) {
                            if (filesContent == null)
                                filesContent = new ConcurrentHashMap<String, Object[]>();
                            filesContent.put(objFile.getName().substring(DFS_PLSQL_PATH.length() + 1), content);
                        }
                    }
                } else {
                    srvInterface.log("WARNING: no object exist!");
                }

                return filesContent;
            } else {
                DFSFile objFile = new DFSFile(srvInterface, DFS_PLSQL_PATH + "/" + objName);
                Object[] content = readFile(srvInterface, objFile);
                if (content != null) {
                    Map<String, Object[]> filesContent = new ConcurrentHashMap<String, Object[]>(1);
                    filesContent.put(objName, content);
                    return filesContent;
                } else {
                    return null;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(
                    String.format("ERROR: failed reading PL/SQL from database, caused by %s", e.getMessage()));
        }
    }

    /**
     * Read PL/SQL code from one DFS
     * 
     * @return Object[codePLSQL, GZIPInputStream(ObjectInputStream)]
     */
    private static Object[] readFile(ServerInterface srvInterface, DFSFile objFile)
            throws DestroyInvocation, IOException, ClassNotFoundException {
        Object[] content = null;

        if (objFile.exists()) {
            DFSFileReader objReader = new DFSFileReader(objFile);
            try {
                objReader.open();
                int size = (int) objReader.size();
                if (size > 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(size);
                    objReader.read(buffer, size);
                    buffer.position(0);

                    ByteArrayInputStream baios = new ByteArrayInputStream(buffer.array());
                    DataInputStream din = new DataInputStream(baios);

                    content = new Object[2];
                    content[0] = din.readUTF();
                    int len = din.readInt();
                    if (len > 0) {
                        byte[] baObject = new byte[len];
                        din.read(baObject);
                        content[1] = baObject;
                    } else {
                        content[1] = null;
                    }

                    din.close();
                }
            } finally {
                objReader.close();
            }
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
                PLSQLCache.remove(objName);
                isDeleted = true;
            } else {
                srvInterface.log("WARNING: object [%s] does not exist!", objName);
            }

            return isDeleted;
        } catch (Throwable e) {
            throw new RuntimeException(
                    String.format("ERROR: failed removing PL/SQL from database, caused by %s", e.getMessage()));
        }
    }
}