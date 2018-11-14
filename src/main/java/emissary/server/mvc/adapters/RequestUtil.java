package emissary.server.mvc.adapters;

import javax.servlet.ServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for dealing with request parameters
 */
public class RequestUtil {
    public static final int INT_PARAM_NOT_FOUND = -99;
    public static final float FLOAT_PARAM_NOT_FOUND = -99.99f;

    private static Logger logger = LoggerFactory.getLogger(RequestUtil.class);

    private RequestUtil() {}

    /**
     * Get attribute or parameter from request. Attribute has priority over parameter when both are present. If no parameter
     * is found, return the default value.
     */
    public static String getParameter(final ServletRequest request, final String param, final String defaultVal) {
        Object o = request.getAttribute(param);
        if (o == null) {
            o = request.getParameter(param);
        }

        if (o == null) {
            o = defaultVal;
        }
        return (String) o;
    }


    /**
     * Get attribute or parameter from request. Attribute has priority over parameter when both are present. If no parameter
     * is found, return null.
     */
    public static String getParameter(final ServletRequest request, final String param) {
        return getParameter(request, param, null);
    }

    /**
     * Get attribute or parameters from request and return an array of Strings. Attribute has priority over parameter when
     * both are present
     */
    public static String[] getParameterValues(final ServletRequest request, final String param) {
        String[] retArray = null;
        Object o = request.getAttribute(param);

        if (o == null) {
            o = request.getParameterValues(param);
        }

        try {
            retArray = (String[]) o;
        } catch (ClassCastException e) {
            retArray = new String[1];
            retArray[0] = (String) o;
        }

        if (retArray != null) {
            for (int i = 0; i < retArray.length; i++) {
                logger.debug("RequestUtil.getParameterValues for " + param + " [" + i + "]: " + retArray[i]);
            }
        } else {
            logger.debug("RequestUtil.getParameterValues for " + param + " is null");
        }

        return retArray;
    }

    /**
     * gets an int parameter.
     *
     * @return the parameter's int value, or -99 if the parameter is null.
     */
    public static int getIntParam(final ServletRequest request, final String param) {
        return getIntParam(request, param, INT_PARAM_NOT_FOUND);
    }

    /**
     * gets an int parameter.
     *
     * @return the parameter's int value, or the default value if the parameter is null.
     */
    public static int getIntParam(final ServletRequest request, final String param, final int defValue) {
        int retval = defValue;
        String s_int = getParameter(request, param);
        if (s_int != null) {
            s_int = s_int.trim();
            if (s_int.length() > 0) {
                try {
                    retval = Integer.parseInt(s_int);
                } catch (NumberFormatException e) {
                    logger.info("RequestUtil.getIntParam. Param " + param + " had unparseable value '" + s_int + "'.");

                }
            }
        }

        logger.debug("RequestUtil.getIntParam for " + param + ": " + retval + ".");

        return retval;
    }

    /**
     * Retrieves boolean as a String ("true") and returns boolean.
     * 
     * @return boolean
     */
    public static boolean getBooleanParam(final ServletRequest request, final String param) {
        return getBooleanParam(request, param, "true", false);
    }

    /**
     * Retrieves boolean if the string value of the parameter equals the trueString argument. If the parameter is not
     * present, false will be returned
     * 
     * @return boolean
     */
    public static boolean getBooleanParam(final ServletRequest request, final String param, final String trueString) {
        return getBooleanParam(request, param, trueString, false);
    }

    /**
     * Retrieves boolean if the string value of the parameter equals the trueString argument.
     * 
     * @return boolean
     */
    public static boolean getBooleanParam(final ServletRequest request, final String param, final String trueString, final boolean defaultVal) {
        final String s = getParameter(request, param);
        if (s != null) {
            return s.equalsIgnoreCase(trueString);
        }

        return defaultVal;
    }

    /**
     * Retrieves a list of Integers from the request
     * 
     * @return Integer[]
     */
    public static Integer[] getIntegers(final ServletRequest request, final String param) {
        return getIntegers(request, param, INT_PARAM_NOT_FOUND);
    }

    /**
     * Retrieves a list of Integers from the request
     * 
     * @return Integer[]
     */
    public static Integer[] getIntegers(final ServletRequest request, final String param, final int defValue) {
        final String[] values = getParameterValues(request, param);

        if (values == null) {
            return new Integer[0];
        }

        final Integer[] intValues = new Integer[values.length];

        for (int i = 0; i < values.length; i++) {
            try {
                String temp = values[i];
                if (temp != null) {
                    temp = temp.trim();
                }
                intValues[i] = Integer.valueOf(temp);
            } catch (NumberFormatException ne) {
                logger.info("RequestUtil.getIntegers. Param " + param + " had unparseable value '" + values[i] + "'.");

                intValues[i] = Integer.valueOf(defValue);
            }
        }

        return intValues;
    }

    /**
     * gets a string parameter
     *
     * @return the parameter's value, or "" if the parameter is null.
     */
    public static String getParamNoNull(final ServletRequest request, final String param) {
        final String temp = getParameter(request, param);
        if (temp != null) {
            return temp;
        }
        return "";
    }

    /**
     * gets a float parameter.
     *
     * @return the parameter's int value, or -99 if the parameter is null.
     */
    public static float getFloatParam(final ServletRequest request, final String param) {
        return getFloatParam(request, param, FLOAT_PARAM_NOT_FOUND);
    }

    /**
     * gets a float parameter.
     *
     * @return the parameter's float value, or the default value if the parameter is null.
     */
    public static float getFloatParam(final ServletRequest request, final String param, final float defValue) {
        float retval = defValue;
        String s_f = getParameter(request, param);
        if (s_f != null) {
            s_f = s_f.trim();
            if (s_f.length() > 0) {
                try {
                    retval = Float.parseFloat(s_f);
                } catch (NumberFormatException e) {
                    logger.info("RequestUtil.getFloatParam. Param " + param + " had unparseable value '" + s_f + "'.");

                }
            }
        }

        logger.debug("RequestUtil.getFloatParam for " + param + ": " + retval + ".");

        return retval;
    }
}
