/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

import com.vertica.sdk.*;

/**
 * Cache for PLSQL, including serialization to disk to improve performance.
 */
public class PLSQLCache {
    private static final String CACHPATH = "/tmp/vplsql.cache";

    private static Object data = null;

    private static boolean initialized = false;

    /**
     * Enable parsed PLSQL tree serializable, and load latest cache from disk.
     * 
     * <b>Notice:</b> This methord should be callled before any HPLSQL code,
     * otherwise it will break the feature of parsed PL/SQL code serializaton.
     */
    public static synchronized void init(ServerInterface srvInterface) {
        boolean serialization = true;
        if (srvInterface != null && srvInterface.getParamReader().containsParameter(PLSQLExecutor.WITHCACHE))
            serialization = srvInterface.getParamReader().getBoolean(PLSQLExecutor.WITHCACHE);

        if (!PLSQLCache.initialized) {
            PLSQLCache.initialized = true;

            // dynamically mark Serializable on classes of antlr for serialization
            String[] arrClassName = new String[] { "org.antlr.v4.runtime.misc.IntegerList",
                    "org.antlr.v4.runtime.tree.Tree", "org.antlr.v4.runtime.IntStream",
                    "org.antlr.v4.runtime.TokenFactory", "org.antlr.v4.runtime.TokenSource" };
            try {
                ClassPool cp = ClassPool.getDefault();
                // dependency libraries may be not in system classpath
                cp.appendClassPath(new ClassClassPath(cp.getClass()));
                CtClass intSerial = cp.getCtClass(Serializable.class.getName());
                for (String className : arrClassName) {
                    CtClass cls = cp.getCtClass(className);
                    cls.addInterface(intSerial);
                    cls.toClass();
                }
            } catch (Throwable e) {
                throw new RuntimeException("Mark class serializable, but: " + e.getMessage());
            }

            // loading cache if serialized cache file if newer than DSF timestamp
            if (serialization) {
                ObjectInputStream in = null;
                try {
                    File fcache = new File(PLSQLCache.CACHPATH);
                    if (srvInterface == null || fcache.exists() && fcache.lastModified() >= DFSOperations.getLastModified(srvInterface)) {
                        in = new ObjectInputStream(new FileInputStream(PLSQLCache.CACHPATH));
                        PLSQLCache.data = in.readObject();
                    }
                } catch (Throwable e) {
                    // keep silent, maybe serialized cache is not compatible
                } finally {
                    if (in != null)
                        try {
                            in.close();
                        } catch (Throwable e) {
                        }
                }
            }
        }
    }

    /**
     * Cache parsed PLSQL tree, serialization if possible.
     */
    public static synchronized void setData(Object value, boolean serialization) throws IOException {
        if (serialization && PLSQLCache.data != value) {
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream(new FileOutputStream(PLSQLCache.CACHPATH));
                out.writeObject(value);
            } finally {
                if (out != null)
                    out.close();
            }
        }

        PLSQLCache.data = value;
    }

    public static synchronized Object getData() {
        return PLSQLCache.data;
    }

    public static boolean isValidate() {
        return PLSQLCache.data != null;
    }

    public static void clear() {
        PLSQLCache.data = null;
        File fcache = new File(PLSQLCache.CACHPATH);
        if (fcache.exists()) {
            fcache.delete();
        }
    }
}