package emissary.util;

import java.security.Permission;

public class UnitTestSecurityManager extends SecurityManager {
    public static final String SYSTEM_EXIT = "System.exit";

    @Override
    public void checkPermission(Permission perm) {}

    @Override
    public void checkPermission(Permission perm, Object context) {}

    @Override
    public void checkExit(int status) {
        super.checkExit(status);
        throw new SecurityException(SYSTEM_EXIT);
    }
}
