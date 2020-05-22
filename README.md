# Vertica PL/SQL

This project integrates  **HPLSQL**  as PL/SQL interpreter, stores PL/SQL code in Vertica database, and run them on Vertica.

Vertica PL/SQL can run in two deploy modes:

- **Standalone mode**. It co-exits with ETL or other applications, which can invoke PL/SQL procedures from CLI or Java code. In this mode, PL/SQL have no direct relation with Vertica, it just issue query to Vertica through JDBC remotely. The mode is supported by **hplsql** component, which is forked from and compatible with [HPLSQL of apache/hive](https://github.com/apache/hive/tree/master/hplsql).

  This is the recommended mode, as it only need one connection to Vertica for running PL/SQL code.

  ```text
  +----------------------+
  |   ETL/Applications   |           +------------+
  | +------------------+ |  JDBC     |  Vertica   |
  | | PL/SQL sandbox   |-+---------->|   Server   |
  | +------------------+ |           |            |
  | |  def scripts     | |           +------------+
  | |   for PROCs      | |
  | +------------------+ |
  | |       JAVA       | |
  | +------------------+ |
  +----------------------+
  ```

- **In Vertica mode**. This mode is supported by **vplsql** component, which contains some Vertica UDFs to manage PL/SQL procedures/functions, and run them on Vertical database.

  ```text
  +---------+         +-------------------------------------------------------------+
  +   Apps  +         |                       Vertica Server                        |
  +---------+         |  +---------------+               JDBC w/trust local auth    |
  | Vertica |---------+->|   Sessions    |<---------------------------------------+ |
  | Clients |         |  +---------------+                                        | |
  +---------+         |  |  Parser/Opt   |                                        | |
                      |  +---------------+  call     +------------------+         | |
                      |  |    EE         |---------->|    PL/SQL UDx    |         | |
                      |  +---------------+           +------------------+  query  | |
                      |  | SAL(ROS, DFS) |<----------|    sandbox       |---------+ |
                      |  +---------------+ meta/DFS  +------------------+           |
                      |                              |       JAVA       |           |
                      |                              +------------------+           |
                      +-------------------------------------------------------------+
  ```

## PL/SQL Reference

Vertica PL/SQL keeps compatible with **HPLSQL** of apache/hive, which is compatible to a large extent with Oracle PL/SQL, ANSI/ISO SQL/PSM (IBM DB2, MySQL, Teradata i.e), Teradata BTEQ, PostgreSQL PL/pgSQL (Netezza/GPDB), Transact-SQL (Microsoft SQL Server and Sybase) that allows you leveraging existing code and save cost for migration to Veritca.

**Notice:** pay attention with CURSOR and LOOP of PL/SQL, it will be performance killer if it refers to dataset with many rows.

### Language Reference Guide is [here.](http://hplsql.org/doc#language_elements)

### Vertica PL/SQL Management Functions

1. **PLSQL_CREATE** (USING PARAMETERS **content** VARCHAR) RETURNS VARCHAR

   Description: define procedures and functions wheth PL/SQL code.

   Parameters:

    - **content**: PL/SQL code, VARCHAR.
    - **withStderr**: return whether including info from stderr, BOOLEAN, optional, default is **false**. Giving true will be helpful for troubleshooting.

   Return: info of procedures and functions created, VARCHAR.

   Examples:

    ``` SQL
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
    ```

2. **PLSQL_DROP** (USING PARAMETERS **name** VARCHAR) RETURNS BOOLEAN

   Description: drop specified procedure or function.

   Parameters:

    - **name**: name for procedure/function, VARCHAR.

   Return: whether the specified procedure/function is dropped or not, BOOLEAN.

   Examples:

    ``` SQL
    select PLSQL_DROP(using parameters name='p_hello');
     PLSQL_DROP
    ------------
     t
    (1 row)
    ```

3. **PLSQL_EXEC** (**content** VARCHAR [USING PARAMETERS **trace** BOOLEAN, **withStderr** BOOLEAN, **dryRun** BOOLEAN) RETURNS VARCHAR

   Description: run specified procedure, function, or dynamic PL/SQL code.

   Arguments:

    - **content**: PL/SQL procedure/function name or code, VARCHAR.

   Parameters:

    - **trace**: return whether including detail runtime info, BOOLEAN, optional, default is **false**. True will be helpful for debugging.
    - **withStderr**: return whether including info from stderr, BOOLEAN, optional, default is **false**. Giving true will be helpful for troubleshooting.
    - **dryRun**: whether ignore database operations, BOOLEAN, optional, default is **false**. True with no influence your data.

   Return: executing result, VARCHAR.

   Examples:

    ``` SQL
    select PLSQL_EXEC($$p_hello('world')$$);
      PLSQL_EXEC
    ----------------
     Hello, world!
    1+2 = 3
    (1 row)

    select PLSQL_EXEC($$p_hello('world')$$ using parameters trace=true);
                   PLSQL_EXEC
    --------------------------------------------------
    EXEC PROCEDURE p_hello
    Ln:1 SET PARAM msg = world
    Ln:4 PRINT
    Hello, world!
    Ln:5 PRINT
    Ln:5 EXEC FUNCTION f_add2ints
    Ln:5 SET PARAM i1 = 1
    Ln:5 SET PARAM i2 = 2
    Ln:57 RETURN
    1+2 = 3
    (1 row)
    ```

4. **PLSQL_EXPORT** ([USING PARAMETERS **name** VARCHAR]) RETURNS BOOLEAN

   Description: export PL/SQL code of specified procedure or function.

   Parameters:

    - **name**: procedure/function, VARCHAR, procedure/function, default is null which means all stored PL/SQL objects.

   Return: DDL for the specified procedures/functions, VARCHAR.

   Examples:

    ``` SQL
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

## Get Started
  
### Installation
  
  You can install Vertica PL/SQL by [downloading](https://github.com/dingqiangliu/vertica-plsql/releases) the latest version of binary, or build it from the source code.
  
#### Requirements

- Java 1.7 or 1.8

- [Optional] Maven 2.2.1+ for build from source code.

- [Optional] Vertica JDBC Driver in maven repository for build from source code.

  ``` BASH
  $ wget -o /tmp/vertica-jdbc-9.3.0-0.jar https://www.vertica.com/client_drivers/9.3.x/9.3.0-0/vertica-jdbc-9.3.0-0.jar
  $
  $ mvn install:install-file -DgroupId=com.vertica -DartifactId=vertica-jdbc -Dversion=9.3.0-0 -Dpackaging=jar -DgeneratePom=true -Dfile=/tmp/vertica-jdbc-9.3.0-0.jar
  
  $ rm /tmp/vertica-jdbc-9.3.0-0.jar
  ```

- [Optional] Vertica SDK in maven repository for build from source.

  ``` BASH
  $ mvn install:install-file -DgroupId=com.vertica -DartifactId=vertica-sdk -Dversion=9.3.0-0 -Dpackaging=jar -DgeneratePom=true -Dfile=/opt/vertica/bin/VerticaSDK.jar
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

    optional step for test

    ``` BASH
    $ export HPLSQL_HOME="$(pwd)"
    $ test/standalone/testSuite.sh
    Begin testing ...
        testing case [Copy] ... passed.
        testing case [Datatype] ... passed.
        testing case [FunctionCall] ... passed.
        testing case [InsertInto] ... passed.
        testing case [Output] ... passed.
  
    Summary: tested 5 cases totally, all cases passed.
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
    $
    $ test/invertica/testSuite.sh # optional step for test
    Begin testing ...
        testing case [Copy] ... passed.
        testing case [Datatype] ... passed.
        testing case [FunctionCall] ... passed.
        testing case [InsertInto] ... passed.
        testing case [Output] ... passed.
  
    Summary: tested 5 cases totally, all cases passed.
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
