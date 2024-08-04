package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "2.2.4.0";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}