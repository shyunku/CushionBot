package core;

import Utilities.TokenManager;

public class Version {
    public static String CURRENT = "2.0.2";
    public static boolean PRODUCTION_MODE = new TokenManager().isProduction();
}
