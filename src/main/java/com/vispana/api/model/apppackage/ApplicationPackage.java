package com.vispana.api.model.apppackage;

import java.util.Map;

// Application package
public record ApplicationPackage(
    String appPackageGeneration,
    String servicesContent,
    String hostsContent,
    Map<String, String> queryProfilesContent,
    Map<String, String> queryProfileTypesContent) {}
