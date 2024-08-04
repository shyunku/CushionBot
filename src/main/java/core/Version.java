package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "2.2.6.3";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}