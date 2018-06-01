package emissary.util.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SocketUtils {

    public static final String RCS_ID = "$Id$";

    public static void sendString(String str, DataOutputStream os) throws IOException {
        os.writeInt(str.length());
        os.write(str.getBytes(), 0, str.length());
    }

    public static String readString(DataInputStream is) throws IOException {
        int contentSize = is.readInt();
        byte[] theContent = new byte[contentSize];
        is.readFully(theContent);
        String contentString = new String(theContent);
        return contentString;
    }

    public static void sendByteArray(byte[] bb, DataOutputStream os) throws IOException {
        os.writeInt(bb.length);
        os.write(bb, 0, bb.length);
    }

    public static byte[] readByteArray(DataInputStream theStream) throws IOException {
        int contentSize = theStream.readInt();
        byte[] theContent = new byte[contentSize];
        theStream.readFully(theContent);
        return theContent;
    }

    /** This class is not meant to be instantiated. */
    private SocketUtils() {}
}
