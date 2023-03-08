package emissary.server.mvc.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletRequest;

/**
 * Utilities for dealing with request parameters
 */
public class RequestUtil {
    public static final int INT_PARAM_NOT_FOUND = -99;
    public static final float FLOAT_PARAM_NOT_FOUND = -99.99f;

    private static final Logger logger = LoggerFactory.getLogger(RequestUtil.class);

    private RequestUtil() {}

    /**
     * Get attribute or parameter from request. Attribute has priority over parameter when both are present. If no parameter
     * is found, return the default value.
     */
    public static String getParameter(final ServletRequest request, final String param, final String defaultVal) {
        Object o = request.getAttribute(param);
        if (o == null) {
            o = sanitizeParameter(request.getParameter(param));
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
        String[] retArray;
        Object o = request.getAttribute(param);

        if (o == null) {
            o = sanitizeParameters(request.getParameterValues(param));
        }

        try {
            retArray = (String[]) o;
        } catch (ClassCastException e) {
            retArray = new String[1];
            retArray[0] = (String) o;
        }

        if (retArray != null) {
            for (int i = 0; i < retArray.length; i++) {
                logger.debug("RequestUtil.getParameterValues for {} [{}]: {}", param, i, retArray[i]);
            }
        } else {
            logger.debug("RequestUtil.getParameterValues for {} is null", param);
        }

        return retArray;
    }

    /**
     * Get attribute or parameters from request and return a list of Strings. Attribute has priority over parameter when
     * both are present
     */
    public static List<String> getParameterValuesStringList(final ServletRequest request, final String param) {
        List<String> retList = new ArrayList<>();
        Object o = request.getAttribute(param);

        if (o == null) {
            try {
                o = sanitizeParametersStringList(Arrays.asList(request.getParameterValues(param)));
            } catch (NullPointerException e) {
                logger.debug("RequestUtil.getParameterValues for {} is null", param);
                return retList;
            }
        }

        try {
            retList = (List<String>) o;
        } catch (ClassCastException e) {
            retList = new ArrayList<>(1);
            retList.add(0, (String) o);
        }

        if (retList != null) {
            for (int i = 0; i < retList.size(); i++) {
                logger.debug("RequestUtil.getParameterValues for {} [{}]: {}", param, i, retList.get(i));
            }
        } else {
            logger.debug("RequestUtil.getParameterValues for {} is null", param);
        }

        return retList;
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
        String sInt = getParameter(request, param);
        if (sInt != null) {
            sInt = sInt.trim();
            if (sInt.length() > 0) {
                try {
                    retval = Integer.parseInt(sInt);
                } catch (NumberFormatException e) {
                    logger.info("RequestUtil.getIntParam. Param {} had unparseable value '{}'.", param, sInt);

                }
            }
        }

        logger.debug("RequestUtil.getIntParam for {}: {}.", param, retval);

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
                logger.info("RequestUtil.getIntegers. Param {} had unparseable value '{}'.", param, values[i]);

                intValues[i] = defValue;
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
        String sFloat = getParameter(request, param);
        if (sFloat != null) {
            sFloat = sFloat.trim();
            if (sFloat.length() > 0) {
                try {
                    retval = Float.parseFloat(sFloat);
                } catch (NumberFormatException e) {
                    logger.info("RequestUtil.getFloatParam. Param {} had unparseable value '{}'.", param, sFloat);

                }
            }
        }

        logger.debug("RequestUtil.getFloatParam for {}: {}.", param, retval);

        return retval;
    }

    /**
     * Sanitize request parameter to remove CR/LF values
     *
     * @param parameter the String to sanitize
     * @return a new String object with any CR/LF characters removed or null when the provided argument is null
     */
    public static String sanitizeParameter(String parameter) {
        return (null == parameter ? null : parameter.replaceAll("[\n\r]", "_"));
    }

    /**
     * Sanitize request parameters to remove CR/LF values
     *
     * @param parameters the String[] to sanitize
     * @return a new String[] object with any CR/LF characters removed
     */
    public static String[] sanitizeParameters(String[] parameters) {
        List<String> sanitizedParameters = new ArrayList<>();
        if (null != parameters) {
            for (String parameter : parameters) {
                sanitizedParameters.add(sanitizeParameter(parameter));
            }
        }
        return sanitizedParameters.toArray(new String[0]);
    }

    /**
     * Sanitize request parameters to remove CR/LF values
     *
     * @param parameters the List String to sanitize
     * @return a new List String object with any CR/LF characters removed
     */
    public static List<String> sanitizeParametersStringList(List<String> parameters) {
        List<String> sanitizedParameters = new ArrayList<>();
        if (null != parameters) {
            for (String parameter : parameters) {
                sanitizedParameters.add(sanitizeParameter(parameter));
            }
        }
        return sanitizedParameters;
    }
}
