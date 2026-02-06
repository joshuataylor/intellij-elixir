package org.elixir_lang;

import com.ericsson.otp.erlang.OtpNode;

import java.io.IOException;

/**
 * Created by luke.imhoff on 12/31/14.
 */
public class IntellijElixir {
    private static OtpNode localNode = null;

    private static String getQuoterHost() {
        String host = System.getProperty("quoter.host");
        if (host == null || host.isEmpty()) {
            host = "127.0.0.1";
        }
        return host;
    }

    public static final String REMOTE_NODE = "intellij_elixir@" + getQuoterHost();

    public static OtpNode getLocalNode() throws IOException {
        if (localNode == null) {
            localNode = new OtpNode("intellij-elixir@" + getQuoterHost(), "intellij_elixir");
        }

        return localNode;
    }
}
