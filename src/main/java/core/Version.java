package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "3.1.2.1";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}