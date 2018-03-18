package main.java.util;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class ZipStream {
    public static BufferedReader getCompressedBufferedReaderForInputStream(InputStream inputStream) throws FileNotFoundException, CompressorException {
        BufferedReader br2 = new BufferedReader(new InputStreamReader(getInputStreamForCompressedInputStream(inputStream)));
        return br2;
    }

    public static InputStream getInputStreamForCompressedInputStream(InputStream in) throws FileNotFoundException, CompressorException {
        BufferedInputStream bis = new BufferedInputStream(in);
        CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
        return input;
    }

    public static InputStream getInputStreamForCompressedFile(String fileIn) throws FileNotFoundException, CompressorException {
        FileInputStream fin = new FileInputStream(fileIn);
        BufferedInputStream bis = new BufferedInputStream(fin);
        CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
        return input;
    }

    public static void downloadFileFromUrlToFile(URL url, File file) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fos = new FileOutputStream(file);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }


}
