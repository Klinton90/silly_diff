package com.silly_diff.Helpers

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.NodeChild
import groovy.xml.XmlUtil
import com.silly_diff.Infostructure.AbstractDiffHelper
import com.silly_diff.Util.SqlUtil

import java.util.regex.Pattern

/**
 * Some useful hints for this helper<br>
 * ***********************************************************<br>
 * For comparing DB with XML that has complicate structure use aliases for columns.<br>
 * Use NodeNames from XmlTree in aliases, every NodeName should be separated with "." (dot).<br>
 * AttributeNames should be wrapped with "@" symbol.<br>
 * Nodes with the same names under the same parent, additionally should be marked with ">{unique_name}" badge<br>
 * ***********************************************************<br>
 * Some examples:<br>
 * <ul>
 *  <li>
 * 1) Unique names for Nodes: <br>
 * <pre>{@code
 * <data> 
 *     <dealer> 
 *         <En> 
 *             <name>DName1</name> 
 *             <address>DAddress1</address> 
 *         </En> 
 *         <Fr> 
 *             <name>DName1</name> 
 *             <address>DAddress1</address> 
 *         </Fr> 
 *     </dealer> 
 * </data> 
 * SELECT 
 *  `nameEn` AS "En.name", 
 *  `addressEn` AS "En.address", 
 *  `nameFr` AS "Fr.name", 
 *  `addressFr` AS "Fr.address" 
 * FROM DEALERS;
 * }</pre>
 *  </li>
 *  <li>
 * 2) Attributes: <br>
 * <pre>{@code
 * <data> 
 *     <dealer pos="pos1"> 
 *         <name value="Name1"> 
 *         <address value="Address1"> 
 *    </dealer> 
 * </data> 
 * SELECT 
 *  `pos` AS "@pos", 
 *  `name` AS "name.@value", 
 *  `address` AS "address.@value" 
 * FROM DEALERS;
 * }</pre>
 *  </li>
 *  <li>
 * 3) Nodes with same name under the one parent <br>
 * <pre>{@code
 * <data> 
 *     <dealer> 
 *         <Name>Name1</Name> 
 *         <SpecialProperties count="2"> 
 *             <property attr="1">NCV</property> 
 *             <property attr="2">Leaf</property> 
 *         </SpecialProperties> 
 *     <\dealer> 
 * </data> 
 * SELECT 
 *  d.`name` AS "Name", 
 *  IF(ncv.`Description` IS NULL, 0, 1) + IF(leaf.`Description` IS NULL, 0, 1) AS "SpecialProperties.@count", 
 *  ncv.`Description` AS "SpecialProperties.property.>1", 
 *  ncv.`FakeAttr` AS "SpecialProperties.property.>1.@attr", 
 *  leaf.`Description` AS "SpecialProperties.property.>2", 
 *  leaf.`FakeAttr` AS "SpecialProperties.property.>2.@attr" 
 * FROM DEALERS AS d 
 * LEFT JOIN DEALER_SPECIAL AS ncv ON ncv.DealerId = d.DealerId AND ncv.Description = "NCV" 
 * LEFT JOIN DEALER_SPECIAL AS ncv ON leaf.DealerId = d.DealerId AND leaf.Description = "Leaf";
 * }</pre>
 *  </li>
 * </ul>
 * ***********************************************************<br>
 * You can exclude some Columns or Nodes from comparison.<br>
 * Use "ignoredValue" variable to set value that will be compared with DB value.<br>
 * By default ignoredValue = "ignore".<br>
 * E.g. <br>
 * <pre>{@code
 * <data> 
 *     <dealer IgnoredAttr="bla-bla"> 
 *         <NAME>name<NAME> 
 *     </dealer> 
 * </data> 
 * SELECT "name" AS "NAME", "ignore" AS "@IgnoredAttr";
 * }</pre>
 * Selected row and XML matches, as "IgnoredAttr" will be excluded from comparison<br>
 * ***********************************************************<br>
 * By default CalcDiff() function works in "OrderlySafe" mode. It means, that nodes in Lists will be compared in exact order.<br>
 * You can change that by setting {@code orderlySafeMode == false}<br>
 * For example: <br>
 * <pre>{@code
 * <data> 
 *     <dealer attr="val1"> 
 *     <dealer attr="val2"> 
 *     <dealer attr="val3"> 
 * </data> 
 * SELECT "val2" AS "@attr" 
 * UNION 
 * SELECT "val1" AS "@attr" 
 * UNION 
 * SELECT "val3" AS "@attr"
 * }</pre>
 * <ul>
 *     <li>
 *      1)  {@code orderlySafeMode = false; // Default}<br>
 *          Result: Xml and DbResult match. No differences where found.<br>
 *     </li>
 *     <li>
 *      2)  {@code orderlySafeMode = true;}<br>
 *          Result: Only attr="val3" matches. attr="val1" and attr="val2" are marked as differences.<br>
 *     </li>
 * </ul>
 * ***********************************************************<br>
 * By default CompareNodes() function works in "NonOrderlySafe" mode. It means, that nodes with same name under one parent<br>
 * will be compared without checking order of unique identifier.<br>
 * Always use this mode, if your XML may have different number of nodes with same name under one parent.<br>
 * You can change that by setting {@code orderlySafeArrayMode == true}<br>
 * For example: <br>
 * <pre>{@code
 * <data> 
 *     <dealer attr="val1"> 
 *         <property>PreOwned<property> 
 *         <property>NCV<property>
 *         <property>Leaf<property>
 *     <\dealer>
 * </data>
 * SELECT
 *  "val1" AS "@attr"
 *  po.`Description` AS "property.>match",
 *  NULL AS "property.>fake"
 *  leaf.`Description` AS "property.>1",
 *  ncv.`Description` AS "property.>2"
 * FROM DEALERS AS p
 * LEFT JOIN DEALER_SPECIAL AS ncv ON ncv.DealerId = d.DealerId AND ncv.Description = "NCV"
 * LEFT JOIN DEALER_SPECIAL AS leaf ON leaf.DealerId = d.DealerId AND leaf.Description = "Leaf"
 * LEFT JOIN DEALER_SPECIAL AS po ON po.DealerId = d.DealerId AND po.Description = "PreOwned";
 * }</pre>
 * <ul>
 *     <li>
 *      1)  {@code orderlySafeArrayMode = false; // Default}<br>
 *          Result: Xml and DbResult match. No differences where found.<br>
 *     </li>
 *     <li>
 *      2)  {@code orderlySafeArrayMode = true;}<br>
 *          Result: Only <property>PreOwned<property> matches. <property>NCV<property> and <property>Leaf<property> are marked as differences.<br>
 *     </li>
 * </ul>
 * ***********************************************************<br>
 * When XML may delete some Nodes if those nodes doesn't have value, DB must return NULL value for these nodes.<br>
 * If your XML should NOT delete Nodes, and you want to Tests it, DB must return {@code String#EmptyString} or 1 {@code SPACE} character.<br>
 * {@code SPACE} hack is required for ORACLE DB, that returns {@link null} instead of {@code String#EmptyString}.<br>
 * When XML node should appear in XmlTree, but doesn't have value, both NULL and String.Empty will match with Node.<br>
 * Same thing works for attributes.<br>
 * For example:<br>
 * <pre>{@code
 * <dealers>
 *     <dealer>
 *         <name>Name1</name>
 *         <address>Address1</address>
 *         <phone>Phone1</phone>
 *     </dealer>
 *     <dealer>
 *         <name>Name2</name>
 *         <phone>Phone1</phone>
 *     </dealer>
 *     <dealer>
 *         <name>Name3</name>
 *         <address>Address3</address>
 *     </dealer>
 * </dealers>
 * SELECT `name`,
 *         IF(`address` = "" OR `address` IS NULL, NULL, `address`) AS "address",
 *         IF(`phone` = "" OR `phone` IS NULL, "", `phone`) AS "phone"
 * FROM DEALERS;
 * }</pre>
 * <ul>
 *  <li>Dealer1 - matches.</li>
 *  <li>Dealer2 - matches even in case when "address" Node doesn't exists, as system will skip DB columns with NULL value.</li>
 *  <li>Dealer3 - does NOT match, because or DB column contains String.Empty value, that indicates, that Node with same name must appear in XML.</li>
 * </ul>
 * ***********************************************************<br>
 * Note! You may skip columns from DB query if Nodes in XML are always empty.<br>
 * It applies to every node with children, but without own TextValue.<br>
 * For example:<br>
 * <pre>{@code
 * <dealers>
 *     <dealer>
 *         <SpecialProperties attr="attr2">
 *             <some1>val1<\some1>
 *         </SpecialProperties>
 *     <dealer>
 * </dealers>
 * SELECT  `attr` AS "SpecialProperties.@attr"
 *         `some1` AS "SpecialProperties.some1"
 * FROM DEALERS;
 * }</pre>
 * DB query doesn't return any values for "dealer" and "SpecialProperties" nodes.<br>
 * But DB and XML match anyway as empty XML nodes without TextValue are skipped.<br>
 * ***********************************************************<br>
 * NOTE! All fields in DB ResultSet that starts with "_" will be skipped during comparison.<br>
 * You can use this pattern for providing additional parameters to SubQuery.<br>
 * For more information please refer to {@link DbDiffHelper#includedNodes} parameter description.<br>
 * ***********************************************************<br>
 * To create XmlTreeMap (list of column names) for DB query, use static function<br> {@link com.silly_diff.Util.XmlUtil#mapXml(NodeChild node, String prefix = "")}
 * that consumes 1 parameter NodeChild,<br>
 * which is XML that you are going to use for comparing with each DbRow.<br>
 * Second parameter String @prefix should not be used in most cases,<br>
 * as it is used by function itself for recursive mapping.<br>
 * ***********************************************************<br>
 * Use these statements to Tests mapXml creation (new "xmlMap.txt" file with XmlTreeMap will be created in "/output" folder):<br>
 * <pre>{@code
 * public Tests(){
 *  NodeChild qaXML = (NodeChild)new XmlSlurper().parse(new File(props.outputDir+"/example/incentive.xml"));
 *  new File(outputLocation,"xmlMap.txt").withWriter{writer -> writer.write(DbDiffHelper.mapXml(XmlDiffHelper.walkXmlByPath(listPath, qaXML)[0]))};
 * }
 * }</pre>
 * ***********************************************************<br>
 * Use these statements to Tests ArrayMatching (case when few nodes with same name located under the same parent):<br>
 * <pre>{@code
 * public Tests(){
 *  String queryTest = """
 *                        SELECT 
 *                          NULL AS "some3", 
 *                          NULL AS "some3.@attr",
 *                          NULL AS "some2",
 *                          "" AS "some",
 *                          "abc1" AS "@count",
 *                          "qwe1-1" AS "property.>1.@attr",
 *                          "qwe1-2" AS "property.>2.@attr",
 *                          "zxc1-1-1" AS "property.>1.value.>1",
 *                          "zxc1-1-2" AS "property.>1.value.>2",
 *                          "zxc1-2-1" AS "property.>2.value.>1",
 *                          "zxc1-2-2" AS "property.>2.value.>2",
 *                          "rty1-1-1" AS "property.>1.value.>1.@attr",
 *                          "rty1-1-2" AS "property.>1.value.>2.@attr",
 *                          "rty1-2-1" AS "property.>2.value.>1.@attr",
 *                          "rty1-2-2" AS "property.>2.value.>2.@attr"
 *                       UNION
 *                       SELECT
 *                          "" AS "some3",
 *                          "val" AS "some3.@attr",
 *                          "" AS "some2",
 *                          "" AS "some",
 *                          "abc2" AS "@count",
 *                          "qwe2-1" AS "property.>1.@attr",
 *                          "qwe2-2" AS "property.>2.@attr",
 *                          "zxc2-1-1" AS "property.>1.value.>1",
 *                          "zxc2-1-2" AS "property.>1.value.>2",
 *                          "zxc2-2-1" AS "property.>2.value.>1",
 *                          "zxc2-2-2" AS "property.>2.value.>2",
 *                          "rty2-1-1" AS "property.>1.value.>1.@attr",
 *                          "rty2-1-2" AS "property.>1.value.>2.@attr",
 *                          "rty2-2-1" AS "property.>2.value.>1.@attr",
 *                          "rty2-2-2" AS "property.>2.value.>2.@attr";
 *                     """;
 *
 *  NodeChild qaXML = new XmlSlurper().parse(new File(props.outputDir+"/example/fullDealersResponse1.xml"));
 *  DbDiffHelper ddh = new DbDiffHelper(XmlDiffHelper.walkXmlByPath("dealer", qaXML), SqlUtil.Home.createSqlConn(props).rows(queryTest));
 *  ddh.calcDiff();
 * }
 * }</pre>
 * ***********************************************************
 */

@Slf4j
class DbDiffHelper extends AbstractDiffHelper {
    /**
     * By default CalcDiff() function works in "OrderlySafe" mode. It means, that nodes in Lists will be compared in exact order.<br>
     * You can change that by setting {@code orderlySafeMode == false}<br>
     * For example: <br>
     * <pre>{@code
     * <data> 
     *     <dealer attr="val1"> 
     *     <dealer attr="val2"> 
     *     <dealer attr="val3"> 
     * </data> 
     * SELECT "val2" AS "@attr" 
     * UNION 
     * SELECT "val1" AS "@attr" 
     * UNION 
     * SELECT "val3" AS "@attr"
     * }</pre>
     * <ul>
     *     <li>
     *      1)  {@code orderlySafeMode = false; // Default}<br>
     *          Result: Xml and DbResult match. No differences where found.<br>
     *     </li>
     *     <li>
     *      2)  {@code orderlySafeMode = true;}<br>
     *          Result: Only attr="val3" matches. attr="val1" and attr="val2" are marked as differences.<br>
     *     </li>
     * </ul>
     */
    public Boolean orderlySafeMode = true;

    /**
     * By default CompareNodes() function works in "NonOrderlySafe" mode. It means, that nodes with same name under one parent<br>
     * will be compared without checking order of unique identifier.<br>
     * Always use this mode, if your XML may have different number of nodes with same name under one parent.<br>
     * You can change that by setting {@code orderlySafeArrayMode == true}<br>
     * For example: <br>
     * <pre>{@code
     * <data> 
     *     <dealer attr="val1"> 
     *         <property>PreOwned<property> 
     *         <property>NCV<property>
     *         <property>Leaf<property>
     *     <\dealer>
     * </data>
     * SELECT
     *  "val1" AS "@attr"
     *  po.`Description` AS "property.>match",
     *  NULL AS "property.>fake"
     *  leaf.`Description` AS "property.>1",
     *  ncv.`Description` AS "property.>2"
     * FROM DEALERS AS p
     * LEFT JOIN DEALER_SPECIAL AS ncv ON ncv.DealerId = d.DealerId AND ncv.Description = "NCV"
     * LEFT JOIN DEALER_SPECIAL AS leaf ON leaf.DealerId = d.DealerId AND leaf.Description = "Leaf"
     * LEFT JOIN DEALER_SPECIAL AS po ON po.DealerId = d.DealerId AND po.Description = "PreOwned";
     * }</pre>
     * <ul>
     *     <li>
     *      1)  {@code orderlySafeArrayMode = false; // Default}<br>
     *          Result: Xml and DbResult match. No differences where found.<br>
     *     </li>
     *     <li>
     *      2)  {@code orderlySafeArrayMode = true;}<br>
     *          Result: Only <property>PreOwned<property> matches. <property>NCV<property> and <property>Leaf<property> are marked as differences.<br>
     *     </li>
     * </ul>
     */
    public Boolean orderlySafeArrayMode = false;

    /**
     * When use this class with Included SQL Queries, it may be more convenient to store SQL on disc as separate file rather than provide SQL string.
     * Default value is {@link true}
     * */
    public Boolean subQueryFromFile = true;

    /**
     * By default system expects to run the most strict version of comparison.<br>
     * It means that if DB result contains node that doesn't exist in XML - system will treat that situation as difference.<br>
     * However, if it is expected to store more data in DB than have in XML, you can simply skip all additional DB nodes.<br>
     * Default value is {@link false}
     * */
    public Boolean skipMissedXml = false;

    /**
     * By default system expects to run the most strict version of comparison.<br>
     * It means that if XML contains node that doesn't exist in DB - system will treat that situation as difference.<br>
     * However, if it is expect to test only couple DB-XML pairs instead of entire XML, you can simply skip all additional DB nodes.<br>
     * Generally, this option has been added in case, when you want to skip multiple Nodes in XML.<br>
     * Default value is {@link false}
     * */
    public Boolean skipMissedDb = false;

    /**
     * You can exclude some Columns or Nodes from comparison.<br>
     * Use "ignoredValue" variable to set value that will be compared with DB value.<br>
     * By default ignoredValue = "ignore".<br>
     * E.g. <br>
     * <pre>{@code
     * <data> 
     *     <dealer IgnoredAttr="bla-bla"> 
     *         <NAME>name<NAME> 
     *     </dealer> 
     * </data> 
     * SELECT "name" AS "NAME", "ignore" AS "@IgnoredAttr";
     * }</pre>
     * Selected row and XML matches, as "IgnoredAttr" will be excluded from comparison<br>
     * Accepts {@code Regexp} expressions
     */
    public String ignoredValue = "ignore";

    /**
     * Sometimes it is very complicate to query DB for all parameters under same row. In SQL we have "JOIN" mechanism.<br>
     * However, it is extremely hard to implement something similar in such small library.<br>
     * That's why it has been proposed to replace mentioned nodes with "Included Queries".<br>
     * For example:<br>
     * <pre>{@code<node>
     *     <name>Name1</name>
     *     <subNode>val1</subNode>
     *     <subNode>val2</subNode>
     * </node>
     * <node>
     *     <name>Name2</name>
     *     <subNode>val1</subNode>
     *     <subNode>val2</subNode>
     *     <subNode>val3</subNode>
     * </node>
     * }</pre><br>
     * As you can see, in provided example XML nodes has different amount of {@code <subNode>} elements.<br>
     * In SQL we would have something like:<br>
     * {@code SELECT t1.name, t2.subNode FROM Table1 AS t1 INNER JOIN Table2 AS t2 ON t1.field1 = t2.field1}
     * that will result in multiple rows.<br>
     * Proposed solution is:<br>
     * <ul>
     *     <li>
     *          Query1:<br>
     *          {@code SELECT name, field1 AS "_field1" FROM Table1}
     *     </li>
     *     <li>
     *         Query2:<br>
     *         {@code SELECT * FROM Table2 WHERE field1 = :_field1}
     *     </li>
     * </ul>
     * When we have both ResultSet, system will merge them into 1 {@link Map} that will be used for XML 2 DB comparison.
     * */
    public Map<String, String> includedNodes = new HashMap<String, String>();

    /**
     * {@link Sql} class that will be used for getting data for {@link DbDiffHelper#includedNodes}
     * */
    public Sql sql;
    
    public DbDiffHelper(List<NodeChild> xml, List<Map<String, String>> rows){
        source1 = xml;
        source2 = rows;
    }

    /**
     * Automatically scans {@code params} for elements that match with names of {@code public} parameters of {@link XmlDiffHelper} and assigns values to them.
     * @param params    {@link Map} of parameters that will be automatically mapped to {@code public} parameters of {@link XmlDiffHelper}
     */
    public void setupFromConfig(Map params){
        orderlySafeMode = (Boolean)params.get("orderlySafeMode", orderlySafeMode);
        orderlySafeArrayMode = (Boolean)params.get("orderlySafeArrayMode", orderlySafeArrayMode);
        subQueryFromFile = (Boolean)params.get("subQueryFromFile", subQueryFromFile);
        showErrors = (Boolean)params.get("showErrors", showErrors);
        skipMissedXml = (Boolean)params.get("skipMissedXml", skipMissedXml);
        skipMissedDb = (Boolean)params.get("skipMissedDb", skipMissedDb);
        ignoredValue = params.get("ignoredValue", ignoredValue).toString();
        includedNodes = (HashMap<String, String>)params.get("includedNodes", includedNodes);
        modifications1 = (HashMap<String, String>)params.get("modifications1", modifications1);
        modifications2 = (HashMap<String, String>)params.get("modifications2", modifications2);
    }

    /**
     * Performs calculation of differences between consumed {@link List}.<br>
     * Result of Diff calculation depends on parameters specified before calling this method.<br>
     * Every time when called, overrides Diff and output Xml.
     */
    public void calcDiff(){
        outputList1 = new ArrayList<NodeChild>();
        outputList2 = new ArrayList<HashMap<String, String>>();
        if(source2.size() > 0) {
            for(int i = 0; i < source2.size(); i++){
                for(int j = 0; j < includedNodes.keySet().size(); j++){
                    String prefix = includedNodes.keySet()[j];
                    String query = includedNodes.get(prefix, includedNodes.get(prefix.toUpperCase()));
                    List<GroovyRowResult> subRows;
                    if(subQueryFromFile){
                        subRows = SqlUtil.executeFile(sql, query, source2[i])
                    }else{
                        subRows = SqlUtil.execute(sql, query, source2[i]);
                    }

                    _addSubRowsToParentRow(subRows, prefix, source2[i]);
                }
            }
            if (orderlySafeMode) {
                int maxSize = Math.min(source1.size(), source2.size());
                for (int i = 0; i < maxSize; i++) {
                    if(watchDog != null && watchDog()){
                        log.info("WatchDog in Xml2Db");
                        break;
                    }
                    NodeChild curXml = source1[i];
                    Map<String, String> curRow = source2[i];
                    notified = false;
                    Map<String, String> shallowCopy = new HashMap<String, String>(curRow);
                    if(!compareNodes(curXml, shallowCopy) || !_isCompleteMatch(shallowCopy)){
                        if(curXml != null){
                            outputList1.add(curXml);
                        }
                        if(curRow != null){
                            outputList2.add(curRow);
                        }
                    }
                }
            } else {
                List<Map<String, String>> rowsCopy = source2.toList();
                for(int k = 0; k < source1.size(); k++){
                    NodeChild curXml = source1[k];
                    
                    if(watchDog != null && watchDog()){
                        log.info("WatchDog in Xml2Db");
                        break;
                    }
                    
                    Boolean match = false;
                    for (int i = 0; i < rowsCopy.size(); i++) {
                        notified = false;
                        Map<String, String> curRow = new HashMap<String, String>(rowsCopy[i]);
                        if (compareNodes(curXml, curRow) && _isCompleteMatch(curRow)) {
                            rowsCopy.remove(i);
                            match = true;
                            break;
                        }
                    }
                    if(!match){
                        outputList1.add(curXml);
                    }
                }
                outputList2 = rowsCopy;
            }
        }else{
            outputList1.addAll(source1);
        }
    }

    /**
     * Performs comparison between specified {@link groovy.util.slurpersupport.NodeChild} and {@link Map}.<br>
     * Result of comparison depends on parameters specified before calling this method.<br>
     * If notifications are enabled, may create Console messages with additional info if Diff found.<br>
     * Console messages may appear even if result is {@link true}. It depends on {@code orderlySafeArrayMode}.<br>
     * If {@code orderlySafeArrayMode = false}, Diff could be found between 2 elements, but match may happen<br>
     * with other element in future as system will continue comparison with all elements on the same level.
     * @param node
     * @param row
     * @param       Other parameters are optional and are used by function itself when performing recursive comparison. It is strongly suggested never use them.
     * @return      {@link true} if params match
     */
    public Boolean compareNodes(NodeChild node, Map<String, String> row, HashMap<String, ArrayList<Integer>> arrayNodes = null, Map<String, String> rowCopy = null, String prefix = ""){
        if(rowCopy == null){
            rowCopy = new HashMap<String, String>(row);
        }
        
        if(arrayNodes == null){
            arrayNodes = _parseColumns(row);
        }
        Boolean result = false;
        String newPrefix = prefix == "" ? "" : prefix + ".";
        
        Boolean isArray = false;
        if(!arrayNodes.containsKey(newPrefix)){
            if(arrayNodes.containsKey(newPrefix.toUpperCase())){
                isArray = true;
                newPrefix = newPrefix.toUpperCase();
            }
        }else{
            isArray = true;
        }
        
        if(isArray){
            Integer needle;
            ArrayList<Integer> arrayKeys = arrayNodes.get(newPrefix).sort();
            if(orderlySafeArrayMode){
                needle = 0;
                result = _compareNodes(node, row, arrayNodes, rowCopy, prefix, newPrefix, arrayKeys[0]);
            }else {
                for (int i = 0; i < arrayKeys.size(); i++) {
                    notified = false;
                    if (_compareNodes(node, row, arrayNodes, rowCopy, prefix, newPrefix, arrayKeys[i])) {
                        needle = i;
                        result = true;
                        break;
                    }
                }
            }

            if(needle != null){
                arrayKeys.remove(needle);
                arrayNodes.put(newPrefix, arrayKeys);
            }
        }else{
            result = _compareNodes(node, row, arrayNodes, rowCopy, prefix, newPrefix);
        }

        return result;
    }

    private Boolean _compareNodes(NodeChild node, Map<String, String> row, HashMap<String, ArrayList<Integer>> arrayNodes, Map<String, String> rowCopy, String prefix = "", String newPrefix = "", Integer arrayKey = -1){
        Boolean result = false;
        int attrsMatchCnt = 0;
        Map<String, NodeChild> attrs = node.attributes();

        Map<String, List<String>> debug = new HashMap<String, List<String>>();

        for(int i = 0; i < attrs.size(); i++) {
            String attrName = attrs.keySet()[i];
            String rowAttrKey = arrayKey >= 0 ? newPrefix + '>' + arrayKey + ".@" + attrName : newPrefix + "@" + attrName;
            if(!rowCopy.containsKey(rowAttrKey)){
                rowAttrKey = rowAttrKey.toUpperCase();
            }
            String rowAttr = rowCopy.get(rowAttrKey);

            debug.put(rowAttrKey, [rowAttr, attrs.get(attrName).toString()]);

            if((rowAttr == null && skipMissedDb) || (
                Pattern.compile(ignoredValue).matcher(rowAttr != null ? rowAttr : "").find()
                || _applyModifications(modifications1, rowAttrKey, rowAttr) == _applyModifications(modifications2, attrName, attrs.get(attrName).toString())
            )){
                attrsMatchCnt++;
                row.remove(rowAttrKey);
            }else{
                break;
            }
        }

        if(attrsMatchCnt == attrs.size()){
            String rowTextKey = arrayKey >= 0 ? newPrefix + '>' + arrayKey : (prefix == "" ? node.name() : prefix);
            if(!rowCopy.containsKey(rowTextKey)){
                rowTextKey = rowTextKey.toUpperCase();
            }
            String rowText = rowCopy.get(rowTextKey, null);

            debug.put("value", [rowText, node.localText().size() > 0 ? node.localText()[0].toString() : ""]);

            if(
                (rowText == null && skipMissedDb)
                || (
                    node.localText().size() == 0 
                    && (
                        rowText == "" 
                        || rowText == " " 
                        || rowText == null
                    )
                ) 
                || Pattern.compile(ignoredValue).matcher(rowText != null ? rowText : "").find() 
                || _applyModifications(modifications1, rowTextKey, rowText) == _applyModifications(modifications2, node.name(), _cleanNewLines(node.localText()[0]))
            ){
                row.remove(rowTextKey);
                int childrenCntMatch = 0;

                for(int i = 0; i < node.children().size(); i++){
                    NodeChild child = (NodeChild)node.children()[i];
                    if(compareNodes(child, row, arrayNodes, rowCopy, newPrefix + (arrayKey >= 0 ? '>' + arrayKey + "." : "") + child.name())){
                        childrenCntMatch++;
                    }else{
                        break;
                    }
                }

                if(childrenCntMatch == node.children().size()){
                    result = true;
                }
            }
        }
        
        if(!result){
            if((showErrors) && !notified){
                notified = true;
                String message = "First mismatch at [${prefix == "" ? node.name() : prefix}${arrayKey >= 0 ? '.>' + arrayKey : ''}] with params: [DB, XML] {";
                debug.each{String name, List<String> val ->
                    message += "#$name : '$val', "
                }
                log.info message + "}";
            }
        }

        return result;
    }
    
    protected Map<String, String> _addSubRowsToParentRow(List<Map<String, String>> subRows, String prefix, Map<String, String> row){
        for(int i = 0; i < subRows.size(); i++){
            Map<String, String> curRow = subRows[i]
            for(int j = 0; j < curRow.keySet().size(); j++){
                String curKey = curRow.keySet()[j];
                List<String> keyParts = curKey.split("\\.");
                String suffix = "";
                for(int k = 1; k < keyParts.size(); k++){
                    suffix += "." + keyParts[k];
                }

                row.put("${prefix}.${keyParts[0]}.>${i}${suffix}".toString(), curRow[curKey].toString());
            }
        }
        return row;
    }

    private Boolean _isCompleteMatch(Map<String, String> shallowCopy){
        Boolean completeMatch = true;
        for(int c = 0; c < shallowCopy.size(); c++){
            String key = shallowCopy.keySet()[c];
            if(!skipMissedXml && key.substring(0,1) != "_" && shallowCopy.get(key) != null && !Pattern.compile(ignoredValue).matcher(shallowCopy.get(key, "")).find()){
                log.info "XML matches with DB result, but DB has NON NULL value (looks like XML is missing this node): {${shallowCopy.keySet()[c]} : '${shallowCopy.get(shallowCopy.keySet()[c])}'}";

                completeMatch = false;
                break;
            }
        }
        return completeMatch;
    }

    private String _cleanNewLines(String source){
        if(source != null && source.substring(0,1) == "\n" && source.substring(source.size() - 1) == "\n"){
            source = source.substring(1);
            source = source.substring(0, source.size() - 1);
        }
        return source;
    }

    private HashMap<String, ArrayList<Integer>> _parseColumns(Map<String, String> row){
        HashMap<String, ArrayList<Integer>> arrayNodes = new HashMap<String, ArrayList<Integer>>();
        for(int i = 0; i < row.keySet().size(); i++){
            String key = row.keySet()[i];
            int pos = key.lastIndexOf(">");
            if(pos > -1){
                String _key = key.substring(0, pos);
                int suffix = key.substring(pos+1).split("\\.")[0].toInteger();
                ArrayList<Integer> tmp = arrayNodes.get(_key, new ArrayList<Integer>());
                if(!tmp.contains(suffix)) {
                    tmp.add(suffix);
                    arrayNodes.put(_key, tmp);
                }
            }
        }
        return arrayNodes;
    }

    /**
     * Returns {@link String} with XML that shows diff found in specified element
     * @deprecated This method will not be updated anymore. May be broken in future.
     * @param source    {@link true} to use {@code #source1}, {@link false} to use {@code #source2}
     * @return          Returns {@link String} with XML or JSON that shows diff found in specified element
     */
    @Deprecated
    public String getDiffString(Boolean source){
        String output = "";
        if(source){
            XmlDiffHelper xdh = new XmlDiffHelper(source1, toDel);
            List<NodeChild> diff = xdh.retainNodes(true);
            if(diff.size() > 0){
                diff.each{i ->
                    output += XmlUtil.serialize(i).substring(38);
                };
            }
        }else{
            List<Map<String, String>> rowsCopy = source2.toList();
            rowsCopy.retainAll(toDelRows);
            rowsCopy.each { i ->
                output += i.toMapString();
            };
        }

        return output;
    }

    /**
     * Performing Diff calculation between consumed {@link List}
     * and deletion from {@code #source1} elements that match with second list.
     * @deprecated This method will not be updated anymore. May be broken in future.
     * @return          Returns {@link List} of {@link groovy.util.slurpersupport.NodeChild} with nodes that do not match with second list.
     */
    @Deprecated
    public List<NodeChild> retainNodes(){
        List<NodeChild> _source = source1.toList();
        source2.each{i1 ->
            for(int i = 0; i < _source.size(); i++){
                if(compareNodes(_source[i], i1)){
                    _source.remove(i);
                    break;
                }
            }
        };
        return _source;
    }
}
