package com.silly_diff.Util

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j

import java.util.regex.*

/**
 * Few XML functions required for diff application<br>
 * -----------------------------------------------<br>
 * You can use {@link SqlUtil#execute(Sql sql, String query, Map structure)} for Fast DB query<br>
 * or perform all operations manually using provided functions for your convenience.
 * */

@Slf4j
class SqlUtil{

    /**
     * You can predefine Sql connection parameters. This can be used when necessary to create multiple SQL connections and you need to store all values in same place.<br/>
     * 
     */
    public static Map<String, String> props;

    protected static Sql _createSqlConnProps(String name = "", Map outProps = null){
        Map _props = outProps != null ? outProps : props;
        if(_props != null && _props.containsKey(name + "DbUrl") && _props.containsKey(name + "DbUser") && _props.containsKey(name + "DbPass") && _props.containsKey(name + "DbDriver")){
            return Sql.newInstance(
                url: _props."${name}DbUrl".toString(),
                user: _props."${name}DbUser".toString(),
                password: _props."${name}DbPass".toString(),
                driver: _props."${name}DbDriver".toString()
            );
        }else{
            throw new Exception("EMException: Can't create SQL instance");
        }
    }

    /**
     * Create Sql connection from provided Properties Map. Use it with {@link SqlUtil#props} property
     * @param name {@link String} Sql connection name from provided {@link SqlUtil#props} property. System will use {@code "${name}DbUrl"} pattern for receiving Sql Connection parameters.
     * @return {@link Sql}
     */
    public static Sql createSqlConn(String name){
        _createSqlConnProps(name);
    }

    /** 
     * Create Sql connection from provided Properties Map
     * @param outProps {@link Map} of SQL connection parameters
     * @return {@link Sql}
     */
    public static Sql createSqlConn(Map outProps){
        _createSqlConnProps("", outProps);
    }

    /**
     * Creates SQL query string from file name.<br>
     * File with specified name will be looked among project's Resources
     * @param fileLocation {@link String} path to file with SQL script
     * @return {@link String} query from specified file
     */
    public static String getQueryFromFile(String fileLocation){
        String query = "";
        InputStream file = SqlUtil.class.classLoader.getResourceAsStream(fileLocation);

        if(file != null){
            file.readLines().each{String line ->
                query += "\r\n" + line;
            };
        }else{
            throw new Exception("EMException: SQL file $fileLocation not found");
        }

        Matcher matcher = Pattern.compile(/;\s$/).matcher(query);
        if(matcher.find()){
            query = matcher.replaceAll("");
        }

        return query;
    }

    /**
     * Transforms {@link List} of {@link String} elements into {@code IN('el1', 'el2', .., 'elN')} format that can be accepted by SQL processor.<br>
     * Only {@code keys} from {@code vars} that starts with {@code in_} will be treated as {@code IN()} parameters.<br>
     * However, SQL must contain variables without {@code in_} prefix. For example:<br>
     * {@code query='SELECT * FROM SomeTable WHERE SomeField IN (:some_field)'}<br>
     * {@code vars=['in_some_field' : ['a', 'b', 'c']]}
     * @param vars {@link Map} of params that will be passed to {@link Sql#rows(Map params, String query)} function.
     * @return {@link String} modified {@code query}
     * */
    public static String applyInVars(Map<String,Object> vars, String query){
        vars.each{String key, Object val ->
            if(key.substring(0,3) == "in_"){
                query = query.replace(":$key", val.toString());
            }
        }
        return query;
    }

    /**
     * Searches in provided {@code vars} for elements that can be used as SQL {@code IN('el1', 'el2', .., 'elN')} parameter.<br>
     * Only {@code keys} from {@code vars} that starts with {@code in_} will be treated as {@code IN()} parameters.<br>
     * For more information, please refer to {@link SqlUtil#applyInVars(Map vars, String query)}
     * @param vars {@link Map} of params that will be passed to {@link Sql#rows(Map params, String query)} function.
     * @return {@link Map} of {@code key-value} pairs that matches to specified pattern
     * */
    public static Map<String, String> getInParams(Map<String,Object> vars){
        HashMap<String, String> result = new HashMap<String, String>();
        vars.each{String key, Object val->
            if(val instanceof List || val instanceof Object[]){
                result["in_$key"] = "'${val.toList().join("','")}'";
            }
        };
        return result;
    }

    /**
     * Simple alias for {@link Sql#rows(Map params, String query)} or {@link Sql#rows(String query)} function with couple safety checks.<br>
     * Main reason for creation of this function is weird Groovy SQL API.<br>
     * 1) Different functions for selecting rows in DB when {@code params} exists or not.<br>
     * 2) Calling {@link Sql#rows(Map params, String query)} function with empty {@code params} element causes {@link Exception}.<br>
     * 3) Default API doesn't have any built in function for transforming {@link List} into SQL {@code IN('el1', 'el2', .., 'elN')} element.<br>
     * This function is Fatest way to query DB without manual safety checks of your SQL input parameters.<br>
     * @param sql {@link Sql} Object that holds DB connection
     * @param query {@link String} query String that will be tested for {@code IN('el1', 'el2', .., 'elN')} parameters
     * @param params {@link Map} params that holds SQL input parameters.
     * @return {@link List} of {@linl GroovyRowResult} returned by DB
     * */
    public static List<GroovyRowResult> execute(Sql sql, String query, Map<String, Object> params = null){
        List<GroovyRowResult> result;
        Map<String,String> inParams = getInParams(params);
        String _query = applyInVars(inParams, query);
        if(_query.indexOf(":") >=0 && params != null){
            result = sql.rows(params, _query);
        }else{
            result = sql.rows(_query);
        }
        return result;
    }

    /**
     * Simple alias for {@link Sql#rows(Map params, String query)} or {@link Sql#rows(String query)} function with couple safety checks.<br>
     * For more information refer to {@link SqlUtil#execute(Sql sql, String query, Map structure)}<br>
     * Only difference - this function accepts {@code fileName} that holds your SQL.<br>
     * {@link SqlUtil#getQueryFromFile(String fileLocation)} will be used for getting query.<br>
     * Then {@link SqlUtil#execute(Sql sql, String query, Map structure)} will be executed.
     * @param sql {@link Sql} Object that holds DB connection
     * @param fileLocation {@link String} path to file that holds your SQL query
     * @param params {@link Map} params that holds SQL input parameters.
     * @return {@link List} of {@linl GroovyRowResult} returned by DB
     * */
    public static List<GroovyRowResult> executeFile(Sql sql, String fileLocation, Map<String, Object> structure = null){
        String query = getQueryFromFile(fileLocation);
        return execute(sql, query, structure);
    }
}