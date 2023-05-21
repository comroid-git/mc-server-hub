package org.comroid.ioredir;

import org.comroid.api.os.OS;

public class Program {
    public static void main(String[] args) {
        if (!OS.isUnix)
            throw new RuntimeException("Only Unix is supported");
    }
}
