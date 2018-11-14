package emissary.directory;

import static emissary.directory.KeyManipulator.CLASSSEPARATOR;
import static emissary.directory.KeyManipulator.DOLLAR;

import java.io.Serializable;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.place.IServiceProviderPlace;
import emissary.util.xml.JDOMUtil;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is container object for storing directory entry information such as the service name and type, the
 * location, the cost and quality and description of a place.
 *
 * The implementation is not synchronized and is not suitable for multiple threads that will be changing the contents
 * unless external synchronization is used.
 */
public class DirectoryEntry implements Serializable {

    // Serializable
    static final long serialVersionUID = 2629953887545857011L;

    /** The key for this entry as a string */
    protected String theKey;

    /** The cost of this entry */
    protected int theCost = 0;

    /** The quality of this entry */
    protected int theQuality = 100;

    /**
     * The expense is computed from the cost and quality by {@link #calculateExpense}
     */
    protected int theExpense = 0;

    /**
     * Nominal starting path weight. Making it lower makes the entry less likely to be selected from a group with equal
     * expense. Making it higher makes it more likely to be selected from a group with equal expense. Between entries with
     * different expense values there is no effect.
     */
    protected transient int pathWeight = 500;

    /** Protection against repeated namespace lookups for this entry */
    protected transient boolean lookupAttempted = false;

    /** Cached reference to the place instance if local */
    protected transient IServiceProviderPlace localPlace = null;

    /** The data type from the key */
    protected String dataType;

    /** The DataType::ServiceType from the key */
    protected String dataID;

    /** THe service name from the key */
    protected String serviceName;

    /** The service type from the key */
    protected String serviceType;

    /** THe service location from the key */
    protected String serviceLocation;

    /** The service URL from the key */
    protected String serviceHostURL;

    /** Logger instance */
    protected static final Logger logger = LoggerFactory.getLogger(DirectoryEntry.class);

    /** The description field for this entry */
    protected String description;

    /** Age of this entry */
    protected transient long age = System.currentTimeMillis();

    /** Xml name of entry element value is {@value} */
    public static final String ENTRY = "entry";

    /** Xml name of key element value is {@value} */
    public static final String KEY = "key";

    /** Xml name of description element value is {@value} */
    public static final String DESC = "description";

    /** Xml name of cost element value is {@value} */
    public static final String COST = "cost";

    /** Xml name of quality element value is {@value} */
    public static final String QUALITY = "quality";

    /** Xml name of expense element value is {@value} */
    public static final String EXPENSE = "expense";

    /** Value of PRESERVE_TIME flag */
    public static final boolean PRESERVE_TIME = true;

    /**
     * Constructor which create a new directory entry and new directoryInfo object.
     * 
     * @param key four-tuple key of the place
     * @param description from config file
     * @param cost from config file
     * @param quality from config file
     */
    public DirectoryEntry(final String key, final String description, final int cost, final int quality) {
        if (logger.isDebugEnabled()) {
            logger.debug("Directory entry: " + key + "," + cost + "," + quality + ",\"" + description + "\"");
        }
        setKey(key);
        this.theQuality = quality;
        this.theCost = cost;
        calculateExpense();

        this.description = description;
    }

    /**
     * Create an entry from nothing but a key
     * 
     * @param key the key to use
     */
    public DirectoryEntry(final String key) {
        setKey(key);
    }

    /**
     * Make an entry from parts, specifying expense
     * 
     * @param dataType the first part of the key
     * @param serviceName the second part of the key
     * @param serviceType the third part of the key
     * @param serviceLocation the fourth part of the key
     * @param description the description
     * @param expense the expense
     */
    public DirectoryEntry(final String dataType, final String serviceName, final String serviceType, final String serviceLocation,
            final String description, final int expense) {
        this.dataType = dataType;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.serviceLocation = serviceLocation;
        this.description = description;
        setCQEFromExp(expense);
        buildKey();
    }

    /**
     * Make an entry from parts, specifing cost and quality
     * 
     * @param dataType the first part of the key
     * @param serviceName the second part of the key
     * @param serviceType the third part of the key
     * @param serviceLocation the fourth part of the key
     * @param description the description
     * @param cost the cost of the place
     * @param quality the quality of the place
     */
    public DirectoryEntry(final String dataType, final String serviceName, final String serviceType, final String serviceLocation,
            final String description, final int cost, final int quality) {
        this.dataType = dataType;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.serviceLocation = serviceLocation;
        this.description = description;
        this.theCost = cost;
        this.theQuality = quality;
        calculateExpense();
        buildKey();
    }

    /**
     * Make an entry from parts, default expense
     * 
     * @param dataType the first part of the key
     * @param serviceName the second part of the key
     * @param serviceType the third part of the key
     * @param serviceLocation the fourth part of the key
     * @param description the description
     */
    public DirectoryEntry(final String dataType, final String serviceName, final String serviceType, final String serviceLocation,
            final String description) {
        this(dataType, serviceName, serviceType, serviceLocation, description, 0, 0);
    }

    /**
     * Copy constructor
     * 
     * @param that the entry to copy
     */
    public DirectoryEntry(final DirectoryEntry that) {
        this(that, !PRESERVE_TIME);
    }

    /**
     * Copy constructor with time copy
     * 
     * @param that the entry to copy
     * @param preserveTime copy the time value also when true
     */
    DirectoryEntry(final DirectoryEntry that, final boolean preserveTime) {
        this.theKey = that.theKey;
        this.serviceType = that.serviceType;
        this.serviceName = that.serviceName;
        this.dataType = that.dataType;
        this.dataID = that.dataID;
        this.serviceLocation = that.serviceLocation;
        this.serviceHostURL = that.serviceHostURL;
        this.theQuality = that.theQuality;
        this.theCost = that.theCost;
        this.calculateExpense();
        this.pathWeight = that.pathWeight;
        this.description = that.description;
        if (preserveTime) {
            this.age = that.age;
        }
    }

    /**
     * Ensure this directory entry contains a valid key
     */
    public boolean isValid() {
        return KeyManipulator.isValid(this.theKey);
    }

    /**
     * Set the expense, cost and quality from the expense
     * 
     * @param expense the expense to use
     */
    protected void setCQEFromExp(final int expense) {
        if (expense >= 100) {
            final int invQual = expense % 100;
            this.theQuality = 100 - invQual;
            this.theCost = (expense - invQual) / 100;
        } else {
            // Should give expense of 0
            this.theCost = 0;
            this.theQuality = 100;
        }

        this.theExpense = expense;

    }

    /**
     * Get place description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Returns the key
     */
    public String getKey() {
        return this.theKey;
    }

    /**
     * Return the full key with expense
     */
    public String getFullKey() {
        return this.theKey + DOLLAR + this.theExpense;
    }

    /**
     * Get quality of how well a place does its function
     */
    public int getQuality() {
        return this.theQuality;
    }

    /**
     * Set the quality associated with the entry and force the expese to be recalculated
     */
    public void setQuality(final int quality) {
        this.theQuality = quality;
        calculateExpense();
    }

    /**
     * Get and set cost of using system, ie. how fast the place processes data
     */
    public int getCost() {
        return this.theCost;
    }

    /**
     * Set the cost of processing at a place.
     * 
     * @param cost the new cost
     */
    public void setCost(final int cost) {
        this.theCost = cost;
        calculateExpense();
    }

    /**
     * Increment the cost of a place's processing
     * 
     * @param costIncrement the increment to add
     */
    public void addCost(final int costIncrement) {
        this.theCost += costIncrement;
        calculateExpense();
    }

    /**
     * Set key and precompute stuff we need
     * 
     * @see #buildKey
     * @param key the key
     */
    protected void setKey(final String key) {
        this.theKey = KeyManipulator.removeExpense(key);
        this.serviceType = KeyManipulator.getServiceType(this.theKey);
        this.serviceName = KeyManipulator.getServiceName(this.theKey);
        this.dataType = KeyManipulator.getDataType(key);
        this.dataID = this.dataType + KeyManipulator.DATAIDSEPARATOR + this.serviceType;
        this.serviceLocation = KeyManipulator.getServiceLocation(key);
        this.serviceHostURL = KeyManipulator.getServiceHostURL(key);
        final int exp = KeyManipulator.getExpense(key, -1);
        if (exp > -1) {
            setCQEFromExp(exp);
        }
    }

    /**
     * Get the path weight of moving to a place Nominal starting path weight is 500. Making it lower makes the entry less
     * likely to be selected from a group with equal expense. Making it higher makes it more likely to be selected from a
     * group with equal expense. Between entries with different expense values there is no effect. The value is not allowed
     * to go below zero.
     * 
     * @return the current path weight value
     */
    public int getPathWeight() {
        return this.pathWeight;
    }

    /**
     * Set the path weight of moving to a place
     * 
     * @see #getPathWeight()
     */
    public void setPathWeight(final int value) {
        this.pathWeight = value;
        if (this.pathWeight < 0) {
            this.pathWeight = 0;
        }
    }


    /**
     * Add to the path weight. The increment can be negative to remove weight. The path weight is not allowed to go below
     * zero.
     * 
     * @see #getPathWeight()
     * @param weightIncrement amount to add to weight
     */
    public void addPathWeight(final int weightIncrement) {
        this.pathWeight += weightIncrement;
        if (this.pathWeight < 0) {
            this.pathWeight = 0;
        }
    }

    /**
     * Set a new data type
     * 
     * @param dataType the new data type
     */
    public void setDataType(final String dataType) {
        this.dataType = dataType;
        buildKey();
    }

    /**
     * Set a new servicelocation
     * 
     * @param serviceLocation the new value
     */
    public void setServiceLocation(final String serviceLocation) {
        this.serviceLocation = serviceLocation;
        this.serviceHostURL = serviceLocation.substring(0, serviceLocation.lastIndexOf(CLASSSEPARATOR) + 1);
        buildKey();
    }

    /**
     * Set a reference to the local place object
     * 
     * @param place the local reference
     */
    protected void setLocalPlace(final IServiceProviderPlace place) {
        this.localPlace = place;
    }

    /**
     * Get service name
     * 
     * @return service name from key
     */
    public String getDataType() {
        return this.dataType;
    }

    /**
     * Get dataID
     * 
     * @return dataID from key
     */
    public String getDataID() {
        return this.dataID;
    }

    /**
     * Get service location
     * 
     * @return service location from key
     */
    public String getServiceLocation() {
        return this.serviceLocation;
    }

    /**
     * Get service host url
     * 
     * @return service host url from key
     */
    public String getServiceHostURL() {
        return this.serviceHostURL;
    }

    /**
     * Get the reference to the local place
     * 
     * @return local place reference or null if not local
     */
    public IServiceProviderPlace getLocalPlace() {
        isLocal(); // force lookup if needed
        return this.localPlace;
    }

    /**
     * String rep for debug
     */
    @Override
    public String toString() {
        return getFullKey() + (this.description != null ? (" (" + this.description + ")") : "");
    }

    /**
     * Used to order directory entries. Returns true if this entry is better than the argument entry
     * 
     * @param that the entry to test
     * @return true if this is better than that
     */
    public boolean isBetterThan(final DirectoryEntry that) {
        return this.theExpense < that.getExpense();
    }

    /**
     * test if the current dataEntry matches the passed key pattern.
     */
    public boolean equals(final String pattern) {
        return equals(pattern.toCharArray());
    }

    /**
     * test if the current dataEntry matches the passed key pattern
     */
    public boolean equals(final char[] pattern) {
        return KeyManipulator.gmatch(this.theKey.toCharArray(), pattern);
    }


    /**
     * test if the current dataEntry matches the passed key pattern specifically ignoring cost in the incoming pattern (if
     * any)
     */
    public boolean equalsIgnoreCost(final String pattern) {
        return equals(KeyManipulator.removeExpense(pattern));
    }

    /**
     * Return the service type from the key of this entry
     */
    public String getServiceType() {
        return this.serviceType;
    }

    /**
     * Set a new service type
     */
    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
        buildKey();
    }

    /**
     * Return the service name from the key of this entry
     */
    public String getServiceName() {
        return this.serviceName;
    }


    /**
     * Set a new service name
     */
    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
        buildKey();
    }

    /**
     * Return the expense associated with this entry
     */
    public int getExpense() {
        return this.theExpense;
    }

    /**
     * Expense includes the cost and quality, cost is primary
     */
    protected void calculateExpense() {
        this.theExpense = calculateExpense(this.theCost, this.theQuality);
    }

    /**
     * Calculate an expense value from the cost and quality, cost is primary
     * 
     * @param cost the cost
     * @param quality the quality
     */
    public static int calculateExpense(final int cost, final int quality) {
        return (cost * 100) + (100 - quality);
    }

    /**
     * Keep the internal key parameters in sync when one of the setters is called
     * 
     * @see #setKey(String)
     */
    protected void buildKey() {
        this.theKey = KeyManipulator.makeKey(this.dataType, this.serviceName, this.serviceType, this.serviceLocation);
        this.dataID = this.dataType + KeyManipulator.DATAIDSEPARATOR + this.serviceType;
    }

    /**
     * Determine if local by looking up in namespace
     * 
     * @return true if local
     */
    public boolean isLocal() {
        if (!this.lookupAttempted) {
            try {
                setLocalPlace((IServiceProviderPlace) Namespace.lookup(this.serviceLocation));
            } catch (NamespaceException e) {
                // empty catch block
            }
            logger.debug("NS Lookup for locality on " + this.serviceLocation + (this.localPlace == null ? " failed" : " passed"));

            this.lookupAttempted = true;
        }
        return this.localPlace != null;
    }

    /**
     * Change the key such that the place specified by proxyKey acts as a proxy for the current key. We keep the same data
     * type, service type, service name and expense but change the place to the proxy
     * 
     * @param proxyKey the replacement key
     */
    public void proxyFor(final String proxyKey) {
        final String newKey = KeyManipulator.makeProxyKey(this.theKey, proxyKey, this.theExpense);
        setKey(newKey);
    }

    /**
     * Show the creation date of this entry
     */
    public long getAge() {
        return this.age;
    }

    /**
     * Package access to the age value so that creation time can be preserved in some cases
     * 
     * @param age the age to be preserved
     */
    void preserveCopyAge(final long age) {
        this.age = age;
    }

    /**
     * Build an entry from the supplied xml fragment
     * 
     * @param e a JDOM Element
     */
    public static DirectoryEntry fromXML(final Element e) {
        final String key = e.getChildTextTrim(KEY);
        final String desc = e.getChildTextTrim(DESC);
        final int cost = JDOMUtil.getChildIntValue(e, COST);
        final int quality = JDOMUtil.getChildIntValue(e, QUALITY);
        final DirectoryEntry d = new DirectoryEntry(key, desc, cost, quality);
        return d;
    }

    /**
     * Turn this entry into an xml fragment
     */
    public Element getXML() {
        final Element root = new Element(ENTRY);
        root.addContent(JDOMUtil.simpleElement(KEY, this.theKey));
        root.addContent(JDOMUtil.simpleElement(DESC, this.description));
        root.addContent(JDOMUtil.simpleElement(COST, this.theCost));
        root.addContent(JDOMUtil.simpleElement(QUALITY, this.theQuality));
        root.addContent(JDOMUtil.simpleElement(EXPENSE, this.theExpense));
        return root;
    }
}
