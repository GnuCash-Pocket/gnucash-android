package org.gnucash.android.util;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/**
 * Misc methods for dealing with files.
 */
public final class FileUtils {

    public static void zipFiles(List<File> files, File zipFile) throws IOException {
        OutputStream outputStream = new FileOutputStream(zipFile);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        byte[] buffer = new byte[1024];
        for (File file : files) {
            FileInputStream fileInputStream = new FileInputStream(file);
            zipOutputStream.putNextEntry(new ZipEntry(file.getName()));

            int length;
            while ((length = fileInputStream.read(buffer)) > 0) {
                zipOutputStream.write(buffer, 0, length);
            }
            zipOutputStream.closeEntry();
            fileInputStream.close();
        }
        zipOutputStream.close();
    }

    /**
     * Moves a file from <code>src</code> to <code>dst</code>
     *
     * @param src Absolute path to the source file
     * @param dst Absolute path to the destination file
     * @throws IOException if the file could not be moved.
     */
    public static void moveFile(String src, String dst) throws IOException {
        File srcFile = new File(src);
        File dstFile = new File(dst);
        moveFile(srcFile, dstFile);
    }

    /**
     * Moves a file from <code>src</code> to <code>dst</code>
     *
     * @param srcFile the source file
     * @param dstFile the destination file
     * @throws IOException if the file could not be moved.
     */
    public static void moveFile(File srcFile, File dstFile) throws IOException {
        FileChannel inChannel = new FileInputStream(srcFile).getChannel();
        FileChannel outChannel = new FileOutputStream(dstFile).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            inChannel.close();
            outChannel.close();
        }
        srcFile.delete();
    }

    /**
     * Move file from a location on disk to an outputstream.
     * The outputstream could be for a URI in the Storage Access Framework
     *
     * @param src          Input file (usually newly exported file)
     * @param outputStream Output stream to write to
     * @throws IOException if error occurred while moving the file
     */
    public static void moveFile(@NonNull File src, @NonNull OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        try (FileInputStream inputStream = new FileInputStream(src)) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            outputStream.flush();
            outputStream.close();
        }
        Timber.i("Deleting temp export file: %s", src);
        src.delete();
    }
}
