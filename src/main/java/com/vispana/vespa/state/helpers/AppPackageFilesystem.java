package com.vispana.vespa.state.helpers;

import static com.vispana.vespa.state.helpers.Request.requestGetWithDefaultValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight filesystem browser for Vespa application package at /content/ This class builds a
 * tree structure WITHOUT loading file contents to avoid OOM. File contents are fetched on-demand
 * via separate API calls.
 */
public class AppPackageFilesystem {

  private static final Logger logger = LoggerFactory.getLogger(AppPackageFilesystem.class);

  // Lightweight filesystem node - NO content stored here
  public static class FileNode {
    private final String name;
    private final String path;
    private final boolean isFile;
    private final Map<String, FileNode> children;

    @JsonCreator
    public FileNode(
        @JsonProperty("name") String name,
        @JsonProperty("path") String path,
        @JsonProperty("isFile") boolean isFile,
        @JsonProperty("children") Map<String, FileNode> children) {
      this.name = name;
      this.path = path;
      this.isFile = isFile;
      this.children = children != null ? children : new HashMap<>();
    }

    @JsonProperty("name")
    public String getName() {
      return name;
    }

    @JsonProperty("path")
    public String getPath() {
      return path;
    }

    @JsonProperty("isFile")
    public boolean isFile() {
      return isFile;
    }

    @JsonProperty("children")
    public Map<String, FileNode> getChildren() {
      return children;
    }

    public void addChild(String name, FileNode node) {
      children.put(name, node);
    }
  }

  // Root filesystem structure
  public static class FileTree {
    private final FileNode root;
    private final int totalFiles;
    private final int totalDirectories;

    @JsonCreator
    public FileTree(
        @JsonProperty("root") FileNode root,
        @JsonProperty("totalFiles") int totalFiles,
        @JsonProperty("totalDirectories") int totalDirectories) {
      this.root = root;
      this.totalFiles = totalFiles;
      this.totalDirectories = totalDirectories;
    }

    public FileNode getRoot() {
      return root;
    }

    public int getTotalFiles() {
      return totalFiles;
    }

    public int getTotalDirectories() {
      return totalDirectories;
    }
  }

  /**
   * Recursively builds the file tree structure by listing directories. Does NOT download file
   * contents - only builds the tree structure.
   */
  public static FileTree buildFileTree(String configHost) {
    logger.info("Building file tree from configHost: {}", configHost);
    String appUrl = ApplicationUrlFetcher.fetch(configHost);

    String contentUrl = appUrl + "/content/";
    logger.info("Content URL: {}", contentUrl);

    Map<String, FileNode> rootChildren = new HashMap<>();
    int[] counters = new int[2]; // [files, directories]

    try {
      // Start recursive traversal
      traverseDirectory(contentUrl, contentUrl, rootChildren, counters);

      FileNode root = new FileNode("content", "/", false, rootChildren);
      logger.info(
          "Successfully built file tree: {} files, {} directories", counters[0], counters[1]);

      return new FileTree(root, counters[0], counters[1]);

    } catch (Exception e) {
      logger.error("Error building file tree from: {}", contentUrl, e);
      return new FileTree(new FileNode("content", "/", false, new HashMap<>()), 0, 0);
    }
  }

  /**
   * Recursively traverses directories and builds the tree. Note: This makes HTTP calls for each
   * directory, but doesn't download file contents. Edge case: If there are thousands of
   * directories, this could still be slow but won't OOM.
   */
  private static void traverseDirectory(
      String currentUrl, String baseUrl, Map<String, FileNode> parentChildren, int[] counters) {

    // Get list of entries in current directory
    logger.debug("Fetching directory listing from: {}", currentUrl);
    List<String> entries = requestGetWithDefaultValue(currentUrl, List.class, List.of());
    logger.debug("Found {} entries at {}", entries.size(), currentUrl);

    for (String entry : entries) {
      // Vespa returns either full URLs or relative paths
      String resolvedUrl = resolveUrl(currentUrl, entry);
      boolean isDirectory = entry.endsWith("/");

      // Extract the name (last segment of path)
      String name = extractName(entry);

      // Calculate relative path from base
      String relativePath = resolvedUrl.substring(baseUrl.length());

      logger.debug(
          "Entry: '{}' | isDirectory: {} | name: '{}' | relativePath: '{}'",
          entry,
          isDirectory,
          name,
          relativePath);

      if (isDirectory) {
        // Create directory node
        Map<String, FileNode> children = new HashMap<>();
        FileNode dirNode = new FileNode(name, relativePath, false, children);
        parentChildren.put(name, dirNode);
        counters[1]++; // increment directory count

        logger.debug("Created DIRECTORY node: {}", name);

        // Recursively traverse subdirectory
        traverseDirectory(resolvedUrl, baseUrl, children, counters);

      } else {
        // Create file node (no content)
        FileNode fileNode = new FileNode(name, relativePath, true, null);
        parentChildren.put(name, fileNode);
        counters[0]++; // increment file count

        logger.debug("Created FILE node: {}", name);
      }
    }
  }

  /** Resolves relative URLs to absolute URLs */
  private static String resolveUrl(String currentUrl, String entry) {
    if (entry.startsWith("http://") || entry.startsWith("https://")) {
      return entry;
    }
    if (currentUrl.endsWith("/")) {
      return currentUrl + entry;
    }
    return currentUrl + "/" + entry;
  }

  /** Extracts filename/directory name from path or URL */
  private static String extractName(String pathOrUrl) {
    String cleaned = pathOrUrl;
    // Remove trailing slash for extraction
    if (cleaned.endsWith("/")) {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    int lastSlash = cleaned.lastIndexOf('/');
    return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
  }

  /**
   * Fetches the content of a single file. This is called on-demand when user clicks on a file in
   * the UI.
   */
  public static HashMap<String, String> getFileContent(String configHost, String relativePath) {
    String appUrl = ApplicationUrlFetcher.fetch(configHost);
    String fileUrl = appUrl + "/content/" + relativePath;
    logger.info("Fetching file content from: {}", fileUrl);

    try {
      // Try to get as String (for text files)
      String content = requestGetWithDefaultValue(fileUrl, String.class, "");
      if (content.isEmpty()) {
        logger.warn("File content is empty or unreadable: {}", fileUrl);
        return new HashMap<String, String>() {
          {
            put("url", fileUrl);
            put("content", "// Empty file or could not read as text");
          }
        };
      }
      logger.info("Successfully fetched file content ({} chars)", content.length());
      return new HashMap<String, String>() {
        {
          put("url", fileUrl);
          put("content", content);
        }
      };

    } catch (Exception e) {
      logger.error("Error fetching file content from: {}", fileUrl, e);
      return new HashMap<String, String>() {
        {
          put("url", fileUrl);
          put("content", "// Error reading file: " + e.getMessage());
        }
      };
    }
  }
}
