package com.minecraftcivilizations.specialization.StaffTools;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;

public class HotswapInspector {
    public static void inspect(Class<?> cls, Path candidateClassFile) {
        try {
            System.out.println("=== INSPECT " + cls.getName() + " ===");

            ClassLoader cl = cls.getClassLoader();
            System.out.println("classloader: " + cl);

            URL codeSource = (cls.getProtectionDomain() != null && cls.getProtectionDomain().getCodeSource() != null)
                    ? cls.getProtectionDomain().getCodeSource().getLocation()
                    : null;
            System.out.println("codeSource location: " + codeSource);

            // Read bytes the JVM is using via classloader resource
            String resource = cls.getName().replace('.', '/') + ".class";
            try (InputStream in = cl.getResourceAsStream(resource)) {
                if (in == null) {
                    System.out.println("JVM class resource not found via classloader: " + resource);
                } else {
                    byte[] loadedBytes = in.readAllBytes();
                    System.out.println("JVM class bytes SHA256: " + sha256(loadedBytes));
                }
            }

            // Reflection: list declared methods (incl. synthetic/bridge)
            System.out.println("--- runtime declared methods ---");
            for (Method m : cls.getDeclaredMethods()) {
                String mods = Modifier.toString(m.getModifiers());
                System.out.println(String.format("%s %s %s(%s) [synthetic:%s bridge:%s]",
                        mods,
                        m.getReturnType().getTypeName(),
                        m.getName(),
                        String.join(", ", toNames(m.getParameterTypes())),
                        m.isSynthetic(),
                        m.isBridge()));
            }

            // Candidate disk file SHA256
            if (candidateClassFile != null && Files.exists(candidateClassFile)) {
                byte[] fileBytes = Files.readAllBytes(candidateClassFile);
                System.out.println("disk file: " + candidateClassFile + " SHA256: " + sha256(fileBytes));

                // Optional: print size
                System.out.println("disk file bytes: " + fileBytes.length);
            } else {
                System.out.println("candidateClassFile not provided or missing: " + candidateClassFile);
            }
            System.out.println("=== END ===");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String[] toNames(Class<?>[] types) {
        String[] s = new String[types.length];
        for (int i = 0; i < types.length; i++) s[i] = types[i].getTypeName();
        return s;
    }

    private static String sha256(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(md.digest(b));
    }
}
