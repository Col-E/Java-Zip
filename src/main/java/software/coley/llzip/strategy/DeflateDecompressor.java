package software.coley.llzip.strategy;

import software.coley.llzip.ZipCompressions;
import software.coley.llzip.part.LocalFileHeader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Decompressor implementation for {@link ZipCompressions#DEFLATED}
 *
 * @author Matt Coley
 */
public class DeflateDecompressor implements Decompressor {
	@Override
	public byte[] decompress(LocalFileHeader header, byte[] bytes) throws IOException {
		if (header.getCompressionMethod() != ZipCompressions.DEFLATED)
			throw new IOException("LocalFileHeader contents not using 'Deflated'!");
		Inflater inflater = new Inflater(true);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes), inflater);
		byte[] buffer = new byte[header.getUncompressedSize()];
		int read = in.read(buffer);
		out.write(buffer, 0, read);
		return out.toByteArray();
	}
}