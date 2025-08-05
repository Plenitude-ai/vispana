package com.vispana.api.model.apppackage;

import java.util.List;
import java.util.Map;
import com.vispana.vespa.state.helpers.JavaComponentsFilesystem;

// Application package
public record ApplicationPackage(
        String appPackageGeneration,
        String servicesContent,
        String hostsContent,
        List<String> modelsContent,
        Map<String, String> queryProfilesContent,
        Map<String, String> queryProfileTypesContent,
        JavaComponentsFilesystem.Filesystem javaComponentsContent) {
}
