package com.memory.analysis;

import com.squareup.haha.perflib.*;
import com.squareup.haha.perflib.io.HprofBuffer;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class memory {

    public static void main(String[] args) {
        final File hprofFile = new File("/Users/weiersyuan/Desktop/test.hprof");
        try {
            final HprofBuffer buffer = new MemoryMappedFileBuffer(hprofFile);
            final HprofParser parser = new HprofParser(buffer);
            final Snapshot snapshot = parser.parse();
            snapshot.computeDominators();
            snapshot.dumpInstanceCounts();

            final ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");

            final int bitmapCount = bitmapClass.getInstanceCount();
            System.out.println("Found bitmap instances: " + bitmapCount);

            final List<Instance> bitmapInstances =  bitmapClass.getInstancesList();
            int n = 0;
            for (Instance bitmapInstance : bitmapInstances) {
                System.out.print("bitmap size is ----> + " + bitmapInstance.getTotalRetainedSize()/1024.0/1024.0 + "M");
                try {
                    if (bitmapInstance instanceof ClassInstance) {
                        int width = 0;
                        int height = 0;
                        byte[] data = null;

                        final ClassInstance bitmapObj = (ClassInstance) bitmapInstance;
                        final List<ClassInstance.FieldValue> values = bitmapObj.getValues();

                        for (ClassInstance.FieldValue fieldValue : values) {
                            if ("mWidth".equals(fieldValue.getField().getName())) {
                                width = (Integer) fieldValue.getValue();
                            } else if ("mHeight".equals(fieldValue.getField().getName())) {
                                height = (Integer) fieldValue.getValue();
                            } else if ("mBuffer".equals(fieldValue.getField().getName())) {
                                ArrayInstance arrayInstance = (ArrayInstance) fieldValue.getValue();
                                Object[] boxedBytes = arrayInstance.getValues();
                                data = new byte[boxedBytes.length];
                                for (int i = 0; i < data.length; i++) {
                                    data[i] = (Byte) boxedBytes[i];
                                }
                            }
                        }
                        System.out.println("Bitmap #" + n + ": " + width + "x" + height);

                        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                        for (int row = 0; row < height; row++) {
                            for (int col = 0; col < width; col++) {
                                int offset = 4 * (row * width + col);

                                int byte3 = 0xff & data[offset++];
                                int byte2 = 0xff & data[offset++];
                                int byte1 = 0xff & data[offset++];
                                int byte0 = 0xff & data[offset++];

                                int alpha = byte0;
                                int red = byte1;
                                int green = byte2;
                                int blue = byte3;

                                int pixel = (alpha << 24) | (blue << 16) | (green << 8) | red;

                                image.setRGB(col, row, pixel);
                            }
                        }

                        final OutputStream inb = new FileOutputStream("bitmap-0x" + Integer.toHexString((int) bitmapObj.getId()) + ".png");
                        final ImageWriter wrt = ImageIO.getImageWritersByFormatName("png").next();
                        final ImageOutputStream imageOutput = ImageIO.createImageOutputStream(inb);
                        wrt.setOutput(imageOutput);
                        wrt.write(image);
                        inb.close();

                        n++;
                    }
                } catch(Exception e) {
                    continue;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
