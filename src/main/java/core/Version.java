package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "3.2.2.7";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}