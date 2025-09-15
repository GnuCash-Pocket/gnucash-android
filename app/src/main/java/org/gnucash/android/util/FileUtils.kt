package org.gnucash.android.util

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Misc methods for dealing with files.
 */
object FileUtils {
    @Throws(IOException::class)
    fun zipFiles(files: List<File>, zipFile: File) {
        val outputStream: OutputStream = FileOutputStream(zipFile)
        val zipOutputStream = ZipOutputStream(outputStream)
        val buffer = ByteArray(1024)
        for (file in files) {
            val fileInputStream = FileInputStream(file)
            zipOutputStream.putNextEntry(ZipEntry(file.name))

            var length: Int
            while ((fileInputStream.read(buffer).also { length = it }) > 0) {
                zipOutputStream.write(buffer, 0, length)
            }
            zipOutputStream.closeEntry()
            fileInputStream.close()
        }
        zipOutputStream.close()
    }

    /**
     * Moves a file from `src` to `dst`
     *
     * @param src Absolute path to the source file
     * @param dst Absolute path to the destination file
     * @throws IOException if the file could not be moved.
     */
    @Throws(IOException::class)
    fun moveFile(src: String, dst: String) {
        val srcFile = File(src)
        val dstFile = File(dst)
        moveFile(srcFile, dstFile)
    }

    /**
     * Moves a file from `src` to `dst`
     *
     * @param srcFile the source file
     * @param dstFile the destination file
     * @throws IOException if the file could not be moved.
     */
    @Throws(IOException::class)
    fun moveFile(srcFile: File, dstFile: File) {
        val inChannel = FileInputStream(srcFile).channel
        val outChannel = FileOutputStream(dstFile).channel
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel)
        } finally {
            inChannel.close()
            outChannel.close()
        }
        srcFile.delete()
    }

    /**
     * Move file from a location on disk to an outputstream.
     * The outputstream could be for a URI in the Storage Access Framework
     *
     * @param src          Input file (usually newly exported file)
     * @param outputStream Output stream to write to
     * @throws IOException if error occurred while moving the file
     */
    @Throws(IOException::class)
    fun moveFile(src: File, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        try {
            FileInputStream(src).use { inputStream ->
                while ((inputStream.read(buffer).also { read = it }) != -1) {
                    outputStream.write(buffer, 0, read)
                }
            }
        } finally {
            outputStream.flush()
            outputStream.close()
        }
        Timber.i("Deleting temp export file: %s", src)
        src.delete()
    }
}
