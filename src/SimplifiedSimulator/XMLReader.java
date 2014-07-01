/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

/**
 *
 * @author Herman
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.File;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Herman
 */
public class XMLReader {

    File _XMLFile;

    DocumentBuilderFactory dbFactory;
    DocumentBuilder dBuilder;

    Document doc;

    public XMLReader(String path_name) throws Exception {

        _XMLFile = new File(path_name);
        this.dbFactory = DocumentBuilderFactory.newInstance();
        dBuilder = dbFactory.newDocumentBuilder();
        doc = dBuilder.parse(_XMLFile);

        doc.getDocumentElement().normalize();

    }

    public void Parse() {
        System.out.println("Root Element : " + doc.getDocumentElement().getNodeName());

        NodeList nList = doc.getElementsByTagName("Item");

        for (int i = 0; i < nList.getLength(); i++) {
            Node nNode = nList.item(i);

            System.out.println("\nCurrent Element :" + nNode.getNodeName());

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;

                int Type = Integer.parseInt(eElement.getElementsByTagName("Type").item(0).getTextContent());
                System.out.println("Type : " + Type);
                int StartPoint = Integer.parseInt(eElement.getElementsByTagName("StartPoint").item(0).getTextContent());
                System.out.println("StartPoint : " + StartPoint);
                int EndPoint = Integer.parseInt(eElement.getElementsByTagName("EndPoint").item(0).getTextContent());
                System.out.println("Last Name : " + EndPoint);
                int Time = Integer.parseInt(eElement.getElementsByTagName("Time").item(0).getTextContent());
                System.out.println("Time : " + Time);

                SimulationEvent tempEvent = new SimulationEvent(Type, StartPoint, EndPoint, Time);
                AddToEvents(tempEvent);
            }
        }
    }

    public void AddToEvents(SimulationEvent event) {
        SimplifiedSimulator.getInstance().addEvent(event);
    }

}
