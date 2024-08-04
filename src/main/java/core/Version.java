package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "2.2.6.1";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}