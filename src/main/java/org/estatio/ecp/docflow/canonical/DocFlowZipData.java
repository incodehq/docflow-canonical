package org.estatio.ecp.docflow.canonical;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Charsets;

import org.apache.isis.applib.value.Blob;
import org.apache.isis.applib.value.Clob;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

class DocFlowZipData {

    @Getter @Setter
    long sdId;

    @Getter @Setter
    Clob xmlFileMetadati;

    @Getter @Setter
    Clob xmlFatturaElettronica;

    @Getter @Setter
    Blob pdfFatturaElettronica;

    @Getter @Setter
    Blob p7mFatturaElettronica;

    @Getter @Setter
    Blob pdfSupplier;

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
        zipData.setSdId(sdId);
        final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));

        ZipEntry entry;
        int i = 0;
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

            switch (i) {
            case 0:
                zipData.setPdfFatturaElettronica(toBlob(zipEntryName, "application/pdf", bytes));
                break;
            case 1:
                if(zipEntryName.toLowerCase().endsWith(".src.p7m")) {
                    zipData.setP7mFatturaElettronica(toBlob(zipEntryName, "application/pkcs7-signature", bytes));
                } else {
                    // otherwise just ignore
                }
                break;
            case 2:
                zipData.setXmlFatturaElettronica(toClob(zipEntryName, "application/xml", bytes));
                break;
            case 3:
                zipData.setXmlFileMetadati(toClob(zipEntryName, "application/xml", bytes));
                break;
            case 4:
                zipData.setPdfSupplier(toBlob(zipEntryName, "application/pdf", bytes));
                break;
            }

            i++;
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
