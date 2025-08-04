package com.vispana.vespa.state.assemblers;

import static com.vispana.vespa.state.helpers.Request.requestGet;
import static com.vispana.vespa.state.helpers.Request.requestGetWithDefaultValue;

import com.vispana.api.model.apppackage.ApplicationPackage;
import com.vispana.client.vespa.model.ApplicationSchema;
import java.util.List;

public class AppPackageAssembler {

  public static ApplicationPackage assemble(String appUrl) {
    var appSchema = requestGet(appUrl, ApplicationSchema.class);
    var hostContent = requestGetWithDefaultValue(appUrl + "/content/hosts.xml", String.class, "");
    var servicesContent = requestGet(appUrl + "/content/services.xml", String.class);

    List<String> modelsContent =
        requestGetWithDefaultValue(appUrl + "/content/models/", List.class, List.of());

    // Parse JSON array and extract model names
    if (modelsContent.isEmpty()) {
      modelsContent = List.of();
    } else {
      try {
        modelsContent =
            modelsContent.stream()
                .map(
                    fullUrl -> {
                      // Subtract filename from URL
                      int lastSlashIndex = fullUrl.lastIndexOf('/');
                      return lastSlashIndex >= 0 ? fullUrl.substring(lastSlashIndex + 1) : fullUrl;
                    })
                .toList();
      } catch (Exception e) {
        // Handle JSON parsing error - return empty list or throw exception
        modelsContent = List.of("Error parsing models");
      }
    }

    // http://localhost:19071/application/v2/tenant/default/session/23
    List<String> queryProfilesContent =
        requestGetWithDefaultValue(
            appUrl + "/content/search/query-profiles/", List.class, List.of());
    if (queryProfilesContent.isEmpty()) {
      queryProfilesContent = List.of();
    } else {
      try {
        queryProfilesContent =
            queryProfilesContent.stream()
                .map(
                    fullUrl -> {
                      // Subtract filename from URL
                      int lastSlashIndex = fullUrl.lastIndexOf('/');
                      return lastSlashIndex >= 0 ? fullUrl.substring(lastSlashIndex + 1) : fullUrl;
                    })
                .filter(name -> name.endsWith(".xml"))
                .toList();
      } catch (Exception e) {
        queryProfilesContent = List.of("Error parsing query profiles");
      }
    }

    List<String> queryProfileTypesContent =
        requestGetWithDefaultValue(
            appUrl + "/content/search/query-profiles/types/", List.class, List.of());
    if (queryProfileTypesContent.isEmpty()) {
      queryProfileTypesContent = List.of();
    } else {
      try {
        queryProfileTypesContent =
            queryProfileTypesContent.stream()
                .map(
                    fullUrl -> {
                      // Subtract filename from URL
                      int lastSlashIndex = fullUrl.lastIndexOf('/');
                      return lastSlashIndex >= 0 ? fullUrl.substring(lastSlashIndex + 1) : fullUrl;
                    })
                .filter(name -> name.endsWith(".xml"))
                .toList();
      } catch (Exception e) {
        queryProfileTypesContent = List.of("Error parsing query profile types");
      }
    }

    return new ApplicationPackage(
        appSchema.getGeneration().toString(),
        servicesContent,
        hostContent,
        modelsContent,
        queryProfilesContent,
        queryProfileTypesContent);
  }
}
