package com.vispana.vespa.state.helpers;

import static com.vispana.vespa.state.helpers.Request.requestGetWithDefaultValue;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class VespaAppPackageFetcher {

  private static final Logger logger = LoggerFactory.getLogger(VespaAppPackageFetcher.class);

  // Buffer size for streaming
  // 8KB = good balance between memory usage and speed
  private static final int BUFFER_SIZE = 8192;

  public VespaAppPackageFetcher() {}

  /**
   * Streams the application package as ZIP directly to the output stream. This avoids loading the
   * entire package into memory - suitable for large packages.
   *
   * @param configHost the Vespa config host
   * @param outputStream the stream to write the ZIP to
   * @throws IOException on IO problems
   * @throws InterruptedException on HTTP client interruption
   */
  public void streamAppPackageAsZip(String configHost, OutputStream outputStream)
      throws IOException, InterruptedException {
    String appUrl = ApplicationUrlFetcher.fetch(configHost);
    String contentUrl = appUrl + "/content/";

    // We'll use a queue for BFS traversal of "folders"
    Queue<String> queue = new LinkedList<>();
    queue.add(contentUrl);

    // Stream directly to output - never hold entire ZIP in memory
    try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
      // Keep a set of zip entries already added to avoid duplicates (especially
      // directories)
      Set<String> addedEntries = new HashSet<>();

      while (!queue.isEmpty()) {
        String currentUrl = queue.poll();

        // GET the current URL. Expect a JSON array of strings (either files or
        // subfolders).
        List<String> entries = requestGetWithDefaultValue(currentUrl, List.class, List.of());

        for (String entryUrlOrPath : entries) {
          // Vespa may return absolute URLs or relative paths. Resolve to an absolute URL.
          String resolvedUrl = resolveUrl(currentUrl, entryUrlOrPath);

          boolean isDirectory = entryUrlOrPath.endsWith("/");

          // Determine zip path: remove contentUrl prefix from resolvedUrl
          String zipPath = toZipPath(contentUrl, resolvedUrl, isDirectory);

          // Ensure directories are explicitly present in the ZIP (use trailing slash)
          if (isDirectory) {
            if (!addedEntries.contains(zipPath)) {
              ZipEntry dirEntry = new ZipEntry(zipPath);
              zos.putNextEntry(dirEntry);
              zos.closeEntry();
              addedEntries.add(zipPath);
            }
            // enqueue directory for further listing
            queue.add(resolvedUrl);
          } else {
            logger.debug("Streaming file: {}", zipPath);

            // Ensure parent directories exist in ZIP
            createParentDirsInZip(zos, zipPath, addedEntries);

            // Create ZIP entry for this file
            ZipEntry fileEntry = new ZipEntry(zipPath);
            zos.putNextEntry(fileEntry);

            // Stream the file in chunks
            // Using the generic requestGetWithDefaultValue with InputStream.class
            try (InputStream fileStream =
                requestGetWithDefaultValue(
                        resolvedUrl, Resource.class, new ByteArrayResource(new byte[0]))
                    .getInputStream()) {
              byte[] buffer = new byte[BUFFER_SIZE];
              int bytesRead;

              // Keep reading chunks from remote into buffer until file is done
              while ((bytesRead = fileStream.read(buffer)) != -1) {
                // Write this chunk to ZIP (which streams to browser)
                zos.write(buffer, 0, bytesRead);
              }

              logger.debug("Finished streaming: {}", zipPath);
            } catch (Exception e) {
              logger.warn("Failed to stream file {}: {}", zipPath, e.getMessage());
              // Continue with other files even if one fails
            }

            zos.closeEntry();
            addedEntries.add(zipPath);
          }
        }
      } // end traversal

      zos.finish();
    }
  }

  // Helpers
  private String resolveUrl(String currentUrl, String entry) {
    // If entry looks like a full URL, return it
    if (entry.startsWith("http://") || entry.startsWith("https://")) {
      return entry;
    }
    // If entry is absolute path that starts with '/', try to construct using
    // currentUrl's host
    try {
      if (entry.startsWith("/")) {
        URI baseUri = URI.create(currentUrl);
        URI resolved = baseUri.resolve(entry);
        return resolved.toString();
      }
      // Otherwise assume it's relative to currentUrl (which should be a folder URL)
      URI baseUri = URI.create(currentUrl);
      URI resolved = baseUri.resolve(entry);
      return resolved.toString();
    } catch (Exception e) {
      // fallback: concatenate
      if (currentUrl.endsWith("/")) return currentUrl + entry;
      else return currentUrl + "/" + entry;
    }
  }

  private String toZipPath(String contentUrl, String resolvedUrl, boolean isDirectory) {
    String zipPath;
    // If resolvedUrl starts with contentUrl, remove contentUrl
    if (resolvedUrl.startsWith(contentUrl)) {
      zipPath = resolvedUrl.substring(contentUrl.length());
    } else {
      // Otherwise take last path segments (fallback). Use entire path after host.
      try {
        URI uri = URI.create(resolvedUrl);
        String path = uri.getPath();
        // strip leading slash
        if (path.startsWith("/")) path = path.substring(1);
        zipPath = path;
      } catch (Exception e) {
        // as last resort use full URL but sanitize
        zipPath = resolvedUrl.replaceAll("[:/?#]+", "_");
      }
    }
    // Ensure directories end with '/'
    if (isDirectory && !zipPath.endsWith("/")) zipPath += "/";
    return zipPath;
  }

  private void createParentDirsInZip(ZipOutputStream zos, String zipPath, Set<String> addedEntries)
      throws IOException {
    int lastSlash = zipPath.lastIndexOf('/');
    if (lastSlash <= 0) return;

    for (int i = 0; i <= lastSlash; i++) {
      char c = (i < zipPath.length()) ? zipPath.charAt(i) : '/';
      if (c == '/') {
        String dir = zipPath.substring(0, i + 1);
        if (!addedEntries.contains(dir)) {
          ZipEntry dirEntry = new ZipEntry(dir);
          zos.putNextEntry(dirEntry);
          zos.closeEntry();
          addedEntries.add(dir);
        }
      }
    }
  }
}
