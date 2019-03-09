/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 */

package org.apache.hive.hplsql;

/**
 * just publish Exec interface
 */
public class Exec4Vertica extends Exec {
    public String criticalExceptionMsg;

    public Exec4Vertica() {
        super();
    }

    public Conn getConn() {
        return super.conn;
    }

    @Override
    public void initOptions() {
        // hack Conn to mock vertica connection as MySQL for using native SQL except
        // Hive SQL
        super.conn = new Conn(this) {
            Conn.Type getType(String connStr) {
                if (connStr.contains("com.vertica.jdbc.")) {
                    return Conn.Type.MYSQL;
                } else {
                    return super.getType(connStr);
                }
            }
        };

        super.initOptions();
    }

    @Override
    public void includeRcFile() {
        super.includeRcFile();
    }
}