package com.silly_diff.Helpers

import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.Attribute
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.NodeChildren
import groovy.xml.StreamingMarkupBuilder
import com.silly_diff.Infostructure.AbstractDiffHelper
import com.silly_diff.Util.XmlUtil as MyXmlUtil
import java.util.regex.Pattern

/**
 * Some useful hints for this Helper<br>
 * ****************************************************************************<br>
 * By default CalcDiff() function works in "NonOrderlySafe" mode. It means, that nodes in Lists will be compared without<br>
 * checking order of nodes.<br>
 * You can change that by setting "orderlySafeMode" == True.<br>
 * For example:<br>
 *         XmlList1:<br>
 * <pre>{@code
 * <Nodes>
 *     <Node1/>
 *     <Node2/>
 *     <Node3/>
 * </Nodes>
 * }</pre>
 *        Xml2List2:<br>
 * <pre>{@code
 * <Nodes>
 *     <Node1/>
 *     <Node3/>
 *     <Node2/>
 * </Nodes>
 * }</pre>
 * 1) {@code orderlySafeMode = false; // Default}<br>
 * Result: XmlLists match. No differences where found.<br>
 * 2) {@code orderlySafeMode = true;}<br>
 * Result: Only <Node1/> matches. <Node2/> and <Node3/> are marked as differences.<br>
 * *****************************************************************************<br>
 * By default compareNodes() works in "OrderlySafe" mode. It means, that ChildrenNodes will be compared in order as they comes<br>
 * in original XML feed.<br>
 * You can change that by setting "orderlySafeCompareChildrenMode" == False.<br>
 * NOTE! All children will be compared according to selected Mode. Even children included in child included in child etc.<br>
 * For example:<br>
 * <ul>
 *         <li>Xml1:<br>
 * <pre>{@code
 * <Dealers>
 *     <Dealer id="1">
 *         <Prop1>
 *         <Prop2>
 *         <Prop3>
 *     </Dealer>
 *     <Dealer id="2">
 *         <Prop1>
 *         <Prop2>
 *         <Prop3>
 *     </Dealer>
 * </Dealers>
 * }</pre></li>
 *        <li>Xml2:<br>
 * <pre>{@code
 * <Dealers><br>
 *     <Dealer id="1"><br>
 *         <Prop1><br>
 *         <Prop3><br>
 *         <Prop2><br>
 *     </Dealer><br>
 *     <Dealer id="2"><br>
 *         <Prop1><br>
 *         <Prop2><br>
 *         <Prop3><br>
 *     </Dealer><br>
 * </Dealers><br>
 * }</pre></li>
 * </ul>
 * <ul>
 *  <li>
 *     1) {@code orderlySafeCompareChildrenMode = false;}<br>
 *     Result: Xmls match. No differences where found.<br></li>
 *  <li>
 *      2) {@code orderlySafeCompareChildrenMode = true; //Default}<br>
 *      Result: Only <Dealer id="2"> matches. <Dealer id="1"> is marked as differences.<br>
 *  </li>
 * </ul>
 * *****************************************************************************<br>
 * NOTE! You can control orderSafe Modes separately for XmlList and XmlChildren in reason, that as per Autodata's workflow<br>
 * some WebServices (e.g. "marketing") do not have any default sorting of entities. But internal structure of these entities must follow specific structure.<br>
 * According to these requirements, default values in Helper are set to <br>
 * orderableSafeMode = false;                   // <----- Elements in List can be in any order<br>
 * orderableSafeCompareChildrenMode = true;     // <----- But order of internal elements (children) must match<br>
 * *****************************************************************************<br>
 * Note! At the moment Attribute matching is always NON orderableSafe!<br>
 * Unfortunately, Groovy->ChildNode->attributes() returns Map<String, String> that is sorted by AttributeName.<br>
 * As result, I don't have access to original order of attributes.<br>
 * *****************************************************************************<br>
 * Use these attributes to exclude from comparing ChildNodes and NodeAttributes:<br>
 * <ul>
 * <li>
 *     a) Ignorable Lists:<br>
 * <ul>
 *         <li>
 *             1) Attributes.<br>
 *             Put {@link List<String>} to ignoreAttrs with xmlPath to Attribute(s) that you want to ignore during comparing.<br>
 *             xmlPath in this case in list of NodeNames, separated with dot "." to your Attribute.<br>
 *             You don't have to put whole path to Attribute. <br>
 *             If you aware, that AttributeName is unique in XML, you can put it to the map.<br>
 *             If you want to exclude Attribute from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
 *             Note! That last element in tree must be Attribute name and it must be marked with "@". Mostly, it's done for making distinction with {@code #ignoreNodes}<br>
 *             For example:<br>
 * <pre>{@code
 * <dealer count="235">
 *      <SpecialProperties count="0"/>
 * </dealer>
 * }</pre>
 * {@code xdh.ignoreAttrs = ["SpecialProperties.@count"]; // "count" Attribute from "SpecialProperties" only will be ignored}
 *      </li>
 *      <li>
 *             2) Nodes. <br>
 *             Put {@link List<String>} to ignoreNodes with xmlPath to Node(s) that you want to ignore during comparing.<br>
 *             xmlPath in this case in list of NodeNames, separated with dot "." to your Node.<br>
 *             You don't have to put whole path to Node.<br>
 *             If you aware, that NodeName is unique in XML, you can put it to the map.<br>
 *             If you want to exclude Node from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
 *             Also you can delete Node that has {@link Attribute}. In this case last element in Path must be marked with '@' symbol.<br>
 *             Also you can delete Node that has another {@link NodeChild} or {@link Attribute}.<br>
 *             For doing that, separate your path with '>' symbol. Everything before '>' is related to current {@link NodeChild} itself,<br>
 *             everything after is related to {@link NodeChildren}. In this case you have to put whole path to children element.<br>
 *             For example:<br>
 * <pre>{@code
 * <dealer>
 *     <property1/>
 *     <SpecialProperties>
 *         <property1>
 *         <property2>
 *     </SpecialProperties>
 * </dealer>
 * }</pre>
 * {@code xdh.ignoreAttrs = ["SpecialProperties.property1"]; // "property1" Node from "SpecialProperties" only will be ignored}<br>
 *     </li>
 *     <li>
 *              3) Nodes with Value:<br>
 *              Put {@link Map<String, String>} to {@code ignoreNodesWValues}, where<br>
 *              {@code Key} - xmlPath to Node(s) that you want to ignore during comparing,<br>
 *              {@code Value} - value that specified Node must have to be ignored.<br>
 *              You don't have to put whole path to Node.<br>
 *              If you aware, that NodeName is unique in XML, you can put it to the map.<br>
 *              If you want to exclude Node from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
 *              Also you can delete Node that has {@link Attribute} equal to specified {@code Value}. In this case last element in Path must be marked with '@' symbol.<br>
 *              Also you can delete Node that has another {@link NodeChild} or {@link Attribute} that is equal to specified {@code Value}.<br>
 *              For doing that, separate your path with '>' symbol. Everything before '>' is related to current {@link NodeChild} itself,<br>
 *              everything after is related to {@link NodeChildren}. In this case you have to put whole path to children element.<br>
 *              For example:<br>
 * <pre>{@code
 * <dealer><br>
 *     <SpecialProperties><br>
 *         <property>NVD</property><br>
 *     </SpecialProperties><br>
 * </dealer><br>
 * <dealer><br>
 *     <SpecialProperties><br>
 *         <property>Special</property><br>
 *     </SpecialProperties><br>
 * </dealer><br>
 * }</pre>
 * {@code xdh.ignoreNodesWValues = ["dealer.SpecialProperties.property" = "Special"]; // Only Dealer2 will be ignored.}<br>
 *     </li>
 * </li>
 * <li>
 *     b) Ignorable Closures<br>
 *      <ul>
 *        <li>
 *             1) Attributes:<br>
 *             Assign {@link Closure} that accepts 1 {@link NodeChild} parameter to {@code #ignoreCommand}.<br>
 *             {@link Closure} must return {@link Boolean} value only.<br>
 *             {@link true}  -> Attribute will be ignored<br>
 *             {@link false} -> Attribute will be used for comparing<br>
 *             For example:<br>
 * <pre>{@code
 * <dealer count="235">
 *     <SpecialProperties count="0"/>
 * </dealer>
 * xdh.ignoreCommand = {NodeChild XML ->
 *     return XML.@count != "" && XML.parent().name() == "SpecialProperties";
 * };
 * }</pre>
 *              "count" Attribute from "SpecialProperties" only will be ignored<br>
 *         </li>
 *         </li>
 *              2) Nodes:<br>
 *              Assign {@link Closure} that accepts 1 {@link NodeChild} parameter to {@code ignoreCommand}.<br>
 *              {@link Closure} must return Boolean value only.<br>
 *              {@link true}   -> Node will be ignored<br>
 *              {@link false}  -> Node will be used for comparing<br>
 *              For example:<br>
 * <pre>{@code
 * <dealer>
 *     <SpecialProperties>
 *         <property>NVD</property>
 *         <property>Special</property>
 *     </SpecialProperties>
 * </dealer>
 * xdh.ignoreCommand = {NodeChild XML ->
 *     return XML.name() == "property" && XML.localText()[0] == "NVD";
 * };
 * }</pre>
 *              "property" Node with text "NVD" only will be ignored. "property" Node with text "Special" will be compared.<br>
 *          </li>
 *       </ul>
 * </li>
 * </ul>
 * *********************************************<br>
 * You can use static function {@link MyXmlUtil#walkXmlByPath(String listPath, NodeChild Xml)}
 * that accepts 2 parameters<br>
 * <ul>
 *      <li>{@code listPath} - string of NodeNames separated with "." (dots)</li>
 *      <li>{@code xml} - original XML</li>
 * </ul>
 * to get List of nodes that will be consumed by class itself.<br>
 * *********************************************<br>
 * Use that to Tests {@link XmlDiffHelper} for {@code ignoreFilter}<br>
 * <pre>{@code
 * public Tests(){
 *     NodeChild qaXML = new XmlSlurper().parse(new File(props.outputDir+"/example/qa_diff-getDealers"));
 *     NodeChild prodXML = new XmlSlurper().parse(new File(props.outputDir+"/example/prod_diff-getDealers"));
 * 
 *     XmlDiffHelper xdh = new XmlDiffHelper(XmlDiffHelper.walkXmlByPath(listPath, qaXML), XmlDiffHelper.walkXmlByPath(listPath, prodXML));
 *     xdh.setIgnoreAttrs(["SpecialProperties.@count"]);
 *     xdh.ignoreCommand = this.&filter;
 *     xdh.calcDiff();
 * }
 * 
 * public filter(){
 *     return XML.name() == "property" && XML.localText()[0] == "NCV";
 * }
 * }</pre>
 * ****************************************************<br>
 * */
@Slf4j
public class XmlDiffHelper extends AbstractDiffHelper {
    public List<String> needleHelper = new ArrayList<String>();
    
    /**
     * Put {@link List<String>} to ignoreAttrs with xmlPath to Attribute(s) that you want to ignore during comparing.<br>
     * xmlPath in this case in list of NodeNames, separated with dot "." to your Attribute.<br>
     * You don't have to put whole path to Attribute. <br>
     * If you aware, that AttributeName is unique in XML, you can put it to the map.<br>
     * If you want to exclude Attribute from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
     * Note! That last element in tree must be Attribute name and it must be marked with "@". Mostly, it's done for making distinction with {@code #ignoreNodes}<br>
     * For example:<br>
     * <pre>{@code
     * <dealer count="235">
     *      <SpecialProperties count="0"/>
     * </dealer>
     * }</pre>
     * {@code xdh.ignoreAttrs = ["SpecialProperties.@count"]; // "count" Attribute from "SpecialProperties" only will be ignored}
     */
    public List<String> ignoreAttrs = new ArrayList<String>();

    /**
     * Put {@link List<String>} to ignoreNodes with xmlPath to Node(s) that you want to ignore during comparing.<br>
     * xmlPath in this case in list of NodeNames, separated with dot "." to your Node.<br>
     * You don't have to put whole path to Node.<br>
     * If you aware, that NodeName is unique in XML, you can put it to the map.<br>
     * If you want to exclude Node from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
     * Also you can delete Node that has {@link groovy.util.slurpersupport.Attribute}. In this case last element in Path must be marked with '@' symbol.<br>
     * Also you can delete Node that has another {@link groovy.util.slurpersupport.NodeChild} or {@link groovy.util.slurpersupport.Attribute}.<br>
     * For doing that, separate your path with '>' symbol. Everything before '>' is related to current {@link groovy.util.slurpersupport.NodeChild} itself,<br>
     * everything after is related to {@link groovy.util.slurpersupport.NodeChildren}. In this case you have to put whole path to children element.<br>
     * For example:<br>
     * <pre>{@code
     * <dealer>
     *     <property1/>
     *     <SpecialProperties>
     *         <property1>
     *         <property2>
     *     </SpecialProperties>
     * </dealer>
     * }</pre>
     * {@code xdh.ignoreAttrs = ["SpecialProperties.property1"]; // "property1" Node from "SpecialProperties" only will be ignored}<br>
     */
    public List<String> ignoreNodes = new ArrayList<String>();

    /**
     * Put {@link Map<String, String>} to {@code ignoreNodesWValues}, where<br>
     * {@code Key} - xmlPath to Node(s) that you want to ignore during comparing,<br>
     * {@code Value} - value that specified Node must have to be ignored. {@code RegExp} expression are also accepted and will be tested.<br>
     * You don't have to put whole path to Node.<br>
     * If you aware, that NodeName is unique in XML, you can put it to the map.<br>
     * If you want to exclude Node from specific ParentNode only, then you have to put ParentNodeName into the xmlPath.<br>
     * Also you can delete Node that has {@link groovy.util.slurpersupport.Attribute} equal to specified {@code Value}. In this case last element in Path must be marked with '@' symbol.<br>
     * Also you can delete Node that has another {@link groovy.util.slurpersupport.NodeChild} or {@link groovy.util.slurpersupport.Attribute} that is equal to specified {@code Value}.<br>
     * For doing that, separate your path with '>' symbol. Everything before '>' is related to current {@link groovy.util.slurpersupport.NodeChild} itself,<br>
     * everything after is related to {@link groovy.util.slurpersupport.NodeChildren}. In this case you have to put whole path to children element.<br>
     * For example:<br>
     * <pre>{@code
     * <dealer><br>
     *     <SpecialProperties><br>
     *         <property>NVD</property><br>
     *     </SpecialProperties><br>
     * </dealer><br>
     * <dealer><br>
     *     <SpecialProperties><br>
     *         <property>Special</property><br>
     *     </SpecialProperties><br>
     * </dealer><br>
     * }</pre>
     * {@code xdh.ignoreNodesWValues = ["dealer.SpecialProperties.property" = "Special"]; // Only Dealer2 will be ignored.}<br>
     */
    public HashMap<String, String> ignoreNodesWValues = new HashMap<String, String>();

    /**
     * Assign {@link groovy.lang.Closure} that accepts 1 {@link groovy.util.slurpersupport.NodeChild} parameter to {@code ignoreCommand}.<br>
     * {@link groovy.lang.Closure} must return Boolean value only.<br>
     * {@link true}   -> Node will be ignored<br>
     * {@link false}  -> Node will be used for comparing<br>
     * For example:<br>
     * <pre>{@code
     * <dealer>
     *     <SpecialProperties>
     *         <property>NVD</property>
     *         <property>Special</property>
     *     </SpecialProperties>
     * </dealer>
     * xdh.ignoreCommand = {NodeChild XML ->
     *     return XML.name() == "property" && XML.localText()[0] == "NVD";
     * };
     * }</pre>
     *              "property" Node with text "NVD" only will be ignored. "property" Node with text "Special" will be compared.<br>
     */
    public Closure<NodeChild> ignoreCommand;

    /**
     * By default CalcDiff() function works in "NonOrderlySafe" mode. It means, that nodes in Lists will be compared without<br>
     * checking order of nodes.<br>
     * You can change that by setting "orderlySafeMode" == True.<br>
     * For example:<br>
     *         XmlList1:<br>
     * <pre>{@code
     * <Nodes>
     *     <Node1/>
     *     <Node2/>
     *     <Node3/>
     * </Nodes>
     * }</pre>
     *        Xml2List2:<br>
     * <pre>{@code
     * <Nodes>
     *     <Node1/>
     *     <Node3/>
     *     <Node2/>
     * </Nodes>
     * }</pre>
     * 1) {@code orderlySafeMode = false; // Default}<br>
     * Result: XmlLists match. No differences where found.<br>
     * 2) {@code orderlySafeMode = true;}<br>
     * Result: Only <Node1/> matches. <Node2/> and <Node3/> are marked as differences.<br>
     */
    public Boolean orderlySafeMode = false;

    /**
     * By default compareNodes() works in "OrderlySafe" mode. It means, that ChildrenNodes will be compared in order as they comes<br>
     * in original XML feed.<br>
     * You can change that by setting "orderlySafeCompareChildrenMode" == False.<br>
     * NOTE! All children will be compared according to selected Mode. Even children included in child included in child etc.<br>
     * For example:<br>
     * <ul>
     *         <li>Xml1:<br>
     * <pre>{@code
     * <Dealers>
     *     <Dealer id="1">
     *         <Prop1>
     *         <Prop2>
     *         <Prop3>
     *     </Dealer>
     *     <Dealer id="2">
     *         <Prop1>
     *         <Prop2>
     *         <Prop3>
     *     </Dealer>
     * </Dealers>
     * }</pre></li>
     *        <li>Xml2:<br>
     * <pre>{@code
     * <Dealers><br>
     *     <Dealer id="1"><br>
     *         <Prop1><br>
     *         <Prop3><br>
     *         <Prop2><br>
     *     </Dealer><br>
     *     <Dealer id="2"><br>
     *         <Prop1><br>
     *         <Prop2><br>
     *         <Prop3><br>
     *     </Dealer><br>
     * </Dealers><br>
     * }</pre></li>
     * </ul>
     * <ul>
     *  <li>
     *     1) {@code orderlySafeCompareChildrenMode = false;}<br>
     *     Result: Xmls match. No differences where found.<br></li>
     *  <li>
     *      2) {@code orderlySafeCompareChildrenMode = true; //Default}<br>
     *      Result: Only <Dealer id="2"> matches. <Dealer id="1"> is marked as differences.<br>
     *  </li>
     * </ul>
     */
    public Boolean orderlySafeChildrenMode = true;
    
    public XmlDiffHelper(List<NodeChild> xml1, List<NodeChild> xml2){
        source1 = xml1;
        source2 = xml2;
    }

    /**
     * Automatically scans {@code params} for elements that match with names of {@code public} parameters of {@link XmlDiffHelper}
     * and assigns values to them.
     * @param params    {@link Map} of parameters that will be automatically mapped to {@code public} parameters of {@link XmlDiffHelper}
     */
    public void setupFromConfig(Map params){
        orderlySafeMode = params.get("orderlySafeMode", orderlySafeMode);
        orderlySafeChildrenMode = params.get("orderlySafeChildrenMode", orderlySafeChildrenMode);
        showErrors = params.get("showErrors", showErrors);
        ignoreAttrs = (List<String>)params.get("ignoreAttrs", ignoreAttrs);
        ignoreNodes = (List<String>)params.get("ignoreNodes", ignoreNodes);
        ignoreNodesWValues = (HashMap<String, String>)params.get("ignoreNodesWValues", ignoreNodesWValues);
        if(params.containsKey("ignoreCommand" + "_path")){
            ignoreCommand = _getCommandFromParams(params, "ignoreCommand" + "_path");
        }
        modifications1 = (HashMap<String, String>)params.get("modifications1", modifications1);
        modifications2 = (HashMap<String, String>)params.get("modifications2", modifications2);
        needleHelper = (List<String>)params.get("needleHelper", needleHelper);
        needleHelper.removeAll(['', null]);
    }

    /**
     * Performs calculation of differences between consumed {@link List} of {@link groovy.util.slurpersupport.NodeChild}.
     * Result of Diff calculation depends on parameters specified before calling this method.
     * Every time when called, overrides Diff and output Xml.
     */
    public void calcDiff(){
        outputList1 = new ArrayList<NodeChild>();
        outputList2 = new ArrayList<NodeChild>();

        _deleteIgnoredNodes(source1).each{NodeChild xml->
            outputList1.add(new XmlSlurper().parseText(new StreamingMarkupBuilder().bindNode(_deleteIgnoredElements(xml)).toString()));
        };
        _deleteIgnoredNodes(source2).each{NodeChild xml->
            outputList2.add(new XmlSlurper().parseText(new StreamingMarkupBuilder().bindNode(_deleteIgnoredElements(xml)).toString()));
        };

        Iterator<NodeChild> xmlIter1 = outputList1.listIterator();
        Iterator<NodeChild> xmlIter2 = outputList2.listIterator();
        while(!(watchDog != null && watchDog()) && xmlIter1.hasNext()){
            NodeChild curXml1 = xmlIter1.next();
            if(orderlySafeMode){
                notified = false;
                if(compareNodes(curXml1, xmlIter2.next())){
                    xmlIter1.remove();
                    xmlIter2.remove();
                }
            }else if(needleHelper.size() > 0){
                for(int j = 0; j < outputList2.size(); j++){
                    Boolean found = true;
                    NodeChild curXml2 = outputList2[j];
                    for(int k = 0; k < needleHelper.size(); k++){
                        if(MyXmlUtil.walkXmlByPathForValue(needleHelper[k], curXml1) != MyXmlUtil.walkXmlByPathForValue(needleHelper[k], curXml2)){
                            found = false;
                            break;
                        }
                    }
                    if(found && compareNodes(curXml1, curXml2)){
                        outputList2.remove(j);
                        xmlIter1.remove();
                        break;
                    }
                }
            }else{
                Iterator<NodeChild> _xmlIter2 = outputList2.listIterator();
                while(_xmlIter2.hasNext()){
                    notified = false;
                    if(compareNodes(curXml1, _xmlIter2.next())){
                        xmlIter1.remove();
                        _xmlIter2.remove();
                        break;
                    }
                }
            }
        }
    }
    
    protected NodeChild _deleteIgnoredElements(NodeChild node){
        List<String> tdas = _getIgnoredAttrs(node);
        for(int i = 0; i < tdas.size(); i++){
            node.attributes().remove(tdas[i]);
        }
        
        List<NodeChild> tdns = new ArrayList<NodeChild>();
        List<NodeChild> nodeChildren = _deleteIgnoredNodes(node.children().list(), tdns);
        for(int i = 0; i < tdns.size(); i++){
            tdns[i].replaceNode{};
        }

        for(int i = 0; i < nodeChildren.size(); i++){
            _deleteIgnoredElements(nodeChildren[i]);
        }
        
        return node;
    }

    /**
     * Performs comparison between specified {@link groovy.util.slurpersupport.NodeChild}.
     * Result of comparison depends on parameters specified before calling this method.
     * If notifications are enabled, may create Console messages with additional info if Diff found.
     * Console messages may appear even if result is {@link true}. It depends on {@code orderlySafeChildrenMode}.
     * If {@code orderlySafeChildrenMode = false}, Diff could be found between 2 child nodes, but match may happen
     * with other child node in future as system will continue comparison with all children on the same level.
     * @param node1
     * @param node2
     * @return      {@link true} if both {@link groovy.util.slurpersupport.NodeChild} matches
     */
    public Boolean compareNodes(NodeChild node1, NodeChild node2){
        Boolean result = false;

        Map<String, List<String>> debug = new HashMap<String, List<String>>();

        if(node1 != null && node2 != null && node1.name() == node2.name()){
            Map<String, String> attrs1 = node1.attributes();
            Map<String, String> attrs2 = node2.attributes()

            int aCnt1 = attrs1.size();
            int aCnt2 = attrs2.size();
            debug.put("attrSize", [aCnt1.toString(), aCnt2.toString()]);
            
            if(aCnt1 == aCnt2 && (aCnt1 == 0 || _compareAttrs(attrs1, attrs2, debug))){
                List<NodeChild> nodeChildren1 = node1.children().list();
                List<NodeChild> nodeChildren2 = node2.children().list();

                int cCnt1 = nodeChildren1.size();
                int cCnt2 = nodeChildren2.size();
                debug.put("ChildrenCount", [cCnt1.toString(), cCnt2.toString()]);
                
                if(cCnt1 == cCnt2 && (cCnt1 == 0 || _compareChildren(nodeChildren1, nodeChildren2))){
                    if(node1.localText().size() == node2.localText().size()){
                        int textMatchCnt = 0;
                        for(int i = 0; node1.localText().size() > i; i++){
                            String text1 = node1.localText().getAt(i);
                            String text2 = node2.localText().getAt(i);
                            
                            debug.put(node1.name(), [_applyModifications(modifications1, node1.name(), text1), _applyModifications(modifications2, node2.name(), text2)]);

                            if(text1 == text2){
                                textMatchCnt++;
                            }else{
                                break;
                            }
                        }
                        
                        result = textMatchCnt == node1.localText().size();
                    }
                }
            }
        }

        if(!result){
            Boolean _debug = false;
            if((showErrors || _debug) && !notified){
                notified = true;
                String message = "First mismatch for [${node1.name()}] and [${node2.name()}] ";
                if(debug.size() > 0){
                    message +=  "with params: {";
                    debug.each{String name, List<String> val ->
                        message += "#$name : '$val', "
                    }
                }else{
                    message += "{as names are different"
                }

                log.info message + "}";
            }
        }

        return result;
    }
    
    private _getIgnoredAttrs(NodeChild node){
        ArrayList<String> result = new ArrayList<String>();
        Map<String, String> attrs = node.attributes().clone();
        for(int i = 0; attrs.size() > i; i++){
            String attrName = attrs.keySet()[i];
            if((ignoreCommand != null && ignoreCommand(node)) || _isAttrIgnorable(node, attrName)){
                result.add(attrName);
            }
        }
        return result;
    }

    private Boolean _isAttrIgnorable(NodeChild node, String attrName){
        Boolean result = false;
        for(int i = 0; i < ignoreAttrs.size(); i++){
            String[] _parts = ignoreAttrs[i].split("@");
            if(_parts.size() == 2 && attrName == _parts[1] && MyXmlUtil.isPathInXmlTree(ignoreAttrs[i], node)){
                result = true;
                break;
            }
        }
        return result;
    }
    
    private Boolean _compareAttrs(Map<String, String> attrs1, Map<String, String> attrs2, Map<String, List<String>> debug){
        int matchCnt = 0;
        for(int i = 0; i < attrs1.size(); i++){
            String curAttr = attrs1.keySet()[i];
            String attr1 = attrs1.get(curAttr);
            String attr2 = attrs2.get(curAttr);
            
            debug.put(curAttr, [attr1, attr2]);

            if(_applyModifications(modifications1, curAttr, attr1) == _applyModifications(modifications2, curAttr, attr2)){
                matchCnt++;
            }else{
                break;
            }
        }
        
        return matchCnt == attrs1.size();
    }
    
    private List<NodeChild> _deleteIgnoredNodes(List<NodeChild> Xml, List<NodeChild> _ref = null){
        List<NodeChild> xml = Xml.toList();
        ArrayList<Integer> tmp = new ArrayList<Integer>();
        for(int i = 0; xml.size() > i; i++){
            NodeChild child = xml[i];
            if((ignoreCommand != null && ignoreCommand(child)) || _isNodeIgnorable(child)){
                tmp.add(i);
            }
        }
        for(int j = tmp.size() - 1; j >= 0; j--){
            if(_ref != null){
                _ref.add(xml[tmp[j]]);
            }
            xml.remove(tmp[j]);
        }
        return xml;
    }

    private Boolean _isNodeIgnorable(NodeChild node){
        Boolean result = false;
        for(int i = 0; i < ignoreNodes.size(); i++){
            String iPath = ignoreNodes[i];
            String[] iPathParts = iPath.split('>');
            if(
                (
                    iPathParts.size() == 2 && iPathParts[0].size() > 0 && iPathParts[1].size() > 0 &&
                    MyXmlUtil.isPathInXmlTree(iPathParts[0], node) && MyXmlUtil.walkXmlByPath(iPathParts[1], node).size() > 0
                ) 
                || MyXmlUtil.isPathInXmlTree(iPath, node)
            ){
                result = true;
                break;
            }
        }

        if(!result){
            for(int i = 0; i < ignoreNodesWValues.size(); i++){
                String iPath = ignoreNodesWValues.keySet()[i];
                String[] iPathParts = iPath.split('>');
                
                if(
                    (
                        iPathParts.size() == 2
                        && MyXmlUtil.isPathInXmlTree(iPathParts[0], node)
                        && Pattern.compile(ignoreNodesWValues[iPath]).matcher(MyXmlUtil.walkXmlByPathForValue(iPathParts[1], node)).find()
                    )
                    || Pattern.compile(ignoreNodesWValues[iPath]).matcher(MyXmlUtil.walkXmlByPathForValue(iPath, node)).find()
                ){
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private Boolean _compareChildren(List<NodeChild> nodeChildren1, List<NodeChild> nodeChildren2){
        int matchCnt = 0;

        if(orderlySafeChildrenMode){
            for(int i = 0; i < nodeChildren1.size(); i++){
                if(compareNodes(nodeChildren1[i], nodeChildren2[i])){
                    matchCnt++;
                }else{
                    break;
                }
            }
        }else{
            nodeChildren1.each{i1 ->
                for(int i = 0; i < nodeChildren2.size(); i++){
                    notified = false;
                    if(compareNodes(i1, nodeChildren2[i])){
                        matchCnt++;
                        nodeChildren2.remove(i);
                        break;
                    }
                }
            }
        }

        return nodeChildren1.size() == matchCnt;
    }
}