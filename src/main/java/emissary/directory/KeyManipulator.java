package emissary.directory;

import java.io.Serializable;

import emissary.place.IServiceProviderPlace;

/**
 * A class of utility methods for manipulating dictionary keys. Keys are stored in the dictionary with the following
 * format:
 *
 * <code>dataType.serviceName.serviceType.location$expense</code>
 * 
 * Example: UNKNOWN.servicename.ID.tcp://host.dom.com:8001/FilePlace$5050
 * 
 * <table>
 * <caption>For the example key above the return values are as follows:</caption>
 * <tr>
 * <td>routine</td>
 * <td>return value</td>
 * </tr>
 * <tr>
 * <td>dataType</td>
 * <td>UNKOWN</td>
 * </tr>
 * <tr>
 * <td>serviceName</td>
 * <td>servicename</td>
 * </tr>
 * <tr>
 * <td>serviceType</td>
 * <td>ID</td>
 * </tr>
 * <tr>
 * <td>serviceHost</td>
 * <td>host.dom.com:8001</td>
 * </tr>
 * <tr>
 * <td>serviceHostURL</td>
 * <td>http://host.dom.com:8001/</td>
 * </tr>
 * <tr>
 * <td>serviceLocation</td>
 * <td>http://host.dom.com:8001/FilePlace</td>
 * </tr>
 * <tr>
 * <td>expense</td>
 * <td>5050</td>
 * </tr>
 * <tr>
 * <td>serviceClassname</td>
 * <td>FilePlace</td>
 * </tr>
 * <tr>
 * <td>dataID</td>
 * <td>UNKOWN::ID</td>
 * </tr>
 * <tr>
 * <td>defaultDirectoryKey</td>
 * <td>*.*.*.http://host.dom.com:8001/DirectoryPlace</td>
 * </tr>
 * </table>
 *
 */
public class KeyManipulator implements Serializable {

    // Serializable
    static final long serialVersionUID = 2456659383313218695L;

    /**
     * How we separate portions of the key
     */
    public static final char SEPARATOR = '.';
    public static final char HOSTSEPARATOR = ':';
    public static final char CLASSSEPARATOR = '/';
    public static final String DOLLAR = "$";
    public static final String DATAIDSEPARATOR = "::";
    public static final String WILDCARD_THREE = "*.*.*.";

    /**
     * How many portions (separated by SEPARATOR) are in a complete key
     */
    public static final int NUMTUPLES = 4;

    private static final String doubleSlash = "//";

    /**
     * Make a key from parts
     *
     * @param dataType the first part of the key
     * @param serviceName the second part of the key
     * @param serviceType the third part of the key
     * @param serviceLocation the fourth part of the key
     */
    public static String makeKey(final String dataType, final String serviceName, final String serviceType, final String serviceLocation) {
        return dataType + SEPARATOR + serviceName + SEPARATOR + serviceType + SEPARATOR + serviceLocation;
    }

    /**
     * Return the data type field from a dictionary formatted key.
     */
    public static String getDataType(final String key) {
        final int firstSeparator = key.indexOf(SEPARATOR);

        if (firstSeparator >= 0) {
            return key.substring(0, firstSeparator);
        }
        return "";
    }

    public static String getDataID(final String key) {
        return getDataType(key) + DATAIDSEPARATOR + getServiceType(key);
    }

    public static String getServiceTypeFromDataID(final String dataid) {
        final int pos = dataid.indexOf(DATAIDSEPARATOR);
        if (pos > -1) {
            return dataid.substring(pos + DATAIDSEPARATOR.length());
        }
        return "";
    }

    /**
     * Performs wildcard (? | *) string matching for dictionary key searches.
     */
    public static boolean gmatch(final String s, final String p) {
        return gmatch2(s.toCharArray(), p.toCharArray(), 0, 0);
    }

    /**
     * Performs wildcard (? | *) character array matching for dictionary key searches.
     */
    public static boolean gmatch(final char[] s, final char[] p) {
        return gmatch2(s, p, 0, 0);
    }

    private static boolean gmatch2(final char[] s, final char[] p, final int spos, final int ppos) {
        // fastest interpreted version
        if (p.length == ppos) {
            return s.length == spos;
        }
        if (s.length == spos) {
            return false;
        }
        final char scc = s[spos];
        final char c = p[ppos];
        if (c == '?') {
            if (scc > 0) {
                return gmatch2(s, p, spos + 1, ppos + 1);
            }
            return false;
        } else if (c == '*') {
            final int ppos2 = ppos + 1;
            if (p.length == ppos2) {
                return true;
            }
            int spos2 = spos;
            while (s.length > ++spos2) {
                if (p[ppos2] == s[spos2]) {
                    if (gmatch2(s, p, spos2, ppos2)) {
                        return true;
                    }
                }
            }
            return false;
        } else if (c == scc) {
            return gmatch2(s, p, spos + 1, ppos + 1);
        } else {
            return false;
        }
    }

    /**
     * Return whether the key passed in is complete or not This is useful in determining if the key must be wildcarded for a
     * query.
     *
     * @param key the key to check
     */
    public static boolean isKeyComplete(final String key) {
        if (numTuplesInKey(key) < KeyManipulator.NUMTUPLES) {
            return false;
        }
        if (getServiceHost(key).length() == 0) {
            return false;
        }
        return true;
    }

    /**
     * Return the number of tuples in the Key passed in
     */
    public static int numTuplesInKey(final String key) {
        int count = 0;
        final byte[] keyBytes = key.getBytes();

        for (int i = 0; i < keyBytes.length; i++) {
            if (keyBytes[i] == KeyManipulator.SEPARATOR) {
                count++;
            }
            // Dont count separators in the hostname portion
            if ((i > 0) && (keyBytes[i] == CLASSSEPARATOR) && (keyBytes[i - 1] == CLASSSEPARATOR)) {
                break;
            }
        }

        // There is one more tuple than separators x.x.x.x
        return count + 1;
    }

    /**
     * Attempt to set out some rules for a key to allow validation
     *
     * @param key the putative key
     * @return true if key is valid
     */
    public static boolean isValid(final String key) {
        return (key != null) && (numTuplesInKey(key) == 4);
    }

    /**
     * Returns the class name from a dictionary formatted key
     */
    public static String getServiceClassname(final String key) {
        final String location = getServiceLocation(key);
        final int sep = location.lastIndexOf(CLASSSEPARATOR);

        if (sep >= 0) {
            return location.substring(sep + 1);
        }
        return "";
    }

    /**
     * Returns the hostname:port from a dictionary formatted key
     */
    public static String getServiceHost(final String key) {
        final String location = getServiceLocation(key);
        final int ds = location.indexOf(doubleSlash);

        if (ds > -1) {
            final int cs = location.indexOf(CLASSSEPARATOR, ds + 2);

            if (cs > -1) {
                return location.substring(ds + 2, cs);
            }
            return "";
        }
        return "";
    }

    /**
     * Returns the protocol://hostname:port/ from a dictionary formatted key
     */
    public static String getServiceHostURL(final String key) {
        final String location = getServiceLocation(key);
        final int ds = location.lastIndexOf(CLASSSEPARATOR);

        if (ds > -1) {
            return location.substring(0, ds + 1);
        }
        return "";
    }

    /**
     * Returns true if k1 and k2 are local to each other
     *
     * @param k1 key for first place
     * @param k2 key for second place
     * @return true if local to each other
     */
    public static boolean isLocalTo(final String k1, final String k2) {
        return getServiceHostURL(k1).equals(getServiceHostURL(k2));
    }

    /**
     * Returns the service location (host:port/className) field from a dictionary formatted key.
     */
    public static String getServiceLocation(final String key) {
        final int firstSeparator = key.indexOf(SEPARATOR);
        final int secondSeparator = key.indexOf(SEPARATOR, firstSeparator + 1);
        final int thirdSeparator = key.indexOf(SEPARATOR, secondSeparator + 1);
        final int fourthSeparator = key.indexOf(DOLLAR, thirdSeparator + 1);

        if ((thirdSeparator >= 0)) {
            if (fourthSeparator > 0) {
                return key.substring(thirdSeparator + 1, fourthSeparator);
            }
            return key.substring(thirdSeparator + 1);
        }
        return "";
    }

    /**
     * Return the expense of the service
     */
    public static int getExpense(final String key) {
        return getExpense(key, -1);
    }

    public static int getExpense(final String key, final int dflt) {
        final int pos = key.lastIndexOf(DOLLAR);
        int expense = dflt;
        try {
            expense = Integer.parseInt(key.substring(pos + 1));
        } catch (NumberFormatException e) {
            // It's optional...
        }
        return expense;
    }

    /**
     * Add expense record onto a key
     */
    public static String addExpense(final String key, final int expense) {
        if (getExpense(key, -99) == expense) {
            return key;
        }

        if (key.indexOf(DOLLAR) > -1) {
            return key.substring(0, key.lastIndexOf(DOLLAR) + 1) + expense;
        }

        return key + DOLLAR + expense;
    }

    /**
     * Strip the expense from a key if present
     *
     * @param key any key
     * @return the modified key
     */
    public static String removeExpense(final String key) {
        final int pos = key.indexOf(DOLLAR);
        if (pos != -1) {
            return key.substring(0, pos);
        }
        return key;
    }

    /**
     * Returns the service name field from a dictionary formatted key.
     */
    public static String getServiceName(final String key) {
        final int firstSeparator = key.indexOf(SEPARATOR);
        final int secondSeparator = key.indexOf(SEPARATOR, firstSeparator + 1);

        if ((firstSeparator >= 0) && (secondSeparator >= 0)) {
            return key.substring(firstSeparator + 1, secondSeparator);
        }
        return "";
    }

    /**
     * Returns the service type field from a dictionary formatted key.
     */
    public static String getServiceType(final String key) {
        final int firstSeparator = key.indexOf(SEPARATOR);
        final int secondSeparator = key.indexOf(SEPARATOR, firstSeparator + 1);
        final int thirdSeparator = key.indexOf(SEPARATOR, secondSeparator + 1);

        if ((secondSeparator >= 0) && (thirdSeparator >= 0)) {
            return key.substring(secondSeparator + 1, thirdSeparator);
        }
        return "";
    }

    /**
     * Add in the remote overhead to the "to" key if it was a move
     *
     * @param from the key we came from
     * @param to the key we went to
     * @return the &quot;to&quot; key with remote overhead or as it was
     */
    public static String addRemoteCostIfMoved(final String from, final String to) {

        // If no move, just return the "to" key
        if (getServiceHost(from).equals(getServiceHost(to))) {
            return to;
        }

        // Add remote overhead to the "to" key
        return addExpense(to, getExpense(to) + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD);
    }

    /**
     * Mangle origKey such that the host on proxyKey becomes a proxy for it
     *
     * @param origKey the original key
     * @param proxyKey the key representing the place that wants to proxy
     * @param dfltExp default expense to use if origKey has none or -1 for no dflt
     * @return a key with the original data type, service type and service name and expense but the new place location from
     *         proxyKey
     */
    public static String makeProxyKey(final String origKey, final String proxyKey, final int dfltExp) {
        if (isLocalTo(origKey, proxyKey) && dfltExp > -1) {
            return addExpense(origKey, getExpense(origKey, dfltExp));
        }
        final int newExp = getExpense(origKey, dfltExp);
        return getDataType(origKey) + SEPARATOR + getServiceName(origKey) + SEPARATOR + getServiceType(origKey) + SEPARATOR
                + getServiceLocation(proxyKey) + (newExp > -1 ? (DOLLAR + newExp) : "");
    }

    /**
     * Build a key to the directory of the place represented by the key
     *
     * @param key the key of the place to find a directory for
     */
    public static String getDefaultDirectoryKey(final String key) {
        return WILDCARD_THREE + KeyManipulator.getServiceHostURL(key) + "DirectoryPlace";
    }


    public static String getHostMatchKey(final String key) {
        return WILDCARD_THREE + KeyManipulator.getServiceHostURL(key) + "*";
    }

    public static void main(final String[] argv) {
        String temp = "UNKNOWN.place.ID.tcp://host.domain.com:8001/thePlace$5050";
        String pat = "UNKNOWN.place.*";

        if (argv.length > 1) {
            temp = argv[0];
            pat = argv[1];
        }

        if (gmatch(temp, pat)) {
            System.out.println("***matched --> " + temp);
            System.out.println("ServiceType: " + getServiceType(temp));
            System.out.println("ServiceName: " + getServiceName(temp));
            System.out.println("DataType   : " + getDataType(temp));
            System.out.println("Location   : " + getServiceLocation(temp));
            System.out.println("HostURL    : " + getServiceHostURL(temp));
            System.out.println("HostName   : " + getServiceHost(temp));
            System.out.println("ClassName  : " + getServiceClassname(temp));
            System.out.println("Expense    : " + getExpense(temp));
            System.out.println("Default Directory  : " + getDefaultDirectoryKey(temp));
            final String hckey = getHostMatchKey(temp);
            System.out.println("Host match key : " + hckey + (gmatch(temp, hckey) ? " matches" : " does not match"));
        } else {
            System.out.println(" --- no match ---");
        }
    }

    public static String makeSproutKey(final String placeKey) {
        return "*.*." + IServiceProviderPlace.SPROUT_KEY + "." + getServiceLocation(placeKey) + DOLLAR + "0";
    }

    /** This class is not meant to be instantiated. */
    private KeyManipulator() {}
}
