/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.util.Map;

import com.vertica.sdk.*;

/**
 * Cache for PLSQL, including serialization to disk to improve performance.
 */
public class PLSQLCache {
    private static Map<String, Object[]> data = null;

    /**
     * Enable cache parsed PLSQL tree
     */
    public static synchronized void init(ServerInterface srvInterface) {
        Serialization.init(srvInterface);
    }

    /**
     * Cache parsed PLSQL tree
     */
    public static synchronized void setData(Map<String, Object[]> value) {
        PLSQLCache.data = value;
    }

    /**
     * Put item to cache
     */
    public static synchronized void put(String name, String codePLSQL, Object tree) {
        if (PLSQLCache.data != null) {
            Object[] content = new Object[2];
            content[0] = codePLSQL;
            content[1] = tree;
            PLSQLCache.data.put(name, content);
        }
    }

    /**
     * Remove item from cache
     */
    public static synchronized void remove(String name) {
        if (PLSQLCache.data != null) {
            PLSQLCache.data.remove(name);
        }
    }

    public static synchronized Map<String, Object[]> getData() {
        return PLSQLCache.data;
    }

    public static boolean isValidate() {
        return PLSQLCache.data != null;
    }

    public static void clear() {
        PLSQLCache.data = null;
    }
}