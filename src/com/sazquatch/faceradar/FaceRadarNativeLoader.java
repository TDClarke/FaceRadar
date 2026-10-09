package com.sazquatch.faceradar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import org.opencv.core.Core;
import org.sleuthkit.autopsy.coreutils.Logger;

/** Loads the OpenCV native library once, falling back to extracting it from the module JAR. */
public final class FaceRadarNativeLoader {

    private static final Logger logger_ = Logger.getLogger(FaceRadarNativeLoader.class.getName());
    private static boolean loaded_ = false;

    private FaceRadarNativeLoader() {}

    public static synchronized void load() {
        if (loaded_) {
            return;
        }
        String libName = Core.NATIVE_LIBRARY_NAME; // e.g. opencv_java490
        try {
            System.loadLibrary(libName);
            loaded_ = true;
            logger_.log(Level.INFO, "Loaded OpenCV native library via java.library.path: " + libName);
            return;
        } catch (UnsatisfiedLinkError e) {
            logger_.log(Level.WARNING, "System.loadLibrary(" + libName + ") failed, trying bundled copy: " + e);
        }

        // Fallback: DLL placed in src/com/sazquatch/faceradar/ so it is packaged in the JAR
        String fileName = System.mapLibraryName(libName); // opencv_java490.dll
        try (InputStream in = FaceRadarNativeLoader.class.getResourceAsStream(fileName)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Bundled " + fileName + " not found in module JAR");
            }
            File tmpDir = Files.createTempDirectory("faceradar_native").toFile();
            File tmp = new File(tmpDir, fileName);
            tmp.deleteOnExit();
            tmpDir.deleteOnExit();
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.getAbsolutePath());
            loaded_ = true;
            logger_.log(Level.INFO, "Loaded OpenCV native library from " + tmp);
        } catch (IOException ex) {
            throw new UnsatisfiedLinkError("Could not extract " + fileName + ": " + ex);
        }
    }
}
