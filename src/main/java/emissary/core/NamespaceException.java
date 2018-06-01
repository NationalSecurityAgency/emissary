/*
 * NameSpaceException.java
 *
 * Created on December 20, 2002, 10:48 AM
 */

package emissary.core;

/**
 * An exception in the namespace mechanism
 *
 * @author ce
 */
public class NamespaceException extends emissary.core.EmissaryException {

    // Serializable
    static final long serialVersionUID = 3860002960394131834L;

    /**
     * Creates a new instance of NameSpaceException
     * 
     * @param s the message to go along with the exception
     */
    public NamespaceException(final String s) {
        super(s);
    }
}
