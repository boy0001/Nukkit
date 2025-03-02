package cn.nukkit.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;


public abstract class Zlib {

    public static byte[] deflate(byte[] data) throws Exception {
        return deflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    public static byte[] deflate(Deflater deflater, byte[] data, int level, int strategy) throws Exception {
        deflater.reset();
        deflater.setLevel(level);
        deflater.setStrategy(strategy);
        deflater.setInput(data);
        deflater.finish();
        BinaryStream bos = new BinaryStream(Math.max(39, data.length >> 6));
        byte[] buf = new byte[Math.max(1024, data.length >> 6)];
        try {
            while (!deflater.finished()) {
                int i = deflater.deflate(buf);
                bos.write(buf, 0, i);
            }
        } finally {
            deflater.end();
        }
        byte[] result = bos.toByteArray();
        return bos.toByteArray();
    }

    public static byte[] deflate(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level);
        return deflate(deflater, data, level, Deflater.DEFAULT_STRATEGY);
    }



    public static byte[] inflate(InputStream stream) throws IOException {
        InflaterInputStream inputStream = new InflaterInputStream(stream);
        BinaryStream outputStream = new BinaryStream(39);
        byte[] buffer = new byte[1024];
        int length;

        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }

        buffer = outputStream.toByteArray();
        outputStream.flush();
        outputStream.close();
        inputStream.close();

        return buffer;
    }

    public static byte[] inflate(byte[] data) throws IOException {
        return inflate(new ByteArrayInputStream(data));
    }

    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        return inflate(new ByteArrayInputStream(data, 0, maxSize));
    }

}

