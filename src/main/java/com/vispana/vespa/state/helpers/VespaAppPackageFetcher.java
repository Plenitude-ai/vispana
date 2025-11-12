package com.vispana.vespa.state.helpers;

import static com.vispana.vespa.state.helpers.Request.requestGetWithDefaultValue;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class VespaAppPackageFetcher {

  public VespaAppPackageFetcher() {}

  /**
   * Recursively downloads the application package from the vespa appUrl and returns a ZIP as a
   * byte[].
   *
   * @param appUrl the root "content" URL (must end with a slash ideally)
   * @return byte[] containing zip archive
   * @throws IOException on IO problems
   * @throws InterruptedException on HTTP client interruption
   */
  public byte[] buildAppPackageBinary(String configHost) throws IOException, InterruptedException {
    String appUrl = ApplicationUrlFetcher.fetch(configHost);
    String contentUrl = appUrl + "/content/";

    // We'll use a queue for BFS traversal of "folders"
    Queue<String> queue = new LinkedList<>();
    queue.add(contentUrl);

    // Collect files as map: zipEntryPath -> byte[]
    // But instead of storing them all before zipping, we'll stream them into
    // ZipOutputStream.
    // For buildAppPackageBinary we need the full byte[] at the end, so we stream
    // into a ByteArrayOutputStream.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {

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
            // It's a file: GET its bytes and add to the zip under zipPath
            byte[] fileContent = requestGetWithDefaultValue(resolvedUrl, byte[].class, new byte[0]);

            // Ensure parent directories exist in ZIP
            createParentDirsInZip(zos, zipPath, addedEntries);

            ZipEntry fileEntry = new ZipEntry(zipPath);
            zos.putNextEntry(fileEntry);
            zos.write(fileContent);
            zos.closeEntry();
            addedEntries.add(zipPath);
          }
        }
      } // end traversal

      zos.finish();
    }

    return baos.toByteArray();
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
