package software.coley.lljzip.format.compression;

import software.coley.lljzip.format.model.LocalFileHeader;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/**
 * Outlines decompression of {@link LocalFileHeader#getFileData()}.
 *
 * @author Matt Coley
 */
public interface Decompressor {
	/**
	 * @param header
	 * 		Header containing the bytes, for any context needed.
	 * @param bytes
	 * 		Bytes to decompress.
	 *
	 * @return Decompressed bytes.
	 *
	 * @throws IOException
	 * 		Decompression failure.
	 */
	MemorySegment decompress(LocalFileHeader header, MemorySegment bytes) throws IOException;
}
