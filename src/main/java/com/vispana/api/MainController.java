package com.vispana.api;

import com.vispana.api.model.VispanaRoot;
import com.vispana.vespa.query.VespaQueryClient;
import com.vispana.vespa.state.VespaStateClient;
import com.vispana.vespa.state.helpers.AppPackageFetcher;
import com.vispana.vespa.state.helpers.AppPackageFilesystem;
import com.vispana.vespa.state.helpers.ApplicationUrlFetcher;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class MainController {

  private final VespaStateClient vespaStateClient;
  private final VespaQueryClient vespaQueryClient;
  private final AppPackageFetcher appPackageFetcher;

  @Autowired
  public MainController(
      VespaStateClient vespaStateClient,
      VespaQueryClient vespaQueryClient,
      AppPackageFetcher appPackageFetcher) {
    this.vespaStateClient = vespaStateClient;
    this.vespaQueryClient = vespaQueryClient;
    this.appPackageFetcher = new AppPackageFetcher();
  }

  @GetMapping(
      value = "/api/overview",
      produces = {"application/json"})
  @ResponseBody
  public VispanaRoot root(@RequestParam(name = "config_host") String configHost) {
    return vespaStateClient.vespaState(configHost);
  }

  @PostMapping(
      value = "/api/query",
      produces = {"application/json"})
  @ResponseBody
  public String query(
      @RequestParam(name = "container_host") String containerHost, @RequestBody String query) {
    return vespaQueryClient.query(containerHost, query);
  }

  /**
   * Returns the file tree structure for the application package. This endpoint only returns
   * metadata (paths, names) - no file content.
   */
  @GetMapping(
      value = "/api/apppackage/tree",
      produces = {"application/json"})
  @ResponseBody
  public AppPackageFilesystem.FileTree getAppPackageTree(
      @RequestParam(name = "config_host") String configHost) {

    AppPackageFilesystem.FileTree tree = AppPackageFilesystem.buildFileTree(configHost);

    return tree;
  }

  /**
   * Returns (file_url,file_content) pair for a single file from the application package. Called
   * on-demand when user clicks on a file in the UI.
   */
  @GetMapping(
      value = "/api/apppackage/file",
      produces = {"application/json"})
  @ResponseBody
  public HashMap<String, String> getAppPackageFile(
      @RequestParam(name = "config_host") String configHost,
      @RequestParam(name = "file_path") String filePath) {

    HashMap<String, String> file_Url_Content = new HashMap<>();

    // Build file full URL
    String appUrl = ApplicationUrlFetcher.fetch(configHost);
    String fileUrl = appUrl + "/content/" + filePath;
    file_Url_Content.put("url", fileUrl);

    // If file is NON human-readable, return placeholder
    List<String> nonReadableFormats = Arrays.asList("jar", "zip", "class");
    String fileExtension = getLastElement(filePath, "\\.");
    if (nonReadableFormats.contains(fileExtension) || filePath.contains("models/")) {
      file_Url_Content.put("content", "// Binary file: Not displayable as text");
      return file_Url_Content;
    }

    // Get file content
    String content = AppPackageFilesystem.getFileContent(fileUrl);
    file_Url_Content.put("content", content);

    return file_Url_Content;
  }

  /**
   * Downloads the entire application package as a ZIP archive. Uses streaming to handle large
   * packages (up to several GB) without OOM errors.
   */
  @GetMapping(value = "/api/apppackage/download")
  public ResponseEntity<StreamingResponseBody> downloadAppPackage(
      @RequestParam(name = "config_host") String configHost) {

    StreamingResponseBody stream =
        outputStream -> {
          try {
            appPackageFetcher.streamAppPackageAsZip(configHost, outputStream);
          } catch (Exception e) {
            throw new RuntimeException("Failed to stream app package", e);
          }
        };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "vespa-app-package.zip");
    // Content-Length is not set - using chunked transfer encoding for streaming

    return ResponseEntity.ok().headers(headers).body(stream);
  }

  private String getLastElement(String string, String delimiter) {
    String[] result = string.split(delimiter);
    if (result.length > 0) {
      return result[result.length - 1];
    }
    return "";
  }
}
