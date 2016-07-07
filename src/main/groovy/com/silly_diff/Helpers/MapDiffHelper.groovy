package com.silly_diff.Helpers

import groovy.util.logging.Slf4j
import com.silly_diff.Infostructure.AbstractDiffHelper

import java.util.regex.Pattern

/**
 * This class is currently under development. So please, don't use it. Hopefully, it will be finished soon...
 * */
@Slf4j
class MapDiffHelper extends AbstractDiffHelper{

    public Boolean orderlySafeMode = false;

    public Boolean skipMissedDb = true;
    
    public ArrayList<String> ignoredPairs = new ArrayList<String>();
    
    public HashMap<String, String> ignoredPairsWValues = new HashMap<>();

    public HashMap<String, Object> ignoredRows = new HashMap<>();
    
    public Closure<Boolean> ignorePairCommand;
    
    protected final String PROCESSED_KEY = "_processed_";
    
    public MapDiffHelper(List<Map<String, Object>> list1, List<Map<String, Object>> list2){
        source1 = new ArrayList<Map<String, String>>();
        source2 = new ArrayList<Map<String, String>>();
        list1.each{Map<String, Object> row ->
            HashMap<String, String> newRow = new HashMap<>();
            row.each{String key, Object val ->
                newRow.put(key, val == null ? null : val.toString());
            }
            source1.add(newRow);
        };
        list2.each{Map<String, Object> row ->
            HashMap<String, String> newRow = new HashMap<>();
            row.each{String key, Object val ->
                newRow.put(key, val.toString());
            }
            source2.add(newRow);
        };
    }
    
    public void setupFromConfig(Map params){
        showErrors = params.get("showErrors", showErrors).asBoolean();
        orderlySafeMode = params.get("orderlySafeMode", orderlySafeMode).asBoolean();
        ignoredRows = (HashMap<String, Object>)params.get('ignoredRows', ignoredRows);
        ignoredPairs = (ArrayList<String>)params.get('ignoredPairs', ignoredPairs);
        ignoredPairsWValues = (HashMap<String, String>)params.get("ignoredPairsWValues", ignoredPairsWValues);
        if(params.containsKey("ignorePairCommand" + "_path")){
            ignorePairCommand = _getCommandFromParams(params, "ignorePairCommand" + "_path");
        }
        _createModificators((HashMap<String, String>)params.get("modifications1"), modifications1);
        _createModificators((HashMap<String, String>)params.get("modifications2"), modifications2);
    }

    public void calcDiff(){
        counter = 0;
        outputList1 = new ArrayList(source1);
        outputList2 = new ArrayList(source2);
        
        total = Math.max(outputList1.size(), outputList2.size());
        
        Iterator<HashMap<String, Object>> rowIter1 = outputList1.listIterator();
        Iterator<HashMap<String, Object>> rowIter2 = outputList2.listIterator();
        while(!(watchDog != null && watchDog()) && rowIter1.hasNext()){
            counter++;
            HashMap<String, Object> curRow1 = rowIter1.next();
            if(_rowPostProcessing(curRow1, modifications1)){
                if(orderlySafeMode){
                    notified = false;
                    while(rowIter2.hasNext()){
                        HashMap<String, Object> curRow2 = rowIter2.next();
                        if(_rowPostProcessing(curRow2, modifications2)){
                            if(compareRows(curRow1, curRow2)){
                                rowIter1.remove();
                                rowIter2.remove();
                            }
                            break;
                        }else{
                            rowIter2.remove();
                            source2.remove(curRow2);
                        }
                    }
                }else{
                    Iterator<HashMap<String, Object>> _rowIter2 = outputList2.listIterator();
                    while(_rowIter2.hasNext()){
                        notified = false;
                        HashMap<String, Object> curRow2 = _rowIter2.next();
                        if(_rowPostProcessing(curRow2, modifications2)){
                            if(compareRows(curRow1, curRow2)){
                                rowIter1.remove();
                                _rowIter2.remove();
                                break;
                            }
                        }else{
                            _rowIter2.remove();
                            source2.remove(curRow2);
                        }
                    }
                }
            }else{
                rowIter1.remove();
                source1.remove(curRow1);
            }
        }
    }
    
    public Boolean compareRows(Map<String, Object> row1, Map<String, Object> row2){
        Map<String, List<String>> debug = new HashMap<String, List<String>>();
        Set<String> keys2 = new HashSet<>(row2.keySet());
        keys2.remove(PROCESSED_KEY);

        int cnt1 = row1.size();
        int cntMatch = 0;
        for(int i = 0; i < cnt1; i++){
            String key = row1.keySet()[i];
            String val1 = row1[key] == null ? "" : row1[key];
            String val2 = row2[key];
            
            debug.put(key, [val1, val2]);
            
            if(key == PROCESSED_KEY){
                cntMatch ++;
            }else if(_isPairIgnorable2(key, val1) || _isPairIgnorable2(key, val2)){
                cntMatch++;
                keys2.remove(key);
            }else if(row2.containsKey(key)){
                if(val1 == val2){
                    cntMatch++;
                    keys2.remove(key);
                }else{
                    break;
                }
            }else if(skipMissedDb){
                cntMatch++;
            }else{
                break;
            }
        }
        
        Boolean result = cnt1 == cntMatch && keys2.size() == 0;
        
        if(!result){
            Boolean _debug = false;
            if((showErrors || _debug) && !notified){
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
    
    protected Boolean _rowPostProcessing(Map<String, Object> row, Map<String, Object> modifications){
        if(!row.containsKey(PROCESSED_KEY)){
            Iterator<Map.Entry<String, Object>> pairIter = row.iterator();
            while(pairIter.hasNext()){
                Map.Entry<String, Object> curPair = pairIter.next();
                row[curPair.getKey()] = _applyModifications(modifications, curPair.getKey(), row).trim();
                if(ignoredPairs.contains(curPair.getKey())){
                    pairIter.remove();
                }
            }
            row.put(PROCESSED_KEY, true);
            return !_isRowIgnorable(row, ignoredRows);
        }else{
            return true;
        }
    }
    
    protected List<HashMap<String, Object>> _deleteIgnoredElements(List<HashMap<String, Object>> source, Map<String, String> modifications){
        ArrayList<HashMap<String, Object>> result = new ArrayList<>();
        Iterator<HashMap<String, Object>> rowIter = source.iterator();
        while(rowIter.hasNext()){
            HashMap<String, Object> curRow = rowIter.next();
            Iterator<Map.Entry<String, Object>> pairIter = curRow.iterator();
            while(pairIter.hasNext()){
                Map.Entry<String, Object> curIter = pairIter.next();
                curRow[curIter.getKey()] = _applyModifications(modifications, curIter.getKey(), curRow).trim();
                if(_isPairIgnorable(curIter)){
                    pairIter.remove();
                }
            }
            if(!_isRowIgnorable(curRow, ignoredRows)){
                result.add(curRow);
            }else{
                rowIter.remove();
            }
        }
        
        return result;
    }

    protected static Boolean _isRowIgnorable(Map<String, Object> row, HashMap<String, Object> ignorables, Boolean and = false){
        Boolean result = false;

        for(Map.Entry<String, Object> curIgnored : ignorables){
            String ignoredKey = curIgnored.getKey();
            if(ignoredKey == "AND"){
                result = _isRowIgnorable(row, (HashMap<String, Object>) curIgnored.getValue(), true);
            }else if(ignoredKey == "OR"){
                result = _isRowIgnorable(row, (HashMap<String, Object>) curIgnored.getValue());
            }else{
                Boolean isNullableKey = ignoredKey[-1] == "?";
                if(isNullableKey){
                    ignoredKey = ignoredKey.substring(0, ignoredKey.size() - 1);
                }
                
                if(row.containsKey(ignoredKey)){
                    String ignoredValue = row[ignoredKey];
                    String curIgnoredValue = curIgnored.getValue().toString();
                    if(curIgnoredValue.startsWith("col:")){
                        result = ignoredValue == row[curIgnoredValue.substring(4, curIgnoredValue.size())];
                    }else if(curIgnoredValue.startsWith("!col:")){
                        result = ignoredValue != row[curIgnoredValue.substring(5, curIgnoredValue.size())];
                    }else{
                        result = Pattern.compile(curIgnored.getValue().toString()).matcher(ignoredValue).find();
                    }
                    if(and != result){
                        break;
                    }
                }else if(and && !isNullableKey){
                    result = false;
                }
            }

            if(and != result){
                break;
            }
        }

        return result;
    }
    
    protected Boolean _isPairIgnorable(Map.Entry<String, Object> pair){
        Boolean result = false;

        if(ignorePairCommand != null){
            result = ignorePairCommand(pair);
        }else if(ignoredPairs.contains(pair.getKey())){
            result = true;
        }else {
            if(ignoredPairsWValues.containsKey(pair.getKey())){
                result = Pattern.compile(ignoredPairsWValues[pair.getKey()]).matcher(pair.getValue().toString()).find();
            }
        }
        
        return result;
    }

    protected Boolean _isPairIgnorable2(String key, String value){
        Boolean result = false;

        if(ignorePairCommand != null){
            result = ignorePairCommand(key, value);
        }else {
            if(ignoredPairsWValues.containsKey(key)){
                result = Pattern.compile(ignoredPairsWValues[key]).matcher(value).find();
            }
        }

        return result;
    }
}
