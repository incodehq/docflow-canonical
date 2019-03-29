package org.estatio.ecp.docflow.canonical;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import org.apache.isis.applib.value.Blob;
import org.apache.isis.applib.value.Clob;

import lombok.Getter;
import lombok.SneakyThrows;

public class DocFlowZipData {

    private DocFlowZipData(){}

    @Getter
    long sdId;

    @Getter
    Clob xmlFileMetadati;

    @Getter
    Clob xmlFatturaElettronica;

    @Getter
    Blob pdfFatturaElettronica;

    @Getter
    Blob p7mFatturaElettronica;

    @Getter
    private Blob pdfSupplier;

    static Pattern ENTRY_NAME_PATTERN = Pattern.compile(
            "(?<prefix>[\\d]{7})"
                    + "(?<pdffat>\\.PDF)?"
                    + "(?<p7m>0\\.SRC\\.P7M)?"
                    + "(?<xmlfat>1\\.XML)?"
                    + "(?<xmlmet>2\\.XML)?"
                    + "(?<pdfsup2>2\\.PDF)?"   // if metadata is missing, then supplier PDF moves up one.
                    + "(?<pdfsup3>3\\.PDF)?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * The same logic, more or less, can be found in EstatioClient within estatio-camel's dflw2est module.
     *
     * I thought about extracting out a common library (something like docflow-canonical ?) but decided it
     * wasn't really worth the overhead.
     *
     * @param sdId
     * @param zipBytes
     * @return
     */
    @SneakyThrows
    public static DocFlowZipData from(final long sdId, final byte[] zipBytes) {

        final DocFlowZipData zipData = new DocFlowZipData();
        zipData.sdId = sdId;
        final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            final String zipEntryName = entry.getName();

            int size;
            byte[] buffer = new byte[2048];
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((size = zis.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, size);
            }
            baos.flush();
            baos.close();
            final byte[] bytes = baos.toByteArray();

            final Matcher matcher = ENTRY_NAME_PATTERN.matcher(zipEntryName);
            if(!matcher.matches()) {
                continue;
                }
            if(!Strings.isNullOrEmpty(matcher.group("pdffat"))) {
                zipData.pdfFatturaElettronica =
                        toBlob(zipEntryName, "application/pdf", bytes);
            } else
            if(!Strings.isNullOrEmpty(matcher.group("p7m"))) {
                zipData.p7mFatturaElettronica =
                        toBlob(zipEntryName, "application/pkcs7-signature", bytes);
            } else
            if(!Strings.isNullOrEmpty(matcher.group("xmlfat"))) {
                zipData.xmlFatturaElettronica =
                        toClob(zipEntryName, "application/xml", bytes);
            } else
            if(!Strings.isNullOrEmpty(matcher.group("xmlmet"))) {
                zipData.xmlFileMetadati =
                        toClob(zipEntryName, "application/xml", bytes);
            } else
            if(!Strings.isNullOrEmpty(matcher.group("pdfsup2"))) {
                zipData.pdfSupplier =
                        toBlob(zipEntryName, "application/pdf", bytes);
            }
            if(!Strings.isNullOrEmpty(matcher.group("pdfsup3"))) {
                if(zipData.pdfSupplier != null) {
                    // if we've already encountered a PDF for the supplier, then ignore any further ones.
                    // (we didn't encounter any so far in the real-world samples, so this is only theoretical).
                } else {
                    zipData.pdfSupplier =
                            toBlob(zipEntryName, "application/pdf", bytes);
                }
            }
        }
        zis.close();

        return zipData;
    }

    private static Blob toBlob(
            final String fileName, final String mimeType, final byte[] bytes) {
        return new Blob(fileName, mimeType, bytes);
    }

    private static Clob toClob(
            final String fileName, final String mimeType, final byte[] utf8Bytes) {
        final String chars = new String(utf8Bytes, Charsets.UTF_8);
        return new Clob(fileName, mimeType, chars);
    }

}
