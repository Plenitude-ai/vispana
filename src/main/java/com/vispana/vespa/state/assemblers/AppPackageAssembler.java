package com.vispana.vespa.state.assemblers;

import static com.vispana.vespa.state.helpers.Request.requestGet;
import static com.vispana.vespa.state.helpers.Request.requestGetWithDefaultValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vispana.api.model.apppackage.ApplicationPackage;
import com.vispana.client.vespa.model.ApplicationSchema;
import java.util.List;
import java.util.stream.Collectors;

public class AppPackageAssembler {

  public static ApplicationPackage assemble(String appUrl) {
    var appSchema = requestGet(appUrl, ApplicationSchema.class);
    var hostContent = requestGetWithDefaultValue(appUrl + "/content/hosts.xml", String.class, "");
    var servicesContent = requestGet(appUrl + "/content/services.xml", String.class);
    var modelsContentRaw =
        requestGetWithDefaultValue(appUrl + "/content/models/", String.class, "");

    // Parse JSON array and extract model names
    String modelsContent;
    if (modelsContentRaw.isEmpty()) {
      modelsContent = "";
    } else {
      try {
        ObjectMapper mapper = new ObjectMapper();
        List<String> fullUrls =
            mapper.readValue(modelsContentRaw, new TypeReference<List<String>>() {});

        modelsContent =
            fullUrls.stream()
                .map(
                    fullUrl -> {
                      // Subtract filename from URL
                      int lastSlashIndex = fullUrl.lastIndexOf('/');
                      return lastSlashIndex >= 0 ? fullUrl.substring(lastSlashIndex + 1) : fullUrl;
                    })
                .collect(Collectors.joining("\n"));
      } catch (Exception e) {
        // Handle JSON parsing error - return empty list or throw exception
        modelsContent = "Error parsing models";
      }
    }
    return new ApplicationPackage(
        appSchema.getGeneration().toString(), servicesContent, hostContent, modelsContent);
  }
}
