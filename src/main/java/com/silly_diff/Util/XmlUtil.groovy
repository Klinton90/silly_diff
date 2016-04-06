package main.java.com.silly_diff.Util

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.NodeChildren

/**
 * Few XML functions required for diff application
 * */
class XmlUtil{
    /**
     * Step by step looking among {@link groovy.util.slurpersupport.NodeChild#children()} of {@code Xml} elements with {@link groovy.util.slurpersupport.NodeChild#name()}<br>
     * equals to elements in {@code listPath}. When last element in {@code listPath} reached, returns all elements that match specified Path.
     * @param listPath  Node names separated with '.' for 'walking' in depth of {@link groovy.util.slurpersupport.NodeChild#children()} in {@code Xml}
     * @param Xml       XmlNode that will be investigated
     * @return          {@link List} of elements that were found among {@link groovy.util.slurpersupport.NodeChild#children()} that located under specified {@code listPath}
     */
    public static List<NodeChild> walkXmlByPath(String listPath, NodeChild Xml){
        ArrayList<NodeChild> result = new ArrayList<NodeChild>();
        if(listPath != ""){
            String[] pathNodes = listPath.trim().split("\\.");
            GPathResult targetNode = Xml;
            for(int i = 0; i < pathNodes.size(); i++){
                NodeChildren tmp = targetNode[pathNodes[i].trim()];
                if(tmp.size() != -1){
                    targetNode = tmp;
                }
            }
            if(targetNode.size() != -1){
                result = targetNode.toList();
            }
        }else{
            result = Xml.toList();
        }
        return result;
    }

    /**
     * Step by step looking among {@link groovy.util.slurpersupport.NodeChild#children()} of {@code Xml} elements with {@link groovy.util.slurpersupport.NodeChild#name()}<br>
     * equals to elements in {@code listPath}. When last element in {@code listPath} reached, returns {@link groovy.util.slurpersupport.NodeChild#text()}<br>
     * or {@link groovy.util.slurpersupport.Attribute#name()} value.<br>
     * If last element in {@code listPath} starts with '@' it is considered as {@link groovy.util.slurpersupport.Attribute}
     * @param listPath  Node names separated with '.' for 'walking' in depth of {@link groovy.util.slurpersupport.NodeChild#children()}
     * @param Xml       XmlNode that will be investigated
     * @return          {@link String} with value found in {@link groovy.util.slurpersupport.NodeChild#attributes()} or in {@link groovy.util.slurpersupport.NodeChild#text()}
     */
    public static String walkXmlByPathForValue(String listPath, NodeChild Xml){
        String result;

        String[] pathNodes = listPath.trim().split("\\.");
        NodeChildren targetNode = Xml[pathNodes[0].trim()];
        for(int i = 1; i < pathNodes.size(); i++){
            targetNode = targetNode[pathNodes[i].trim()];
        }

        String lastEl = pathNodes.last().trim();
        if(lastEl.size() > 2 && lastEl.substring(0, 1) == '@'){
            result = targetNode;
        }else if(targetNode.children().size() == 0){
            result = targetNode.text();
        }

        return result;
    }

    /**
     * Step by step comparing {@link groovy.util.slurpersupport.NodeChild#parent()} of {@code Xml} with elements from {@code listPath}.<br>
     * If last element in {@code listPath} starts with '@' it is considered as {@link groovy.util.slurpersupport.Attribute}
     * @param listPath  Node names separated with '.' for 'walking' upward
     * @param Xml       XmlNode that will be investigated
     * @return          {@link true} if all elements listed in {@code listPath} exists for {@code Xml.parent()}
     */
    public static Boolean isPathInXmlTree(String listPath, NodeChild Xml, Boolean forceHasAttr = false){
        Boolean result = false;
        if(!forceHasAttr || listPath.split('@').size() == 2){
            String[] pathNodes = listPath.trim().split("\\.");
            String attrName = pathNodes.last().trim();
            if(attrName.substring(0, 1) == '@' && Xml[attrName] != ""){
                pathNodes = pathNodes.dropRight(1);
            }
            result = _isNodeInXmlTree(pathNodes, Xml);
        }
        return result;
    }

    /**
     * Step by step looking among {@link groovy.util.slurpersupport.NodeChild#children()} of {@code Xml} elements with {@link groovy.util.slurpersupport.NodeChild#name()}<br>
     * equals to elements in {@code listPath}.
     * Last element in {@code listPath} should be marked with '@' sign to define {@link groovy.util.slurpersupport.Attribute}
     * @deprecated Use {@link #walkXmlByPathForValue(String listPath, groovy.util.slurpersupport.NodeChild Xml)} instead
     * @param listPath  Node names separated with '.' for 'walking' in depth of {@code Xml.children()}
     * @param Xml       XmlNode that will be investigated
     * @return          {@link String} with {@link groovy.util.slurpersupport.Attribute#value}
     */
    @Deprecated
    public static String walkXmlByPathForAttr(String listPath, NodeChild Xml){
        String attrVal = "";

        String[] pathNodes = listPath.trim().split("\\.");
        if(pathNodes.last().trim().substring(0, 1) == '@'){
            NodeChildren targetNode = Xml[pathNodes[0].trim()];
            for(int i = 1; i < pathNodes.size(); i++){
                targetNode = targetNode[pathNodes[i].trim()];
            }
            attrVal = targetNode.toString();
        }
        return attrVal;
    }

    /**
     * Step by step comparing {@link groovy.util.slurpersupport.NodeChild#parent()} of {@code Xml} with elements from listPath.<br>
     * Last element in {@code listPath} should be marked with '@' sign to define {@link groovy.util.slurpersupport.Attribute}
     * @deprecated Use {@link #isPathInXmlTree(String listPath, groovy.util.slurpersupport.NodeChild Xml, Boolean forceHasAttr = false)} instead
     * @param listPath  Node names separated with '.' for 'walking' upward
     * @param Xml       XmlNode that will be investigated
     * @return          {@link true} if Attribute exists for {@code Xml} and all elements listed in {@code listPath} exists for {@code Xml.parent()}
     */
    @Deprecated
    public static Boolean isAttrInXmlTree(String listPath, NodeChild Xml){
        Boolean result = false;
        String[] pathNodes = listPath.trim().split("\\.");
        int chainLength = pathNodes.size();
        String attrName = pathNodes.last().trim();
        if(attrName.substring(0, 1) == '@' && Xml[attrName] != ""){
            if(chainLength > 1){
                result = _isNodeInXmlTree(Arrays.copyOf(pathNodes, chainLength - 1), Xml);
            }else{
                result = true;
            }
        }
        return result;
    }

    /**
     * Step by step comparing {@link groovy.util.slurpersupport.NodeChild#parent()} of {@code Xml} with elements from listPath
     * @deprecated Use {@link #isPathInXmlTree(String listPath, groovy.util.slurpersupport.NodeChild Xml)} instead
     * @param listPath  Node names separated with '.' for 'walking' upward
     * @param Xml       XmlNode that will be investigated
     * @return          {@link true} if all elements listed in {@code listPath} exists for {@code Xml.parent()}
     */
    @Deprecated
    public static Boolean isNodeInXmlTree(String listPath, NodeChild Xml){
        String[] pathNodes = listPath.trim().split("\\.");
        return _isNodeInXmlTree(pathNodes, Xml);
    }

    private static Boolean _isNodeInXmlTree(String[] pathNodes, NodeChild Xml){
        int chainLength = pathNodes.size();
        if(Xml.name() == pathNodes.last().trim()){
            if(chainLength > 1){
                NodeChild parentNode = Xml.parent();
                for(int i = chainLength - 2; i > -1; i--){
                    if(parentNode.name() == pathNodes[i]){
                        parentNode = parentNode.parent();
                    }else{
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Simple alias for {@link groovy.xml.XmlUtil#serialize(GPathResult node)} function
     * @param element {@link NodeChild} XML object that will be transformed into {@link String}
     * @return {@link String} pretty print of JSON object
     * */
    public static String printNode(NodeChild element){
        return groovy.xml.XmlUtil.serialize(element).substring(38);
    }

    /**
     * Use this method to create list of Aliases for SQL columns that this class will use for SQL to DB comparison.
     * @param node      {@link groovy.util.slurpersupport.NodeChild} with XML structure that needs to be investigated
     * @param prefix    Optional parameter. {@link String} that will be added to every row in result.
     *                  Mostly it is used by function itself for recursive calls.
     *                  It is suggested to skip this parameter in all cases
     * @return          List of Aliases for XML nodes and attributes according to selected naming contract
     */
    public static String mapXml(NodeChild node, String prefix = ""){
        String result = "";
        String newPrefix = (prefix != "" ? prefix + "." : "");

        node.attributes().keySet().each{String a ->
            result += newPrefix + "@" + a.toString() + "\r\n";
        };

        ArrayList<String> nodeNames = new ArrayList<String>();
        HashMap<String, Integer> nodeArrays = new HashMap<String, Integer>();
        node.children().each{NodeChild child ->
            if(nodeNames.contains(child.name())){
                nodeArrays.put(child.name(), 0);
            }else{
                nodeNames.add(child.name());
            }
        };

        node.children().each{NodeChild child ->
            int cnt = nodeArrays.get(child.name(), -1);
            String subPrefix = newPrefix + child.name();
            if(cnt >= 0){
                subPrefix += ".>" + cnt;
                nodeArrays.put(child.name(), ++cnt);
            }
            result += subPrefix + (child.children().size() > 0 ? " = ''" : "") + "\r\n";
            result += mapXml(child, subPrefix);
        };

        return result;
    }
}
