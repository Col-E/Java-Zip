package software.coley.llzip.format.write;

import software.coley.llzip.format.compression.ZipCompressions;
import software.coley.llzip.format.model.CentralDirectoryFileHeader;
import software.coley.llzip.format.model.LocalFileHeader;
import software.coley.llzip.format.model.ZipArchive;
import software.coley.llzip.util.ByteDataUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Uses the Java {@link ZipOutputStream} to recompute the zip file format.
 * The only used data in this case is {@link LocalFileHeader#getFileData()} and the file name
 * which can be either {@link CentralDirectoryFileHeader#getFileName()} or {@link LocalFileHeader#getFileName()}.
 *
 * @author Matt Coley
 */
public class JavaZipWriterStrategy implements ZipWriterStrategy {
	@Override
	public void write(ZipArchive archive, OutputStream os) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(os)) {
			for (LocalFileHeader fileHeader : archive.getLocalFiles()) {
				CentralDirectoryFileHeader linked = fileHeader.getLinkedDirectoryFileHeader();
				String name = linked == null ? fileHeader.getFileNameAsString() : linked.getFileNameAsString();
				if (fileHeader.getFileData().length() > 0L) {
					// File, may need to patch things like trailing '/' for '.class' files.
					if (name.endsWith(".class/"))
						name = name.substring(0, name.length() - 1);
					zos.putNextEntry(new ZipEntry(name));
					zos.write(ByteDataUtil.toByteArray(ZipCompressions.decompress(fileHeader)));
					zos.closeEntry();
				} else {
					// Directory, don't need to do any extra work
					zos.putNextEntry(new ZipEntry(name));
					zos.closeEntry();
				}
			}
		}
	}
}
