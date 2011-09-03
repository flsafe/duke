
package no.priv.garshol.duke;

import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.io.InputStream;
import java.io.IOException;
import org.xml.sax.XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLReaderFactory;
import org.xml.sax.helpers.DefaultHandler;

import no.priv.garshol.duke.utils.ObjectUtils;
import no.priv.garshol.duke.comparators.ExactComparator;

/**
 * Can read XML configuration files and return a fully set up configuration.
 */
public class ConfigLoader {

  /**
   * Note that if file starts with 'classpath:' the resource is looked
   * up on the classpath instead.
   */
  public static Configuration load(String file)
    throws IOException, SAXException {
    Configuration cfg = new Configuration();

    XMLReader parser = XMLReaderFactory.createXMLReader();
    parser.setContentHandler(new ConfigHandler(cfg));
    if (file.startsWith("classpath:")) {
      String resource = file.substring("classpath:".length());
      ClassLoader cloader = Thread.currentThread().getContextClassLoader();
      InputStream istream = cloader.getResourceAsStream(resource);
      parser.parse(new InputSource(istream));
    } else
      parser.parse(file);

    return cfg;
  }

  private static class ConfigHandler extends DefaultHandler {
    private Configuration config;
    private Collection<Property> properties;

    private double threshold;
    private double thresholdMaybe;
    private double low;
    private double high;
    private String path;
    private String name;
    private boolean idprop;
    private Comparator comparator;
    
    private Set<String> keepers;
    private int groupno; // counts datasource groups
    private Map<String, Object> objects; // configured Java beans for reuse
    private DataSource datasource;
    private Object currentobj; // Java bean currently being configured by <param>
    
    private boolean keep;
    private StringBuffer content;

    private ConfigHandler(Configuration config) {
      this.config = config;
      this.properties = new ArrayList<Property>();

      this.objects = new HashMap();
      this.keepers = new HashSet();
      this.content = new StringBuffer();

      keepers.add("threshold");
      keepers.add("maybe-threshold");
      keepers.add("path");
      keepers.add("name");
      keepers.add("low");
      keepers.add("high");
      keepers.add("comparator");
    }

    public void	startElement(String uri, String localName, String qName,
                             Attributes attributes) {
      if (keepers.contains(localName)) {
        keep = true;
        content.setLength(0); // clear
      } else if (localName.equals("property")) {
        String type = attributes.getValue("type");
        idprop = type != null && type.equals("id");
      } else if (localName.equals("csv")) {
        datasource = new CSVDataSource();
        currentobj = datasource;
      } else if (localName.equals("jdbc")) {
        datasource = new JDBCDataSource();
        currentobj = datasource;
      } else if (localName.equals("sparql")) {
        datasource = new SparqlDataSource();
        currentobj = datasource;
      } else if (localName.equals("ntriples")) {
        datasource = new NTriplesDataSource();
        currentobj = datasource;
      } else if (localName.equals("data-source")) {
        datasource = (DataSource) instantiate(attributes.getValue("class"));
        currentobj = datasource;
      } else if (localName.equals("column")) {
        String name = attributes.getValue("name");
        String property = attributes.getValue("property");
        String prefix = attributes.getValue("prefix");
        String cleanername = attributes.getValue("cleaner");
        Cleaner cleaner = null;
        if (cleanername != null) {
          cleaner = (Cleaner) objects.get(cleanername);
          if (cleaner == null) // wasn't a configured bean
            cleaner = (Cleaner) instantiate(cleanername);
        }

        if (datasource instanceof ColumnarDataSource)
          ((ColumnarDataSource) datasource).addColumn(
              new Column(name, property, prefix, cleaner));
        else
          throw new RuntimeException("Column inside data source which does " +
                                     "not support it: " + datasource);
      } else if (localName.equals("param"))
        ObjectUtils.setBeanProperty(currentobj, attributes.getValue("name"),
                                    attributes.getValue("value"));
      else if (localName.equals("group")) {
        groupno++;
        // FIXME: now possible to have data sources between the two
        // groups.  need to check for that, too. ideally XML
        // validation should take care of all this for us.
        if (groupno == 1 && !config.getDataSources().isEmpty())
          throw new DukeConfigException("Cannot have groups in deduplication mode");
        else if (groupno == 3)
          throw new DukeConfigException("Record linkage mode only supports " +
                                        "two groups");
        
      } else if (localName.equals("object")) {
        String klass = attributes.getValue("class");
        String name = attributes.getValue("name");
        currentobj = instantiate(klass);
        objects.put(name, currentobj);
      }
    }

    public void characters(char[] ch, int start, int length) {
      if (keep)
        content.append(ch, start, length);
    }

    public void endElement(String uri, String localName, String qName) {
      if (localName.equals("threshold"))
        threshold = Double.parseDouble(content.toString());
      else if (localName.equals("maybe-threshold"))
        thresholdMaybe = Double.parseDouble(content.toString());
      else if (localName.equals("path"))
        path = content.toString();
      else if (localName.equals("name"))
        name = content.toString();
      else if (localName.equals("property")) {
        if (idprop)
          properties.add(new Property(name));
        else {
          if (comparator == null)
            comparator = new ExactComparator(); // default value
          properties.add(new Property(name, comparator, low, high));
        }
      } else if (localName.equals("low"))
        low = Double.parseDouble(content.toString());
      else if (localName.equals("high"))
        high = Double.parseDouble(content.toString());
      else if (localName.equals("comparator")) {
        comparator = (Comparator) objects.get(content.toString());
        if (comparator == null) // wasn't a configured bean
          comparator = (Comparator) instantiate(content.toString());
      } else if (localName.equals("csv") ||
               localName.equals("jdbc") ||
               localName.equals("ntriples") ||
               localName.equals("sparql") ||
               localName.equals("data-source")) {
        config.addDataSource(groupno, datasource);
        datasource = null;
        currentobj = null;
      } else if (localName.equals("object"))
        currentobj = null;
      
      if (keepers.contains(localName))
        keep = false;
    }

    public void endDocument() {
      config.setDatabase(new Database(path, properties, threshold,
                                      thresholdMaybe, true));
    }
  }  

  private static Object instantiate(String classname) {
    try {
      Class klass = Class.forName(classname);
      return klass.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}