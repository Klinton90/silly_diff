package com.silly_diff.Helpers

import groovy.util.logging.Slf4j
import com.silly_diff.Infostructure.AbstractDiffHelper

/**
 * This class is currently under development. So please, don't use it. Hopefully, it will be finished soon...
 * */
@Slf4j
class MapDiffHelper extends AbstractDiffHelper{
    List<Map<String, Object>> _list1;
    List<Map<String, Object>> _list2;

    public Boolean orderlySafeMode = false;
    
    public ArrayList<String> ignoredKeys = new ArrayList<String>();

    public MapDiffHelper(List<Map<String, Object>> list1, List<Map<String, Object>> list2){
        _list1 = list1;
        _list2 = list2;
    }
    
    public void setupFromConfig(Map params){
        showErrors = params.get("showErrors", showErrors);
        orderlySafeMode = (Boolean)params.get("orderlySafeMode", orderlySafeMode);
        ignoredKeys = (ArrayList<String>)params.get('ignoredKeys', ignoredKeys);
        modifications1 = (HashMap<String, String>)params.get("modifications1", modifications1);
        modifications2 = (HashMap<String, String>)params.get("modifications2", modifications2);
    }

    public void calcDiff(){
        outputList1 = new ArrayList<HashMap<String, Object>>();
        outputList2 = new ArrayList<HashMap<String, Object>>();
        if(_list1.size() > 0 && _list2.size() > 0){
            if(orderlySafeMode){
                int maxSize = Math.max(_list1.size(), _list2.size());
                for(int i = 0; i < maxSize; i++){
                    notified = false;
                    Map<String, Object> curRow1 = _list1[i];
                    Map<String, Object> curRow2 = _list2[i];
                    if(curRow1 == null || curRow2 == null || compareRows(curRow1, curRow2)){
                        if(curRow1 != null){
                            outputList1.add(curRow1);
                        }
                        if(curRow2 != null){
                            outputList2.add(curRow2);
                        }
                    }
                }
            }else{
                List<Map<String,Object>> listCopy1 = new ArrayList<Map<String,Object>>(_list1);
                List<Map<String,Object>> listCopy2 = new ArrayList<Map<String,Object>>(_list2);
                listCopy1.each{i1 ->
                    Boolean match = false;
                    for(int i = 0; i < listCopy2.size(); i++){
                        notified = false;
                        if(compareRows(i1, listCopy2[i])){
                            listCopy2.remove(i);
                            match = true;
                            break;
                        }
                    }
                    if(!match){
                        outputList1.add(i1);
                    }
                }
                outputList2 = listCopy2;
            }
        }
    }
    
    public Boolean compareRows(Map<String, Object> row1, Map<String, Object> row2){
        Boolean result = false;
        Map<String, List<String>> debug = new HashMap<String, List<String>>();

        ArrayList<String> testKeys = new ArrayList<String>();
        row1.keySet().each{String k->
            if(!ignoredKeys.contains(k)){
                testKeys.add(k);
            }
        }
        int cnt = testKeys.size();
        int cntMatch = 0;
        
        for(int i = 0; i < cnt; i++){
            String key = testKeys[i];
            
            if(row2.containsKey(key)){
                String val1 = _applyModifications(modifications1, key, row1.get(key).toString()).trim();
                String val2 = _applyModifications(modifications2, key, row2.get(key).toString()).trim();

                debug.put(key, [val1, val2]);

                if(val1 == val2){
                    cntMatch++;
                }else{
                    break;
                }
            }
        }
        
        result = cnt == cntMatch;
        
        if(!result){
            if(showErrors && !notified){
                notified = true;
                String message = "First mismatch at: {";
                debug.each{String name, List<String> val ->
                    message += "#$name : '$val', "
                }
                log.info message + "}";
            }
        }
        
        return result;
    }
}
