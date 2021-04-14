package android.util;

public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static int d(String tag, String msg) {
        System.out.println("DEBUG: " + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("INFO: " + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("WARN: " + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("ERROR: " + tag + ": " + msg);
        return 0;
    }

    public static boolean 	isLoggable(String tag, int level) {
        return true;
    }

    public static String getStackTraceString (Throwable tr) {
        return tr.toString();
    }

    public static int println (int priority,
                               String tag,
                               String msg) {
        String prefix;

        switch (priority) {
            case VERBOSE:
                prefix = new String("VERBOSE: ");
                break;
            case DEBUG:
                prefix = new String("DEBUG: ");
                break;
            case INFO:
                prefix = new String("INFO: ");
                break;
            case WARN:
                prefix = new String("WARN: ");
                break;
            case ERROR:
                prefix = new String("ERROR: ");
                break;
            case ASSERT:
                prefix = new String("ASSERT: ");
                break;
            default:
                prefix = new String("MSG: ");
                break;
        }

        String totalmsg =  prefix + tag + ": " + msg;

        System.out.println(totalmsg);

        return totalmsg.length() + 1;

    }
    // add other methods if required...
}
