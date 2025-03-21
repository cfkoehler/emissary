package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.core.TransformHistory;
import emissary.util.xml.JDOMUtil;

import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilities for dealing with IBaseDataObject and Lists thereof
 */
public class PayloadUtil {
    public static final Logger logger = LoggerFactory.getLogger(PayloadUtil.class);

    private static final String LS = System.getProperty("line.separator");
    private static final Pattern validFormRegex = Pattern.compile("^[\\w-)(/+]+$");

    protected static final Map<String, String> historyPreference = new HashMap<>();

    protected static final String REDUCED_HISTORY = "REDUCED_HISTORY";
    protected static final String NO_URL = "NO_URL";

    @SuppressWarnings("NonFinalStaticField")
    protected static boolean compactHistory;

    static {
        configure();
    }

    protected static void configure() {
        Configurator configG = null;
        try {
            configG = ConfigUtil.getConfigInfo(PayloadUtil.class);
            compactHistory = configG.findBooleanEntry("COMPACT_HISTORY", false);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
        }

        // fill lists with data from PayloadUtil.cfg for transform itinerary/history
        if (configG != null) {
            for (String fileType : configG.findEntries(REDUCED_HISTORY)) {
                setFileTypeHistoryPreference(fileType, REDUCED_HISTORY);
            }
            for (String fileType : configG.findEntries(NO_URL)) {
                setFileTypeHistoryPreference(fileType, NO_URL);
            }
        }
    }

    /**
     * Check if fileType is already mapped to history preference. If not, set fileType -$gt; preference in map
     *
     * @param fileType current fileType pulled from cfg
     * @param preference preference associated with current cfg fileType
     */
    protected static void setFileTypeHistoryPreference(String fileType, String preference) {
        if (historyPreference.containsKey(fileType)) {
            if (historyPreference.get(fileType).equals(preference)) {
                // log if filetype is assigned in the same preference more than once
                logger.warn("FileType {} is assigned to {} in cfg more than once.", fileType, preference);
            } else {
                // log if filetype already has previous preference assignment
                logger.warn("FileType {} already has history preference {} assigned. {} will be ignored.", fileType,
                        historyPreference.get(fileType), preference);
            }
        } else {
            historyPreference.put(fileType, preference);
        }
    }

    /**
     * Try really hard to get a meaningful name for a payload object
     */
    public static String getName(final Object o) {
        String payloadName = o.getClass().getName();
        if (o instanceof IBaseDataObject) {
            payloadName = ((IBaseDataObject) o).shortName();
        } else if (o instanceof Collection) {
            final Iterator<?> pi = ((Collection<?>) o).iterator();
            if (pi.hasNext()) {
                payloadName = ((IBaseDataObject) pi.next()).shortName() + "(" + ((Collection<?>) o).size() + ")";
            }
        }
        return payloadName;
    }

    /**
     * Generate a string about the payload object
     * 
     * @param payload the payload to describe
     * @param oneLine true for a condensed one-line string
     */
    public static String getPayloadDisplayString(final IBaseDataObject payload, final boolean oneLine) {
        return oneLine ? getPayloadOneLineString(payload) : getPayloadDisplayString(payload);
    }

    /**
     * Generate a string about the payload object
     * 
     * @param payload the payload to describe
     */
    public static String getPayloadDisplayString(final IBaseDataObject payload) {
        final StringBuilder sb = new StringBuilder();
        final TransformHistory th = payload.getTransformHistory();
        final String fileName = payload.getFilename();
        final String fileType = payload.getFileType();
        final List<String> currentForms = payload.getAllCurrentForms();
        final Instant creationTimestamp = payload.getCreationTimestamp();

        sb.append("\n").append("filename: ").append(fileName).append("\n").append("   creationTimestamp: ").append(creationTimestamp).append("\n")
                .append("   currentForms: ").append(currentForms).append("\n").append("   filetype: ").append(fileType).append("\n")
                .append("   transform history (").append(th.size(true)).append(") :").append("\n");

        // transform history output
        String historyCase = configureHistoryCase(fileType);
        if (historyCase.equals(REDUCED_HISTORY)) {
            // found reduced history match, output only dropoff
            sb.append("   ** reduced transform history **").append("\n").append("     dropOff -> ")
                    .append(payload.getLastPlaceVisited())
                    .append("\n");
        } else if (compactHistory) {
            for (String history : th.format()) {
                sb.append("     ").append(history).append("\n");
            }
        } else {
            for (final TransformHistory.History h : th.getHistory()) {
                sb.append("     ").append(h.getKey(historyCase.equals(NO_URL))).append("\n");
                for (final String coord : h.getCoordinated(historyCase.equals(NO_URL))) {
                    sb.append("      ").append(coord).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Check if fileType in PayloadUtil.cfg matches current payload fileType
     *
     * @param fileType current payload fileType
     * @return string for output case
     */
    public static String configureHistoryCase(String fileType) {
        if (historyPreference.containsKey(fileType)) {
            return historyPreference.get(fileType);
        }
        // no match for current fileType, return empty string
        return "";
    }

    /**
     * Generate a one-line string about the payload object
     * 
     * @param payload the payload to describe
     */
    public static String getPayloadOneLineString(final IBaseDataObject payload) {
        final StringBuilder sb = new StringBuilder();
        final String fn = payload.getFilename();
        final int attPos = fn.indexOf(Family.SEP);
        if (attPos != -1) {
            sb.append(fn.substring(attPos + 1)).append(" ");
        }
        final List<String> th = payload.transformHistory(true);
        String prev = "";
        for (final String h : th) {
            final int pos = h.indexOf(".");
            if (pos > 0) {
                final String prefix = h.substring(0, pos);
                if (!prev.equals(prefix)) {
                    if (prev.length() != 0) {
                        sb.append(",");
                    }
                    sb.append(prefix);
                    prev = prefix;
                }
            }
        }
        sb.append(">>").append(payload.getAllCurrentForms());
        sb.append("//").append(payload.getFileType());
        sb.append("//").append(payload.getCreationTimestamp());
        return sb.toString();
    }

    /**
     * Turn the payload into an xml jdom document
     * 
     * @param d the payload
     */
    public static Document toXml(final IBaseDataObject d) {
        final Element root = new Element("payload");
        root.addContent(JDOMUtil.protectedElement("name", d.getFilename()));
        final Element cf = new Element("current-forms");
        for (final String c : d.getAllCurrentForms()) {
            cf.addContent(JDOMUtil.simpleElement("current-form", c));
        }
        root.addContent(cf);
        root.addContent(JDOMUtil.simpleElement("encoding", d.getFontEncoding()));
        root.addContent(JDOMUtil.simpleElement("filetype", d.getFileType()));
        root.addContent(JDOMUtil.simpleElement("classification", d.getClassification()));
        final Element th = new Element("transform-history");
        for (final String s : d.transformHistory(true)) {
            th.addContent(JDOMUtil.simpleElement("itinerary-step", s));
        }
        root.addContent(th);
        if (d.getProcessingError() != null) {
            root.addContent(JDOMUtil.simpleElement("processing-error", d.getProcessingError()));
        }
        final Element meta = new Element("metadata");
        for (final String key : d.getParameters().keySet()) {
            final Element m = JDOMUtil.protectedElement("param", d.getStringParameter(key));
            m.setAttribute("name", key);
            meta.addContent(m);
        }
        root.addContent(meta);

        if (d.header() != null) {
            root.addContent(JDOMUtil.protectedElement("header", d.header()));
        }
        if (d.dataLength() > 0) {
            root.addContent(JDOMUtil.protectedElement("data", d.data()));
        }
        if (d.footer() != null) {
            root.addContent(JDOMUtil.protectedElement("footer", d.footer()));
        }

        // Alt views
        if (d.getNumAlternateViews() > 0) {
            final Element views = new Element("views");
            for (final String av : d.getAlternateViewNames()) {
                final Element v = JDOMUtil.protectedElement("view", d.getAlternateView(av));
                v.setAttribute("name", av);
                views.addContent(v);
            }
            root.addContent(views);
        }

        logger.debug("Produced xml document for {}", d.shortName());
        return new Document(root);
    }

    /**
     * Turn a list of payload into an xml jdom ocument
     * 
     * @param list the payload list
     */
    public static Document toXml(final List<IBaseDataObject> list) {
        final Element root = new Element("payload-list");
        for (final IBaseDataObject d : list) {
            final Document doc = toXml(d);
            root.addContent(doc.detachRootElement());
            logger.debug("Adding xml content for {} to document", d.shortName());
        }
        return new Document(root);
    }

    /**
     * Turn the payload into an xml string
     * 
     * @param d the payload
     */
    public static String toXmlString(final IBaseDataObject d) {
        return JDOMUtil.toString(toXml(d));
    }

    /**
     * Turn the payload list into an xml string
     * 
     * @param list the payload list
     */
    public static String toXmlString(final List<IBaseDataObject> list) {
        return JDOMUtil.toString(toXml(list));
    }

    /**
     * Print formatted metadata key:value pairs
     */
    private static final String SEP = ": ";

    public static String printFormattedMetadata(final IBaseDataObject payload) {
        final StringBuilder out = new StringBuilder();
        out.append(LS);
        for (final Map.Entry<String, Collection<Object>> entry : payload.getParameters().entrySet()) {
            out.append(entry.getKey()).append(SEP).append(entry.getValue()).append(LS);
        }
        return out.toString();
    }

    /**
     * Checks whether the form complies with form rules established by a regex
     *
     * Approved forms can contain alpha-numerics, '-', '_', '()', '/', '+'
     *
     * @param form The form to be tested
     * @return Whether the form is Emissary compliant
     */
    public static boolean isValidForm(String form) {
        return validFormRegex.matcher(form).find();
    }

    /** This class is not meant to be instantiated. */
    private PayloadUtil() {}
}
