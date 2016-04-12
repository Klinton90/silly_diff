package com.silly_diff.Util

import groovy.json.JsonOutput

/**
 * Few JSON functions required for diff application
 * */
class JsonUtil{

    /**
     * Searches inside of JSON object for element (another JSON object) that has {@code key} equal to element in {@code listPath}.<br>
     * When last element in {@code listPath} reached, returns all elements that match specified Path.<br>
     * @param listPath    Node (or {@code key}) names separated with '.' for 'walking' im depth of JSON object in {@code structure}
     * @param structure   JSON object OR {@link List} of JSON objects that will be transformed and returned
     * @return {@link List} of JSON objects located in {@code structure} under {@code listPath} path
     * */
    public static List walkJsonByPath(String listPath, Object structure){
        List result = [];
        if(listPath != ""){
            String[] pathNodes = listPath.trim().split("\\.");
            result = _help(pathNodes, structure);
        }
        return result;
    }
    
    protected static List _help(String[] pathNodes, Object structure){
        List result = [];
        if(pathNodes.size() > 0){
            List target;
            if(structure instanceof List){
                target = structure;
            }else if(structure instanceof Map){
                target = [structure];
            }
            if(target.size() > 0){
                String pathNode = pathNodes[0];
                List newTarget = [];
                target.each{curEl ->
                    if(curEl instanceof List && pathNode.substring(0, 1) == ">"){
                        try{
                            int idx = Integer.parseInt(pathNode.substring(1));
                            if(idx < curEl.size()){
                                newTarget.add(curEl.getAt(idx));
                            }
                        }catch(Exception e){
                        }
                    }else if(curEl instanceof Map && curEl.containsKey(pathNode)){
                        newTarget.addAll(_help(pathNodes.drop(1), curEl[pathNode]));
                    }
                }
                if(newTarget.size() > 0){
                    result = newTarget;
                }
            }
        }else{
            result = structure;
        }
        return result;
    }

    /**
     * Simple alias for {@link JsonOutput#prettyPrint(String jsonPayload)} function
     * @param source {@link Map} JSON object or {@link Map} that will be transformed into {@link String}
     * @return {@link String} pretty print of JSON object
     * */
    public static String printMap(Map source){
        return JsonOutput.prettyPrint(JsonOutput.toJson(source)) + ",\r\n";
    }
}
