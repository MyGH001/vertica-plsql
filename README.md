# Vertica PL/SQL

This project integrates  **HPLSQL**  as PL/SQL interpreter, stores PL/SQL code in Vertica database, and run them on Vertica.

Vertica PL/SQL can run in two deploy modes:

- **Standalone mode**. It co-exits with ETL or other applications, which can invoke PL/SQL procedures from CLI or Java code. In this mode, PL/SQL have no direct relation with Vertica, it just issue query to Vertica through JDBC remotely. The mode is supported by **hplsql** component, which is forked from and compatible with [HPLSQL of apache/hive](https://github.com/apache/hive).
- **In Vertica mode**. This mode is supported by **vplsql** component, which contains some Vertica UDFs to manage PL/SQL procedures/functions, and run them on Vertical database.

## PL/SQL Reference

Vertica PL/SQL keeps compatible with **HPLSQL** of apache/hive, which is compatible to a large extent with Oracle PL/SQL, ANSI/ISO SQL/PSM (IBM DB2, MySQL, Teradata i.e), Teradata BTEQ, PostgreSQL PL/pgSQL (Netezza/GPDB), Transact-SQL (Microsoft SQL Server and Sybase) that allows you leveraging existing code and save cost for migration to Veritca.

Here is it's [Language Reference Guide](http://hplsql.org/doc#language_elements).

## Get Started

### Installation

You can install Vertica PL/SQL by [downloading](https://github.com/dingqiangliu/vertica-plsql/releases) the latest version of binary, or build it from the source code.

#### Requirements

- Java 1.7 or 1.8

- [Optional] Maven 2.2.1+ for build from source code.

- [Optional] Vertica JDBC Driver in maven repository for build from source code.

  ``` BASH
  $ wget -o /tmp/vertica-jdbc-9.2.0-0.jar https://www.vertica.com/client_drivers/9.2.x/9.2.0-0/vertica-jdbc-9.2.0-0.jar
  $
  $ mvn install:install-file -DgroupId=com.vertica -DartifactId=vertica-jdbc -Dversion=9.2.0-0 -Dpackaging=jar -DgeneratePom=true -Dfile=/tmp/vertica-jdbc-9.2.0-0.jar
  
  $ rm /tmp/vertica-jdbc-9.2.0-0.jar
  ```

- [Optional] Vertica SDK in maven repository for build from source.

  ``` BASH
  $ mvn install:install-file -DgroupId=com.vertica -DartifactId=vertica-sdk -Dversion=9.2.0-0 -Dpackaging=jar -DgeneratePom=true -Dfile=/opt/vertica/bin/VerticaSDK.jar
  $
  ```

#### [Optional] Build from source code
  
You will get binary package under [packaging/target/] after correctly running following commands under top of souce code tree.

``` BASH
mvn -DskipTests=true clean install

(cd packaging; mvn -DskipTests=true clean package -Pdist)
```

#### Setup Standalone mode

1. unpack binary file

    ``` BASH
    $ tar -xzvf packaging/target/vertica-plsql-${VERSION}-bin.tar.gz
    $
    $ cd vertica-plsql-${VERSION}/
    ```

2. correct access right of file [conf/hplsql-site.xml] and JDBC properties in it

    ``` BASH
    $ chmod 600 conf/hplsql-site.xml
    $
    ```

    ``` XML
    <configuration>
      <!-- ... -->
      <property>
        <name>hplsql.conn.verticaconn</name>
        <value>com.vertica.jdbc.Driver;jdbc:vertica://${verticaHost}:${verticaPort}/${verticaDBName};${verticaUser};${verticaPassword}</value>
        <description>Vertica connection</description>
      </property>
      <!-- ... -->
    </configuration>
    ```

3. run PL/SQL code in CLI

    ``` BASH
    $ bin/hplsql -e "Now()"
    2019-02-20 22:01:54.542
    $
    $ cat <<-'EOF' > /tmp/test.sql
      create or replace procedure p_hello(msg varchar)
      as
      begin
        print('Hello, ' || msg || '!');
      end;

      begin
        call p_hello('world')
      end;
    EOF
    $
    $ bin/hplsql -f /tmp/test.sql
    Hello, world!
    $
    $ rm -f /tmp/test.sql
    ```

4. run PL/SQL code in your Java application

    ``` JAVA
    import org.apache.hive.hplsql.Exec4Vertica;
    // ...
    // run PL/SQL code in string
    new Exec4Vertica().run( new String[]{ "-e", strPLSQL });
    // ...
    // run PL/SQL code from file
    new Exec4Vertica().run(new String[]{ "-f", strFileOfPLSQL });
    ```

#### Setup In Vertica mode

1. upload binary file to one of Vertica node, and unpack and intall it

    ``` BASH
    $ tar -xzvf packaging/target/vertica-plsql-${VERSION}-bin.tar.gz
    $
    $ cd vertica-plsql-${VERSION}/
    $
    $ export VSQL="/opt/vertica/bin/vsql -U dbadmin -w '${yourPassword}'"
    $
    $ $VSQL -f ddl/uninstall.sql # optional step for upgrade
    $ $VSQL -f ddl/install.sql
    ```

2. run Vertica PL/SQL UDFs to manage and run PL/SQL code through any client of Vertica

    ``` SQL
    -- drop procedure
    select PLSQL_DROP(using parameters name='p_hello');
     PLSQL_DROP 
    ------------
     t
    (1 row)

    -- create procedures and functions
    select PLSQL_CREATE(using parameters content=$$
      -- PL/SQL code ...
      create function f_add2ints(i1 int, i2 int)
      returns int
      as
      begin
        return i1+i2;
      end;

      create or replace procedure p_hello(msg varchar)
      as
      begin
        print('Hello, ' || msg || '!');
        print('1+2 = ' || f_add2ints(1, 2));
      end;
      -- PL/SQL code ...
      $$);
             PLSQL_CREATE
    -------------------------------
     Ln:3 CREATE FUNCTION f_add2ints
    Ln:10 CREATE PROCEDURE p_hello
    (1 row)
  
    -- execute procedure
    select PLSQL_EXEC($$p_hello('world')$$);
      PLSQL_EXEC
    ----------------
     Hello, world!
    1+2 = 3
    (1 row)

    -- export procedure/function
    select PLSQL_EXPORT(using parameters name='f_add2ints');
                     PLSQL_EXPORT
    ----------------------------------------------
     create function f_add2ints(i1 int, i2 int)
      returns int
      as
      begin
        return i1+i2;
      end
    (1 row)
    ```
