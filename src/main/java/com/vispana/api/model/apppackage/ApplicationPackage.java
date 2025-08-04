package com.vispana.api.model.apppackage;

import java.util.List;

// Application package
public record ApplicationPackage(
    String appPackageGeneration,
    String servicesContent,
    String hostsContent,
    List<String> modelsContent,
    List<String> queryProfilesContent,
    List<String> queryProfileTypesContent) {}
