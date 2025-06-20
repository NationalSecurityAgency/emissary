package emissary.test.core.junit5;

import emissary.core.BaseDataObject;

class ClearDataBaseDataObject extends BaseDataObject {
    private static final long serialVersionUID = -8728006876784881020L;

    protected void clearData() {
        theData = null;
        seekableByteChannelFactory = null;
    }
}
