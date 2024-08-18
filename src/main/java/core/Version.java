package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "3.1.3.0";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}