package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "3.1.1.0";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}