package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "3.2.0.12";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}