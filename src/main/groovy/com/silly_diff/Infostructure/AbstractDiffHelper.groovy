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

    public HashMap<String, Object> modifications1 = new HashMap<>();
    public HashMap<String, Object> modifications2 = new HashMap<>();

    public int counter = 0;
    public int total = 0;

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
    
    protected static shell = new GroovyShell();
    
    protected static void _createModificators(HashMap<String, String> _mods, HashMap<String, Object> mods){
        if(_mods != null){
            for(Map.Entry<String, String> mod : _mods){
                String key = mod.getKey();
                if(key.endsWith("_path")){
                    String[] path = mod.getValue().split(">");
                    mods.put(key, Class.forName(path[0]).&"${path[1]}");
                }else{
                    mods.put(key, shell.parse(mod.getValue()));
                }
            }
        }
    }

    protected static String _applyModifications(Map<String, Object> mods, String key, Object obj, Boolean all = false){
        String result;
        if(obj instanceof NodeChild && key.substring(0, 1) != "@"){
            result = obj.localText()[0];
        }else{
            result = obj[key].toString();
        }
        
        if(mods.size() > 0){
            if(mods.containsKey(key)){
                try{
                    Binding binding = new Binding();
                    binding.setProperty("value", result);
                    binding.setProperty("key", key);
                    binding.setProperty("obj", obj);
                    binding.setProperty("result", "");
                    Script script = (Script)mods.get(key);
                    script.binding = binding;
                    script.run();
                    result = binding.getProperty("result").toString();
                }catch(Exception e){
                    log.info("Modificator failed: '" + e.getMessage() + "'. Continue execution with original value.");
                }
            }else if(mods.containsKey(key + "_path")){
                Closure command = (Closure)mods.get(key + "_path");
                result = command(result, key, obj);
            }else if(!all){
                if(mods.containsKey("_all")){
                    _applyModifications(mods, "_all", obj, true);
                }else if(mods.containsKey("_all_path")){
                    _applyModifications(mods, "_all_path", obj, true);
                }
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
