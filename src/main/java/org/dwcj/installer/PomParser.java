package org.dwcj.installer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * This class is reponsible for parsing the POM so the installer can access the options.
 */
public class PomParser {

  private final String pom;
  private HashMap<String, String> configEntries = new HashMap<>();

  /**
   * Constructor.
   *
   * @param pomfile - the path to find the pom.xml
   */
  public PomParser(String pomfile) {
    this.pom = pomfile;

    File inputFile = new File(pom);
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = null;
    try {
      docBuilder = dbFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    Document doc = null;
    try {
      doc = docBuilder.parse(inputFile);
    } catch (SAXException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    doc.getDocumentElement().normalize();

    NodeList nodeList = doc.getElementsByTagName("plugin");
    for (int temp = 0; temp < nodeList.getLength(); temp++) {
      Node theNode = nodeList.item(temp);
      Element theElement = (Element) theNode;
      if (theElement.getElementsByTagName("groupId").item(0).getTextContent().equals("org.dwcj")
          && theElement.getElementsByTagName("artifactId").item(0)
          .getTextContent().equals("dwcj-install-maven-plugin")) {
        Element cfg = (Element) theElement.getElementsByTagName("configuration").item(0);
        NodeList configs = cfg.getChildNodes();
        for (int i = 0; i < configs.getLength(); i++) {
          String entry = configs.item(i).getNodeName();
          if (entry.startsWith("#")) {
            continue;
          }
          configEntries.put(entry, configs.item(i).getTextContent());
        }
        break;
      }
    }
    System.out.println(configEntries.toString());
  }

  public String getConfiguration(String key) {
    return configEntries.get(key);
  }

}
