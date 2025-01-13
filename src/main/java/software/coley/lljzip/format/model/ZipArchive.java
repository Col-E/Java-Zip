package software.coley.lljzip.format.model;

import software.coley.lljzip.format.read.ZipReader;
import software.coley.lljzip.format.transform.ZipPartMapper;
import software.coley.lljzip.util.OffsetComparator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Zip file outline.
 *
 * @author Matt Coley
 */
public class ZipArchive implements AutoCloseable, Iterable<ZipPart> {
	private final List<ZipPart> parts = new ArrayList<>();
	private final Closeable closableBackingResource;
	private MemorySegment prefixData;

	/**
	 * New zip archive without any backing resource.
	 */
	public ZipArchive() {
		closableBackingResource = null;
	}

	/**
	 * New zip archive with a backing resource.
	 *
	 * @param closableBackingResource
	 * 		Closable resource backing the zip archive.
	 */
	public ZipArchive(@Nonnull Closeable closableBackingResource) {
		this.closableBackingResource = closableBackingResource;
	}

	/**
	 * If the {@link ZipReader} used to read this archive supports tracking such information this will return
	 * any data not contained in the archive that was found at the front of the input content.
	 * <p/>
	 * Some applications like Jar2Exe will prepend a loader executable in front of the jar, in which case this content
	 * would be that executable. It can be any kind of arbitrary data though.
	 *
	 * @return Data at the front of the archive.
	 */
	@Nullable
	public MemorySegment getPrefixData() {
		return prefixData;
	}

	/**
	 * @param prefixData
	 * 		Data at the front of the archive.
	 */
	public void setPrefixData(@Nullable MemorySegment prefixData) {
		this.prefixData = prefixData;
	}

	/**
	 * @param mapper
	 * 		Part mapper to manipulate contained zip parts.
	 *
	 * @return Copy of archive with mapping applied.
	 */
	@Nonnull
	public ZipArchive withMapping(@Nonnull ZipPartMapper mapper) {
		ZipArchive copy = new ZipArchive();
		for (ZipPart part : parts) {
			ZipPart mappedPart = mapper.map(this, part);
			if (mappedPart != null) copy.addPart(mappedPart);
		}
		return copy;
	}

	/**
	 * @param part
	 * 		Part to add.
	 */
	public void addPart(@Nonnull ZipPart part) {
		parts.add(part);
	}

	/**
	 * @param index
	 * 		Index to add at.
	 * @param part
	 * 		Part to add.
	 */
	public void addPart(int index, ZipPart part) {
		parts.add(index, part);
	}

	/**
	 * @param part
	 * 		Part to remove.
	 *
	 * @return {@code true} when part was removed. {@code false} when it was not in the archive.
	 */
	public boolean removePart(ZipPart part) {
		return parts.remove(part);
	}

	/**
	 * @param index
	 * 		Index to remove part of.
	 *
	 * @return Part removed.
	 */
	@Nullable
	public ZipPart removePart(int index) {
		return parts.remove(index);
	}

	/**
	 * @param comparator
	 * 		Comparator to sort the parts list with.
	 */
	public void sortParts(Comparator<ZipPart> comparator) {
		parts.sort(comparator);
	}

	/**
	 * @return All parts of the zip archive.
	 */
	@Nonnull
	public List<ZipPart> getParts() {
		return Collections.unmodifiableList(parts);
	}

	/**
	 * @param nameFilter
	 * 		Filter to limit entries with by file path name.
	 *
	 * @return Central directory header entries matching the given file path filter.
	 */
	@Nonnull
	public List<CentralDirectoryFileHeader> getNameFilteredCentralDirectories(Predicate<String> nameFilter) {
		return getCentralDirectories().stream()
				.filter(c -> nameFilter.test(c.getFileNameAsString()))
				.collect(Collectors.toList());
	}

	/**
	 * @param nameFilter
	 * 		Filter to limit entries with by file path name.
	 *
	 * @return Local file header entries matching the given file path filter.
	 */
	@Nonnull
	public List<LocalFileHeader> getNameFilteredLocalFiles(Predicate<String> nameFilter) {
		// Use central names when possible, as they are authoritative
		List<CentralDirectoryFileHeader> centralDirectories = getCentralDirectories();
		if (!centralDirectories.isEmpty())
			return centralDirectories.stream()
					.filter(c -> nameFilter.test(c.getFileNameAsString()))
					.map(CentralDirectoryFileHeader::getLinkedFileHeader)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

		// Fallback to using local entries
		return getLocalFiles().stream()
				.filter(c -> nameFilter.test(c.getFileNameAsString()))
				.collect(Collectors.toList());
	}

	/**
	 * Searches for a local file entry for the given name.
	 * The authoritative {@link CentralDirectoryFileHeader#getFileName()} is used,
	 * not the {@link LocalFileHeader#getFileName()}.
	 *
	 * @param name
	 * 		Name to fetch contents of.
	 *
	 * @return Local file header for the path, or {@code null} if no such entry for the name exists.
	 */
	@Nullable
	public LocalFileHeader getLocalFileByName(String name) {
		List<LocalFileHeader> matches = getNameFilteredLocalFiles(name::equals);
		if (matches.isEmpty()) return null;
		return matches.getFirst();
	}

	/**
	 * @return Local file header entries.
	 */
	@Nonnull
	public List<LocalFileHeader> getLocalFiles() {
		return parts.stream()
				.filter(part -> part.type() == PartType.LOCAL_FILE_HEADER)
				.map(part -> (LocalFileHeader) part)
				.collect(Collectors.toList());
	}

	/**
	 * @return Central directory header entries.
	 */
	@Nonnull
	public List<CentralDirectoryFileHeader> getCentralDirectories() {
		return parts.stream()
				.filter(part -> part.type() == PartType.CENTRAL_DIRECTORY_FILE_HEADER)
				.map(part -> (CentralDirectoryFileHeader) part)
				.collect(Collectors.toList());
	}

	/**
	 * @return End of central directory.
	 */
	@Nullable
	public EndOfCentralDirectory getEnd() {
		return parts.stream()
				.filter(part -> part.type() == PartType.END_OF_CENTRAL_DIRECTORY)
				.sorted(new OffsetComparator().reversed()) // Yield the LAST entry
				.limit(1)
				.map(part -> (EndOfCentralDirectory) part)
				.findFirst().orElse(null);
	}

	/**
	 * @return Closable resource backing the zip archive.
	 */
	@Nullable
	protected Closeable getClosableBackingResource() {
		return closableBackingResource;
	}

	@Override
	public void close() throws IOException {
		if (closableBackingResource != null)
			closableBackingResource.close();
	}

	@Override
	public Iterator<ZipPart> iterator() {
		return parts.listIterator();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ZipArchive that = (ZipArchive) o;

		return parts.equals(that.parts);
	}

	@Override
	public int hashCode() {
		return parts.hashCode();
	}
}
