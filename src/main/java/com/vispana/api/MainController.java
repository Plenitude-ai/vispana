package com.vispana.api;

import com.vispana.api.model.VispanaRoot;
import com.vispana.vespa.query.VespaQueryClient;
import com.vispana.vespa.state.VespaStateClient;
import com.vispana.vespa.state.helpers.AppPackageFilesystem;
import com.vispana.vespa.state.helpers.VespaAppPackageFetcher;
import java.util.HashMap;
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

@RestController
public class MainController {

  private final VespaStateClient vespaStateClient;
  private final VespaQueryClient vespaQueryClient;
  private final VespaAppPackageFetcher appPackageFetcher;

  @Autowired
  public MainController(
      VespaStateClient vespaStateClient,
      VespaQueryClient vespaQueryClient,
      VespaAppPackageFetcher appPackageFetcher) {
    this.vespaStateClient = vespaStateClient;
    this.vespaQueryClient = vespaQueryClient;
    this.appPackageFetcher = new VespaAppPackageFetcher();
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

    HashMap<String, String> file_Url_Content =
        AppPackageFilesystem.getFileContent(configHost, filePath);
    return file_Url_Content;
  }

  /**
   * Downloads the entire application package as a ZIP archive. The ZIP is streamed to avoid holding
   * everything in memory.
   */
  @GetMapping(value = "/api/apppackage/download")
  public ResponseEntity<byte[]> downloadAppPackage(
      @RequestParam(name = "config_host") String configHost) {
    try {
      byte[] zipBytes = appPackageFetcher.buildAppPackageBinary(configHost);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      headers.setContentDispositionFormData("attachment", "vespa-app-package.zip");
      headers.setContentLength(zipBytes.length);

      return ResponseEntity.ok().headers(headers).body(zipBytes);

    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }
}
