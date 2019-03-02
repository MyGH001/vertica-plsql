/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package com.vertica.plsql.udf;

import java.io.Serializable;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

import com.vertica.sdk.*;

/**
 * Serialization for PL/SQL tree.
 */
public class Serialization {
    private static boolean initialized = false;

    /**
     * Enable parsed PLSQL tree serializable, and load latest cache from disk.
     * 
     * <b>Notice:</b> This methord should be callled before any HPLSQL code,
     * otherwise it will break the feature of parsed PL/SQL code serializaton.
     */
    public static synchronized void init(ServerInterface srvInterface) {
        if (!Serialization.initialized) {
            Serialization.initialized = true;

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
        }
    }

}
