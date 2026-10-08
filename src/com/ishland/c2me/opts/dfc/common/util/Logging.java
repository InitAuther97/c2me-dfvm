package com.ishland.c2me.opts.dfc.common.util;

import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Logging {

    private static final Logger LOG = LogManager.getLogManager().getLogger("C2ME-DFVM");

    public static Logger logger() {
        return LOG;
    }
}
