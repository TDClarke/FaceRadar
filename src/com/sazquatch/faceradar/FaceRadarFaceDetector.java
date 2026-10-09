/*
 *  FaceRadar
 *  Copyright (C) 2015 Blaize Strothers
<<<<<<< HEAD
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
=======
 *  
 *  Derived from OpenCV tutorial
 */
package com.sazquatch.faceradar;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

public class FaceRadarFaceDetector {

    public boolean detectFaces(BufferedImage bufferedImage) {
        boolean hasFace = false;
        //ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);

        // Create a face detector from the cascade file in the resources directory
        // By copying the face detection xml resource to a temporary local file
        // Because we can't load resources directly from the JAR
        File tempCascadefile = null;
        String resource = "haarcascade_frontalface_alt.xml";
        //"haarcascade_frontalface_alt.xml"
        //"lbpcascade_frontalface.xml"
        URL resourceURL = getClass().getResource("haarcascade_frontalface_alt.xml");
        if (resourceURL.toString().startsWith("jar:")) {
            try {
                InputStream input = getClass().getResourceAsStream(resource);
                tempCascadefile = File.createTempFile(new Date().getTime() + "", ".xml");
                OutputStream out = new FileOutputStream(tempCascadefile);
                int read;
                byte[] bytes = new byte[1024];

                while ((read = input.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
                }
                out.flush();
                out.close();
                input.close();
                tempCascadefile.deleteOnExit();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        // Use the temporary local file to create the face detection CascadeClassifier
        CascadeClassifier faceDetector = new CascadeClassifier(tempCascadefile.getPath());

        // Convert the Java BufferedImage to OpenCV Mat
        byte[] bufferedImagePixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

        Mat bufferedImageAsMat = new Mat(bufferedImage.getHeight(), bufferedImage.getWidth(), CvType.CV_8UC3);
        //Mat bufferedImageAsMat = new Mat(480, 640, CvType.CV_8UC3);
        bufferedImageAsMat.put(0, 0, bufferedImagePixels);

        // Detect faces in the image
        MatOfRect faceDetections = new MatOfRect();
        faceDetector.detectMultiScale(bufferedImageAsMat, faceDetections);
        //assert !faceDetections.empty();
        //System.out.println(String.format("Detected %s faces", faceDetections.toArray().length));
        // Draw a bounding box around each face
        //for (Rect rect : faceDetections.toArray()) {
        //    Imgproc.rectangle(img, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0));
        //}
        Rect[] faceDetectionsArray = faceDetections.toArray();
        if (faceDetectionsArray.length == 0) {
            hasFace = false;
        } else {
            hasFace = true;
        }

        return hasFace;
    }

>>>>>>> origin/master
}
