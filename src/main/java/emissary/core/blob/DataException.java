package emissary.core.blob;

import emissary.core.IBaseDataObject;

/**
 * Runtime exception that can be thrown by a data container on error without a breaking change to the
 * {@link IBaseDataObject} API.
 *
 */
public class DataException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = -7025870592738740195L;

    public DataException() {
        super();
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataException(String message) {
        super(message);
    }

    public DataException(Throwable cause) {
        super(cause);
    }

}
