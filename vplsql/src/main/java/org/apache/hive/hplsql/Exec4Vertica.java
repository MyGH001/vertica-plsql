/* Copyright (c) DingQiang Liu(dingqiangliu@gmail.com), 2012 - 2019
 *
 * Description: just publish Exec interface
 *
 */

package org.apache.hive.hplsql;


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
        super.initOptions();
    }

    @Override
    public void includeRcFile() {
        super.includeRcFile();
    }

    @Override
    public void include(String content) throws Exception {
        super.include(content);
    }
}