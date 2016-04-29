# silly_diff
Allows to compare XML 2 XML or XML 2 DB objects.

More information can be found in Wiki.

Try basic XML comparison features: http://diffdemo.azurewebsites.net/
Please note! It is running on free Azure account, so it may take time to launch app 
(30-40 seconds), so please refresh your page couple times in case of loading failure.

## Requirements:
* Groovy-all 2.4.4 (at least it has been tested/developed around that version)
* Slf4j-api 1.7.12
* Log4j-Over-Slf4j 1.7.12
* Gradle 2.3

## Links
* DemoApp: http://diffdemo.azurewebsites.net/
* DemoApp source code: https://github.com/Klinton90/DiffDemo
* Wiki: https://github.com/Klinton90/silly_diff/wiki
* GroovyDoc: http://klinton90.github.io/silly_diff/

## Examples
### Hello World
```
public void HelloWorld(String _xml1, String _xml2){
    XmlSlurper xs = new XmlSlurper();
    NodeChild xml1 = xs.parseText(_xml1);
    NodeChild xml2 = xs.parseText(_xml2);

    AbstractDiffHelper xdh = new XmlDiffHelper(xml1.toList(), xml2.toList());
    xdh.calcDiff();
    log.info(AbstractDiffHelper.getOutputListAsString(xdh.outputList1));
    log.info(AbstractDiffHelper.getOutputListAsString(xdh.outputList2));
}
```

### Hello Universe
```
public void HelloUniverse(){
    String _xml1 = """
        <result>
            <dealers>
                <dealer>
                    <name>www</name>
                    <id>2</id>
                </dealer>
                <dealer>
                    <name>qqq</name>
                    <id>1</id>
                </dealer>
            </dealers>
        </result>
    """;
    String _xml2 = """
        <result>
            <dealers>
                <dealer>
                    <name>qqq</name>
                    <id>1</id>
                </dealer>
                <dealer>
                    <name>eee</name>
                    <id>3</id>
                </dealer>
            </dealers>
        </result>
    """;
    String errors = "";
    
    //turn XML strings into NodeChild object
    XmlSlurper xs = new XmlSlurper();
    NodeChild xml1;
    NodeChild xml2;
    try{
        xml1 = xs.parseText(_xml1);
        xml2 = xs.parseText(_xml2);
    }catch(Exception e){
        errors += e.getMessage();
    }
    
    if(xml1 && xml2){
        // XmlDiffHelper contructor accepts List<NodeChild> object. 
        // If you want to compare entire XMLs, use 
        // new XmlDiffHelper(xml1.toList(), xml2.toList());
        // But in our case only interesting part is "delaer" node, so let's pass XmlPath to that object:
        String xmlPath = "dealers.dealer";
        List<NodeChild> xmlList1 = XmlUtil.walkXmlByPath(xmlPath, xml1);
        List<NodeChild> xmlList2 = XmlUtil.walkXmlByPath(xmlPath, xml2);
        
        AbstractDiffHelper xdh = new XmlDiffHelper(xmlList1, xmlList2);
        
        // Put any additional parameters as Map<String, Object>
        xdh.setupFromConfig([
            "orderlySafeMode": false,
            "showErrors": false,
            "orderlySafeChildrenMode": true
        ]);
                    
        // Or individually
        // Skip XML object order
        xdh.orderlySafeMode = false;
        // Don't put anything into console
        xdh.showErrors = false;
        // Check order of Internal nodes
        xdh.orderlySafeChildrenMode = true;
        
        // Now run comparison
        xdh.calcDiff();
        
        // Now let's review results
        if(xdh.isSimilar()){
            log.info("Success! No diff found!");
        }else{
            String xmlResult1 = AbstractDiffHelper.getOutputListAsString(xdh.outputList1);
            String xmlResult2 = AbstractDiffHelper.getOutputListAsString(xdh.outputList2);
            
            assert xmlResult1 == """<dealer>\r\n  <name>www</name>\r\n  <id>2</id>\r\n</dealer>\r\n""";
            assert xmlResult2 == """<dealer>\r\n  <name>eee</name>\r\n  <id>3</id>\r\n</dealer>\r\n""";
        }
    }
}
```