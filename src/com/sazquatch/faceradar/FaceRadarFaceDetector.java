/*
 *  FaceRadar
 *  Copyright (C) 2015 Blaize Strothers
 *
 *  Face detection now uses OpenCV's YuNet (FaceDetectorYN) ONNX model
 *  instead of Haar/LBP cascades. Requires OpenCV 4.5.4 or newer.
 */
package com.sazquatch.faceradar;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;

public class FaceRadarFaceDetector {

    // Model file, placed in src/com/sazquatch/faceradar/ next to this class
    private static final String MODEL_RESOURCE = "face_detection_yunet_2023mar.onnx";

    private static final float SCORE_THRESHOLD = 0.6f;
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int TOP_K = 5000;

    // Larger images are downscaled (keeping aspect ratio) before detection
    private static final int MAX_DIMENSION = 1600;

    private static File modelFile_ = null;

    // FaceDetectorYN is not thread-safe, and Autopsy runs file ingest modules on several threads
    private static final ThreadLocal<FaceDetectorYN> detectors_ = new ThreadLocal<>();

    /** Copies the ONNX model out of the JAR to a temp file (once) because OpenCV needs a real path. */
    private static synchronized File getModelFile() throws IOException {
        if (modelFile_ == null || !modelFile_.exists()) {
            try (InputStream in = FaceRadarFaceDetector.class.getResourceAsStream(MODEL_RESOURCE)) {
                if (in == null) {
                    throw new IOException("Model resource not found: " + MODEL_RESOURCE);
                }
                File tmp = File.createTempFile("faceradar_yunet_", ".onnx");
                tmp.deleteOnExit();
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                modelFile_ = tmp;
            }
        }
        return modelFile_;
    }

    private static FaceDetectorYN getDetector(int width, int height) throws IOException {
        FaceDetectorYN detector = detectors_.get();
        if (detector == null) {
            detector = FaceDetectorYN.create(getModelFile().getPath(), "",
                    new Size(width, height), SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K);
            detectors_.set(detector);
        } else {
            detector.setInputSize(new Size(width, height));
        }
        return detector;
    }

    /** Returns the image as 3-byte BGR, downscaled if very large. */
    private static BufferedImage toBgr(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        if (scale == 1.0 && src.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return src;
        }
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null); // also handles alpha/palette/gray images
        } finally {
            g.dispose();
        }
        return out;
    }

    public boolean detectFaces(BufferedImage bufferedImage) {
        Mat image = null;
        Mat faces = new Mat();
        try {
            BufferedImage bgr = toBgr(bufferedImage);
            byte[] pixels = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();

            image = new Mat(bgr.getHeight(), bgr.getWidth(), CvType.CV_8UC3);
            image.put(0, 0, pixels);

            FaceDetectorYN detector = getDetector(bgr.getWidth(), bgr.getHeight());
            detector.detect(image, faces);

            // One row per detected face (x, y, w, h, 5 landmarks, score)
            return faces.rows() > 0;
        } catch (IOException ex) {
            throw new RuntimeException("Unable to load YuNet model", ex);
        } finally {
            if (image != null) {
                image.release();
            }
            faces.release();
        }
    }
}
