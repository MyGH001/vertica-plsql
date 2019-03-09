
/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package org.apache.hive.hplsql;

/**
 * Entry for Hplsql CLI with customized executor for Vertica
 */
public class Hplsql4Vertica {
    public static void main(String[] args) throws Exception {
        System.exit(new Exec4Vertica().run(args));
    }
}
