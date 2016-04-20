package com.silly_diff.Infostructure

import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.NodeChild
import com.silly_diff.Util.JsonUtil
import com.silly_diff.Util.XmlUtil

/**
 * Parent class for Comparison implementations.<br>
 * Mostly is used as {@code Interface} (I'm too lazy for writing separate interface) and as another Util class with shared functions
 * */
@Slf4j
public abstract class AbstractDiffHelper{

    public Closure<Boolean> watchDog;

    public Boolean showErrors = false;
    protected Boolean notified = false;

    public List outputList1;
    public List outputList2;
    
    public List source1;
    public List source2;

    public Map<String, String> modifications1 = new HashMap<String, String>();
    public Map<String, String> modifications2 = new HashMap<String, String>();

    /**
     * Setup all input parameters from {@link Map} {@code config} file
     * */
    abstract public void setupFromConfig(Map params);

    /**
     * Most important function of this class that must provide object comparison
     * */
    abstract public void calcDiff();

    /**
     * Simple check if comparison has been completed successfully and getting simple result
     * @return {@link Boolean} {@link true} if no differences has been found.
     * @throws {@link Exception} if this function has been called before {@link AbstractDiffHelper#calcDiff()}
     * */
    public Boolean isSimilar(){
        if(outputList1 == null && outputList2 == null){
            throw new Exception("EMException: Call 'calcDiff()' before using this method!");
        }

        return outputList1.size() == 0 && outputList2.size() == 0;
    }

    protected static Closure _getCommandFromParams(Map params, String name){
        String[] path = params.get(name + "_path").toString().split(">");
        return Class.forName(path[0]).&"${path[1]}";
    }
    
    protected String _applyModifications(Map<String,String> mods, String key, String value){
        String result = value;
        if(mods.size() > 0){
            if(mods.containsKey(key)){
                def mod = mods.get(key);
                if(mod instanceof String){
                    mod.split("\\.").each{String _mod ->
                        result = result."$_mod"();
                    }
                }else{
                    result = mod(result);
                }
            }else if(mods.containsKey(key + "_path")){
                Closure command = _getCommandFromParams(mods, key);
                result = command(result);
            }else if(mods.containsKey("_all")){
                _applyModifications(mods, "_all", result);
            }
        }
        
        return result;
    }

    /**
     * Simple alias for {@link groovy.json.JsonOutput#prettyPrint(String jsonPayload)} or<br>
     * {@link groovy.xml.XmlUtil#serialize(groovy.util.slurpersupport.GPathResult node)} function.<br>
     * Refer to {@link AbstractDiffHelper#getOutputElementAsString(Object element)} function for more information
     * @param outputList {@link List} of {@link NodeChild} or {@link Map} Objects that needs to be pretty printed
     * @return {@link String} pretty print representation of provided Objects
     * */
    public static String getOutputListAsString(List outputList){
        String result = "";
        outputList.each{el->
            result += getOutputElementAsString(el);
        }
        return result;
    }

    /**
     * Simple alias for {@link groovy.json.JsonOutput#prettyPrint(String jsonPayload)} or<br>
     * {@link groovy.xml.XmlUtil#serialize(groovy.util.slurpersupport.GPathResult node)} function.<br>
     * Depending on type of {@code element} corresponding function will be executed
     * @param element {@link NodeChild} or {@link Map} Objects that needs to be pretty printed
     * @return {@link String} pretty print representation of provided Object
     * */
    public static String getOutputElementAsString(Object element){
        String result;
        if(element instanceof NodeChild){
            result = XmlUtil.printNode(element);
        }else{
            result = JsonUtil.printMap(element);
        }
        return result;
    }

}
