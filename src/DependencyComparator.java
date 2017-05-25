import javafx.util.Pair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vendel on 5/24/17.
 */
public class DependencyComparator {

    private static Map<String, List<Dependency>> moduleDependencyMap = new HashMap<>();
    private static List<Pair<String, String>> projectFilePairs = new ArrayList<>();

    public static void main(String[] args) {

        try {
            gatherAllPoms(ProjectRoot.URBANPULSECONNECTOR);
            for (Pair<String, String> pair :
                    projectFilePairs) {
                getDifference(pair.getKey(), pair.getValue());
            }
            createXmlFromResults();

            gatherAllPoms(ProjectRoot.URBANPULSEEP);
            for (Pair<String, String> pair :
                    projectFilePairs) {
                getDifference(pair.getKey(), pair.getValue());
            }
            createXmlFromResults();

            gatherAllPoms(ProjectRoot.COCKPITV2);
            for (Pair<String, String> pair :
                    projectFilePairs) {
                getDifference(pair.getKey(), pair.getValue());
            }
            createXmlFromResults();
        } catch (IOException | ParseException ex) {
            ex.printStackTrace();
        }

     /*   for (Map.Entry<String, List<Dependency>> entry :
                moduleDependencyMap.entrySet()) {
            System.out.println(entry.getKey());
            for (Dependency dependency :
                    entry.getValue()) {
                System.out.println(dependency);
            }
        }*/

    }

    public static void getDifference(String moduleName, String path) {
        List<Dependency> resultList = new ArrayList<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            //Parsing base pom
            File basepom = getBasePom();
            Document basedoc = dBuilder.parse(basepom);
            basedoc.getDocumentElement().normalize();

            //Parsing external pom
            File pomfile = new File(path);
            Document doc = dBuilder.parse(pomfile);
            doc.getDocumentElement().normalize();

            NodeList baseDependencyList = basedoc.getElementsByTagName("dependency");
            NodeList externalDependencyList = doc.getElementsByTagName("dependency");

            for (int i = 0; i < baseDependencyList.getLength(); i++) {
                Node basenode = baseDependencyList.item(i);
                if (basenode.getNodeType() == Node.ELEMENT_NODE) {
                    Element baseNodeElement = (Element) basenode;
                    String baseNodeArtifactId = baseNodeElement.getElementsByTagName("artifactId").item(0).getTextContent();
                    String baseNodeVersion = baseNodeElement.getElementsByTagName("version").item(0).getTextContent();
                    for (int j = 0; j < externalDependencyList.getLength(); j++) {
                        Node node = externalDependencyList.item(j);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element nodeElement = (Element) node;
                            String nodeArtifactId = nodeElement.getElementsByTagName("artifactId").item(0).getTextContent();
                            String nodeGroupId = nodeElement.getElementsByTagName("groupId").item(0).getTextContent();
                            NodeList versionNode = nodeElement.getElementsByTagName("version");
                            if (versionNode != null && versionNode.getLength() != 0) {
                                if (baseNodeArtifactId.equals(nodeArtifactId) && !baseNodeVersion.equals(versionNode.item(0).getTextContent())) {
                                    resultList.add(new Dependency(nodeGroupId, nodeArtifactId, versionNode.item(0).getTextContent()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException | ParseException ex) {
            ex.printStackTrace();
        }
        if (!resultList.isEmpty())
            moduleDependencyMap.put(moduleName, resultList);
    }

    public static void createXmlFromResults() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document document = dBuilder.newDocument();
            String filename = new String();

            for (Map.Entry<String, List<Dependency>> entry :
                    moduleDependencyMap.entrySet()) {
                String[] pathParts = entry.getKey().split("/");
                filename=pathParts[pathParts.length-1];
                Element rootElement = document.createElement(entry.getKey());
                document.appendChild(rootElement);


                for (int i = 0; i < entry.getValue().size(); i++) {
                    //Create dependency tag
                    Element dependencyElement = document.createElement("dependency");
                    rootElement.appendChild(dependencyElement);
                    //Append groupId
                    Element groupId = document.createElement("groupId");
                    dependencyElement.appendChild(groupId);
                    groupId.appendChild(document.createTextNode(entry.getValue().get(i).getGroupId()));

                    //Append artifactId
                    Element artifactId = document.createElement("artifactId");
                    dependencyElement.appendChild(artifactId);
                    artifactId.appendChild(document.createTextNode(entry.getValue().get(i).getArtifactId()));

                    //Append version
                    Element version = document.createElement("version");
                    dependencyElement.appendChild(version);
                    version.appendChild(document.createTextNode(entry.getValue().get(i).getVersion()));
                }
            }
            //Write to output xml
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(new File(filename + "differences.xml"));
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(source, result);
            System.out.println("File saved!");
        } catch (ParserConfigurationException | TransformerException ex) {
            ex.printStackTrace();
        }
    }

    public static File getBasePom() throws FileNotFoundException, IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(new FileReader("pathconfig/config.json"));
        JSONObject jsonObject = (JSONObject) obj;
        String basePomPath = (String) jsonObject.get("basePomPath") + "/pom.xml";
        return new File(basePomPath);
    }

    public static void gatherAllPoms(ProjectRoot projectRoot) throws FileNotFoundException, IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(new FileReader("pathconfig/config.json"));
        JSONObject jsonObject = (JSONObject) obj;
        List<File> pomfiles;
        if (projectRoot == ProjectRoot.URBANPULSEEP) {
            projectFilePairs.clear();
            String urbanPulseEpRootPath = (String) jsonObject.get("urbanPulseEPPath");
            gatherPomsFromProject(urbanPulseEpRootPath);
        } else if (projectRoot == ProjectRoot.COCKPITV2) {
            projectFilePairs.clear();
            String cockpitV2Path = (String) jsonObject.get("cockpitV2Path");
            gatherPomsFromProject(cockpitV2Path);
            ;
        } else if (projectRoot == ProjectRoot.URBANPULSECONNECTOR) {
            projectFilePairs.clear();
            String urbanPulseConnectorPath = (String) jsonObject.get("urbanPulseConnectorPath");
            gatherPomsFromProject(urbanPulseConnectorPath);
        }
    }

    private static void gatherPomsFromProject(String rootPath) {
        File file = new File(rootPath);
        File[] files = file.listFiles();
        for (File f :
                files) {
            if (f.isFile() && f.getName().equals("pom.xml")) {
                String[] pathParts = f.getParent().split("/");
                projectFilePairs.add(new Pair<>(pathParts[pathParts.length - 1], f.getAbsolutePath()));
            } else if (f.isDirectory()) {
                gatherPomsFromProject(f.getAbsolutePath());
            }
        }
    }
}
