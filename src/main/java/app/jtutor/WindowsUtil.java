package app.jtutor;

public class WindowsUtil {
    public static String windowsCompatiblePath(String path) {
        path = path.replace("\\", "/");
        return path;
    }
}
