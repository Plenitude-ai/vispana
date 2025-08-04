package com.vispana.api.model.apppackage;

import java.util.List;

// Application package
public record ApplicationPackage(
    String appPackageGeneration,
    String servicesContent,
    String hostsContent,
    String modelsContent,
    List<String> queryProfilesContent) {}
